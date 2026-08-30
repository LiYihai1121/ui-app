import * as fs from "node:fs";
import * as path from "node:path";
import * as crypto from "node:crypto";
import { config } from "../config";
import type {
  AppStat,
  CleanedRules,
  RulesPackage,
  SkipEvent,
  StatsDay,
  StatsSummary,
} from "../types/rules";

const statsCache = new Map<string, { data: StatsDay; dirty: boolean; timer: ReturnType<typeof setTimeout> | null }>();
const FLUSH_DELAY_MS = 5000;

/** 汇总缓存：recordSkip / 过期清理时失效，避免每次 summary 全量读盘 */
let summaryCache: StatsSummary | null = null;
let rulesCache: RulesPackage | null = null;

type CachedDay = (typeof statsCache) extends Map<string, infer V> ? V : never;

function readJson(file: string, fallback: any): any {
  try {
    const raw = fs.readFileSync(file, "utf8");
    return JSON.parse(raw);
  } catch {
    return fallback;
  }
}

function writeJson(file: string, obj: any): void {
  const dir = path.dirname(file);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  const tmp = file + ".tmp";
  fs.writeFileSync(tmp, JSON.stringify(obj, null, 2));
  fs.renameSync(tmp, file);
}

function dirOrFileExists(file: string): boolean {
  try {
    fs.accessSync(file);
    return true;
  } catch {
    return false;
  }
}

function defaultRules(): any {
  const now = new Date().toISOString();
  return {
    schemaVersion: config.SCHEMA_VERSION,
    version: 1,
    updatedAt: now,
    hash: "",
    rules: {
      globalKeywords: ["跳过", "跳過", "skip", "跳过广告", "关闭广告"],
      globalViewIds: ["skip", "jump"],
      apps: {},
      disabled: [],
    },
    keywords: ["跳过", "跳過", "skip", "跳过广告", "关闭广告"],
    viewIds: ["skip", "jump"],
    packages: {},
  };
}

export function computeHash(rules: any): string {
  const content = JSON.stringify({
    schemaVersion: rules.schemaVersion,
    rules: rules.rules || {},
  });
  return "sha256:" + crypto.createHash("sha256").update(content).digest("hex").slice(0, 16);
}

/**
 * 兼容 v0/v1 两种形状：补全缺失字段，并把 legacy 的
 * keywords/viewIds/packages 重新指向 v1 的同一份引用。
 */
function ensureCompatShape(input: unknown): RulesPackage {
  let rules: any = input;
  if (!rules || typeof rules !== "object") rules = defaultRules();
  if (typeof rules.schemaVersion !== "number") rules.schemaVersion = config.SCHEMA_VERSION;
  if (!rules.rules || typeof rules.rules !== "object") rules.rules = {};
  const r = rules.rules;
  const legacyKw: string[] = Array.isArray(rules.keywords) ? rules.keywords : [];
  const legacyVid: string[] = Array.isArray(rules.viewIds) ? rules.viewIds : [];

  if (!Array.isArray(r.globalKeywords)) r.globalKeywords = legacyKw.slice();
  if (!Array.isArray(r.globalViewIds)) r.globalViewIds = legacyVid.slice();
  if (!r.apps || typeof r.apps !== "object" || Array.isArray(r.apps)) r.apps = {};
  if (!Array.isArray(r.disabled)) r.disabled = [];

  if (!Array.isArray(rules.keywords)) rules.keywords = r.globalKeywords.slice();
  if (!Array.isArray(rules.viewIds)) rules.viewIds = r.globalViewIds.slice();
  if (!rules.packages || typeof rules.packages !== "object" || Array.isArray(rules.packages)) {
    rules.packages = {};
  }

  rules.keywords = r.globalKeywords;
  rules.viewIds = r.globalViewIds;
  rules.packages = r.apps;

  if (typeof rules.version !== "number") rules.version = 1;
  if (typeof rules.updatedAt !== "string") rules.updatedAt = new Date().toISOString();
  return rules;
}

