/**
 * 应用全局状态(Zustand)
 *
 * slothrag 简化版:只承载知识库列表/当前知识库(问答接口必填 kbId)。
 */
import { create } from 'zustand';
import { kbApi } from '@/api/client';
import type { KbItem } from '@/types';

interface AppState {
  /** 知识库列表(GET /api/kb) */
  kbs: KbItem[];
  /** 当前选中的知识库 id */
  currentKbId: number | null;
  /** 加载知识库列表(首次加载后默认选中第一个) */
  loadKbs: () => Promise<void>;
  /** 切换当前知识库 */
  setCurrentKbId: (id: number) => void;
}

export const useAppStore = create<AppState>((set) => ({
  kbs: [],
  currentKbId: null,
  loadKbs: async () => {
    try {
      const kbs = await kbApi.list();
      set((s) => ({ kbs, currentKbId: s.currentKbId ?? (kbs[0]?.id ?? null) }));
    } catch {
      // 加载失败保持空,由组件层提示
    }
  },
  setCurrentKbId: (id) => set({ currentKbId: id }),
}));
