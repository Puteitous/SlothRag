/**
 * useSessionStream - 读取聊天流式状态
 *
 * slothrag 简化版:单会话,直接订阅 chatStore 的渲染字段。
 */
import { useChatStore } from '@/stores/chatStore';

export function useSessionStream() {
  const messages = useChatStore((s) => s.messages);
  const isSending = useChatStore((s) => s.isSending);
  const error = useChatStore((s) => s.error);
  return { messages, isSending, error };
}
