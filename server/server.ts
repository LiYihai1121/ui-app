import * as path from "node:path";
import * as fs from "node:fs";
import * as os from "node:os";
import { config } from "./src/config";
import { handleApi } from "./src/api";
import { withCors, errorJson, errorResponseFrom, HttpError } from "./src/utils/httpUtil";
import { base32Decode } from "./src/utils/totp";
import { isRulesSigningConfigured } from "./src/utils/rulesSigner";
import { cleanupOldStats, flush } from "./src/storage/store";
import { recordAccess } from "./src/middleware/accessLog";
import { logger } from "./src/utils/logger";

export interface StartOptions {
  port?: number;
  host?: string;
  adminToken?: string;
  apkFile?: string;
  dataDir?: string;
}

/**
 * 落地页版本占位符 `{{APP_VERSION}}` 的数据源。
 * 以 `server/package.json` 为单一事实源（发布流程会同步 bump），
 * 避免静态 HTML 写死版本号导致落地页与已发布版本漂移。
 */
function readAppVersion(): string {
  try {
    const raw = fs.readFileSync(path.join(import.meta.dir, "package.json"), "utf8");
    return (JSON.parse(raw) as { version?: string }).version ?? "dev";
  } catch {
    return "dev";
  }
}

const APP_VERSION = readAppVersion();

/** 模块级引用，供优雅停机使用（仅 import.meta.main 场景赋值） */
let server: Bun.Server<undefined> | null = null;

/** 启动 HTTP 服务，返回 Bun.Server（供测试注入端口/数据目录/令牌） */
export function startServer(options: StartOptions = {}): Bun.Server<undefined> {
  if (options.adminToken !== undefined) config.ADMIN_TOKEN = options.adminToken;
  if (options.apkFile !== undefined) config.APK_FILE = options.apkFile;
  if (options.dataDir !== undefined) {
    config.DATA_DIR = options.dataDir;
    config.RULES_FILE = path.join(options.dataDir, "rules.json");
    config.STATS_DIR = path.join(options.dataDir, "stats");
    config.BACKUP_DIR = path.join(options.dataDir, "backups");
  }

  if (config.STATS_DIR_CLEANUP_ON_START) cleanupOldStats();

  const port = options.port ?? config.PORT;
  const host = options.host ?? config.HOST;

  // TLS 必须整对配置：只给一半（挂了证书忘配私钥）静默回退明文是最危险的失败方式
  if (Boolean(config.TLS_CERT) !== Boolean(config.TLS_KEY)) {
    throw new Error("TLS_CERT and TLS_KEY must be configured together (half-configured TLS is refused)");
  }
  const tlsConfig = config.TLS_CERT && config.TLS_KEY
    ? { tls: { cert: Bun.file(config.TLS_CERT), key: Bun.file(config.TLS_KEY) } }
    : {};

  if (!tlsConfig.tls && host !== "127.0.0.1" && host !== "::1" && host !== "localhost") {
    // 明文 + 非回环监听：admin bearer 令牌与规则流量可被同网段嗅探，必须显式知悉
    logger.warn("plaintext_non_loopback", {
      impact: "admin_token_and_traffic_cleartext",
      hint: "配置 TLS_CERT/TLS_KEY 启用 HTTPS，或把 HOST 收到 127.0.0.1",
    });
  }

  const server: Bun.Server<undefined> = Bun.serve({
    port,
    hostname: host,
    ...tlsConfig,
    // 协议层拒绝超大请求体（chunked 无 content-length 时由 Bun 直接拦截，堵内存放大）
    maxRequestBodySize: config.MAX_BODY,
    async fetch(req) {
      const started = performance.now();
      const url = new URL(req.url);
      const origin = req.headers.get("origin");
      const ip = server.requestIP(req)?.address ?? "unknown";
      const ctx = { ip };

      let resp: Response;
      try {
        resp = await route(req, url, origin, ctx);
      } catch (e: any) {
        // 5xx 不回显内部错误细节（fs 异常等可能带完整路径）；细节只进 JSONL 日志
        if (!(e instanceof HttpError)) {
          logger.error("request_failed", {
            path: url.pathname,
            error: String(e?.message ?? e),
          });
        }
        resp = withCors(errorResponseFrom(e), origin);
      }
      recordAccess({
        method: req.method,
        path: url.pathname,
        status: resp.status,
        ip,
        ms: Math.round(performance.now() - started),
      });
      return resp;
    },
    error() {
      return new Response("Internal Error", { status: 500 });
    },
  });

  async function route(
    req: Request,
    url: URL,
    origin: string | null,
    ctx: { ip: string }
  ): Promise<Response> {
    if (req.method === "OPTIONS") {
      return withCors(new Response(null, { status: 204 }), origin);
    }

    if (url.pathname.startsWith("/api/")) {
      return withCors(await handleApi(req, url, ctx), origin);
    }

    // ---------- 静态资源 ----------
    if (req.method === "GET") {
      if (url.pathname === "/") {
        // 落地页按当前版本渲染：替换 {{APP_VERSION}} 占位符，保证下载入口与发布版本一致
        const html = await Bun.file(
          path.join(config.PUBLIC_DIR, "index.html")
        ).text();
        return withCors(
          new Response(html.replaceAll("{{APP_VERSION}}", APP_VERSION), {
            headers: { "Content-Type": "text/html; charset=utf-8" },
          }),
          origin
        );
      }
      if (url.pathname === "/admin") {
        return withCors(
          new Response(Bun.file(path.join(config.PUBLIC_DIR, "admin.html"))),
          origin
        );
      }
      if (url.pathname === "/download") {
        if (!fs.existsSync(config.APK_FILE)) {
          return withCors(errorJson(404, "apk not found"), origin);
        }
        const file = Bun.file(config.APK_FILE);
        return withCors(
          new Response(file, {
            headers: {
              "Content-Type": "application/vnd.android.package-archive",
              "Content-Disposition": `attachment; filename="${path.basename(
                config.APK_FILE
              )}"`,
              "Content-Length": String(file.size),
            },
          }),
          origin
        );
      }
    }

    return withCors(errorJson(404, "not found"), origin);
  }

  return server;
}

