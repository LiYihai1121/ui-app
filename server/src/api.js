'use strict';

/**
 * API 路由处理器：/api/* 下所有接口。
 * 纯函数式处理，依赖 store 层，不感知静态资源。
 */
const store = require('./store');
const { sendJson, readBody } = require('./httpUtil');

const MAX_RULE_FIELD = 512;
const MAX_PACKAGE_RULES = 2000;
const MAX_RULE_ITEM = 256;

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function cleanStringList(value) {
  if (!Array.isArray(value)) return [];
  return value
    .map((item) => String(item).trim().slice(0, MAX_RULE_ITEM))
    .filter((item) => item.length > 0)
    .slice(0, MAX_RULE_FIELD);
}

function cleanRules(input) {
  if (!isPlainObject(input)) return null;

  const keywords = cleanStringList(input.keywords);
  const viewIds = cleanStringList(input.viewIds);
  if (!isPlainObject(input.packages)) return null;

  const packages = Object.create(null);
  for (const [pkg, rule] of Object.entries(input.packages)) {
    if (Object.keys(packages).length >= MAX_PACKAGE_RULES) break;
    const safePkg = String(pkg).trim().slice(0, 256);
    if (!safePkg) continue;
    if (!isPlainObject(rule)) continue;
    packages[safePkg] = {
      keywords: cleanStringList(rule.keywords),
      viewIds: cleanStringList(rule.viewIds),
      disabled: rule.disabled === true,
    };
  }

  return { keywords, viewIds, packages };
}

async function handleApi(req, res, url) {
  // 规则下发（客户端同步）
  if (req.method === 'GET' && url.pathname === '/api/rules/latest') {
    return sendJson(res, 200, store.getRules());
  }

  // 规则发布（管理后台）
  if (req.method === 'PUT' && url.pathname === '/api/rules') {
    const body = await readBody(req);
    let rules;
    try {
      rules = JSON.parse(body);
    } catch (e) {
      return sendJson(res, 400, { error: 'invalid json' });
    }
    const cleaned = cleanRules(rules);
    if (!cleaned) {
      return sendJson(res, 400, { error: 'rules must contain keywords[], viewIds[], packages{}' });
    }
    const version = store.saveRules(cleaned);
    return sendJson(res, 200, { ok: true, version });
  }

  // 跳过上报（客户端）
  if (req.method === 'POST' && url.pathname === '/api/skip') {
    const body = await readBody(req);
    let data;
    try {
      data = JSON.parse(body || '{}');
    } catch (e) {
      return sendJson(res, 400, { error: 'invalid json' });
    }
    if (!isPlainObject(data)) return sendJson(res, 400, { error: 'invalid payload' });
    const pkg = String(data.pkg || '').trim().slice(0, 256) || 'unknown';
    const label = String(data.label || data.pkg || '').trim().slice(0, 128) || pkg;
    store.recordSkip(pkg, label);
    return sendJson(res, 200, { ok: true });
  }

  // 统计汇总（管理后台）
  if (req.method === 'GET' && url.pathname === '/api/stats/summary') {
    return sendJson(res, 200, store.statsSummary());
  }

  return sendJson(res, 404, { error: 'not found' });
}

module.exports = { handleApi };
