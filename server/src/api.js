'use strict';

/**
 * API 路由处理器：/api/* 下所有接口。
 * 纯函数式处理，依赖 store 层，不感知静态资源。
 */
const store = require('./store');
const { sendJson, readBody } = require('./httpUtil');

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
    if (!Array.isArray(rules.keywords) || !Array.isArray(rules.viewIds) || typeof rules.packages !== 'object') {
      return sendJson(res, 400, { error: 'rules must contain keywords[], viewIds[], packages{}' });
    }
    const version = store.saveRules(rules);
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
    store.recordSkip(String(data.pkg || 'unknown'), String(data.label || data.pkg || 'unknown'));
    return sendJson(res, 200, { ok: true });
  }

  // 统计汇总（管理后台）
  if (req.method === 'GET' && url.pathname === '/api/stats/summary') {
    return sendJson(res, 200, store.statsSummary());
  }

  return sendJson(res, 404, { error: 'not found' });
}

module.exports = { handleApi };
