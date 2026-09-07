/**
 * 历史会话列表状态(Zustand)
 *
 * 负责从后端拉取 /api/conversations、删除会话。当前活动的会话以
 * chatStore.conversationId 为准(侧栏高亮),本 store 只维护列表。
 */
import { create } from 'zustand';
import { conversationApi } from '@/api/client';
import type { ConversationItem } from '@/types';

interface ConversationListState {
  list: ConversationItem[];
  loading: boolean;
  /** 拉取会话列表 */
  load: () => Promise<void>;
  /** 删除会话并刷新列表 */
  remove: (sessionId: string) => Promise<void>;
}

export const useConversationListStore = create<ConversationListState>((set) => ({
  list: [],
  loading: false,

  load: async () => {
    set({ loading: true });
    try {
      const res = await conversationApi.list();
      set({ list: res.list });
    } finally {
      set({ loading: false });
    }
  },

  remove: async (sessionId) => {
    await conversationApi.remove(sessionId);
    await useConversationListStore.getState().load();
  },
}));