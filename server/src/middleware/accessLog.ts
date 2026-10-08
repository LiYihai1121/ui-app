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

// 粗粒度运行计数（/metrics 用）：只计数不存明细，内存恒定
const startedAtMs = Date.now();
let totalRequests = 0;
const byStatusClass: Record<string, number> = { "2xx": 0, "3xx": 0, "4xx": 0, "5xx": 0, other: 0 };

export function recordAccess(e: Omit<AccessEntry, "ts">): void {
  ring.unshift({ ...e, ts: new Date().toISOString() });
  if (ring.length > MAX_ENTRIES) ring.length = MAX_ENTRIES;

  totalRequests += 1;
  const cls = e.status >= 200 && e.status < 300 ? "2xx"
    : e.status >= 300 && e.status < 400 ? "3xx"
      : e.status >= 400 && e.status < 500 ? "4xx"
        : e.status >= 500 ? "5xx"
          : "other";
  byStatusClass[cls] += 1;
}

export function recentAccess(): AccessEntry[] {
  return ring.slice();
}

/** 运行指标（/metrics）：进程启动时间与按状态类聚合的请求计数 */
export function accessMetrics(): { uptimeSec: number; total: number; byStatusClass: Record<string, number> } {
  return {
    uptimeSec: Math.floor((Date.now() - startedAtMs) / 1000),
    total: totalRequests,
    byStatusClass: { ...byStatusClass },
  };
}
