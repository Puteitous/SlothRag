/**
 * ragbase 前端核心类型定义
 */

/** 来源引用(sources SSE 事件的单条) */
export interface SourceItem {
  /** 知识片段文本(后端截断至 80 字符) */
  source: string;
}

/** 消息角色:ragbase 仅有用户问答,无工具消息 */
export type MessageRole = 'user' | 'assistant';

export interface Message {
  id: string;
  role: MessageRole;
  content: string;
  /** 是否流式渲染中(末尾闪烁光标,前端态) */
  isStreaming?: boolean;
  /** assistant 回答的来源引用(sources 事件,前端态) */
  sources?: SourceItem[];
  timestamp: number;
}

/** 知识库列表项 - GET /api/kb */
export interface KbItem {
  id: number;
  name: string;
  description?: string;
  embeddingModel?: string;
  docCount?: number;
}

// ============================================================================
// 管理后台类型（对齐后端 knowledge.domain.* / common.web.PageResult）
// ============================================================================

/** 分页返回结构 */
export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  pageSize: number;
}

/** 文档 */
export interface DocItem {
  id: number;
  kbId: number;
  fileName: string;
  filePath: string;
  fileSize: number;
  status: 'PENDING' | 'PARSING' | 'CHUNKING' | 'INDEXED' | 'FAILED';
  chunkCount?: number;
  errorMsg?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** 入库任务 */
export interface IngestTaskItem {
  id: number;
  docId: number;
  kbId: number;
  status: 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED';
  progress: number;
  currentStage?: string;
  errorMsg?: string;
  createdAt?: string;
  updatedAt?: string;
}

// ============================================================================
// 会话（历史会话列表）
// ============================================================================

/** 会话元数据 - GET /api/conversations */
export interface ConversationItem {
  id: number;
  sessionId: string;
  title?: string;
  createdAt?: string;
  updatedAt?: string;
}
