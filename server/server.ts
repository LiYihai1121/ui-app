import * as path from "node:path";
import * as fs from "node:fs";
import * as os from "node:os";
import { isIP } from "node:net";
import { config } from "./src/config";
import { handleApi } from "./src/api";
import { withCors, errorJson } from "./src/utils/httpUtil";
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

function isLoopbackHost(host: string): boolean {
  const normalized = host.replace(/^\[|\]$/g, "").toLowerCase();
  if (normalized === "localhost" || normalized === "::1") return true;
  return isIP(normalized) === 4 && Number(normalized.split(".")[0]) === 127;
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

/**
 * 全响应安全头（含 Bun error() 兜底路径与 OPTIONS 预检）。
 *
 * CSP 里的 script-src 'unsafe-inline' 是**已知取舍**：管理后台
 * public/admin.html 由内联 <script> 与 onclick handler 构成，收紧到
 * 'self' 会直接破坏发布/模拟器入口。frame-ancestors 'none' + X-Frame-Options
 * 已挡住点击劫持；把内联脚本外移后可摘掉 'unsafe-inline'（已列入后续）。
 */
function applySecurityHeaders(resp: Response): Response {
  const h = resp.headers;
  h.set("X-Content-Type-Options", "nosniff");
  h.set("X-Frame-Options", "DENY");
  h.set("Referrer-Policy", "no-referrer");
  h.set(
    "Content-Security-Policy",
    [
      "default-src 'self'",
      "script-src 'self' 'unsafe-inline'",
      "style-src 'self' 'unsafe-inline'",
      "img-src 'self' data:",
      "connect-src 'self'",
      "base-uri 'self'",
      "form-action 'self'",
      "frame-ancestors 'none'",
    ].join("; ")
  );
  // 仅 TLS 模式下发 HSTS：明文部署发 HSTS 没有意义且会钉死浏览器
  if (config.TLS_CERT && config.TLS_KEY) {
    h.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
  }
  return resp;
}

/** 启动服务；明文 HTTP 仅允许 loopback 绑定用于本地开发或反向代理。 */
export function startServer(options: StartOptions = {}): Bun.Server<undefined> {
  const port = options.port ?? config.PORT;
  const host = options.host ?? config.HOST;
  const hasTlsCert = Boolean(config.TLS_CERT);
  const hasTlsKey = Boolean(config.TLS_KEY);
  if (hasTlsCert !== hasTlsKey) {
    throw new Error("TLS_CERT and TLS_KEY must both be configured");
  }
  if (!hasTlsCert && !isLoopbackHost(host)) {
    throw new Error(
      "TLS_CERT and TLS_KEY are required for non-loopback binds; use a loopback bind behind a TLS-terminating reverse proxy for proxy deployments"
    );
  }

  if (options.adminToken !== undefined) config.ADMIN_TOKEN = options.adminToken;
  if (options.apkFile !== undefined) config.APK_FILE = options.apkFile;
  if (options.dataDir !== undefined) {
    config.DATA_DIR = options.dataDir;
    config.RULES_FILE = path.join(options.dataDir, "rules.json");
    config.STATS_DIR = path.join(options.dataDir, "stats");
    config.BACKUP_DIR = path.join(options.dataDir, "backups");
  }

  if (config.STATS_DIR_CLEANUP_ON_START) cleanupOldStats();

  const tlsConfig = hasTlsCert && hasTlsKey
    ? { tls: { cert: Bun.file(config.TLS_CERT), key: Bun.file(config.TLS_KEY) } }
    : {};

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
        const status =
          typeof e?.statusCode === "number" ? e.statusCode : 500;
        // <500 的 HttpError 消息是固定文案（body too large 等），可回显；
        // 500 的异常消息可能含内部路径/配置细节，只进服务端日志，响应体通用化
        if (status >= 500) {
          logger.error("unhandled_error", {
            status,
            path: url.pathname,
            error: String(e?.message ?? e),
          });
        }
        const message = status >= 500 ? "internal error" : String(e?.message ?? e);
        resp = withCors(
          new Response(JSON.stringify({ error: message }), {
            status,
            headers: { "Content-Type": "application/json; charset=utf-8" },
          }),
          origin
        );
      }
      recordAccess({
        method: req.method,
        path: url.pathname,
        status: resp.status,
        ip,
        ms: Math.round(performance.now() - started),
      });
      return applySecurityHeaders(resp);
    },
    error() {
      return applySecurityHeaders(
        new Response(JSON.stringify({ error: "internal error" }), {
          status: 500,
          headers: { "Content-Type": "application/json; charset=utf-8" },
        })
      );
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
  if (!config.RULES_SIGNING_KEY) {
    logger.warn("rules_signing_key_missing", {
      impact: "cloud_rules_integrity_off",
      hint: "配置 RULES_SIGNING_KEY 后 /api/*/rules/latest 才会下发 HMAC 签名；配了密钥的客户端会拒绝未签名载荷",
    });
  } else {
    logger.info("rules_signing_enabled", { header: "X-Rules-Signature" });
  }
  if (config.TRUSTED_PROXIES.length === 0) {
    logger.info("trusted_proxies_empty", {
      impact: "x_forwarded_for_ignored",
      hint: "反向代理部署时用 TRUSTED_PROXIES 声明代理 IP，否则限流以 socket IP 为键",
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
