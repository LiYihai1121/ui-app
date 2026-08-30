import { timingSafeEqual } from "node:crypto";
import { Buffer } from "node:buffer";
import { config } from "../config";

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

export function checkAdminAuth(req: Request): boolean {
  const token = config.ADMIN_TOKEN;
  if (!token) return false;
  const header = req.headers.get("authorization") ?? "";
  if (!header.toLowerCase().startsWith("bearer ")) return false;
  return safeEqual(header.slice(7).trim(), token);
}

export function requireAdmin(
  req: Request
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
  return { ok: true };
}
