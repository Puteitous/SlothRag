/**
 * ConversationSidebar - 历史会话侧栏
 *
 * 列出最近会话,支持新建 / 切换续接 / 删除。当前活动会话以
 * chatStore.conversationId 为准高亮。
 */
import { useChatStore } from '@/stores/chatStore';
import { useConversationListStore } from '@/stores/conversationListStore';
import { useI18n } from '@/i18n';

export function ConversationSidebar() {
  const { t } = useI18n();
  const list = useConversationListStore((s) => s.list);
  const loading = useConversationListStore((s) => s.loading);
  const remove = useConversationListStore((s) => s.remove);
  const conversationId = useChatStore((s) => s.conversationId);
  const reset = useChatStore((s) => s.reset);
  const startSession = useChatStore((s) => s.startSession);

  const handleNew = () => reset();

  const handleSelect = (sessionId: string) => {
    if (sessionId !== conversationId) startSession(sessionId);
  };

  const handleDelete = (e: React.MouseEvent, sessionId: string) => {
    e.stopPropagation();
    if (!window.confirm(t('chat.historyConfirmDelete'))) return;
    void remove(sessionId);
    if (conversationId === sessionId) reset();
  };

  return (
    <aside className="conversation-sidebar">
      <div className="conversation-sidebar-head">
        <span className="conversation-sidebar-title">{t('chat.historyTitle')}</span>
        <button type="button" className="conversation-sidebar-new-btn" onClick={handleNew}>
          {t('chat.newSession')}
        </button>
      </div>
      <div className="conversation-sidebar-list">
        {loading && list.length === 0 && <div className="conversation-sidebar-tip">…</div>}
        {!loading && list.length === 0 && (
          <div className="conversation-sidebar-tip">{t('chat.historyEmpty')}</div>
        )}
        {list.map((c) => (
          <div
            key={c.sessionId}
            className={`conversation-sidebar-item${c.sessionId === conversationId ? ' active' : ''}`}
            onClick={() => handleSelect(c.sessionId)}
            title={c.title || c.sessionId}
          >
            <span className="conversation-sidebar-item-title">{c.title || c.sessionId}</span>
            <button
              type="button"
              className="conversation-sidebar-item-del"
              onClick={(e) => handleDelete(e, c.sessionId)}
              title={t('chat.historyDelete')}
              aria-label={t('chat.historyDelete')}
            >
              <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
                <polyline points="3 6 5 6 21 6" />
                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
                <line x1="10" y1="11" x2="10" y2="17" />
                <line x1="14" y1="11" x2="14" y2="17" />
              </svg>
            </button>
          </div>
        ))}
      </div>
    </aside>
  );
}