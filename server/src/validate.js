'use strict';

/**
 * 载荷校验：与客户端同源的约束常量 + 校验函数。
 *
 * 防止被污染的规则诱导无障碍服务点击敏感按钮：
 * - 关键词长度上限、总条目上限
 * - 包名正则约束
 * - ViewID 正则约束
 * - 未知字段拒绝
 */
const config = require('./config');

// 包名：字母开头，至少含一个点，只允许字母数字下划线
const PKG_RE = /^[a-zA-Z][\w]*(\.[a-zA-Z][\w]*)+$/;
// ViewID：形如 com.example:id/skip_view
const VID_RE = /^[\w.$]+:id\/[\w]+$/;

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

/**
 * 清洗字符串列表：trim、长度截断、去空、去重、总数上限。
 */
function cleanStringList(value, maxLen) {
  if (!Array.isArray(value)) return [];
  const seen = new Set();
  const out = [];
  for (const item of value) {
    const s = String(item).trim().slice(0, maxLen || config.MAX_VIEWID_LEN);
    if (s.length === 0) continue;
    if (seen.has(s.toLowerCase())) continue;
    seen.add(s.toLowerCase());
    out.push(s);
    if (out.length >= config.MAX_RULES_PER_APP) break;
  }
  return out;
}

/**
 * 校验单个关键词：非空、长度上限。
 */
function isValidKeyword(kw) {
  const s = String(kw).trim();
  return s.length > 0 && s.length <= config.MAX_KEYWORD_LEN;
}

/**
 * 校验单个 ViewID 规则：非空、长度上限、可选正则匹配。
 * ViewID 规则允许是简单子串（如 "skip"），不强制全匹配。
 */
function isValidViewIdRule(vid) {
  const s = String(vid).trim();
  return s.length >= 3 && s.length <= config.MAX_VIEWID_RULE_LEN;
}

/**
 * 校验包名格式。
 */
function isValidPackage(pkg) {
  const s = String(pkg).trim();
  return s.length <= 256 && PKG_RE.test(s);
}

/**
 * 清洗并校验规则载荷。
 * @returns {object|null} 清洗后的规则对象；非法返回 null
 */
function cleanRules(input) {
  if (!isPlainObject(input)) return null;

  const keywords = cleanStringList(input.keywords, config.MAX_KEYWORD_LEN)
    .filter(isValidKeyword);
  const viewIds = cleanStringList(input.viewIds, config.MAX_VIEWID_RULE_LEN)
    .filter(isValidViewIdRule);

  if (!isPlainObject(input.packages)) return null;

  const packages = Object.create(null);
  let appCount = 0;
  for (const [pkg, rule] of Object.entries(input.packages)) {
    if (appCount >= config.MAX_APPS) break;
    if (!isValidPackage(pkg)) continue;
    if (!isPlainObject(rule)) continue;

    const pkgKeywords = cleanStringList(rule.keywords, config.MAX_KEYWORD_LEN)
      .filter(isValidKeyword);
    const pkgViewIds = cleanStringList(rule.viewIds, config.MAX_VIEWID_RULE_LEN)
      .filter(isValidViewIdRule);

    packages[pkg] = {
      keywords: pkgKeywords,
      viewIds: pkgViewIds,
      disabled: rule.disabled === true,
    };
    appCount++;
  }

  return { keywords, viewIds, packages };
}

/**
 * 校验上报载荷（单条）。
 * @returns {object|null} 清洗后的事件；非法返回 null
 */
function cleanReportEvent(input) {
  if (!isPlainObject(input)) return null;
  const pkg = String(input.pkg || '').trim().slice(0, 256);
  if (!pkg || !isValidPackage(pkg)) return null;
  const channel = String(input.channel || 'text').trim().slice(0, 32) || 'text';
  if (!['text', 'viewId'].includes(channel)) return null;
  const ts = Number.isFinite(input.ts) ? input.ts : Date.now();
  return { pkg, channel, ts };
}

/**
 * 校验批量上报载荷。
 * @returns {object|null} { deviceId, events } 或 null
 */
function cleanBatchReport(input) {
  if (!isPlainObject(input)) return null;
  const deviceId = String(input.deviceId || '').trim().slice(0, 128);
  if (deviceId.length < 8) return null;
  if (!Array.isArray(input.events)) return null;
  if (input.events.length === 0) return null;

  const events = [];
  for (const ev of input.events) {
    const cleaned = cleanReportEvent(ev);
    if (!cleaned) continue;
    events.push(cleaned);
    if (events.length >= config.MAX_BATCH_EVENTS) break;
  }
  if (events.length === 0) return null;

  return { deviceId, events };
}

module.exports = {
  PKG_RE,
  VID_RE,
  cleanRules,
  cleanReportEvent,
  cleanBatchReport,
  isValidKeyword,
  isValidViewIdRule,
  isValidPackage,
};
