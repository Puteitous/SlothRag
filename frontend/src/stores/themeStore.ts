/**
 * 全局主题状态(Zustand,ragbase 版)
 *
 * 从 HippoBuddy themeStore 搬入并裁剪:去掉 Electron(desktopBridge)与 system 跟随,
 * 保留 light / dark / midnight 三套主题,通过 <html data-theme> 应用。
 * index.css 已内置三套 CSS 变量块(:root / [data-theme="dark"] / [data-theme="midnight"]),
 * 聊天界面组件样式均已消费 data-theme,切换即可全局生效。
 */
import { create } from 'zustand';

export type Theme = 'light' | 'dark' | 'midnight';

const THEME_KEY = 'ragbase-theme';

const THEME_ORDER: Theme[] = ['light', 'dark', 'midnight'];

function readStoredTheme(): Theme | null {
  try {
    const v = localStorage.getItem(THEME_KEY);
    if (v === 'light' || v === 'dark' || v === 'midnight') return v;
  } catch {
    /* localStorage 不可用时静默降级 */
  }
  return null;
}

function saveStoredTheme(theme: Theme): void {
  try {
    localStorage.setItem(THEME_KEY, theme);
  } catch {
    /* 忽略 */
  }
}

/** 应用主题到 <html data-theme> */
function applyDataTheme(theme: Theme): void {
  document.documentElement.setAttribute('data-theme', theme);
}

// 模块加载即同步应用已保存主题,避免刷新后首帧回落到浅色
applyDataTheme(readStoredTheme() ?? 'light');

interface ThemeState {
  theme: Theme;
  /** 设置指定主题(持久化 + 应用 data-theme) */
  setTheme: (theme: Theme) => void;
  /** 循环切换:light → dark → midnight → light */
  cycleTheme: () => Theme;
}

export const useThemeStore = create<ThemeState>((set, get) => ({
  theme: readStoredTheme() ?? 'light',

  setTheme: (theme) => {
    applyDataTheme(theme);
    saveStoredTheme(theme);
    set({ theme });
  },

  cycleTheme: () => {
    const { theme } = get();
    const next = THEME_ORDER[(THEME_ORDER.indexOf(theme) + 1) % THEME_ORDER.length];
    get().setTheme(next);
    return next;
  },
}));
