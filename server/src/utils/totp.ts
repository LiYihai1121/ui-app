import { createHmac, randomBytes, timingSafeEqual } from "node:crypto";

/**
 * TOTP（RFC 6238）实现——管理端双因素认证的第二因子。
 *
 * 零第三方依赖：只用 `node:crypto` 的 HMAC-SHA1；算法正确性由
 * `test/totp.test.ts` 里的 RFC 6238 附录 B 官方向量钉死，
 * 否则自实现与验证器 App 对不上，2FA 形同虚设。
 *
 * 参数固定为事实标准：SHA-1 / 6 位数字 / 30 秒周期（Google Authenticator
 * 等主流验证器的默认组合）。
 */

const BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
const DIGITS = 6;
const PERIOD_SEC = 30;

/** RFC 4648 base32 解码：忽略空白/连字符/填充，大小写不敏感；非法字符抛错 */
export function base32Decode(input: string): Buffer {
  const clean = String(input).replace(/[\s-]/g, "").replace(/=+$/, "").toUpperCase();
  if (!clean) throw new Error("empty base32 input");
  let bits = 0;
  let value = 0;
  const out: number[] = [];
  for (const ch of clean) {
    const idx = BASE32_ALPHABET.indexOf(ch);
    if (idx < 0) throw new Error("invalid base32 character");
    value = ((value << 5) | idx) >>> 0;
    bits += 5;
    if (bits >= 8) {
      out.push((value >>> (bits - 8)) & 0xff);
      bits -= 8;
    }
  }
  return Buffer.from(out);
}

/** RFC 4648 base32 编码（无填充，验证器普遍接受） */
export function base32Encode(buf: Buffer): string {
  let bits = 0;
  let value = 0;
  let out = "";
  for (const byte of buf) {
    value = ((value << 8) | byte) >>> 0;
    bits += 8;
    while (bits >= 5) {
      out += BASE32_ALPHABET[(value >>> (bits - 5)) & 31];
      bits -= 5;
    }
  }
  if (bits > 0) out += BASE32_ALPHABET[(value << (5 - bits)) & 31];
  return out;
}

/** 生成 160 位熵的 base32 密钥（20 字节 → 32 字符，与验证器惯例一致） */
export function generateTotpSecret(): string {
  return base32Encode(randomBytes(20));
}

/**
 * 计算指定时刻的 TOTP。
 * @param secret base32 密钥
 * @param timestampMs 毫秒时间戳
 * @param digits 位数（默认 6；测试传 8 以对照 RFC 6238 官方向量）
 */
export function totpAt(secret: string, timestampMs: number, digits: number = DIGITS): string {
  const counter = Math.floor(timestampMs / 1000 / PERIOD_SEC);
  const msg = Buffer.alloc(8);
  msg.writeBigUInt64BE(BigInt(counter));
  const mac = createHmac("sha1", base32Decode(secret)).update(msg).digest();
  const offset = mac[mac.length - 1] & 0x0f;
  const code =
    (((mac[offset] & 0x7f) << 24) | (mac[offset + 1] << 16) | (mac[offset + 2] << 8) | mac[offset + 3]) %
    10 ** digits;
  return code.toString().padStart(digits, "0");
}

/**
 * 校验 TOTP code。
 *
 * - `window`：容忍的周期漂移（默认 ±1 个周期，覆盖验证器时钟误差）；
 * - 比较用 timingSafeEqual，且**不提前返回**，抹平逐字节比较的时序差异；
 * - 密钥非法或参数异常一律 false（fail-closed，绝不放行）。
 */
export function verifyTotp(
  code: string | null | undefined,
  secret: string,
  opts: { window?: number; timestampMs?: number } = {}
): boolean {
  const clean = String(code ?? "").replace(/\s/g, "");
  if (!new RegExp(`^\\d{${DIGITS}}$`).test(clean)) return false;
  const window = opts.window ?? 1;
  const now = opts.timestampMs ?? Date.now();
  let ok = false;
  try {
    for (let w = -window; w <= window; w++) {
      const candidate = totpAt(secret, now + w * PERIOD_SEC * 1000);
      const a = Buffer.from(candidate, "ascii");
      const b = Buffer.from(clean, "ascii");
      if (a.length === b.length && timingSafeEqual(a, b)) {
        ok = true; // 不 break：保持恒定比较次数
      }
    }
  } catch {
    return false;
  }
  return ok;
}

/** 验证器 App 识别的 otpauth:// 链接（手动录入或生成二维码用） */
export function buildOtpauthUri(secret: string, account: string, issuer: string): string {
  const label = encodeURIComponent(`${issuer}:${account}`);
  return (
    `otpauth://totp/${label}?secret=${secret}` +
    `&issuer=${encodeURIComponent(issuer)}&algorithm=SHA1&digits=${DIGITS}&period=${PERIOD_SEC}`
  );
}
