/**
 * 通用 React Hooks 工具
 */
import { useEffect, useRef } from 'react';

/**
 * 追踪上一个值。每次渲染时记录当前值，
 * 返回上一次 render 时的值（首次返回 undefined）。
 */
export function usePrevious<T>(value: T): T | undefined {
  const ref = useRef<T>();
  useEffect(() => {
    ref.current = value;
  });
  return ref.current;
}
