import { App as AntdApp, ConfigProvider, Tabs, theme as antdTheme, Button } from 'antd';
import { LogoutOutlined } from '@ant-design/icons';
import zhCN from 'antd/locale/zh_CN';
import { useThemeStore } from '@/stores/themeStore';
import { useAuthStore } from '@/stores/authStore';
import { ThemeSwitch } from '@/components/ThemeSwitch';
import KbListPage from './KbListPage';
import DocsPage from './DocsPage';
import TasksPage from './TasksPage';

/**
 * 管理后台入口
 *
 * AntD 主题与全局 CSS 变量主题联动:
 *   - light → defaultAlgorithm
 *   - dark / midnight → darkAlgorithm
 * 主色取自 index.css 变量体系的 --primary-dark(#1a1a2e) 系,保持与聊天界面同源。
 */
export default function AdminPage() {
  const theme = useThemeStore((s) => s.theme);
  const username = useAuthStore((s) => s.username);
  const logout = useAuthStore((s) => s.logout);
  const isDark = theme === 'dark' || theme === 'midnight';

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: isDark ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
        token: {
          colorPrimary: isDark ? '#8fa1c9' : '#1a1a2e',
          borderRadius: 8,
        },
      }}
    >
      <AntdApp>
        <div className="admin-page">
          <div className="admin-page-header">
            <div className="admin-page-heading">
              <h2 className="admin-page-title">管理后台</h2>
              <div className="admin-page-subtitle">知识库 / 文档 / 入库任务管理</div>
            </div>
            <ThemeSwitch />
            <span className="admin-page-user">{username}</span>
            <Button size="small" icon={<LogoutOutlined />} onClick={logout}>
              登出
            </Button>
          </div>
          <Tabs
            className="admin-page-tabs"
            defaultActiveKey="kb"
            items={[
              { key: 'kb', label: '知识库', children: <KbListPage /> },
              { key: 'docs', label: '文档', children: <DocsPage /> },
              { key: 'tasks', label: '入库任务', children: <TasksPage /> },
            ]}
          />
        </div>
      </AntdApp>
    </ConfigProvider>
  );
}
