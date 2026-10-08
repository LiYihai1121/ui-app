import { describe, it, expect } from "bun:test";
import {
  base32Decode,
  base32Encode,
  generateTotpSecret,
  totpAt,
  verifyTotp,
  buildOtpauthUri,
} from "../src/utils/totp";

/**
 * TOTP（RFC 6238）单测：算法正确性用 RFC 官方测试向量钉死，
 * 否则「自己实现的 TOTP」与验证器 App 对不上等于 2FA 形同虚设。
 *
 * 向量：密钥 = ASCII "12345678901234567890"（base32: GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ），
 * SHA-1、8 位数字、30 秒周期，时间戳/期望值取自 RFC 6238 附录 B。
 */
const RFC_SECRET_B32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

describe("base32", () => {
  it("decode/encode 往返一致（忽略大小写与填充）", () => {
    const buf = Buffer.from("12345678901234567890", "ascii");
    expect(base32Decode(RFC_SECRET_B32).equals(buf)).toBe(true);
    expect(base32Decode("gezdgnbvgy3tqojqgezdgnbvgy3tqojq").equals(buf)).toBe(true);
    expect(base32Encode(buf)).toBe(RFC_SECRET_B32);
  });

  it("非法字符直接拒绝", () => {
    expect(() => base32Decode("!!!!")).toThrow();
    // base32 字母表不含 0/1/8/9
    expect(() => base32Decode("ABC0")).toThrow();
  });

  it("生成的密钥为 160 位熵的 base32（32 字符）", () => {
    const secret = generateTotpSecret();
    expect(secret).toMatch(/^[A-Z2-7]{32}$/);
    expect(base32Decode(secret).length).toBe(20);
  });
});

describe("totpAt（RFC 6238 测试向量）", () => {
  const vectors: Array<[number, string]> = [
    // T=59 → counter=1，等于 RFC 4226 HOTP(counter=1)=1094287082（8 位截断 94287082）
    [59, "94287082"],
    [1111111109, "07081804"],
    [1111111111, "14050471"],
    [1234567890, "89005924"],
    [2000000000, "69279037"],
    [20000000000, "65353130"],
  ];

  for (const [t, expected] of vectors) {
    it(`T=${t} → ${expected}`, () => {
      expect(totpAt(RFC_SECRET_B32, t * 1000, 8)).toBe(expected);
    });
  }

  it("6 位模式 = 8 位向量的末 6 位", () => {
    for (const [t, expected] of vectors) {
      expect(totpAt(RFC_SECRET_B32, t * 1000, 6)).toBe(expected.slice(-6));
    }
  });
});

describe("verifyTotp", () => {
  const now = 1111111111 * 1000;

  it("当前窗口与 ±1 窗口的 code 均通过（容忍时钟漂移）", () => {
    for (const skewSec of [-30, 0, 30]) {
      const code = totpAt(RFC_SECRET_B32, now + skewSec * 1000, 6);
      expect(verifyTotp(code, RFC_SECRET_B32, { timestampMs: now })).toBe(true);
    }
  });

  it("±2 窗口之外拒绝", () => {
    const code = totpAt(RFC_SECRET_B32, now + 60 * 1000, 6);
    expect(verifyTotp(code, RFC_SECRET_B32, { timestampMs: now })).toBe(false);
  });

  it("错误 code / 非数字 / 长度不符一律拒绝", () => {
    expect(verifyTotp("000000", RFC_SECRET_B32, { timestampMs: now })).toBe(false);
    expect(verifyTotp("abcdef", RFC_SECRET_B32, { timestampMs: now })).toBe(false);
    expect(verifyTotp("12345", RFC_SECRET_B32, { timestampMs: now })).toBe(false);
    expect(verifyTotp("", RFC_SECRET_B32, { timestampMs: now })).toBe(false);
    expect(verifyTotp(null as unknown as string, RFC_SECRET_B32, { timestampMs: now })).toBe(false);
  });

  it("密钥非法时返回 false 而不是抛出（fail-closed）", () => {
    expect(verifyTotp("123456", "!!!!", { timestampMs: now })).toBe(false);
  });
});

describe("buildOtpauthUri", () => {
  it("生成验证器可识别的 otpauth:// 链接", () => {
    const uri = buildOtpauthUri(RFC_SECRET_B32, "admin", "AdSkip Server");
    expect(uri.startsWith("otpauth://totp/")).toBe(true);
    expect(uri).toContain("secret=" + RFC_SECRET_B32);
    expect(uri).toContain("algorithm=SHA1");
    expect(uri).toContain("digits=6");
    expect(uri).toContain("period=30");
  });
});
