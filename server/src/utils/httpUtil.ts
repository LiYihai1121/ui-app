import { config } from "../config";
import { logger } from "./logger";

/** 请求处理器统一签名 */
export type Handler = (
  req: Request,
  url: URL,
  ctx: { ip: string }
) => Response | Promise<Response>;

/** 携带 HTTP 状态码的可抛出错误 */
export class HttpError extends Error {
  constructor(public statusCode: number, message: string) {
    super(message);
  }
}

/**
 * 依据 CORS 配置写入响应头：白名单命中才回 Allow-Origin，不命中不发（浏览器侧直接拦截）。
 * 安全默认：未配置白名单时**不**发送 Allow-Origin（跨域默认拒绝；同源部署的管理页不受影响），
 * 需要对任意来源开放时显式配置 `CORS_ORIGINS=*`。
 */
export function applyCors(headers: Headers, origin: string | null): void {
  const origins = config.CORS_ORIGINS;
  if (origins && origins.length) {
    if (origins.includes("*")) {
      headers.set("Access-Control-Allow-Origin", "*");
    } else if (origin && origins.includes(origin)) {
      headers.set("Access-Control-Allow-Origin", origin);
      headers.set("Vary", "Origin");
    }
  }
  headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
  headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, If-None-Match");
}

/**
 * 统一安全响应头：所有响应都带 nosniff / 禁嵌入 / 不外泄 Referrer；
 * HTML 响应另加 CSP 与 COOP；TLS 启用时加 HSTS（明文部署下 HSTS 无意义且会锁死回退路径）。
 */
export function applySecurityHeaders(headers: Headers): void {
  headers.set("X-Content-Type-Options", "nosniff");
  headers.set("X-Frame-Options", "DENY");
  headers.set("Referrer-Policy", "no-referrer");
  if (config.TLS_CERT && config.TLS_KEY) {
    headers.set("Strict-Transport-Security", "max-age=31536000");
  }
}

function applyHtmlSecurityHeaders(headers: Headers): void {
  applySecurityHeaders(headers);
  headers.set("Cross-Origin-Opener-Policy", "same-origin");
  // 管理页/落地页是同源内联脚本；CSP 先收口到 self + 内联，杜绝外部脚本/资源注入面
  headers.set(
    "Content-Security-Policy",
    "default-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'"
  );
}

/** 给任意响应附加 CORS 头与安全头 */
export function withCors(resp: Response, origin: string | null): Response {
  const tmp = new Headers();
  applyCors(tmp, origin);
  for (const [k, v] of tmp) resp.headers.set(k, v);
  const isHtml = (resp.headers.get("Content-Type") ?? "").startsWith("text/html");
  if (isHtml) applyHtmlSecurityHeaders(resp.headers);
  else applySecurityHeaders(resp.headers);
  return resp;
}

export function jsonResponse(
  body: unknown,
  status = 200,
  extra: Record<string, string> = {}
): Response {
  const headers = new Headers();
  headers.set("Content-Type", "application/json; charset=utf-8");
  for (const [k, v] of Object.entries(extra)) headers.set(k, v);
  return new Response(JSON.stringify(body), { status, headers });
}

export function errorJson(status: number, error: string): Response {
  return jsonResponse({ error }, status);
}

/**
 * 由异常生成错误响应，避免把内部错误细节（含文件系统路径）回显给客户端：
 * 只有显式携带状态码的 HttpError 才回显其字面量消息，其余一律 "internal error"，
 * 详情由调用方写日志。
 */
export function errorResponseFrom(e: unknown): Response {
  if (e instanceof HttpError) return errorJson(e.statusCode, e.message);
  // 内部错误细节（可能含文件系统路径）只进日志，不回显给客户端
  logger.error("internal_error", { error: String((e as any)?.message ?? e) });
  return errorJson(500, "internal error");
}

export function statusResponse(status: number): Response {
  return new Response(null, { status });
}

export function htmlResponse(html: string): Response {
  const headers = new Headers();
  headers.set("Content-Type", "text/html; charset=utf-8");
  applyHtmlSecurityHeaders(headers);
  return new Response(html, { status: 200, headers });
}

/** 读取请求体：必须显式声明 application/json（阻断跨站表单伪造），超过 MAX_BODY 抛 413 */
export async function readBody(req: Request): Promise<string> {
  const ct = (req.headers.get("content-type") ?? "").toLowerCase();
  if (!ct.startsWith("application/json")) {
    throw new HttpError(415, "content-type must be application/json");
  }
  const len = Number(req.headers.get("content-length") ?? "0");
  if (len > config.MAX_BODY) throw new HttpError(413, "body too large");
  const raw = await req.text();
  if (raw.length > config.MAX_BODY) throw new HttpError(413, "body too large");
  return raw;
}

function isObjOrArr(v: unknown): boolean {
  return !!v && typeof v === "object";
}

function validateDepth(value: unknown, depth: number): void {
  if (depth > config.MAX_BODY_DEPTH) throw new HttpError(400, "body nesting too deep");
  if (Array.isArray(value)) {
    for (const item of value) {
      if (isObjOrArr(item)) validateDepth(item, depth + 1);
    }
  } else if (isObjOrArr(value)) {
    for (const k of Object.keys(value as Record<string, unknown>)) {
      validateDepth((value as Record<string, unknown>)[k], depth + 1);
    }
  }
}

function validateKeyCount(value: unknown, max: number, count: number): number {
  if (Array.isArray(value)) {
    for (const item of value) count = validateKeyCount(item, max, count);
    return count;
  }
  if (isObjOrArr(value)) {
    const obj = value as Record<string, unknown>;
    const keys = Object.keys(obj);
    count += keys.length;
    if (count > max) throw new HttpError(400, "too many keys in body");
    for (const k of keys) count = validateKeyCount(obj[k], max, count);
  }
  return count;
}

/** 解析 JSON 并校验深度/键数，越界抛 400 */
export function safeJsonParse(raw: string): unknown {
  let parsed: unknown;
  try {
    parsed = JSON.parse(raw);
  } catch {
    throw new HttpError(400, "invalid json");
  }
  validateDepth(parsed, 0);
  validateKeyCount(parsed, config.MAX_BODY_KEYS, 0);
  return parsed;
}
