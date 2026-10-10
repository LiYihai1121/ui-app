import { config } from "../config";

interface Bucket {
  tokens: number;
  last: number;
}

const buckets = new Map<string, Bucket>();

/** 桶数量硬上限：键空间完全可能被攻击者灌满（伪造 XFF / 伪造 deviceId），TTL 之外再加一道闸 */
const MAX_BUCKETS = 50_000;

function refill(b: Bucket, capacity: number, now: number): void {
  const elapsed = now - b.last;
  b.tokens = Math.min(capacity, b.tokens + (elapsed / 60000) * capacity);
  b.last = now;
}

function evictOldestIfNeeded(): void {
  if (buckets.size < MAX_BUCKETS) return;
  // Map 保持插入序：从头删最久未写入的键，直到留出余量
  const target = buckets.size - MAX_BUCKETS + 1;
  let removed = 0;
  for (const k of buckets.keys()) {
    if (removed >= target) break;
    buckets.delete(k);
    removed++;
  }
}

export function allow(key: string, capacity: number): boolean {
  const now = Date.now();
  let b = buckets.get(key);
  if (!b) {
    evictOldestIfNeeded();
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
 * 判定客户端 IP。
 *
 * 安全约束：X-Forwarded-For 仅在「对端 IP ∈ TRUSTED_PROXIES」时采信。
 * 直接对外监听时（默认）任意客户端都能伪造 XFF，无限流就等于没限流；
 * 反代部署时把反代内网 IP 填进 TRUSTED_PROXIES 即可恢复按真实客户端限流。
 */
export function clientIp(req: Request, remoteIp: string): string {
  const isTrustedProxy =
    !!remoteIp && config.TRUSTED_PROXIES.includes(remoteIp);
  if (isTrustedProxy) {
    const xff = req.headers.get("x-forwarded-for");
    if (xff) {
      const first = xff.split(",")[0].trim();
      if (first) return first;
    }
  }
  return remoteIp || "unknown";
}

export function limitRead(req: Request, remoteIp: string): boolean {
  return allow("r:" + clientIp(req, remoteIp), config.RATE_LIMIT_READ_PER_MIN);
}

export function limitWrite(req: Request, remoteIp: string): boolean {
  return allow("w:" + clientIp(req, remoteIp), config.RATE_LIMIT_WRITE_PER_MIN);
}

/**
 * 上报限流：IP 桶与设备桶**双重扣减**。
 *
 * deviceId 由客户端自报且服务端只校验长度（无法验证归属），攻击者可伪造
 * 海量设备号绕过设备维度限额；IP 桶不随设备号变化，兜住单位时间内的总量。
 * 两桶任一耗尽即拒绝。
 */
export function limitReport(
  req: Request,
  remoteIp: string,
  deviceId?: string | null
): boolean {
  const ip = clientIp(req, remoteIp);
  if (!allow("d:" + ip, config.RATE_LIMIT_REPORT_PER_MIN)) return false;
  if (deviceId && deviceId !== ip) {
    return allow("d:" + deviceId, config.RATE_LIMIT_REPORT_PER_MIN);
  }
  return true;
}

/** 读体前预检：按来源 IP 探测容量，不扣减（真正扣减在读体并校验之后进行） */
export function probeReportIp(req: Request, remoteIp: string): boolean {
  return probe("d:" + clientIp(req, remoteIp), config.RATE_LIMIT_REPORT_PER_MIN);
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
