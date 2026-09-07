/**
 * 管理后台公共格式化工具
 */

/** 后端 LocalDateTime("2026-09-07T03:05:02.765481") → "2026-09-07 03:05:02" */
export function formatDateTime(s?: string): string {
  if (!s) return '-';
  return s.replace('T', ' ').slice(0, 19);
}

/** 字节数 → 可读大小 */
export function formatSize(bytes?: number): string {
  if (bytes == null) return '-';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}
