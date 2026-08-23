'use strict';

/**
 * 存储层：JSON 文件的原子读写与领域数据访问。
 *
 * v2.2 增强：
 * - 规则发布前轮转备份（保留 N 份）
 * - 统计按天分片（data/stats/YYYY-MM-DD.json）
 * - 内存缓存 + dirty 标记延迟刷盘
 * - 启动时清理过期分片
 * - flush() 供优雅停机调用
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const config = require('./config');

// ---------- 文件原子操作 ----------
function readJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return fallback;
  }
}

function writeJson(file, obj) {
  const dir = path.dirname(file);
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
  const tmp = file + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(obj, null, 2));
  fs.renameSync(tmp, file);
}

// ---------- 规则（带内存缓存 + 备份轮转） ----------
let _rulesCache = null;
let _rulesHash = null;

function getRules() {
  if (_rulesCache) return _rulesCache;
  _rulesCache = readJson(config.RULES_FILE, {
    schemaVersion: config.SCHEMA_VERSION,
    version: 1,
    updatedAt: new Date().toISOString(),
    hash: '',
    rules: {
      globalKeywords: ['跳过', '跳過', 'skip', '跳过广告', '关闭广告'],
      globalViewIds: ['skip', 'jump'],
      apps: {},
      disabled: [],
    },
    // 兼容旧字段（v0 客户端读 keywords/viewIds/packages）
    keywords: ['跳过', '跳過', 'skip', '跳过广告', '关闭广告'],
    viewIds: ['skip', 'jump'],
    packages: {},
  });
  // 确保兼容性
  ensureCompatShape(_rulesCache);
  _rulesHash = computeHash(_rulesCache);
  return _rulesCache;
}

/** 计算规则内容的 SHA-256 哈希（用于 ETag / 304） */
function computeHash(rules) {
  const content = JSON.stringify({
    schemaVersion: rules.schemaVersion,
    rules: rules.rules || {},
  });
  return 'sha256:' + crypto.createHash('sha256').update(content).digest('hex').slice(0, 16);
}

/** 确保规则对象同时含 v0 和 v1 字段（兼容期） */
function ensureCompatShape(rules) {
  if (!rules.schemaVersion) rules.schemaVersion = config.SCHEMA_VERSION;
  if (!rules.rules) rules.rules = {};
  const r = rules.rules;
  r.globalKeywords = r.globalKeywords || rules.keywords || [];
  r.globalViewIds = r.globalViewIds || rules.viewIds || [];
  r.apps = r.apps || rules.packages || {};
  r.disabled = r.disabled || [];
  // 回填兼容字段
  rules.keywords = r.globalKeywords;
  rules.viewIds = r.globalViewIds;
  rules.packages = r.apps;
}

function saveRules(cleanedRules) {
  // 备份轮转
  rotateBackup();

  const prev = getRules();
  const version = (prev.version || 1) + 1;

  // 构建 v1 结构
  const newRules = {
    schemaVersion: config.SCHEMA_VERSION,
    version,
    updatedAt: new Date().toISOString(),
    rules: {
      globalKeywords: cleanedRules.keywords,
      globalViewIds: cleanedRules.viewIds,
      apps: cleanedRules.packages,
      disabled: [],
    },
  };

  // 从 apps 中提取 disabled 列表
  for (const [pkg, rule] of Object.entries(cleanedRules.packages)) {
    if (rule.disabled) newRules.rules.disabled.push(pkg);
  }

  // 兼容 v0 客户端
  ensureCompatShape(newRules);
  newRules.hash = computeHash(newRules);

  writeJson(config.RULES_FILE, newRules);
  _rulesCache = newRules;
  _rulesHash = newRules.hash;
  return version;
}

/** 发布前轮转备份：保留最近 N 份 */
function rotateBackup() {
  try {
    if (!fs.existsSync(config.BACKUP_DIR)) {
      fs.mkdirSync(config.BACKUP_DIR, { recursive: true });
    }
    const stamp = new Date().toISOString().replace(/[:.]/g, '-');
    const backupFile = path.join(config.BACKUP_DIR, `rules-${stamp}.json`);
    if (fs.existsSync(config.RULES_FILE)) {
      fs.copyFileSync(config.RULES_FILE, backupFile);
    }
    // 清理旧备份，只保留最近 N 份
    const files = fs.readdirSync(config.BACKUP_DIR)
      .filter((f) => f.startsWith('rules-') && f.endsWith('.json'))
      .sort()
      .reverse();
    for (let i = config.BACKUP_COUNT; i < files.length; i++) {
      fs.unlinkSync(path.join(config.BACKUP_DIR, files[i]));
    }
  } catch (e) {
    console.warn('[store] backup rotation failed:', e.message);
  }
}

// ---------- 统计（按天分片 + 内存缓存） ----------

