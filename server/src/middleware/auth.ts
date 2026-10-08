import { timingSafeEqual } from "node:crypto";
import { Buffer } from "node:buffer";
import { config } from "../config";
import { verifyTotp } from "../utils/totp";

/** 常数时间字符串比较，避免令牌校验的时序侧信道 */
function safeEqual(a: string, b: string): boolean {
  const ba = Buffer.from(a, "utf8");
  const bb = Buffer.from(b, "utf8");
  if (ba.length !== bb.length) {
    // 长度不等时仍执行一次同长度比较，抹平分支耗时差异
    timingSafeEqual(ba, ba);
    return false;
  }
  return timingSafeEqual(ba, bb);
}

// ---------- 2FA（TOTP）失败锁定 ----------
//
// 安全契约：TOTP 只有 10^6 个 code、±1 窗口下任一时刻有 3 个有效值，
// 仅靠端点限流仍可在数天内被撞出；失败计数必须单独封顶——
// 单 IP 连续失败 [TWOFA_MAX_FAILURES] 次即锁定 [TWOFA_LOCK_MS]，成功验证即清零。
// 锁定与失败计数按 IP 隔离（remoteIp 由限流层的 clientIp 提供）。

const TWOFA_MAX_FAILURES = 5;
const TWOFA_LOCK_MS = 15 * 60 * 1000;
const TWOFA_MAX_TRACKED_IPS = 10000;

interface TwofaFailState {
  count: number;
  lockedUntil: number;
}

const twofaFailures = new Map<string, TwofaFailState>();

function pruneTwofaFailures(): void {
  const now = Date.now();
  for (const [ip, st] of twofaFailures) {
    if (st.lockedUntil <= now && st.count === 0) twofaFailures.delete(ip);
  }
  // 键来自请求 IP，仍可能被伪造/轮换撑大：总量封顶，淘汰最早解锁的
  if (twofaFailures.size > TWOFA_MAX_TRACKED_IPS) {
    let oldestKey: string | null = null;
    let oldest = Infinity;
    for (const [k, st] of twofaFailures) {
      if (st.lockedUntil < oldest) {
        oldest = st.lockedUntil;
        oldestKey = k;
      }
    }
    if (oldestKey !== null) twofaFailures.delete(oldestKey);
  }
}

function recordTwofaFailure(ip: string): void {
  pruneTwofaFailures();
  const st = twofaFailures.get(ip) ?? { count: 0, lockedUntil: 0 };
  st.count += 1;
  if (st.count >= TWOFA_MAX_FAILURES) {
    st.lockedUntil = Date.now() + TWOFA_LOCK_MS;
    st.count = 0;
  }
  twofaFailures.set(ip, st);
}

function isTwofaLocked(ip: string): boolean {
  const st = twofaFailures.get(ip);
  return !!st && st.lockedUntil > Date.now();
}

/** 仅供测试：清空 2FA 失败锁定状态 */
export function _resetTwofaForTests(): void {
  twofaFailures.clear();
}

// ---------- 鉴权 ----------

/**
 * 校验 admin 身份：Bearer token 为第一因子；配置了 `ADMIN_TOTP_SECRET` 时，
 * `X-2FA-Code` 头携带的 TOTP 为第二因子（缺一即拒）。
 *
 * 顺序刻意固定：token 先校验（错误 token 不消耗 2FA 失败计数）→
 * 锁定检查 → code 校验（失败计数 +1，成功清零）。
 */
export function requireAdmin(
  req: Request,
  remoteIp: string = "unknown"
): { ok: true } | { ok: false; status: number; error: string } {
  if (!config.ADMIN_TOKEN) {
    return { ok: false, status: 503, error: "ADMIN_TOKEN not configured" };
  }
  const header = req.headers.get("authorization") ?? "";
  if (!header.toLowerCase().startsWith("bearer ")) {
    return { ok: false, status: 401, error: "unauthorized" };
  }
  if (!safeEqual(header.slice(7).trim(), config.ADMIN_TOKEN)) {
    return { ok: false, status: 401, error: "unauthorized" };
  }

  // 第二因子：未配置密钥时 2FA 关闭（兼容旧行为）
  if (config.ADMIN_TOTP_SECRET) {
    if (isTwofaLocked(remoteIp)) {
      return { ok: false, status: 429, error: "too many 2fa attempts" };
    }
    const code = req.headers.get("x-2fa-code") ?? "";
    if (!code) {
      return { ok: false, status: 401, error: "2fa code required" };
    }
    if (!verifyTotp(code, config.ADMIN_TOTP_SECRET)) {
      recordTwofaFailure(remoteIp);
      return { ok: false, status: 401, error: "invalid 2fa code" };
    }
    twofaFailures.delete(remoteIp);
  }
  return { ok: true };
}

export function checkAdminAuth(req: Request, remoteIp: string = "unknown"): boolean {
  return requireAdmin(req, remoteIp).ok;
}
