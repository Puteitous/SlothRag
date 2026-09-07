/**
 * SSE (Server-Sent Events) 流式读取器
 *
 * slothrag 后端通过 SseEmitter 发送事件,格式:
 *   event: <eventName>\n
 *   data: <payload>\n
 *   \n
 *
 * 前端用 fetch + ReadableStream 读取,逐行解析。
 * 不使用 EventSource 的原因:需要支持 AbortController 主动中断。
 * 与 /api/chat 为 GET 请求,故使用 fetch 而非 EventSource 也可携带 query 参数。
 */
import type { ChatSseEventMap, ChatSseEventName } from '@/types/sse';

/** 单个 SSE 事件 */
export interface SseEvent<K extends ChatSseEventName = ChatSseEventName> {
  event: K;
  data: ChatSseEventMap[K];
}

/**
 * 流式读取 SSE,按事件回调。
 *
 * @param url 请求地址(已含 query 参数)
 * @param onEvent 事件回调(同步,逐个事件触发)
 * @param signal 可选,AbortController.signal 用于中断
 */
export async function streamSse<K extends ChatSseEventName>(
  url: string,
  onEvent: (event: SseEvent<K>) => void,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(url, {
    method: 'GET',
    headers: {
      Accept: 'text/event-stream',
    },
    signal,
  });

  if (!response.ok) {
    let message = `HTTP ${response.status}`;
    try {
      const errBody = await response.json();
      message = errBody?.message ?? message;
    } catch {
      // 非 JSON 错误体,保留默认 message
    }
    throw new Error(message);
  }

  if (!response.body) {
    throw new Error('Response body is null');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });

      // 按空行(\n\n)切分事件块
      let separatorIndex: number;
      while ((separatorIndex = buffer.indexOf('\n\n')) !== -1) {
        const chunk = buffer.slice(0, separatorIndex);
        buffer = buffer.slice(separatorIndex + 2);

        const event = parseSseChunk<K>(chunk);
        if (event) {
          onEvent(event);
        }
      }
    }

    // 处理 buffer 中可能残留的最后一个事件(无尾随 \n\n)
    const trimmed = buffer.trim();
    if (trimmed.length > 0) {
      const event = parseSseChunk<K>(trimmed);
      if (event) {
        onEvent(event);
      }
    }
  } finally {
    reader.releaseLock();
  }
}

/**
 * 解析单个 SSE 事件块(由 \n\n 切分出的字符串)。
 *
 * slothrag 的 data 类型:
 *   - session / delta / error → 纯字符串,直接取 data 行
 *   - sources → JSON 数组,需解析
 */
export function parseSseChunk<K extends ChatSseEventName>(chunk: string): SseEvent<K> | null {
  const lines = chunk.split('\n');
  let eventName: string | null = null;
  const dataLines: string[] = [];

  for (const line of lines) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart());
    }
    // 忽略注释行(以 : 开头)
  }

  if (!eventName) return null;

  const dataStr = dataLines.join('\n');
  let data: ChatSseEventMap[K];

  if (eventName === 'sources') {
    try {
      data = JSON.parse(dataStr) as ChatSseEventMap[K];
    } catch {
      // JSON 解析失败不阻断流,降级为空数组
      console.warn('[sse] 解析 sources 的 data 失败:', dataStr);
      data = [] as unknown as ChatSseEventMap[K];
    }
  } else {
    data = dataStr as unknown as ChatSseEventMap[K];
  }

  return { event: eventName as K, data };
}
