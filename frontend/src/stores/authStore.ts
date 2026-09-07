/**
 * 后台登录状态(Zustand,slothrag 版)
 *
 * token 持久化到 localStorage,http.ts 统一附加 Authorization 头;
 * 401 时由 http.ts 触发 logout 回到登录页。
 */
import { create } from 'zustand';
import { postJson, UNAUTHORIZED_EVENT } from '@/api/http';

const TOKEN_KEY = 'slothrag-auth-token';
const USERNAME_KEY = 'slothrag-auth-username';

/** 使 token/用户名对当前请求生效,模拟签名 → 校验的时序一致 */
function getStoredToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

function saveAuth(token: string, username: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USERNAME_KEY, username);
  } catch {
    /* 忽略 */
  }
}

function clearAuth(): void {
  try {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USERNAME_KEY);
  } catch {
    /* 忽略 */
  }
}

interface AuthState {
  /** 当前有效 token,无则未登录 */
  token: string | null;
  username: string | null;
  /** 登录,成功写入 token/username */
  login: (username: string, password: string) => Promise<void>;
  /** 登出,清空本地凭证 */
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: getStoredToken(),
  username: (() => {
    try {
      return localStorage.getItem(USERNAME_KEY);
    } catch {
      return null;
    }
  })(),

  login: async (username, password) => {
    const res = await postJson<{ code: string; message: string; data: { token: string; username: string } }>(
      '/api/auth/login',
      { username, password },
    );
    if (res.code !== '0') throw new Error(res.message);
    saveAuth(res.data.token, res.data.username);
    set({ token: res.data.token, username: res.data.username });
  },

  logout: () => {
    clearAuth();
    set({ token: null, username: null });
  },
}));

// 任意请求收到 401 时清空内存态,路由守卫随之回落到登录页
window.addEventListener(UNAUTHORIZED_EVENT, () => {
  clearAuth();
  useAuthStore.setState({ token: null, username: null });
});