import type { ReactNode } from 'react';
import { App as AntdApp, ConfigProvider, theme as antdTheme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { useThemeStore } from '@/stores/themeStore';
import { useAuthStore } from '@/stores/authStore';
import LoginPage from './LoginPage';

/**
 * 管理后台路由守卫 + AntD 主题外壳
 *
 * 未登录(token 为空)渲染登录页,已登录放行渲染 children。
 * 任意 /api/kb 请求返回 401 会清空 authStore.token,此处随之回落到登录页。
 */
export default function AdminGuard({ children }: { children: ReactNode }) {
  const token = useAuthStore((s) => s.token);
  const theme = useThemeStore((s) => s.theme);
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
      <AntdApp>{token ? children : <LoginPage />}</AntdApp>
    </ConfigProvider>
  );
}