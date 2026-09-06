/**
 * ragbase Chat SSE 事件类型定义
 *
 * 事件来源:后端 com.ragbase.chat.service.ChatService(通过 SseEmitter 发送)。
 * 共 4 种事件:
 *   session → 会话 id(字符串,多轮对话凭此续接)
 *   delta   → 回答增量(字符串,流式追加)
 *   sources → 来源引用(JSON 数组 [{source: "..."}],回答结束后发送)
 *   error   → 错误信息(字符串)
 */

export type ChatSseEventName = 'session' | 'delta' | 'sources' | 'error';

export interface ChatSseEventMap {
  session: string;
  delta: string;
  sources: { source: string }[];
  error: string;
}
