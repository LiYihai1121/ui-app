/** 内存访问日志环形缓冲（最近 200 条），经 /api/v1/admin/logs 暴露给管理端 */

export interface AccessEntry {
  ts: string;
  method: string;
  path: string;
  status: number;
  ip: string;
  ms: number;
}

const MAX_ENTRIES = 200;
const ring: AccessEntry[] = [];

export function recordAccess(e: Omit<AccessEntry, "ts">): void {
  ring.unshift({ ...e, ts: new Date().toISOString() });
  if (ring.length > MAX_ENTRIES) ring.length = MAX_ENTRIES;
}

export function recentAccess(): AccessEntry[] {
  return ring.slice();
}
