/**
 * slothrag 后端 API 客户端
 */
import { getJson, postJson, deleteJson, authHeaders } from './http';
import { streamSse } from './sse';
import type { ConversationItem, DocItem, IngestTaskItem, KbItem, PageResult } from '@/types';
import type { ChatSseEventName } from '@/types/sse';

/** 统一响应体(slothrag Result 包装) */
interface ApiResult<T> {
  code: string;
  message: string;
  data: T;
}

/** 后台登录认证 */
export const authApi = {
  /** 登录,返回 token + username */
  login: async (username: string, password: string): Promise<{ token: string; username: string }> => {
    const res = await postJson<ApiResult<{ token: string; username: string }>>('/api/auth/login', {
      username,
      password,
    });
    if (res.code !== '0') throw new Error(res.message);
    return res.data;
  },
};

/** 知识库管理 */
export const kbApi = {
  /** 知识库列表(含文档数) */
  list: async (): Promise<KbItem[]> => {
    const res = await getJson<ApiResult<KbItem[]>>('/api/kb');
    if (res.code !== '0') throw new Error(res.message);
    return res.data;
  },

  /** 创建知识库 */
  create: async (name: string, description?: string): Promise<number> => {
    const params = new URLSearchParams({ name });
    if (description) params.set('description', description);
    const res = await postJson<ApiResult<{ kbId: number }>>(`/api/kb?${params.toString()}`);
    if (res.code !== '0') throw new Error(res.message);
    return res.data.kbId;
  },

  /** 删除知识库(级联删文档/分块) */
  remove: async (kbId: number): Promise<void> => {
    const res = await deleteJson<ApiResult<void>>(`/api/kb/${kbId}`);
    if (res.code !== '0') throw new Error(res.message);
  },

  /** 库内文档分页列表 */
  listDocs: async (kbId: number, page: number, pageSize: number): Promise<PageResult<DocItem>> => {
    const res = await getJson<ApiResult<PageResult<DocItem>>>(
      `/api/kb/${kbId}/docs?page=${page}&pageSize=${pageSize}`,
    );
    if (res.code !== '0') throw new Error(res.message);
    return res.data;
  },

  /** 上传文档(异步入库) */
  uploadDoc: async (kbId: number, file: File): Promise<number> => {
    const form = new FormData();
    form.append('file', file);
    const res = await fetch(`/api/kb/${kbId}/docs`, {
      method: 'POST',
      headers: authHeaders(),
      body: form,
    });
    const body = (await res.json()) as ApiResult<{ taskId: number }>;
    if (body.code !== '0') throw new Error(body.message);
    return body.data.taskId;
  },

  /** 删除库内文档(级联删分块) */
  removeDoc: async (kbId: number, docId: number): Promise<void> => {
    const res = await deleteJson<ApiResult<void>>(`/api/kb/${kbId}/docs/${docId}`);
    if (res.code !== '0') throw new Error(res.message);
  },

  /** 入库任务分页列表(可按状态过滤) */
  listTasks: async (
    page: number,
    pageSize: number,
    status?: string,
  ): Promise<PageResult<IngestTaskItem>> => {
    const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
    if (status) params.set('status', status);
    const res = await getJson<ApiResult<PageResult<IngestTaskItem>>>(`/api/kb/tasks?${params.toString()}`);
    if (res.code !== '0') throw new Error(res.message);
    return res.data;
  },

  /** 重试失败入库任务 */
  retryTask: async (taskId: number): Promise<void> => {
    const res = await postJson<ApiResult<void>>(`/api/kb/tasks/${taskId}/retry`);
    if (res.code !== '0') throw new Error(res.message);
  },

  /** 删除终态入库任务 */
  removeTask: async (taskId: number): Promise<void> => {
    const res = await deleteJson<ApiResult<void>>(`/api/kb/tasks/${taskId}`);
    if (res.code !== '0') throw new Error(res.message);
  },
};

/** 消息反馈（👍👎） */
export const feedbackApi = {
  /** 提交反馈（重复提交同一 messageId 会覆盖） */
  submit: async (req: import('@/types').FeedbackRequest): Promise<void> => {
    const res = await postJson<ApiResult<void>>('/api/feedback', req);
    if (res.code !== '0') throw new Error(res.message);
  },

  /** 查询某条消息已有的反馈 */
  get: async (sessionId: string, messageId: string): Promise<import('@/types').FeedbackRecord | null> => {
    const res = await getJson<ApiResult<import('@/types').FeedbackRecord | null>>(
      `/api/feedback?sessionId=${encodeURIComponent(sessionId)}&messageId=${encodeURIComponent(messageId)}`,
    );
    if (res.code !== '0') throw new Error(res.message);
    return res.data;
  },

  /** 管理员：分页查询反馈列表（按时间倒排，可按 feedback 类型过滤） */
  list: async (
    page: number,
    pageSize: number,
    feedbackType?: string,
  ): Promise<PageResult<import('@/types').FeedbackRecord>> => {
    const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
    if (feedbackType) params.set('feedbackType', feedbackType);
    const res = await getJson<ApiResult<PageResult<import('@/types').FeedbackRecord>>>(
      `/api/feedback/page?${params.toString()}`,
    );
    if (res.code !== '0') throw new Error(res.message);
    return res.data;
  },

  /** 管理员：反馈统计 */
  stats: async (): Promise<import('@/types').FeedbackStats> => {
    const res = await getJson<ApiResult<import('@/types').FeedbackStats>>('/api/feedback/stats');
    if (res.code !== '0') throw new Error(res.message);
    return res.data;
  },
};

/** 流式问答 */
export const chatApi = {
  /**
   * 发起流式问答(GET /api/chat)。
   *
   * @param question 问题
   * @param kbId 知识库 id(必填)
   * @param conversationId 已有会话 id;为空时后端新建会话(经 session 事件返回)
   */
  stream: (
    question: string,
    kbId: number | null,
    conversationId: string | null,
    onEvent: (event: { event: ChatSseEventName; data: unknown }) => void,
    signal?: AbortSignal,
  ): Promise<void> => {
    const params = new URLSearchParams({ question });
    if (kbId != null) params.set('kbId', String(kbId));
    if (conversationId) params.set('conversationId', conversationId);
    return streamSse(`/api/chat?${params.toString()}`, onEvent, signal);
  },
};

/** 历史会话 */
export const conversationApi = {
  /** 会话列表(最近活跃在前) */
  list: async (page = 1, pageSize = 20): Promise<PageResult<ConversationItem>> => {
    const res = await getJson<ApiResult<PageResult<ConversationItem>>>(
      `/api/conversations?page=${page}&pageSize=${pageSize}`,
    );
    if (res.code !== '0') throw new Error(res.message);
    return res.data;
  },

  /** 删除会话(记录 + JSONL 文件) */
  remove: async (sessionId: string): Promise<void> => {
    const res = await deleteJson<ApiResult<void>>(`/api/conversations/${sessionId}`);
    if (res.code !== '0') throw new Error(res.message);
  },

  /** 读取会话全部历史消息(按写入顺序,仅 role/content) */
  messages: async (sessionId: string): Promise<{ role: string; content: string }[]> => {
    const res = await getJson<ApiResult<{ role: string; content: string }[]>>(
      `/api/conversations/${sessionId}/messages`,
    );
    if (res.code !== '0') throw new Error(res.message);
    return res.data;
  },
};
