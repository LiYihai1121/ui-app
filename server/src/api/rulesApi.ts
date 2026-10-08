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
  errorResponseFrom,
  type Handler,
} from "../utils/httpUtil";
import { cleanRules, cleanBatchReport, cleanReportEvent, cleanLabel, isValidPackage } from "../utils/validate";

export const v0_latest: Handler = () => {
  const r = getRules();
  return jsonResponse({
    version: r.version,
    updatedAt: r.updatedAt,
    keywords: r.keywords,
    viewIds: r.viewIds,
    // v0 平铺形状的选择器字段：管理后台按 legacy 形状编辑，缺失会导致已配的
    // 选择器在编辑页「看不见」——保存一次就被清空。
    selectors: r.selectors,
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
  // 安全契约：限流必须先于鉴权——失败的鉴权尝试同样消耗令牌桶，
  // 否则 admin token 可被无限次离线暴力猜测（限流是唯一的爆破防护）。
  if (!limitWrite(req, ctx.ip)) return errorJson(429, "rate limited");
  const auth = requireAdmin(req, ctx.ip);
  if (!auth.ok) return errorJson(auth.status, auth.error);
  let body: string;
  try {
    body = await readBody(req);
  } catch (e: any) {
    return errorResponseFrom(e);
  }
  let parsed: any;
  try {
    parsed = safeJsonParse(body);
  } catch (e: any) {
    return errorResponseFrom(e);
  }
  const cleaned = cleanRules(parsed);
  if (!cleaned) return errorJson(400, "invalid rules payload");
  const version = saveRules(cleaned);
  return jsonResponse({ ok: true, version });
};

export const v1_publish: Handler = async (req, _url, ctx) => {
  // 同 v0_publish：限流先于鉴权，失败尝试也消耗令牌桶
  if (!limitWrite(req, ctx.ip)) return errorJson(429, "rate limited");
  const auth = requireAdmin(req, ctx.ip);
  if (!auth.ok) return errorJson(auth.status, auth.error);
  let body: string;
  try {
    body = await readBody(req);
  } catch (e: any) {
    return errorResponseFrom(e);
  }
  let parsed: any;
  try {
    parsed = safeJsonParse(body);
  } catch (e: any) {
    return errorResponseFrom(e);
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
    return errorResponseFrom(e);
  }
  let parsed: any;
  try {
    parsed = safeJsonParse(body);
  } catch (e: any) {
    return errorResponseFrom(e);
  }
  const ev = cleanReportEvent(parsed);
  if (!ev) return errorJson(400, "invalid skip payload");
  // label 会持久化并经统计接口下发到所有端：先做数据卫生（去控制字符/截断）再入库
  recordSkip(ev.pkg, cleanLabel(parsed.label ?? ev.pkg), ev.channel);
  return jsonResponse({ ok: true });
};

export const v1_batchReport: Handler = async (req, _url, ctx) => {
  // 读体之前先按来源 IP 探测容量（不扣减），阻断未认证的大请求体内存放大
  if (!probeReportIp(req, ctx.ip)) return errorJson(429, "rate limited");
  let body: string;
  try {
    body = await readBody(req);
  } catch (e: any) {
    return errorResponseFrom(e);
  }
  let parsed: any;
  try {
    parsed = safeJsonParse(body);
  } catch (e: any) {
    return errorResponseFrom(e);
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

/** 简单选择器：`[key]`、`[key op "value"]`；op ∈ `=` `*` `^` `$`（与客户端文法一致） */
const SIMPLE_SELECTOR_RE = /\[(text|desc|vid|click)(?:([*^$]?=)"([^"]*)")?\]/g;

/** 与客户端 `SelectorParser.MAX_VALUE_LENGTH` 对齐：text/desc 超长直接不匹配 */
const SELECTOR_MAX_VALUE_LEN = 64;

type SelectorVerdict = { verdict: "hit" | "miss" | "unevaluable"; reason: string };

/**
 * 选择器求值（服务端**近似**，只覆盖能判的子集）。
 *
 * 语法权威在客户端 `SelectorParser` / `SelectorMatcher`——组合符要靠节点树
 * （parent / previousSibling）才能判，服务端没有节点树。因此本函数只判定
 * 「单个 compound 且只用 text/vid」，其余一律回报 `unevaluable` 并给出原因，
 * 而不是硬凑一个 hit/miss。
 *
 * 上一版用正则去切选择器字符串再 `sample.includes(...)`：正则不匹配时
 * `String.replace` 原样返回，判定退化成「样本文本是否包含整条选择器」，
 * 会稳定误报；且只覆盖 text 一个通道。
 */
function evalSelector(expr: string, sample: string, viewId: string): SelectorVerdict {
  const simples = [...expr.matchAll(SIMPLE_SELECTOR_RE)];
  if (simples.length === 0) {
    return { verdict: "unevaluable", reason: "表达式里没有可识别的简单选择器" };
  }
  // 覆盖性检查：简单选择器拼起来若不等于整条表达式，说明混进了组合符
  // （空白/>/+）或非选择器字符——这些都需要节点树，判不了。
  if (simples.map((m) => m[0]).join("") !== expr.trim()) {
    return { verdict: "unevaluable", reason: "含组合符（后代/子/前序兄弟）或非选择器字符，需要节点树才能判定" };
  }

  for (const m of simples) {
    const key = m[1]!;
    // 捕获组含等号（`*=`/`=`），判定时只取运算符本身
    const op = m[2] === undefined ? undefined : m[2][0];
    const value = m[3];
    if (key === "desc") {
      return { verdict: "unevaluable", reason: "desc 通道需要节点描述文本，本接口未提供" };
    }
    if (key === "click") {
      return { verdict: "unevaluable", reason: "click 通道需要节点可点击状态，本接口未提供" };
    }
    const target = key === "vid" ? viewId : sample;
    if (!target) {
      return { verdict: "unevaluable", reason: `未提供${key === "vid" ? " ViewID" : "样本文本"}，无法判定` };
    }
    const actual = target.trim().toLowerCase();
    if (key !== "vid" && actual.length > SELECTOR_MAX_VALUE_LEN) {
      return { verdict: "miss", reason: "样本文本超过 64 字符，客户端按设计不参与 text/desc 匹配" };
    }
    const expected = (value ?? "").toLowerCase();
    const ok =
      op === undefined
        ? actual.trim().length > 0
        : op === "="
          ? actual === expected
          : op === "*"
            ? actual.includes(expected)
            : op === "^"
              ? actual.startsWith(expected)
              : actual.endsWith(expected);
    if (!ok) {
      return { verdict: "miss", reason: `${key} 不满足 ${op ?? "存在性"}「${value ?? ""}」` };
    }
  }
  return { verdict: "hit", reason: "text/vid 通道全部满足" };
}

export const v1_testRule: Handler = async (req, _url, ctx) => {
  // 同 v0_publish：限流先于鉴权，失败尝试也消耗令牌桶
  if (!limitRead(req, ctx.ip)) return errorJson(429, "rate limited");
  const auth = requireAdmin(req, ctx.ip);
  if (!auth.ok) return errorJson(auth.status, auth.error);
  let body: string;
  try {
    body = await readBody(req);
  } catch (e: any) {
    return errorResponseFrom(e);
  }
  let parsed: any;
  try {
    parsed = safeJsonParse(body);
  } catch (e: any) {
    return errorResponseFrom(e);
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
  const selectorRules = [...rules.rules.globalSelectors, ...(app?.selectors ?? [])];
  const hits: Array<{ match: string; keyword?: string; rule?: string; field?: string }> = [];
  const selectorNotes: Array<{ rule: string; reason: string }> = [];
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
  for (const selector of selectorRules) {
    const v = evalSelector(selector, sample, vid);
    if (v.verdict === "hit") {
      hits.push({ match: "selector", rule: selector, field: "text" });
    } else if (v.verdict === "unevaluable") {
      // 如实回报「服务端判不了」，避免规则模拟器给出「未命中」的假象
      selectorNotes.push({ rule: selector, reason: v.reason });
    }
  }
  return jsonResponse({ hits, hit: !disabled && hits.length > 0, disabled, selectorNotes });
};