export function getRules(): RulesPackage {
  if (rulesCache) return rulesCache;
  const raw = readJson(config.RULES_FILE, null);
  const shaped = ensureCompatShape(raw);
  shaped.hash = computeHash(shaped);
  rulesCache = shaped;
  return shaped;
}

export function saveRules(cleaned: CleanedRules): number {
  rotateBackup();
  const prev = getRules();
  const version = (typeof prev.version === "number" ? prev.version : 1) + 1;

  const apps: Record<string, any> = {};
  const disabled: string[] = [];
  for (const [pkg, rule] of Object.entries(cleaned.packages)) {
    apps[pkg] = {
      keywords: rule.keywords,
      viewIds: rule.viewIds,
      disabled: rule.disabled === true,
    };
    if (rule.disabled === true) disabled.push(pkg);
  }

  const rules = {
    schemaVersion: config.SCHEMA_VERSION,
    version,
    updatedAt: new Date().toISOString(),
    rules: {
      globalKeywords: cleaned.keywords,
      globalViewIds: cleaned.viewIds,
      apps,
      disabled,
    },
  };
  const compat = ensureCompatShape(rules);
  compat.hash = computeHash(compat);
  writeJson(config.RULES_FILE, compat);
  rulesCache = compat;
  return version;
}

function rotateBackup(): void {
  try {
    if (!dirOrFileExists(config.RULES_FILE)) return;
    const stamp = new Date().toISOString().replace(/[:.]/g, "-");
    const name = `rules-${stamp}.json`;
    const dest = path.join(config.BACKUP_DIR, name);
    fs.mkdirSync(config.BACKUP_DIR, { recursive: true });
    fs.copyFileSync(config.RULES_FILE, dest);

    const files = fs
      .readdirSync(config.BACKUP_DIR)
      .filter((f) => f.startsWith("rules-") && f.endsWith(".json"))
      .sort()
      .reverse();
    for (const f of files.slice(config.BACKUP_COUNT)) {
      try {
        fs.unlinkSync(path.join(config.BACKUP_DIR, f));
      } catch {
        /* ignore */
      }
    }
  } catch (e) {
    console.warn("[store] backup rotation failed:", e);
  }
}

function getDayKey(date: Date = new Date()): string {
  return date.toISOString().slice(0, 10);
}

function loadDayStats(day: string): StatsDay {
  const file = path.join(config.STATS_DIR, `${day}.json`);
  return readJson(file, { day, byApp: {}, events: [] } as StatsDay);
}

function getCachedDay(day: string): CachedDay {
  let c = statsCache.get(day);
  if (!c) {
    c = { data: loadDayStats(day), dirty: false, timer: null };
    statsCache.set(day, c);
  }
  return c;
}

function scheduleFlush(day: string): void {
  const c = statsCache.get(day);
  if (!c || c.timer) return;
  c.timer = setTimeout(() => flushDay(day), FLUSH_DELAY_MS);
  c.timer.unref?.();
}

export function recordSkip(pkgRaw: string, labelRaw: string, channelRaw: string): void {
  const day = getDayKey();
  const c = getCachedDay(day);
  const pkg = String(pkgRaw).slice(0, 256);
  const label = String(labelRaw ?? "").slice(0, 256);
  let channel = String(channelRaw ?? "text").slice(0, 32) || "text";

  if (!c.data.byApp[pkg]) c.data.byApp[pkg] = { label, count: 0, byChannel: {} };
  const entry = c.data.byApp[pkg];
  entry.label = label;
  entry.count += 1;
  entry.byChannel[channel] = (entry.byChannel[channel] ?? 0) + 1;

  c.data.events.unshift({ ts: new Date().toISOString(), pkg, label, channel });
  if (c.data.events.length > config.RECENT_CAP) {
    c.data.events.length = config.RECENT_CAP;
  }
  c.dirty = true;
  summaryCache = null;
  scheduleFlush(day);
}

