/**
 * 生成规则链路签名密钥对（ECDSA P-256）。
 *
 * 用法：cd server && bun run keys:gen
 *
 * - 私钥 `RULES_SIGNING_KEY`：配入服务端环境变量（不入库），用于给规则响应签名；
 * - 公钥 `RULES_SIGNING_PUBKEY`：内置进 Android 客户端（local.properties 的
 *   `adskip.rulesSigningPubkey`），客户端验签通过才落地规则。
 *
 * 轮换：重新运行本命令，替换服务端私钥**与**客户端公钥（发新版）；
 * 旧私钥即刻失效，等同吊销。
 */
import { generateKeyPairSync } from "node:crypto";

const { privateKey, publicKey } = generateKeyPairSync("ec", { namedCurve: "P-256" });

const privB64 = (privateKey.export({ type: "pkcs8", format: "der" }) as Buffer).toString("base64");
const pubB64 = (publicKey.export({ type: "spki", format: "der" }) as Buffer).toString("base64");

console.log("规则链路签名密钥对（ECDSA P-256）已生成。");
console.log("");
console.log("服务端（环境变量，不入库）：");
console.log(`  RULES_SIGNING_KEY=${privB64}`);
console.log("");
console.log("客户端（client/local.properties，随构建内置）：");
console.log(`  adskip.rulesSigningPubkey=${pubB64}`);
console.log("");
console.log("生效：服务端配置私钥后规则响应带 X-Rules-Signature；");
console.log("客户端内置公钥后只接受验签通过的规则（缺签名/验签失败即拒绝落地）。");
console.log("轮换：重跑本命令并同时替换两端（旧私钥即刻失效）。");
