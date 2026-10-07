import * as path from "node:path";
import * as fs from "node:fs";
import * as os from "node:os";
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

  const tlsConfig = config.TLS_CERT && config.TLS_KEY
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