function shutdown(signal: string): void {
  logger.info("shutdown", { signal });
  flush();
  server?.stop(true);
  process.exit(0);
}

if (import.meta.main) {
  server = startServer();
  const protocol = config.TLS_CERT && config.TLS_KEY ? "https" : "http";
  logger.info("server_started", {
    protocol,
    hostname: server.hostname,
    port: server.port,
    adminToken: config.ADMIN_TOKEN ? "configured" : "MISSING",
  });
  if (!config.ADMIN_TOKEN) {
    logger.warn("admin_token_missing", { impact: "write_endpoints_503" });
  }
  if (config.ADMIN_TOTP_SECRET) {
    // 启动即校验密钥合法性：坏密钥会让所有动态码校验失败（fail-closed），
    // 不显式报错会表现为「管理员永远登录不上」且无从排查
    try {
      base32Decode(config.ADMIN_TOTP_SECRET);
      logger.info("admin_2fa_enabled", { mode: "totp-sha1-6-30s" });
    } catch {
      logger.error("admin_2fa_secret_invalid", {
        hint: "ADMIN_TOTP_SECRET 不是合法 base32，2FA 将拒绝所有动态码；运行 bun run totp:gen 重新生成",
      });
    }
  } else {
    logger.warn("admin_2fa_disabled", {
      impact: "admin_token_compromise_grants_full_access",
      hint: "运行 bun run totp:gen 生成密钥并配 ADMIN_TOTP_SECRET 启用双因素",
    });
  }
  if (config.RULES_SIGNING_KEY) {
    if (isRulesSigningConfigured()) {
      logger.info("rules_signing_enabled", { alg: "ecdsa-p256-sha256" });
    } else {
      logger.error("rules_signing_key_invalid", {
        hint: "RULES_SIGNING_KEY 不是合法 PKCS8 密钥，规则响应将不带签名；运行 bun run keys:gen 重新生成",
      });
    }
  } else {
    logger.warn("rules_signing_disabled", {
      impact: "mitm_can_inject_rules_over_cleartext",
      hint: "运行 bun run keys:gen 生成密钥对，配 RULES_SIGNING_KEY 启用规则签名",
    });
  }
  for (const info of Object.values(os.networkInterfaces())) {
    for (const ni of info ?? []) {
      if (ni.family === "IPv4" && !ni.internal) {
        logger.info("lan_endpoint", { address: ni.address, port: server.port, protocol });
      }
    }
  }

  process.on("SIGTERM", () => shutdown("SIGTERM"));
  process.on("SIGINT", () => shutdown("SIGINT"));
}
