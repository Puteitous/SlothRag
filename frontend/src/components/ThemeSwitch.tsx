/**
 * ThemeSwitch - 主题切换按钮
 *
 * 三档循环:浅色(太阳) → 深色(月亮) → 午夜(星月)。
 * 用内联 SVG 而非 @ant-design/icons,避免聊天首屏引入 antd。
 * 聊天顶部栏与管理后台顶部栏共用。
 */
import { useThemeStore, type Theme } from '@/stores/themeStore';
import { useI18n } from '@/i18n';

const SUN_SVG = (
  <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2v2" />
    <path d="M12 20v2" />
    <path d="m4.93 4.93 1.41 1.41" />
    <path d="m17.66 17.66 1.41 1.41" />
    <path d="M2 12h2" />
    <path d="M20 12h2" />
    <path d="m6.34 17.66-1.41 1.41" />
    <path d="m19.07 4.93-1.41 1.41" />
  </svg>
);

const MOON_SVG = (
  <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z" />
  </svg>
);

const MIDNIGHT_SVG = (
  <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
    <path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z" />
    <path d="M19 3v4" />
    <path d="M21 5h-4" />
  </svg>
);

const THEME_ICON: Record<Theme, React.ReactNode> = {
  light: SUN_SVG,
  dark: MOON_SVG,
  midnight: MIDNIGHT_SVG,
};

const THEME_LABEL: Record<Theme, string> = {
  light: 'light',
  dark: 'dark',
  midnight: 'midnight',
};

export function ThemeSwitch() {
  const { t } = useI18n();
  const theme = useThemeStore((s) => s.theme);
  const cycleTheme = useThemeStore((s) => s.cycleTheme);

  return (
    <button
      type="button"
      className="theme-switch"
      onClick={cycleTheme}
      title={t('chat.themeLabel', { theme: THEME_LABEL[theme] })}
      aria-label={t('chat.themeLabel', { theme: THEME_LABEL[theme] })}
    >
      {THEME_ICON[theme]}
    </button>
  );
}
