/**
 * useChatStream - 流式对话 Hook
 *
 * 发送/中断的真实逻辑收敛在 chatStore,本 Hook 仅暴露稳定接口,
 * 并负责从 appStore 取当前知识库 id(kbId 为 null 时走纯 LLM 对话)。
 */
import { useCallback } from 'react';
import { useChatStore } from '@/stores/chatStore';
import { useAppStore } from '@/stores/appStore';

export interface UseChatStreamResult {
  /** 发送消息(启动 SSE 流)。返回 true 表示已发起请求。 */
  send: (message: string) => boolean;
  /** 中断当前流式请求 */
  abort: () => void;
  /** 是否正在发送 */
  isSending: boolean;
}

export function useChatStream(): UseChatStreamResult {
  const sendMessage = useChatStore((s) => s.send);
  const abort = useChatStore((s) => s.abort);
  const isSending = useChatStore((s) => s.isSending);

  const send = useCallback(
    (message: string) => {
      const kbId = useAppStore.getState().currentKbId; // 可为 null，走纯 LLM
      void sendMessage(message, kbId);
      return true;
    },
    [sendMessage],
  );

  return { send, abort, isSending };
}
