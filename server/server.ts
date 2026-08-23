import * as path from "node:path";
import * as fs from "node:fs";
import { config } from "./src/config";
import { handleApi } from "./src/api";
import { withCors, errorJson } from "./src/httpUtil";
import { cleanupOldStats, flush } from "./src/store";

export interface StartOptions {
  port?: number;
  host?: string;
  adminToken?: string;
  apkFile?: string;
  dataDir?: string;
}

/** 启动 HTTP 服务，返回 Bun.Server（供测试注入端口/数据目录/令牌） */
export function startServer(options: StartOptions = {}): Bun.Server {
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

  const server = Bun.serve({
    port,
    hostname: host,
    async fetch(req) {
      const url = new URL(req.url);
      const origin = req.headers.get("origin");
      const ip = server.requestIP(req)?.address ?? "unknown";
      const ctx = { ip };

      try {
        if (req.method === "OPTIONS") {
          return withCors(new Response(null, { status: 204 }), origin);
        }

        if (url.pathname.startsWith("/api/")) {
          return withCors(await handleApi(req, url, ctx), origin);
        }

        // ---------- 静态资源 ----------
        if (req.method === "GET") {
          if (url.pathname === "/") {
            return withCors(
              new Response(Bun.file(path.join(config.PUBLIC_DIR, "index.html"))),
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
      } catch (e: any) {
        const status =
          typeof e?.statusCode === "number" ? e.statusCode : 500;
        return withCors(
          new Response(JSON.stringify({ error: String(e?.message ?? e) }), {
            status,
            headers: { "Content-Type": "application/json; charset=utf-8" },
          }),
          origin
        );
      }
    },
    error() {
      return new Response("Internal Error", { status: 500 });
    },
  });

  return server;
}

function shutdown(signal: string): void {
  console.log(`\n[AdSkip Server] 收到 ${signal}，正在优雅停机…`);
  flush();
  server.stop(true);
  process.exit(0);
}

if (import.meta.main) {
  const server = startServer();
  console.log(`[AdSkip Server] 运行于 http://${server.hostname}:${server.port}`);
  console.log(`[AdSkip Server] 落地页: http://${server.hostname}:${server.port}/`);
  console.log(`[AdSkip Server] 管理后台: http://${server.hostname}:${server.port}/admin`);
  if (!config.ADMIN_TOKEN) {
    console.warn("[AdSkip Server] 警告：未配置 ADMIN_TOKEN，写接口将返回 503");
  }
  for (const info of Object.values(require("node:os").networkInterfaces())) {
    for (const ni of info ?? []) {
      if (ni.family === "IPv4" && !ni.internal) {
        console.log(`  LAN: http://${ni.address}:${server.port}`);
      }
    }
  }

  process.on("SIGTERM", () => shutdown("SIGTERM"));
  process.on("SIGINT", () => shutdown("SIGINT"));
}
