'use strict';

/**
 * 存储层：JSON 文件的原子读写与领域数据访问。
 * 单进程本地服务，无需数据库；写操作用 tmp+rename 保证原子性。
 */
const fs = require('fs');
const config = require('./config');

function readJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (e) {
    return fallback;
  }
}

function writeJson(file, obj) {
  const tmp = file + '.tmp';
  fs.writeFileSync(tmp, JSON.stringify(obj, null, 2));
  fs.renameSync(tmp, file);
}

// ---------- 规则 ----------
function getRules() {
  return readJson(config.RULES_FILE, {
    version: 1,
    updatedAt: new Date().toISOString(),
    keywords: [],
    viewIds: [],
    packages: {},
  });
}

function saveRules(rules) {
  rules.version = (getRules().version || 1) + 1;
  rules.updatedAt = new Date().toISOString();
  writeJson(config.RULES_FILE, rules);
  return rules.version;
}

// ---------- 统计 ----------
function getStats() {
  return readJson(config.STATS_FILE, { total: 0, byDay: {}, byApp: {}, recent: [] });
}

function recordSkip(pkg, label) {
  const now = new Date();
  const day = now.toISOString().slice(0, 10);
  const stats = getStats();
  stats.total += 1;
  stats.byDay[day] = (stats.byDay[day] || 0) + 1;
  if (!stats.byApp[pkg]) stats.byApp[pkg] = { label, count: 0 };
  stats.byApp[pkg].label = label;
  stats.byApp[pkg].count += 1;
  stats.recent.unshift({ ts: now.toISOString(), pkg, label });
  if (stats.recent.length > config.RECENT_CAP) stats.recent.length = config.RECENT_CAP;
  writeJson(config.STATS_FILE, stats);
}

function statsSummary() {
  const stats = getStats();
  const today = new Date().toISOString().slice(0, 10);
  const byApp = Object.entries(stats.byApp)
    .map(([pkg, v]) => ({ pkg, label: v.label, count: v.count }))
    .sort((a, b) => b.count - a.count);
  const days = Object.keys(stats.byDay).sort().slice(-14);
  return {
    total: stats.total,
    today: stats.byDay[today] || 0,
    byDay: days.map((d) => ({ day: d, count: stats.byDay[d] })),
    byApp,
    recent: stats.recent.slice(0, 50),
  };
}

module.exports = { getRules, saveRules, getStats, recordSkip, statsSummary };
