import { config } from "../config";
import type { CleanedRules } from "../types/rules";

// 包名：字母开头，至少含一个点，只允许字母数字下划线
export const PKG_RE = /^[a-zA-Z][\w]*(\.[a-zA-Z][\w]*)+$/;

// ViewID：形如 com.example:id/skip_view
export const VID_RE = /^[\w.$]+:id\/[\w]+$/;

export function isValidKeyword(kw: unknown): boolean {
  const s = String(kw ?? "").trim();
  const len = s.length;
  return len >= 1 && len <= config.MAX_KEYWORD_LEN;
}

export function isValidViewIdRule(vid: unknown): boolean {
  const s = String(vid ?? "").trim();
  const len = s.length;
  return len >= 3 && len <= config.MAX_VIEWID_RULE_LEN;
}

export function isValidPackage(pkg: unknown): boolean {
  const s = String(pkg ?? "").trim();
  return s.length <= 256 && PKG_RE.test(s);
}

function cleanStringList(value: unknown, maxLen = 256): string[] {
  if (!Array.isArray(value)) return [];
  const out: string[] = [];
  const seen = new Set<string>();
  for (const item of value) {
    if (typeof item !== "string") continue;
    const s = item.trim().slice(0, maxLen);
    if (!s) continue;
    const low = s.toLowerCase();
    if (seen.has(low)) continue;
    seen.add(low);
    out.push(s);
    if (out.length >= config.MAX_RULES_PER_APP) break;
  }
  return out;
}

/** 校验并清洗规则载荷（v1/v0 兼容），非法返回 null */
export function cleanRules(input: unknown): CleanedRules | null {
  if (
    !input ||
    typeof input !== "object" ||
    (input as any).packages == null ||
    typeof (input as any).packages !== "object" ||
    Array.isArray((input as any).packages)
  ) {
    return null;
  }
  const packages: Record<
    string,
    { keywords: string[]; viewIds: string[]; disabled: boolean }
  > = Object.create(null);
  let count = 0;
  for (const [pkg, rule] of Object.entries((input as any).packages)) {
    if (count >= config.MAX_APPS) break;
    if (!isValidPackage(pkg)) continue;
    if (!rule || typeof rule !== "object" || Array.isArray(rule)) continue;
    packages[pkg] = {
      keywords: cleanStringList((rule as any).keywords, config.MAX_KEYWORD_LEN),
      viewIds: cleanStringList((rule as any).viewIds, config.MAX_VIEWID_RULE_LEN),
      disabled: (rule as any).disabled === true,
    };
    count++;
  }
  return {
    keywords: cleanStringList((input as any).keywords, config.MAX_KEYWORD_LEN),
    viewIds: cleanStringList((input as any).viewIds, config.MAX_VIEWID_RULE_LEN),
    packages,
  };
}

export function cleanReportEvent(input: unknown): {
  pkg: string;
  channel: string;
  ts: number;
} | null {
  if (!input || typeof input !== "object") return null;
  const pkgRaw = String((input as any).pkg ?? "").trim().slice(0, 256);
  if (!pkgRaw || !PKG_RE.test(pkgRaw)) return null;
  let channel = String((input as any).channel ?? "text").trim().slice(0, 32) || "text";
  if (channel !== "text" && channel !== "viewId") return null;
  const ts =
    typeof (input as any).ts === "number" && Number.isFinite((input as any).ts)
      ? (input as any).ts
      : Date.now();
  return { pkg: pkgRaw, channel, ts };
}

export function cleanBatchReport(input: unknown): {
  deviceId: string;
  events: Array<{ pkg: string; channel: string; ts: number }>;
} | null {
  if (!input || typeof input !== "object") return null;
  const deviceId = String((input as any).deviceId ?? "").trim().slice(0, 128);
  if (deviceId.length < 8) return null;
  if (!Array.isArray((input as any).events) || (input as any).events.length === 0) {
    return null;
  }
  const events: Array<{ pkg: string; channel: string; ts: number }> = [];
  for (const e of (input as any).events) {
    const cleaned = cleanReportEvent(e);
    if (cleaned) {
      events.push(cleaned);
      if (events.length >= config.MAX_BATCH_EVENTS) break;
    }
  }
  if (events.length === 0) return null;
  return { deviceId, events };
}
