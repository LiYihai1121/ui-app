import type { Handler } from "../utils/httpUtil";
import { jsonResponse, errorJson } from "../utils/httpUtil";
import { requireAdmin } from "../middleware/auth";
import { limitRead } from "../middleware/rateLimit";
import { recentAccess } from "../middleware/accessLog";
import * as rulesApi from "./rulesApi";
import * as statsApi from "./statsApi";
import * as healthApi from "./healthApi";

/** API 路由分发（v1 优先，回退 v0；均精确匹配 pathname） */
export async function handleApi(
  req: Request,
  url: URL,
  ctx: { ip: string }
): Promise<Response> {
  const method = req.method;
  const p = url.pathname;

  if (method === "GET" && p === "/api/v1/rules/latest") {
    return rulesApi.v1_latest(req, url, ctx);
  }
  if (method === "PUT" && p === "/api/v1/rules") {
    return rulesApi.v1_publish(req, url, ctx);
  }
  if (method === "POST" && p === "/api/v1/rules/test") {
    return rulesApi.v1_testRule(req, url, ctx);
  }
  if (method === "POST" && p === "/api/v1/reports/batch") {
    return rulesApi.v1_batchReport(req, url, ctx);
  }
  if (method === "GET" && p === "/api/v1/stats/summary") {
    return statsApi.summary(req, url, ctx);
  }
  if (method === "GET" && p === "/api/v1/health") {
    return healthApi.health(req, url, ctx);
  }
  if (method === "GET" && p === "/api/v1/admin/logs") {
    const auth = requireAdmin(req);
    if (!auth.ok) return errorJson(auth.status, auth.error);
    if (!limitRead(req, ctx.ip)) return errorJson(429, "rate limited");
    return jsonResponse({ entries: recentAccess() });
  }
  if (method === "GET" && p === "/api/rules/latest") {
    return rulesApi.v0_latest(req, url, ctx);
  }
  if (method === "PUT" && p === "/api/rules") {
    return rulesApi.v0_publish(req, url, ctx);
  }
  if (method === "POST" && p === "/api/skip") {
    return rulesApi.v0_skip(req, url, ctx);
  }
  if (method === "GET" && p === "/api/stats/summary") {
    return statsApi.summary(req, url, ctx);
  }

  return new Response(JSON.stringify({ error: "not found" }), {
    status: 404,
    headers: { "Content-Type": "application/json; charset=utf-8" },
  });
}
