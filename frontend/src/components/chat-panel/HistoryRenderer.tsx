/**
 * HistoryRenderer - 消息列表(slothrag 简化版)
 *
 * 从 HippoBuddy 版搬入并大幅裁剪:无回合分组 / 工具时间线 / todo 树 /
 * 摘要条,就是纯消息列表(用户 → 助手交替,流式助手消息带光标)。
 */
import { MessageBubble } from './MessageBubble';
import { useSessionStream } from '@/hooks/useSessionStream';
import './HistoryRenderer.css';

export function HistoryRenderer() {
  const { messages } = useSessionStream();

  return (
    <div className="history-list">
      {messages.map((m, idx) => (
        <MessageBubble
          key={m.id}
          message={m}
          isStreaming={m.isStreaming}
          // 为 assistant 消息传递上一条用户提问
          prevQuestion={
            m.role === 'assistant' && idx > 0 && messages[idx - 1].role === 'user'
              ? messages[idx - 1].content
              : undefined
          }
        />
      ))}
    </div>
  );
}