/** @type {Map<string, {data: object, dirty: boolean, timer: any}>} */
const statsCache = new Map();
const FLUSH_DELAY_MS = 5000;

function getDayKey(date) {
  const d = date || new Date();
  return d.toISOString().slice(0, 10);
}

function statsFile(day) {
  return path.join(config.STATS_DIR, `${day}.json`);
}

function loadDayStats(day) {
  const cached = statsCache.get(day);
  if (cached) return cached.data;
  const data = readJson(statsFile(day), {
    day,
    byApp: {},
    events: [],
  });
  const entry = { data, dirty: false, timer: null };
  statsCache.set(day, entry);
  return data;
}

function scheduleFlush(day) {
  const entry = statsCache.get(day);
  if (!entry || !entry.dirty || entry.timer) return;
  entry.timer = setTimeout(() => {
    flushDay(day);
  }, FLUSH_DELAY_MS);
  if (entry.timer.unref) entry.timer.unref();
}

function flushDay(day) {
  const entry = statsCache.get(day);
  if (!entry) return;
  if (entry.timer) {
    clearTimeout(entry.timer);
    entry.timer = null;
  }
  if (entry.dirty) {
    writeJson(statsFile(day), entry.data);
    entry.dirty = false;
  }
}

function recordSkip(pkg, label, channel) {
  const day = getDayKey();
  const data = loadDayStats(day);
  if (!data.byApp[pkg]) data.byApp[pkg] = { label, count: 0, byChannel: {} };
  data.byApp[pkg].label = label;
  data.byApp[pkg].count += 1;
  const ch = channel || 'text';
  data.byApp[pkg].byChannel[ch] = (data.byApp[pkg].byChannel[ch] || 0) + 1;
  data.events.unshift({ ts: new Date().toISOString(), pkg, label, channel: ch });
  if (data.events.length > config.RECENT_CAP) data.events.length = config.RECENT_CAP;

  const entry = statsCache.get(day);
  entry.dirty = true;
  scheduleFlush(day);
}

function statsSummary() {
  const today = getDayKey();
  const todayData = loadDayStats(today);

  // 合并所有分片的 byApp 聚合
  const aggByApp = Object.create(null);
  let total = 0;
  const recent = [];

  const allDays = listStatsDays();
  for (const day of allDays) {
    const data = day === today ? todayData : readJson(statsFile(day), null);
    if (!data) continue;
    for (const [pkg, v] of Object.entries(data.byApp || {})) {
      if (!aggByApp[pkg]) aggByApp[pkg] = { pkg, label: v.label, count: 0 };
      aggByApp[pkg].count += v.count || 0;
      total += v.count || 0;
    }
  }

  // 最近记录取今天的前 50 条
  recent.push(...(todayData.events || []).slice(0, 50));

  const byApp = Object.values(aggByApp).sort((a, b) => b.count - a.count);

  // 14 天趋势
  const days = allDays.slice(-14);
  const byDay = days.map((d) => {
    const data = d === today ? todayData : readJson(statsFile(d), null);
    let count = 0;
    if (data) {
      for (const v of Object.values(data.byApp || {})) count += v.count || 0;
    }
    return { day: d, count };
  });

  return {
    total,
    today: (todayData.byApp && Object.values(todayData.byApp).reduce((s, v) => s + (v.count || 0), 0)) || 0,
    byDay,
    byApp,
    recent,
  };
}

/** 列出所有统计分片日期（排序）——含内存缓存中的日期 */
function listStatsDays() {
  const days = new Set();
  // 从文件系统读取
  try {
    if (fs.existsSync(config.STATS_DIR)) {
      for (const f of fs.readdirSync(config.STATS_DIR)) {
        if (/^\d{4}-\d{2}-\d{2}\.json$/.test(f)) {
          days.add(f.replace('.json', ''));
        }
      }
    }
  } catch { /* ignore */ }
  // 从内存缓存补充（未落盘的数据）
  for (const day of statsCache.keys()) days.add(day);
  return Array.from(days).sort();
}

/** 启动时清理过期分片 */
function cleanupOldStats() {
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - config.STATS_RETENTION_DAYS);
  const cutoffStr = cutoff.toISOString().slice(0, 10);
  for (const day of listStatsDays()) {
    if (day < cutoffStr) {
      try {
        fs.unlinkSync(statsFile(day));
        console.log(`[store] cleaned up old stats: ${day}`);
      } catch { /* ignore */ }
    }
  }
}

// ---------- 优雅停机 ----------
function flush() {
  for (const day of statsCache.keys()) {
    flushDay(day);
  }
  console.log('[store] all stats flushed');
}

module.exports = {
  getRules,
  saveRules,
  recordSkip,
  statsSummary,
  flush,
  cleanupOldStats,
  // 暴露给测试
  _internal: { computeHash, listStatsDays },
};
