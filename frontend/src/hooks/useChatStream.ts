/**
 * useChatStream - 流式对话 Hook
 *
 * 发送/中断的真实逻辑收敛在 chatStore,本 Hook 仅暴露稳定接口,
 * 并负责从 appStore 取当前知识库 id(问答接口必填)。
 */
import { useCallback } from 'react';
import { useChatStore } from '@/stores/chatStore';
import { useAppStore } from '@/stores/appStore';

export interface UseChatStreamResult {
  /** 发送消息(启动 SSE 流)。无可用知识库时返回 false。 */
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
      const kbId = useAppStore.getState().currentKbId;
      if (kbId == null) return false;
      void sendMessage(message, kbId);
      return true;
    },
    [sendMessage],
  );

  return { send, abort, isSending };
}
