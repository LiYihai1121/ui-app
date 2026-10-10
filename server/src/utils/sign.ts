import * as crypto from "node:crypto";
import { config } from "../config";

/** 签名响应头：客户端按同一密钥对收到的原始 body 复算 HMAC 比对 */
export const RULES_SIGNATURE_HEADER = "X-Rules-Signature";

function hmac(key: string, body: string): string {
  return crypto.createHmac("sha256", key).update(body, "utf8").digest("hex");
}

/**
 * 对响应体原文签名。
 *
 * 签名对象是「即将发出的字节串」本身而非重新序列化的对象——客户端验签时
 * 用的就是它收到的那串字节，避免两端 JSON 序列化键序差异造成假失配。
 * 密钥为空时返回空串（调用方据此不下发签名头）。
 */
export function signRulesBody(body: string): string {
  if (!config.RULES_SIGNING_KEY) return "";
  return hmac(config.RULES_SIGNING_KEY, body);
}

/** 校验签名；密钥为空返回 false（未配置签名的服务端发出的载荷一律拒信） */
export function verifyRulesBodySignature(body: string, signature: string | null): boolean {
  if (!config.RULES_SIGNING_KEY || !signature) return false;
  const presented = signature.startsWith("sha256=")
    ? signature.slice("sha256=".length)
    : signature;
  const expected = Buffer.from(hmac(config.RULES_SIGNING_KEY, body), "utf8");
  const actual = Buffer.from(presented, "utf8");
  if (expected.length !== actual.length) return false;
  return crypto.timingSafeEqual(expected, actual);
}
