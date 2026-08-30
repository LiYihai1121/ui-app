import { config } from "../config";
import { getRules, saveRules, recordSkip } from "../store";
import { requireAdmin } from "../auth";
import { limitRead, limitWrite, limitReport } from "../rateLimit";
import {
  jsonResponse,
  errorJson,
  statusResponse,
  readBody,
  safeJsonParse,
} from "../httpUtil";
import { cleanRules, cleanBatchReport } from "../validate";
import type { Handler } from "../types";

export const v0_latest: Handler = () => {
  const r = getRules();
  return jsonResponse({
    version: r.version,
    updatedAt: r.updatedAt,
    keywords: r.keywords,
    viewIds: r.viewIds,
    packages: r.packages,
  });
};

export const v1_latest: Handler = (req) => {
  const rules = getRules();
  const ifNoneMatch = req.headers.get("if-none-match");
  if (ifNoneMatch && ifNoneMatch === rules.hash) return statusResponse(304);
  return jsonResponse(
    {
      schemaVersion: rules.schemaVersion,
      version: rules.version,
      hash: rules.hash,
      updatedAt: rules.updatedAt,
      rules: rules.rules,
    },
    200,
    { ETag: rules.hash }
  );
};

export const v0_publish: Handler = async (req, _url, ctx) => {
  const auth = requireAdmin(req);
  if (!auth.ok) return errorJson(auth.status, auth.error);
  if (!limitWrite(req, ctx.ip)) return errorJson(429, "rate limited");
  let body: string;
  try {
    body = await readBody(req);
  } catch (e: any) {
    return errorJson(e.statusCode ?? 400, e.message);
  }
  let parsed: any;
  try {
    parsed = safeJsonParse(body);
  } catch (e: any) {
    return errorJson(e.statusCode ?? 400, e.message);
  }
  const cleaned = cleanRules(parsed);
  if (!cleaned) return errorJson(400, "invalid rules payload");
  const version = saveRules(cleaned);
  return jsonResponse({ ok: true, version });
};

export const v1_publish: Handler = async (req, _url, ctx) => {
  const auth = requireAdmin(req);
  if (!auth.ok) return errorJson(auth.status, auth.error);
  if (!limitWrite(req, ctx.ip)) return errorJson(429, "rate limited");
  let body: string;
  try {
    body = await readBody(req);
  } catch (e: any) {
    return errorJson(e.statusCode ?? 400, e.message);
  }
  let parsed: any;
  try {
    parsed = safeJsonParse(body);
  } catch (e: any) {
    return errorJson(e.statusCode ?? 400, e.message);
  }
  const cleaned = cleanRules(parsed);
  if (!cleaned) return errorJson(400, "invalid rules payload");
  const version = saveRules(cleaned);
  return jsonResponse({ ok: true, version, hash: getRules().hash });
};

export const v0_skip: Handler = async (req, _url, ctx) => {
  if (!limitWrite(req, ctx.ip)) return errorJson(429, "rate limited");
  let body: string;
  try {
    body = await readBody(req);
  } catch (e: any) {
    return errorJson(e.statusCode ?? 400, e.message);
  }
  let parsed: any;
  try {
    parsed = safeJsonParse(body);
  } catch (e: any) {
    return errorJson(e.statusCode ?? 400, e.message);
  }
  const pkg = String(parsed.pkg ?? "").trim().slice(0, 256);
  if (!pkg) return errorJson(400, "invalid batch report");
  recordSkip(pkg, String(parsed.label ?? pkg), "text");
  return jsonResponse({ ok: true });
};

export const v1_batchReport: Handler = async (req, _url, ctx) => {
  let body: string;
  try {
    body = await readBody(req);
  } catch (e: any) {
    return errorJson(e.statusCode ?? 400, e.message);
  }
  let parsed: any;
  try {
    parsed = safeJsonParse(body);
  } catch (e: any) {
    return errorJson(e.statusCode ?? 400, e.message);
  }
  const cleaned = cleanBatchReport(parsed);
  if (!cleaned) return errorJson(400, "invalid batch report");
  if (!limitReport(req, ctx.ip, cleaned.deviceId)) {
    return errorJson(429, "rate limited");
  }
  for (const ev of cleaned.events) {
    recordSkip(ev.pkg, ev.pkg, ev.channel);
  }
  return jsonResponse({ ok: true, accepted: cleaned.events.length });
};

export const v1_testRule: Handler = async (req) => {
  const auth = requireAdmin(req);
  if (!auth.ok) return errorJson(auth.status, auth.error);
  let body: string;
  try {
    body = await readBody(req);
  } catch (e: any) {
    return errorJson(e.statusCode ?? 400, e.message);
  }
  let parsed: any;
  try {
    parsed = safeJsonParse(body);
  } catch (e: any) {
    return errorJson(e.statusCode ?? 400, e.message);
  }
  const sample = String(parsed.text ?? "").toLowerCase();
  const vid = String(parsed.viewId ?? "").toLowerCase();
  const rules = getRules();
  const hits: any[] = [];
  for (const kw of rules.rules.globalKeywords as string[]) {
    if (sample.includes(kw.toLowerCase())) {
      hits.push({ match: "keyword", keyword: kw, field: "text" });
    }
  }
  for (const rule of rules.rules.globalViewIds as string[]) {
    if (rule.length >= 3 && vid.includes(rule.toLowerCase())) {
      hits.push({ match: "viewId", rule });
    }
  }
  return jsonResponse({ hits, hit: hits.length > 0 });
};
