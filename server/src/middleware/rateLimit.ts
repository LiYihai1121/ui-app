import { config } from "../config";

interface Bucket {
  tokens: number;
  last: number;
}

const buckets = new Map<string, Bucket>();

/**
 * 令牌桶总量硬上限。
 *
 * 安全契约：桶键里含 deviceId 等请求方可控维度，没有上限时攻击者可用
 * 唯一键无限制造桶条目（内存放大）。到顶后淘汰最久未用的桶，保证内存有界
 * 且不把合法用户锁死。
 */
const MAX_BUCKETS = 10000;

function evictOldest(): void {
  let oldestKey: string | null = null;
  let oldest = Infinity;
  for (const [k, b] of buckets) {
    if (b.last < oldest) {
      oldest = b.last;
      oldestKey = k;
    }
  }
  if (oldestKey !== null) buckets.delete(oldestKey);
}

function refill(b: Bucket, capacity: number, now: number): void {
  const elapsed = now - b.last;
  b.tokens = Math.min(capacity, b.tokens + (elapsed / 60000) * capacity);
  b.last = now;
}

export function allow(key: string, capacity: number): boolean {
  const now = Date.now();
  let b = buckets.get(key);
  if (!b) {
    if (buckets.size >= MAX_BUCKETS) evictOldest();
    buckets.set(key, { tokens: capacity, last: now });
    return true;
  }
  refill(b, capacity, now);
  if (b.tokens < 1) return false;
  b.tokens -= 1;
  return true;
}

/** 探测容量但不扣减令牌（用于读取请求体前的低成本预检） */
export function probe(key: string, capacity: number): boolean {
  const now = Date.now();
  const b = buckets.get(key);
  if (!b) return true;
  refill(b, capacity, now);
  return b.tokens >= 1;
}

/**
 * 判定限流维度用的客户端 IP。
 *
 * 安全契约：X-Forwarded-For 是请求方可以任意填写的头，若默认采信，
 * 攻击者每换一个 XFF 值就能拿到一个全新的令牌桶，限流彻底失效。
 * 因此只在显式开启 `config.TRUST_PROXY`（部署在可信反向代理之后）时才取 XFF
 * 首个地址，否则一律使用 socket IP。返回值截断到 64 字符，避免超长头撑爆桶键。
 */
export function clientIp(req: Request, remoteIp: string): string {
  const clamp = (s: string) => s.slice(0, 64);
  if (config.TRUST_PROXY) {
    const xff = req.headers.get("x-forwarded-for");
    if (xff) {
      const first = xff.split(",")[0].trim();
      if (first) return clamp(first);
    }
  }
  return clamp(remoteIp || "unknown");
}

export function limitRead(req: Request, remoteIp: string): boolean {
  return allow("r:" + clientIp(req, remoteIp), config.RATE_LIMIT_READ_PER_MIN);
}

export function limitWrite(req: Request, remoteIp: string): boolean {
  return allow("w:" + clientIp(req, remoteIp), config.RATE_LIMIT_WRITE_PER_MIN);
}

export function limitReport(
  req: Request,
  remoteIp: string,
  deviceId?: string | null
): boolean {
  return allow(
    "d:" + (deviceId || clientIp(req, remoteIp)),
    config.RATE_LIMIT_REPORT_PER_MIN
  );
}

/** 读体前预检：按来源 IP 探测上报容量，不扣减（真正扣减在读体并校验之后按 deviceId/IP 进行） */
export function probeReportIp(req: Request, remoteIp: string): boolean {
  return probe(
    "d:" + clientIp(req, remoteIp),
    config.RATE_LIMIT_REPORT_PER_MIN
  );
}

// 空闲桶 5 分钟后回收（与 Node 版一致）
const gcTimer = setInterval(() => {
  const now = Date.now();
  for (const [k, b] of buckets) {
    if (now - b.last > 5 * 60 * 1000) buckets.delete(k);
  }
}, 60000);
gcTimer.unref?.();

/** 仅供测试：清空限流状态 */
export function _resetRateLimitForTests(): void {
  buckets.clear();
}
