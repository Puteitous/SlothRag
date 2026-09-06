/**
 * 聊天状态(Zustand)
 *
 * ragbase 简化版:单会话(无会话分区)。
 * 数据流:
 *   send → 乐观追加 user + 空 assistant(streaming)
 *        → GET /api/chat SSE 解析
 *        → session 事件记录 conversationId(多轮续接)
 *        → delta 事件追加到 streaming assistant 消息
 *        → sources 事件挂到该消息
 *        → 流结束(正常/中断/错误)后置 isStreaming=false 固化
 */
import { create } from 'zustand';
import { chatApi } from '@/api/client';
import type { Message, SourceItem } from '@/types';

interface ChatState {
  /** 已固化 + 乐观追加的消息(最后一条 assistant 可能处于流式态) */
  messages: Message[];
  /** 当前会话 id(后端 session 事件分配,多轮对话续接用) */
  conversationId: string | null;
  /** 是否正在流式回答 */
  isSending: boolean;
  /** 错误信息(null=无) */
  error: string | null;
  /** 发送问题(基于指定知识库) */
  send: (question: string, kbId: number) => Promise<void>;
  /** 中断当前流式回答(保留已生成内容) */
  abort: () => void;
  /** 清空当前会话(新建对话) */
  reset: () => void;
}

let messageSeq = 0;
const nextId = (prefix: string) => `${prefix}-${Date.now()}-${messageSeq++}`;

/** 模块级活跃请求控制器:中断/发送共用同一通道 */
let activeController: AbortController | null = null;

export const useChatStore = create<ChatState>((set, get) => ({
  messages: [],
  conversationId: null,
  isSending: false,
  error: null,

  send: async (question, kbId) => {
    if (get().isSending) return;
    const trimmed = question.trim();
    if (!trimmed) return;

    const streamingId = nextId('assistant');
    const userMsg: Message = {
      id: nextId('user'),
      role: 'user',
      content: trimmed,
      timestamp: Date.now(),
    };
    const assistantMsg: Message = {
      id: streamingId,
      role: 'assistant',
      content: '',
      isStreaming: true,
      timestamp: Date.now(),
    };
    set((s) => ({
      messages: [...s.messages, userMsg, assistantMsg],
      isSending: true,
      error: null,
    }));

    const controller = new AbortController();
    activeController = controller;

    try {
      await chatApi.stream(trimmed, kbId, get().conversationId, (evt) => {
        switch (evt.event) {
          case 'session':
            set({ conversationId: evt.data as string });
            break;
          case 'delta':
            set((s) => ({
              messages: s.messages.map((m) =>
                m.id === streamingId
                  ? { ...m, content: m.content + (evt.data as string) }
                  : m,
              ),
            }));
            break;
          case 'sources':
            set((s) => ({
              messages: s.messages.map((m) =>
                m.id === streamingId
                  ? { ...m, sources: evt.data as SourceItem[] }
                  : m,
              ),
            }));
            break;
          case 'error':
            set({ error: evt.data as string });
            break;
        }
      }, controller.signal);
    } catch (err) {
      // AbortError = 用户主动中断,保留已生成内容;其余视为流错误
      if ((err as Error).name !== 'AbortError') {
        set({ error: (err as Error).message });
      }
    } finally {
      if (activeController === controller) activeController = null;
      set((s) => ({
        messages: s.messages.map((m) =>
          m.id === streamingId ? { ...m, isStreaming: false } : m,
        ),
        isSending: false,
      }));
    }
  },

  abort: () => {
    activeController?.abort();
  },

  reset: () => {
    activeController?.abort();
    activeController = null;
    set({ messages: [], conversationId: null, isSending: false, error: null });
  },
}));
