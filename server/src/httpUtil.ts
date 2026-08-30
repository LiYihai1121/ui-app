import { config } from "./config";

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

/** 依据 CORS 配置写入响应头（保持与旧版一致的 quirk：未命中白名单时置为 "null"） */
export function applyCors(headers: Headers, origin: string | null): void {
  const origins = config.CORS_ORIGINS;
  if (origins && origins.length) {
    if (origin && origins.includes(origin)) {
      headers.set("Access-Control-Allow-Origin", origin);
      headers.set("Vary", "Origin");
    } else {
      headers.set("Access-Control-Allow-Origin", "null");
    }
  } else {
    headers.set("Access-Control-Allow-Origin", "*");
  }
  headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
  headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization, If-None-Match");
}

/** 给任意响应附加 CORS 头 */
export function withCors(resp: Response, origin: string | null): Response {
  const tmp = new Headers();
  applyCors(tmp, origin);
  for (const [k, v] of tmp) resp.headers.set(k, v);
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

export function statusResponse(status: number): Response {
  return new Response(null, { status });
}

export function htmlResponse(html: string): Response {
  return new Response(html, {
    status: 200,
    headers: { "Content-Type": "text/html; charset=utf-8" },
  });
}

/** 读取请求体，超过 MAX_BODY 抛 413 */
export async function readBody(req: Request): Promise<string> {
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
