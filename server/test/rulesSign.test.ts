import { describe, it, expect, afterAll } from "bun:test";
import { generateKeyPairSync, createVerify, type KeyObject } from "node:crypto";
import { config } from "../src/config";
import { signRulesBody, _resetRulesSignerForTests } from "../src/utils/rulesSigner";
import { v1_latest, v0_latest } from "../src/api/rulesApi";

/**
 * 规则链路签名（防 MITM 注入）契约测试。
 *
 * 威胁模型：明文 HTTP 的局域网链路上，攻击者可篡改规则拉取端点的响应，
 * 向客户端下发恶意规则驱动无障碍点击。签名对**响应原始字节**做
 * ECDSA P-256 / SHA-256，客户端用内置公钥验签后才落地——不依赖传输层。
 */

// 测试用一次性 P-256 密钥对（内存生成，不落盘、不入库）
const { privateKey, publicKey } = generateKeyPairSync("ec", { namedCurve: "P-256" });
const PRIV_PEM = privateKey.export({ type: "pkcs8", format: "pem" }) as string;
const PUB_SPKI_B64 = (publicKey.export({ type: "spki", format: "der" }) as Buffer).toString("base64");

function verifyWith(key: KeyObject, body: string, sigB64: string): boolean {
  const v = createVerify("sha256");
  v.update(body, "utf8");
  v.end();
  return v.verify(key, Buffer.from(sigB64, "base64"));
}

function verifyBody(body: string, sigB64: string): boolean {
  return verifyWith(publicKey, body, sigB64);
}

function useKey(pem: string | null): void {
  config.RULES_SIGNING_KEY = pem ?? "";
  _resetRulesSignerForTests();
}

afterAll(() => {
  useKey(null);
});

describe("signRulesBody", () => {
  it("配置密钥后产出可验证的 base64 签名", () => {
    useKey(PRIV_PEM);
    const body = JSON.stringify({ rules: { globalKeywords: ["跳过"] } });
    const sig = signRulesBody(body);
    expect(sig).not.toBeNull();
    expect(verifyBody(body, sig!)).toBe(true);
  });

  it("body 被篡改（哪怕一字节）验签失败", () => {
    useKey(PRIV_PEM);
    const body = JSON.stringify({ rules: { globalKeywords: ["跳过"] } });
    const sig = signRulesBody(body)!;
    const tampered = body.replace("跳过", "允许");
    expect(verifyBody(tampered, sig)).toBe(false);
  });

  it("未配置密钥时不签名（返回 null，向后兼容）", () => {
    useKey(null);
    expect(signRulesBody("{}")).toBeNull();
  });

  it("密钥损坏时返回 null 而不是抛出（调用方降级为不签，绝不 500 规则端点）", () => {
    useKey("not-a-key");
    expect(signRulesBody("{}")).toBeNull();
  });
});

describe("规则端点签名（X-Rules-Signature）", () => {
  it("v1_latest 签名覆盖响应原始 body", async () => {
    useKey(PRIV_PEM);
    const res = await v1_latest(new Request("http://localhost/api/v1/rules/latest"), new URL("http://localhost/api/v1/rules/latest"), { ip: "10.0.0.1" });
    expect(res.status).toBe(200);
    const sig = res.headers.get("x-rules-signature");
    expect(sig).toBeTruthy();
    const body = await res.text();
    expect(verifyBody(body, sig!)).toBe(true);
  });

  it("v0_latest 同样签名", async () => {
    useKey(PRIV_PEM);
    const res = await v0_latest(new Request("http://localhost/api/rules/latest"), new URL("http://localhost/api/rules/latest"), { ip: "10.0.0.1" });
    expect(res.status).toBe(200);
    const sig = res.headers.get("x-rules-signature");
    expect(sig).toBeTruthy();
    const body = await res.text();
    expect(verifyBody(body, sig!)).toBe(true);
  });

  it("未配置密钥时不带签名头（旧部署行为不变）", async () => {
    useKey(null);
    const res = await v1_latest(new Request("http://localhost/api/v1/rules/latest"), new URL("http://localhost/api/v1/rules/latest"), { ip: "10.0.0.1" });
    expect(res.headers.get("x-rules-signature")).toBeNull();
    await res.text();
  });

  it("密钥轮换后旧签名失效、新签名可验（换钥即吊销旧钥）", () => {
    const other = generateKeyPairSync("ec", { namedCurve: "P-256" });
    useKey(PRIV_PEM);
    const body = '{"v":1}';
    const oldSig = signRulesBody(body)!;
    useKey(other.privateKey.export({ type: "pkcs8", format: "pem" }) as string);
    const newSig = signRulesBody(body)!;
    // 轮换后的公钥（客户端升级内置新公钥）拒绝旧私钥签名、接受新私钥签名
    expect(verifyWith(other.publicKey, body, oldSig)).toBe(false);
    expect(verifyWith(other.publicKey, body, newSig)).toBe(true);
  });
});
