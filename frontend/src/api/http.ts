/**
 * 类型安全的 fetch 封装
 *
 * - 所有方法返回 Promise<T>,失败抛出 ApiError
 * - JSON 请求自动设置 Content-Type
 * - 自动附带后台登录 token(Authorization)
 * - 401 统一清除凭证并派发 slothrag:unauthorized 事件(登录页/守卫据此回落)
 * - 错误响应统一解析为 { error: string } 提取消息
 */
import { ApiError } from './error';

const JSON_HEADERS = { 'Content-Type': 'application/json' } as const;
const TOKEN_KEY = 'slothrag-auth-token';

const UNAUTHORIZED_EVENT = 'slothrag:unauthorized';

function currentToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

function authHeaders(): Record<string, string> {
  const token = currentToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/** 遇到 401:清除本地凭证并广播,前端守卫据此回到登录页 */
function handleUnauthorized(): void {
  try {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem('slothrag-auth-username');
  } catch {
    /* 忽略 */
  }
  window.dispatchEvent(new CustomEvent(UNAUTHORIZED_EVENT));
}

/** 解析后端错误响应,优先取 error 字段,失败回退到 statusText */
async function extractErrorMessage(response: Response, fallback: string): Promise<string> {
  try {
    const body = await response.json();
    const msg = body?.error ?? body?.message ?? fallback;
    return typeof msg === 'string' ? msg : fallback;
  } catch {
    return fallback;
  }
}

/** 统一兜底:校验 ok,失败时对 401 做登出处理再抛错 */
async function guard(response: Response): Promise<Response> {
  if (!response.ok) {
    if (response.status === 401) handleUnauthorized();
    const message = await extractErrorMessage(response, `HTTP ${response.status}`);
    throw new ApiError(message, response.status);
  }
  return response;
}

/** GET 请求 */
export async function getJson<T>(url: string): Promise<T> {
  const response = await guard(await fetch(url, { method: 'GET', headers: authHeaders() }));
  return response.json() as Promise<T>;
}

/** POST 请求(带 JSON body) */
export async function postJson<T>(url: string, body?: unknown): Promise<T> {
  const response = await guard(
    await fetch(url, {
      method: 'POST',
      headers: { ...JSON_HEADERS, ...authHeaders() },
      body: body === undefined ? undefined : JSON.stringify(body),
    }),
  );
  return response.json() as Promise<T>;
}

/** POST 请求(无响应体解析,用于只需 success 状态的接口) */
export async function postEmpty(url: string, body?: unknown): Promise<void> {
  await guard(
    await fetch(url, {
      method: 'POST',
      headers: { ...JSON_HEADERS, ...authHeaders() },
      body: body === undefined ? undefined : JSON.stringify(body),
    }),
  );
}

/** PUT 请求(带 JSON body) */
export async function putJson<T>(url: string, body: unknown): Promise<T> {
  const response = await guard(
    await fetch(url, {
      method: 'PUT',
      headers: { ...JSON_HEADERS, ...authHeaders() },
      body: JSON.stringify(body),
    }),
  );
  return response.json() as Promise<T>;
}

/** DELETE 请求 */
export async function deleteJson<T>(url: string, body?: unknown): Promise<T> {
  const response = await guard(
    await fetch(url, {
      method: 'DELETE',
      headers: { ...JSON_HEADERS, ...authHeaders() },
      body: body === undefined ? undefined : JSON.stringify(body),
    }),
  );
  return response.json() as Promise<T>;
}

export { UNAUTHORIZED_EVENT, authHeaders };