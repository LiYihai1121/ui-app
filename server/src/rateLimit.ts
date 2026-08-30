import { config } from "./config";

interface Bucket {
  tokens: number;
  last: number;
}

const buckets = new Map<string, Bucket>();

function refill(b: Bucket, capacity: number, now: number): void {
  const elapsed = now - b.last;
  b.tokens = Math.min(capacity, b.tokens + (elapsed / 60000) * capacity);
  b.last = now;
}

export function allow(key: string, capacity: number): boolean {
  const now = Date.now();
  let b = buckets.get(key);
  if (!b) {
    buckets.set(key, { tokens: capacity, last: now });
    return true;
  }
  refill(b, capacity, now);
  if (b.tokens < 1) return false;
  b.tokens -= 1;
  return true;
}

export function clientIp(req: Request, remoteIp: string): string {
  const xff = req.headers.get("x-forwarded-for");
  if (xff) return xff.split(",")[0].trim();
  return remoteIp || "unknown";
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
