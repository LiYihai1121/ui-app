/**
 * 生成管理端 2FA（TOTP）密钥。
 *
 * 用法：cd server && bun run totp:gen
 *
 * 输出的 base32 密钥配到环境变量 ADMIN_TOTP_SECRET 即启用 2FA：
 *   ADMIN_TOTP_SECRET=<secret> ADMIN_TOKEN=<token> bun run start
 * 验证器 App（Google/Microsoft Authenticator）用 otpauth:// 链接或手动录入。
 *
 * 密钥**不入库**（不写文件、不进版本库）：打印到终端由部署者自行保存，
 * 与 ADMIN_TOKEN 同等对待。泄露等同第二因子失守，重新生成即可轮换。
 */
import { generateTotpSecret, buildOtpauthUri } from "../src/utils/totp";

const secret = generateTotpSecret();
const issuer = process.env.ADMIN_TOTP_ISSUER ?? "AdSkip Server";

console.log("管理端 2FA（TOTP）密钥已生成。");
console.log("");
console.log(`  ADMIN_TOTP_SECRET=${secret}`);
console.log("");
console.log("验证器 App 添加条目：");
console.log(`  ${buildOtpauthUri(secret, "admin", issuer)}`);
console.log("");
console.log("启用方式：把 ADMIN_TOTP_SECRET 配入服务端环境变量后重启；");
console.log("管理端调用需在 Authorization 之外再带 X-2FA-Code 头（当前 6 位动态码）。");
console.log("轮换：重新运行本命令并替换环境变量（旧密钥立即失效）。");
