import { createSign, createPrivateKey, type KeyObject } from "node:crypto";
import { config } from "../config";

/**
 * 规则链路签名（ECDSA P-256 / SHA-256）。
 *
 * 威胁模型：明文 HTTP 的局域网链路上，攻击者可篡改规则响应、下发恶意规则
 * 驱动客户端无障碍点击。签名覆盖**响应原始字节**（不是重新序列化的对象），
 * 客户端用内置公钥验签通过才落地——安全性不依赖传输层，天然兼容 HTTP。
 *
 * 密钥：`RULES_SIGNING_KEY` 环境变量（PKCS8 PEM 或 base64 DER），
 * 由 `bun run keys:gen` 生成；公钥（`RULES_SIGNING_PUBKEY`）内置进客户端。
 * 未配置密钥时**不签名**（向后兼容，与旧客户端/旧部署共存）；
 * 密钥损坏时同样不签名而不是 500——规则端点是客户端唯一数据源，宁可降级
 * 也不中断（客户端侧是否强制验签由其内置公钥决定）。
 */

let cachedKey: KeyObject | null = null;
let cachedRaw = "";

function loadSigningKey(): KeyObject | null {
  const raw = config.RULES_SIGNING_KEY;
  if (!raw) return null;
  if (raw === cachedRaw) return cachedKey;
  try {
    const key = raw.includes("-----BEGIN")
      ? createPrivateKey(raw)
      : createPrivateKey({ key: Buffer.from(raw, "base64"), format: "der", type: "pkcs8" });
    cachedRaw = raw;
    cachedKey = key;
  } catch {
    cachedRaw = raw;
    cachedKey = null;
  }
  return cachedKey;
}

/** 对响应原始 body 字节签名；未配置/损坏密钥时返回 null（不签） */
export function signRulesBody(body: string): string | null {
  const key = loadSigningKey();
  if (!key) return null;
  try {
    const signer = createSign("sha256");
    signer.update(body, "utf8");
    signer.end();
    return signer.sign(key).toString("base64");
  } catch {
    return null;
  }
}

/** 供启动日志使用：签名是否真正就位 */
export function isRulesSigningConfigured(): boolean {
  return loadSigningKey() !== null;
}

/** 仅供测试：清空密钥缓存（config.RULES_SIGNING_KEY 变更后调用） */
export function _resetRulesSignerForTests(): void {
  cachedRaw = "";
  cachedKey = null;
}
