/**
 * ChatPanel - 聊天面板(ragbase 版)
 *
 * 从 HippoBuddy 版搬入并裁剪:顶部栏(知识库选择 + 新建会话)、
 * 消息列表(流式 tail 与固化消息同 key 复用 DOM)、自动滚动/回底提示、
 * 输入区(行内输入框 + 发送/停止按钮)。砍掉工具时间线 / Token 监控 /
 * 文件变更 / 图片上传 / 模式预设等桌面 Agent 专属能力。
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useAppStore } from '@/stores/appStore';
import { useChatStore } from '@/stores/chatStore';
import { useChatStream } from '@/hooks/useChatStream';
import { useSessionStream } from '@/hooks/useSessionStream';
import { useI18n } from '@/i18n';
import { HistoryRenderer } from './HistoryRenderer';
import { ChatEmptyHero } from './ChatEmptyHero';
import InlineInput from './InlineInput';
import type { InlineInputHandle } from './InlineInput';
import './ChatPanel.css';

export function ChatPanel() {
  const { t } = useI18n();
  const kbs = useAppStore((s) => s.kbs);
  const currentKbId = useAppStore((s) => s.currentKbId);
  const loadKbs = useAppStore((s) => s.loadKbs);
  const setCurrentKbId = useAppStore((s) => s.setCurrentKbId);
  const { messages, isSending, error } = useSessionStream();
  const reset = useChatStore((s) => s.reset);
  const { send, abort } = useChatStream();

  // 行内输入框引用与发送按钮禁用态
  const inlineInputRef = useRef<InlineInputHandle | null>(null);
  const [hasInputContent, setHasInputContent] = useState(false);

  // 挂载时加载知识库列表
  useEffect(() => {
    void loadKbs();
  }, [loadKbs]);

  // ── 自动滚动 ──────────────────────────────────────────────
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const messagesContainerRef = useRef<HTMLDivElement | null>(null);
  const stickToBottomRef = useRef(true);
  const [showScrollHint, setShowScrollHint] = useState(false);

  const scrollToBottom = useCallback((behavior: 'smooth' | 'auto' = 'smooth') => {
    messagesEndRef.current?.scrollIntoView({ behavior, block: 'end' });
  }, []);

  // 消息列表变化时滚到底部(若未被用户上滚打断)
  useEffect(() => {
    if (!stickToBottomRef.current) return;
    scrollToBottom('auto');
  }, [messages, scrollToBottom]);

  // 监听滚动:离开底部 ≥100px 时显示回底提示
  const handleScroll = useCallback(() => {
    const el = messagesContainerRef.current;
    if (!el) return;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    stickToBottomRef.current = distanceFromBottom < 80;
    setShowScrollHint(distanceFromBottom >= 100);
  }, []);

  const handleScrollToBottom = useCallback(() => {
    stickToBottomRef.current = true;
    setShowScrollHint(false);
    scrollToBottom('smooth');
  }, [scrollToBottom]);

  // ── 发送/中断 ────────────────────────────────────────────
  const handleSend = useCallback(() => {
    const text = inlineInputRef.current?.getTextContent() ?? '';
    if (!text.trim() || isSending) return;
    inlineInputRef.current?.clear();
    setHasInputContent(false);
    stickToBottomRef.current = true;
    send(text);
  }, [isSending, send]);

  const handleContentChange = useCallback((hasContent: boolean) => {
    setHasInputContent(hasContent);
  }, []);

  // 新建会话:清空当前对话(会话 id 由后端重新分配)
  const handleNewSession = useCallback(() => {
    reset();
    requestAnimationFrame(() => inlineInputRef.current?.focus());
  }, [reset]);

  // 预设问题填入输入框并聚焦
  const handlePresetSelect = useCallback((prompt: string) => {
    requestAnimationFrame(() => {
      const input = inlineInputRef.current;
      if (!input) return;
      input.setContent(prompt);
      input.focus();
      setHasInputContent(true);
    });
  }, []);

  const showHero = messages.length === 0;

  return (
    <div className="chat-panel">
      {/* 顶部栏:标题 + 知识库选择 + 新建会话 */}
      <div className="chat-panel-header">
        <span className="chat-panel-title">{t('chat.appTitle')}</span>
        {kbs.length > 0 && (
          <select
            className="chat-panel-kb-select"
            value={currentKbId ?? ''}
            onChange={(e) => setCurrentKbId(Number(e.target.value))}
            title={t('chat.selectKb')}
          >
            {kbs.map((kb) => (
              <option key={kb.id} value={kb.id}>
                {kb.name}
              </option>
            ))}
          </select>
        )}
        <button
          type="button"
          className="chat-panel-new-btn"
          onClick={handleNewSession}
          title={t('chat.newSession')}
          aria-label={t('chat.newSession')}
        >
          {t('chat.newSession')}
        </button>
      </div>

      {/* 空态欢迎屏 / 消息区 */}
      {showHero ? (
        <ChatEmptyHero onPresetSelect={handlePresetSelect} />
      ) : (
        <div
          ref={messagesContainerRef}
          className="chat-panel-messages"
          onScroll={handleScroll}
        >
          <HistoryRenderer />
          {error && (
            <div className="chat-panel-error">
              <strong>{t('chat.error')}:</strong> {error}
            </div>
          )}
          <div ref={messagesEndRef} className="chat-panel-anchor" />
        </div>
      )}

      {/* 输入区(始终显示) */}
      <div className="chat-panel-input-area">
        {showScrollHint && (
          <button
            type="button"
            className="new-msg-hint"
            onClick={handleScrollToBottom}
            title={t('chat.scrollToBottom')}
            aria-label={t('chat.scrollToBottom')}
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
              <polyline points="6 9 12 15 18 9" />
            </svg>
          </button>
        )}
        <div className="chat-panel-input-card">
          <div className="chat-panel-input-row">
            <InlineInput
              ref={inlineInputRef}
              placeholder={t('chat.inputPlaceholder')}
              onSend={handleSend}
              onContentChange={handleContentChange}
            />
          </div>
          <div className="chat-panel-input-status-bar">
            <div className="chat-panel-status-actions">
              {isSending ? (
                <button
                  type="button"
                  className="chat-panel-abort-btn"
                  onClick={abort}
                  title={t('chat.stop')}
                  aria-label={t('chat.stop')}
                >
                  <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16" aria-hidden>
                    <rect x="6" y="6" width="12" height="12" rx="2" />
                  </svg>
                </button>
              ) : (
                <button
                  type="button"
                  className="chat-panel-send-btn"
                  onClick={handleSend}
                  disabled={!hasInputContent}
                  title={t('chat.sendMessage')}
                  aria-label={t('chat.sendMessage')}
                >
                  <svg
                    viewBox="0 0 16 16"
                    width="16"
                    height="16"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden
                  >
                    <line x1="8" y1="15" x2="8" y2="1" />
                    <polyline points="2 7 8 1 14 7" />
                  </svg>
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
