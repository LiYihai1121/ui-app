import { config } from "../config";
import { getRules, saveRules, recordSkip } from "../storage/store";
import { requireAdmin } from "../middleware/auth";
import { limitRead, limitWrite, limitReport, probeReportIp } from "../middleware/rateLimit";
import {
  jsonResponse,
  errorJson,
  statusResponse,
  readBody,
  safeJsonParse,
  type Handler,
} from "../utils/httpUtil";
import { cleanRules, cleanBatchReport, cleanReportEvent, isValidPackage } from "../utils/validate";

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
  const ev = cleanReportEvent(parsed);
  if (!ev) return errorJson(400, "invalid skip payload");
  recordSkip(ev.pkg, String(parsed.label ?? ev.pkg).slice(0, 256), ev.channel);
  return jsonResponse({ ok: true });
};

export const v1_batchReport: Handler = async (req, _url, ctx) => {
  // 读体之前先按来源 IP 探测容量（不扣减），阻断未认证的大请求体内存放大
  if (!probeReportIp(req, ctx.ip)) return errorJson(429, "rate limited");
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

export const v1_testRule: Handler = async (req, _url, ctx) => {
  const auth = requireAdmin(req);
  if (!auth.ok) return errorJson(auth.status, auth.error);
  if (!limitRead(req, ctx.ip)) return errorJson(429, "rate limited");
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
  // 与客户端 RulesRepository.ruleSetFor(pkg) 同源：全局 + 应用专属 + 禁用开关
  const pkgRaw = typeof parsed.pkg === "string" ? parsed.pkg.trim() : "";
  const pkg = isValidPackage(pkgRaw) ? pkgRaw : "";
  const sample = String(parsed.text ?? "").toLowerCase();
  const vid = String(parsed.viewId ?? "").toLowerCase();
  const rules = getRules();
  const app = pkg ? rules.rules.apps[pkg] : undefined;
  const disabled =
    app?.disabled === true || (pkg ? rules.rules.disabled.includes(pkg) : false);
  const keywords = [...rules.rules.globalKeywords, ...(app?.keywords ?? [])];
  const viewIdRules = [...rules.rules.globalViewIds, ...(app?.viewIds ?? [])];
  const hits: Array<{ match: string; keyword?: string; rule?: string; field?: string }> = [];
  for (const kw of keywords) {
    if (sample.includes(kw.toLowerCase())) {
      hits.push({ match: "keyword", keyword: kw, field: "text" });
    }
  }
  for (const rule of viewIdRules) {
    if (rule.length >= 3 && vid.includes(rule.toLowerCase())) {
      hits.push({ match: "viewId", rule });
    }
  }
  return jsonResponse({ hits, hit: !disabled && hits.length > 0, disabled });
};