function listStatsDays(): string[] {
  const set = new Set<string>();
  try {
    for (const f of fs.readdirSync(config.STATS_DIR)) {
      const m = /^(\d{4}-\d{2}-\d{2})\.json$/.exec(f);
      if (m) set.add(m[1]);
    }
  } catch {
    /* ignore */
  }
  for (const k of statsCache.keys()) set.add(k);
  return Array.from(set).sort();
}

export function statsSummary(): StatsSummary {
  if (summaryCache) return summaryCache;
  const days = listStatsDays();
  let total = 0;
  const byAppMap: Record<string, { label: string; count: number }> = {};
  const byDay: Array<{ day: string; count: number }> = [];
  const dayData = new Map<string, StatsDay>();

  for (const day of days) {
    const c = statsCache.get(day);
    const data: StatsDay = c ? c.data : loadDayStats(day);
    dayData.set(day, data);
    let dayCount = 0;
    for (const [pkg, info] of Object.entries(data.byApp)) {
      dayCount += info.count;
      total += info.count;
      const cur = byAppMap[pkg] ?? { label: info.label, count: 0 };
      cur.count += info.count;
      cur.label = info.label;
      byAppMap[pkg] = cur;
    }
    byDay.push({ day, count: dayCount });
  }

  byDay.sort((a, b) => (a.day < b.day ? -1 : 1));
  const byApp = Object.entries(byAppMap)
    .map(([pkg, v]) => ({ pkg, label: v.label, count: v.count }))
    .sort((a, b) => b.count - a.count);

  // recent 跨天取最近 50 条（days 升序，从最新一天往前收集；天内 events 已是新→旧）
  const recent: SkipEvent[] = [];
  for (let i = days.length - 1; i >= 0 && recent.length < 50; i--) {
    for (const ev of dayData.get(days[i])!.events) {
      recent.push(ev);
      if (recent.length >= 50) break;
    }
  }

  const summary: StatsSummary = {
    total,
    today: byDay.find((d) => d.day === getDayKey())?.count ?? 0,
    byDay: byDay.slice(-14),
    byApp,
    recent,
  };
  summaryCache = summary;
  return summary;
}

export function cleanupOldStats(): void {
  const cutoff = new Date(
    Date.now() - config.STATS_RETENTION_DAYS * 24 * 3600 * 1000
  )
    .toISOString()
    .slice(0, 10);
  try {
    for (const f of fs.readdirSync(config.STATS_DIR)) {
      const m = /^(\d{4}-\d{2}-\d{2})\.json$/.exec(f);
      if (!m) continue;
      if (m[1] < cutoff) {
        try {
          fs.unlinkSync(path.join(config.STATS_DIR, f));
          summaryCache = null;
        } catch {
          /* ignore */
        }
      }
    }
  } catch {
    /* ignore */
  }
}

// 过期分片定期清理：启动清理只跑一次，长驻进程需要周期执行
const statsCleanupTimer = setInterval(cleanupOldStats, 6 * 60 * 60 * 1000);
statsCleanupTimer.unref?.();

/** 仅供测试：清空汇总缓存 */
export function _resetSummaryCacheForTests(): void {
  summaryCache = null;
}

/** 仅供测试：清空按天统计缓存（隔离跨测试的 config.STATS_DIR 切换） */
export function _resetStatsCacheForTests(): void {
  statsCache.clear();
  summaryCache = null;
}

function flushDay(day: string): void {
  const c = statsCache.get(day);
  if (!c) return;
  if (c.timer) {
    clearTimeout(c.timer);
    c.timer = null;
  }
  if (!c.dirty) return;
  writeJson(path.join(config.STATS_DIR, `${day}.json`), c.data);
  c.dirty = false;
}

export function flush(): void {
  for (const day of statsCache.keys()) flushDay(day);
  console.log("[store] all stats flushed");
}

export const _internal = { computeHash, listStatsDays };
