/**
 * 历史会话列表状态(Zustand)
 *
 * 负责从后端拉取 /api/conversations、删除会话。当前活动的会话以
 * chatStore.conversationId 为准(侧栏高亮),本 store 只维护列表。
 * 支持分页追加加载（滚动到底自动加载更多）。
 */
import { create } from 'zustand';
import { conversationApi } from '@/api/client';
import type { ConversationItem } from '@/types';

interface ConversationListState {
  list: ConversationItem[];
  loading: boolean;
  page: number;
  hasMore: boolean;
  /** 拉取会话列表。append=false 时重置为第 1 页；append=true 时追加下一页 */
  load: (append?: boolean) => Promise<void>;
  /** 删除会话并刷新列表 */
  remove: (sessionId: string) => Promise<void>;
}

const PAGE_SIZE = 20;

export const useConversationListStore = create<ConversationListState>((set, get) => ({
  list: [],
  loading: false,
  page: 1,
  hasMore: true,

  load: async (append = false) => {
    const currentPage = append ? get().page + 1 : 1;
    set({ loading: true });
    try {
      const res = await conversationApi.list(currentPage, PAGE_SIZE);
      set({
        list: append ? [...get().list, ...res.list] : res.list,
        page: currentPage,
        hasMore: currentPage * PAGE_SIZE < res.total,
      });
    } finally {
      set({ loading: false });
    }
  },

  remove: async (sessionId) => {
    await conversationApi.remove(sessionId);
    // 删除后回退到第 1 页重新拉取（列表可能已变化）
    set({ list: [], page: 1, hasMore: true });
    await get().load();
  },
}));