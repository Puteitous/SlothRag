import { Suspense, lazy, useState } from 'react';
import { ChatPanel } from '@/components/chat-panel/ChatPanel';
import { useI18n } from '@/i18n';

// 管理后台懒加载:聊天首屏不引入 antd
const AdminPage = lazy(() => import('@/admin/AdminPage'));

export default function App() {
  const { t } = useI18n();
  const [view, setView] = useState<'chat' | 'admin'>('chat');

  return (
    <div className="app-root">
      <nav className="app-nav">
        <button
          type="button"
          className={`app-nav-btn${view === 'chat' ? ' active' : ''}`}
          onClick={() => setView('chat')}
        >
          {t('app.nav.chat')}
        </button>
        <button
          type="button"
          className={`app-nav-btn${view === 'admin' ? ' active' : ''}`}
          onClick={() => setView('admin')}
        >
          {t('app.nav.admin')}
        </button>
      </nav>
      {view === 'chat' ? (
        <ChatPanel />
      ) : (
        <Suspense fallback={<div className="app-loading" />}>
          <AdminPage />
        </Suspense>
      )}
    </div>
  );
}
