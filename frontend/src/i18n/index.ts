/**
 * i18n — 国际化引擎(精简版)
 *
 * ragbase 沿用 HippoBuddy 的自研轻量方案:key 查表 + {param} 插值。
 * 仅保留中文文案(见 messages.ts),不引入 i18next。
 */
import { useCallback } from 'react';
import { create } from 'zustand';
import { useStore } from 'zustand';
import { zh, en, type Lang } from './messages';

interface I18nState {
  lang: Lang;
  initI18n: () => void;
  setLang: (lang: Lang) => void;
}

export const i18nStore = create<I18nState>((set) => ({
  lang: 'zh',
  initI18n: () => set({ lang: 'zh' }),
  setLang: (lang) => set({ lang }),
}));

/** 翻译查表(非响应式)。目标语言无词 → 回退中文 → 返回 key。 */
export function translate(key: string, params?: Record<string, string | number>): string {
  const dict = i18nStore.getState().lang === 'en' ? en : zh;
  let text = dict[key] ?? zh[key] ?? key;
  if (params) {
    for (const [k, v] of Object.entries(params)) {
      text = text.split(`{${k}}`).join(String(v));
    }
  }
  return text;
}

/** 绑定到当前语言的 t(订阅 store,语言变化触发重渲染) */
export function useI18n() {
  const lang = useStore(i18nStore, (s) => s.lang);
  const t = useCallback((key: string, params?: Record<string, string | number>) => {
    const dict = lang === 'en' ? en : zh;
    let text = dict[key] ?? zh[key] ?? key;
    if (params) {
      for (const [k, v] of Object.entries(params)) {
        text = text.split(`{${k}}`).join(String(v));
      }
    }
    return text;
  }, [lang]);
  return { t, lang };
}

/** 页面初始化调用一次(main.tsx) */
export function initI18n(): void {
  i18nStore.getState().initI18n();
}
