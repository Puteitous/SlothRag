/**
 * MessageBubble - 单条消息气泡(slothrag 简化版)
 *
 * 渲染规则:
 *  - role === 'user':右对齐,纯文本,长内容可折叠
 *  - role === 'assistant':左对齐,Markdown 渲染,末尾带来源引用折叠区 + 反馈按钮
 *  - isStreaming === true:流式态,末尾带闪烁光标
 *
 * 裁剪说明:从 HippoBuddy 版搬入,删除了 tool 分支 / reasoning 折叠 /
 * 联网搜索行 / 文件产物指示器 / 重试分叉回滚按钮(slothrag 无工具链)。
 */
import { memo, ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import type { Message, SourceItem, FeedbackType } from '@/types';
import { renderMarkdown } from '@/utils/markdown';
import { useI18n } from '@/i18n';
import { useChatStore } from '@/stores/chatStore';
import { feedbackApi } from '@/api/client';
import './MessageBubble.css';

interface MessageBubbleProps {
  message: Message;
  /** 是否为流式态(末尾显示闪烁光标) */
  isStreaming?: boolean;
  /** 上一条用户提问(用于反馈时记录) */
  prevQuestion?: string;
}

/** 用户消息长内容折叠阈值(px,超过自动折叠) */
const COLLAPSE_THRESHOLD = 200;

function MessageBubbleComponent({ message, isStreaming = false, prevQuestion }: MessageBubbleProps) {
  const conversationId = useChatStore((s) => s.conversationId);

  // 助手消息的 HTML(Markdown 渲染 + DOMPurify 净化)
  const html = useMemo(
    () => (message.content ? renderMarkdown(message.content) : ''),
    [message.content],
  );

  if (message.role === 'user') {
    return (
      <div className="msg-user-wrap">
        <div className="msg-bubble msg-bubble-user">
          <UserContent content={message.content} />
          {isStreaming && <span className="msg-cursor" aria-hidden />}
        </div>
        <MessageFooter
          time={formatMsgTime(message.timestamp)}
          onCopy={() => copyText(message.content)}
        />
      </div>
    );
  }

  // assistant:完全无内容时不渲染气泡,避免流式空段出现空气泡
  if (!html) return null;

  return (
    <div className="msg-bubble msg-bubble-assistant">
      <div className="msg-markdown" dangerouslySetInnerHTML={{ __html: html }} />
      {isStreaming && <span className="msg-cursor" aria-hidden />}
      {!isStreaming && message.sources && message.sources.length > 0 && (
        <SourcesCollapse sources={message.sources} />
      )}
      <MessageFooter
        onCopy={() => copyText(message.content)}
        extraActions={!isStreaming && conversationId ? (
          <FeedbackButtons
            sessionId={conversationId}
            messageId={message.id}
            question={prevQuestion}
            answer={message.content}
          />
        ) : undefined}
      />
    </div>
  );
}

/** 来源引用折叠区(slothrag 特有:sources SSE 事件) */
function SourcesCollapse({ sources }: { sources: SourceItem[] }) {
  const { t } = useI18n();
  const [expanded, setExpanded] = useState(false);

  return (
    <div className={`msg-sources${expanded ? ' expanded' : ''}`}>
      <div
        className="msg-sources-header"
        role="button"
        tabIndex={0}
        aria-expanded={expanded}
        onClick={() => setExpanded((v) => !v)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            setExpanded((v) => !v);
          }
        }}
      >
        <span className="msg-sources-label">
          {t('chat.sourcesTitle', { count: sources.length })}
        </span>
        <span className="msg-sources-chevron">{CHEVRON_SVG}</span>
      </div>
      {expanded && (
        <div className="msg-sources-list">
          {sources.map((s, i) => (
            <div key={i} className="msg-source-item">
              <span className="msg-source-index">{i + 1}</span>
              <div className="msg-source-body">
                <span className="msg-source-text">{s.source}</span>
                {(s.headingPath || s.fileName) && (
                  <span className="msg-source-meta">
                    {s.fileName && <span className="msg-source-file">{s.fileName}</span>}
                    {s.headingPath && <span className="msg-source-path">{s.headingPath}</span>}
                  </span>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

const CHEVRON_SVG = (
  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="m9 18 6-6-6-6" />
  </svg>
);

/** 用户消息内容(纯文本),支持长内容折叠 */
function UserContent({ content }: { content: string }) {
  const { t } = useI18n();
  const [expanded, setExpanded] = useState(false);
  const contentRef = useRef<HTMLDivElement>(null);
  const [collapsible, setCollapsible] = useState(false);

  useEffect(() => {
    if (contentRef.current) {
      setCollapsible(contentRef.current.scrollHeight > COLLAPSE_THRESHOLD);
    }
  }, [content]);

  return (
    <>
      <div
        ref={contentRef}
        className={`msg-user-collapsible${collapsible ? ' collapsible' : ''}${expanded ? ' expanded' : ''}`}
      >
        <div className="msg-user-text">{content}</div>
      </div>
      {collapsible && (
        <button
          type="button"
          className={`msg-user-expand-btn${expanded ? ' open' : ''}`}
          onClick={() => setExpanded((v) => !v)}
          aria-label={expanded ? t('chat.collapseContent') : t('chat.expandFullText')}
          title={expanded ? t('chat.collapseContent') : t('chat.expandFullText')}
        >
          {expanded ? (
            <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <path d="M3.5 10L8 5.5 12.5 10" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
            </svg>
          ) : (
            <svg width="14" height="14" viewBox="0 0 16 16" fill="currentColor" aria-hidden="true">
              <circle cx="3.5" cy="8" r="1.6" />
              <circle cx="8" cy="8" r="1.6" />
              <circle cx="12.5" cy="8" r="1.6" />
            </svg>
          )}
        </button>
      )}
    </>
  );
}

/* ============================================================
   消息底部操作条:时间 + 复制按钮
   ============================================================ */

const COPY_SVG = (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
    <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1" />
  </svg>
);

const CHECK_SVG = (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <polyline points="20 6 9 17 4 12" />
  </svg>
);

function copyText(text: string): void {
  if (!text) return;
  navigator.clipboard?.writeText(text).catch(() => {});
}

function formatMsgTime(timestamp?: number): string {
  const t = timestamp && Number.isFinite(timestamp) ? timestamp : Date.now();
  return new Date(t).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  });
}

function MessageFooter({ time, onCopy, extraActions }: {
  time?: string;
  onCopy: () => void;
  extraActions?: ReactNode;
}) {
  const { t } = useI18n();
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    onCopy();
    setCopied(true);
    window.setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="message-footer">
      <div className="message-actions">
        <button
          type="button"
          className={`message-action-btn${copied ? ' copied' : ''}`}
          title={copied ? t('chatui.copied') : t('chatui.copy')}
          aria-label={copied ? t('chatui.copied') : t('chatui.copy')}
          onClick={handleCopy}
        >
          {copied ? CHECK_SVG : COPY_SVG}
        </button>
        {extraActions}
      </div>
      {time && <span className="message-time">{time}</span>}
    </div>
  );
}

/* ============================================================
   消息反馈按钮组（👍👎）
   ============================================================ */

const THUMBS_UP_SVG = (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M7 10v12" /><path d="M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z" />
  </svg>
);

const THUMBS_DOWN_SVG = (
  <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M17 14V2" /><path d="M9 18.12 10 14H4.17a2 2 0 0 1-1.92-2.56l2.33-8A2 2 0 0 1 6.5 2H20a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.76a2 2 0 0 0-1.79 1.11L12 22a3.13 3.13 0 0 1-3-3.88Z" />
  </svg>
);

function FeedbackButtons({ sessionId, messageId, question, answer }: {
  sessionId: string;
  messageId: string;
  question?: string;
  answer: string;
}) {
  const { t } = useI18n();
  const [current, setCurrent] = useState<FeedbackType | null>(null);
  const [mounted, setMounted] = useState(false);

  // 挂载时查询已有反馈（历史消息回显）
  useEffect(() => {
    setMounted(true);
    feedbackApi.get(sessionId, messageId).then((rec) => {
      if (rec) setCurrent(rec.feedback);
    }).catch(() => { /* 静默失败，不回显即可 */ });
  }, [sessionId, messageId]);

  const handleFeedback = (type: FeedbackType) => {
    // 点击已选中的按钮 = 取消反馈
    const newValue = current === type ? null : type;
    setCurrent(newValue);

    if (newValue) {
      feedbackApi.submit({ sessionId, messageId, feedback: newValue, question, answer }).catch(() => {
        // 失败时回退
        setCurrent(current);
      });
    }
    // 取消时无需调后端（用 upsert 覆盖为无意义值？暂不处理）
  };

  if (!mounted) return null;

  return (
    <>
      <button
        type="button"
        className={`msg-feedback-btn${current === 'thumbs_up' ? ' active' : ''}`}
        onClick={() => handleFeedback('thumbs_up')}
        title={current === 'thumbs_up' ? t('chat.feedback.retract') : t('chat.feedback.helpful')}
        aria-label={current === 'thumbs_up' ? t('chat.feedback.retract') : t('chat.feedback.helpful')}
      >
        {THUMBS_UP_SVG}
      </button>
      <button
        type="button"
        className={`msg-feedback-btn${current === 'thumbs_down' ? ' active' : ''}`}
        onClick={() => handleFeedback('thumbs_down')}
        title={current === 'thumbs_down' ? t('chat.feedback.retract') : t('chat.feedback.notHelpful')}
        aria-label={current === 'thumbs_down' ? t('chat.feedback.retract') : t('chat.feedback.notHelpful')}
      >
        {THUMBS_DOWN_SVG}
      </button>
    </>
  );
}

export const MessageBubble = memo(MessageBubbleComponent);
