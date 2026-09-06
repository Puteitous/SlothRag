/**
 * ragbase 后端 API 客户端
 */
import { getJson } from './http';
import { streamSse } from './sse';
import type { KbItem } from '@/types';
import type { ChatSseEventName } from '@/types/sse';

/** 统一响应体(ragbase Result 包装) */
interface ApiResult<T> {
  code: string;
  message: string;
  data: T;
}

/** 知识库管理 */
export const kbApi = {
  /** 知识库列表(含文档数) */
  list: async (): Promise<KbItem[]> => {
    const res = await getJson<ApiResult<KbItem[]>>('/api/kb');
    if (res.code !== '0') throw new Error(res.message);
    return res.data;
  },
};

/** 流式问答 */
export const chatApi = {
  /**
   * 发起流式问答(GET /api/chat)。
   *
   * @param question 问题
   * @param kbId 知识库 id(必填)
   * @param conversationId 已有会话 id;为空时后端新建会话(经 session 事件返回)
   */
  stream: (
    question: string,
    kbId: number,
    conversationId: string | null,
    onEvent: (event: { event: ChatSseEventName; data: unknown }) => void,
    signal?: AbortSignal,
  ): Promise<void> => {
    const params = new URLSearchParams({ question, kbId: String(kbId) });
    if (conversationId) params.set('conversationId', conversationId);
    return streamSse(`/api/chat?${params.toString()}`, onEvent, signal);
  },
};
