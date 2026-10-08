import * as path from "node:path";
import * as fs from "node:fs";
import * as os from "node:os";
import { config } from "./src/config";
import { handleApi } from "./src/api";
import { withCors, errorJson, applySecurityHeaders, applyAdminContentSecurityPolicy } from "./src/utils/httpUtil";
import { logger } from "./src/utils/logger";
import { cleanupOldStats, flush, stopBackgroundTimers as stopStoreTimers } from "./src/storage/store";
import { stopBackgroundTimers as stopRateLimitTimers } from "./src/middleware/rateLimit";
import { recordAccess } from "./src/middleware/accessLog";

export interface StartOptions {
  port?: number;
  host?: string;
  adminToken?: string;
  apkFile?: string;
  dataDir?: string;
  /** TLS 证书/私钥 PEM 文件路径；两者同时给定时启用 HTTPS，否则回退 HTTP */
  tls?: { certFile?: string; keyFile?: string };
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

  // TLS：证书与私钥同时给定时启用 HTTPS；否则退化为 HTTP。
  // 部署指引见 docs/development/DEV-ENVIRONMENT.md 与 server 根 README 注释。
  const certFile = options.tls?.certFile ?? config.TLS_CERT_FILE;
  const keyFile = options.tls?.keyFile ?? config.TLS_KEY_FILE;
  const tlsEnabled = certFile !== "" && keyFile !== "";
  if (tlsEnabled) {
    const missing = [certFile, keyFile].filter((f) => !fs.existsSync(f));
    if (missing.length > 0) {
      throw new Error(`TLS 文件缺失：${missing.join(", ")}（同时配置 TLS_CERT_FILE 与 TLS_KEY_FILE 才启用 HTTPS）`);
    }
  }

  const server: Bun.Server<undefined> = Bun.serve({
    port,
    hostname: host,
    // 协议层拒绝超大请求体（chunked 无 content-length 时由 Bun 直接拦截，堵内存放大）
    maxRequestBodySize: config.MAX_BODY,
    tls: tlsEnabled
      ? { cert: Bun.file(certFile), key: Bun.file(keyFile) }
      : undefined,
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
        resp = withCors(
          new Response(JSON.stringify({ error: String(e?.message ?? e) }), {
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
        const headers = new Headers({ "Content-Type": "text/html; charset=utf-8" });
        applySecurityHeaders(headers);
        return withCors(
          new Response(html.replaceAll("{{APP_VERSION}}", APP_VERSION), {
            headers,
          }),
          origin
        );
      }
      if (url.pathname === "/admin") {
        const headers = new Headers({ "Content-Type": "text/html; charset=utf-8" });
        applySecurityHeaders(headers);
        applyAdminContentSecurityPolicy(headers);
        return withCors(
          new Response(Bun.file(path.join(config.PUBLIC_DIR, "admin.html")), {
            headers,
          }),
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
  logger.info(`收到 ${signal}，正在优雅停机…`, { signal });
  flush();
  server?.stop(true);
  // 退出前显式停止后台定时器，避免进程被 GC/清理任务拖住（FOLLOW-UP #7 setInterval 修复）
  stopStoreTimers();
  stopRateLimitTimers();
  process.exit(0);
}

if (import.meta.main) {
  server = startServer();
  const scheme = tlsConfigured() ? "https" : "http";
  if (server) {
    logger.info(`运行于 ${scheme}://${server.hostname}:${server.port}`, {
      scheme,
      host: server.hostname,
      port: server.port,
    });
  }
  const displayBase = `${scheme}://${server?.hostname ?? config.HOST}:${server?.port ?? config.PORT}`;
  logger.info(`落地页: ${displayBase}/`);
  logger.info(`管理后台: ${displayBase}/admin`);
  if (!config.ADMIN_TOKEN) {
    logger.warn("未配置 ADMIN_TOKEN，写接口将返回 503", { hint: "设置环境变量 ADMIN_TOKEN 后重启" });
  }
  for (const info of Object.values(os.networkInterfaces())) {
    for (const ni of info ?? []) {
      if (ni.family === "IPv4" && !ni.internal) {
        logger.info(`LAN: ${scheme}://${ni.address}:${server?.port}`);
      }
    }
  }

  process.on("SIGTERM", () => shutdown("SIGTERM"));
  process.on("SIGINT", () => shutdown("SIGINT"));
}

/** 基于配置判断当前是否启用 TLS（两个文件都设置才启用，与 startServer 一致） */
function tlsConfigured(): boolean {
  return config.TLS_CERT_FILE !== "" && config.TLS_KEY_FILE !== "";
}
