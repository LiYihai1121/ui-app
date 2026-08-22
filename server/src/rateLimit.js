'use strict';

/**
 * 内存令牌桶限频器。
 *
 * - per-IP 限频（读接口 / 写接口）
 * - per-deviceId 限频（上报）
 * - 无外部依赖，单进程内存即可
 */
const config = require('./config');

/** @type {Map<string, {tokens: number, lastRefill: number}>} */
const buckets = new Map();

// 定期清理过期桶（5 分钟未被使用的）
setInterval(() => {
  const now = Date.now();
  for (const [key, bucket] of buckets) {
    if (now - bucket.lastRefill > 5 * 60 * 1000) {
      buckets.delete(key);
    }
  }
}, 60 * 1000).unref();

/**
 * 检查是否被限频。
 * @param {string} key - 限频维度（IP 或 deviceId）
 * @param {number} capacity - 桶容量（每分钟允许的请求数）
 * @returns {boolean} true=允许, false=被限频
 */
function allow(key, capacity) {
  if (!key) return true; // 无维度信息时不限频
  const now = Date.now();
  let bucket = buckets.get(key);
  if (!bucket) {
    bucket = { tokens: capacity, lastRefill: now };
    buckets.set(key, bucket);
  }
  // 补充令牌（按时间线性恢复）
  const elapsed = now - bucket.lastRefill;
  const refill = (elapsed / 60000) * capacity; // 每分钟 capacity 个
  bucket.tokens = Math.min(capacity, bucket.tokens + refill);
  bucket.lastRefill = now;
  if (bucket.tokens < 1) {
    return false;
  }
  bucket.tokens -= 1;
  return true;
}

/** 提取客户端 IP（支持代理转发头） */
function clientIp(req) {
  const fwd = req.headers['x-forwarded-for'];
  if (fwd) return String(fwd).split(',')[0].trim();
  return req.socket.remoteAddress || 'unknown';
}

/** 限频读接口（per-IP） */
function limitRead(req) {
  return allow('r:' + clientIp(req), config.RATE_LIMIT_READ_PER_MIN);
}

/** 限频写接口（per-IP） */
function limitWrite(req) {
  return allow('w:' + clientIp(req), config.RATE_LIMIT_WRITE_PER_MIN);
}

/** 限频上报接口（per-deviceId，回退到 IP） */
function limitReport(req, deviceId) {
  const key = deviceId || clientIp(req);
  return allow('d:' + key, config.RATE_LIMIT_REPORT_PER_MIN);
}

module.exports = { allow, clientIp, limitRead, limitWrite, limitReport };
