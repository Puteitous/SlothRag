/**
 * ragbase 后端 API 客户端
 */
import { getJson, postJson, deleteJson } from './http';
import { streamSse } from './sse';
import type { DocItem, IngestTaskItem, KbItem, PageResult } from '@/types';
import type { ChatSseEventName } from '@/types/sse';

/** 统一响应体(ragbase Result 包装) */
interface ApiResult<T> {
  code: string;
  message: string;
  data: T;
}

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
    const res = await fetch(`/api/kb/${kbId}/docs`, { method: 'POST', body: form });
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
    kbId: number,
    conversationId: string | null,
    onEvent: (event: { event: ChatSseEventName; data: unknown }) => void,
    signal?: AbortSignal,
  ): Promise<void> => {
    const params = new URLSearchParams({ question, kbId: String(kbId) });
    if (conversationId) params.set('conversationId', conversationId);
    return streamSse(`/api/chat?${params.toString()}`, onEvent, signal);
  },
};
