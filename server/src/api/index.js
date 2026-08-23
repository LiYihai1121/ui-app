'use strict';

/**
 * API 路由分发器：统一入口，分发到各子模块。
 *
 * v0（兼容期）：
 *   GET  /api/rules/latest     规则下发（旧格式）
 *   PUT  /api/rules            规则发布（admin token）
 *   POST /api/skip             跳过上报
 *   GET  /api/stats/summary    统计汇总
 *
 * v1：
 *   GET  /api/v1/rules/latest  规则下发（ETag/304）
 *   PUT  /api/v1/rules         规则发布（admin token）
 *   POST /api/v1/rules/test    规则模拟器（admin token）
 *   POST /api/v1/reports/batch 批量上报
 *   GET  /api/v1/stats/summary 统计汇总
 *   GET  /api/v1/health        健康检查
 */
const rulesApi = require('./rulesApi');
const statsApi = require('./statsApi');
const healthApi = require('./healthApi');
const { sendJson } = require('../httpUtil');

async function handleApi(req, res, url) {
  const p = url.pathname;

  // ---------- v1 路由 ----------
  if (p.startsWith('/api/v1/')) {
    // 规则下发（ETag/304）
    if (req.method === 'GET' && p === '/api/v1/rules/latest') {
      return rulesApi.v1_latest(req, res, url);
    }
    // 规则发布
    if (req.method === 'PUT' && p === '/api/v1/rules') {
      return rulesApi.v1_publish(req, res);
    }
    // 规则模拟器
    if (req.method === 'POST' && p === '/api/v1/rules/test') {
      return rulesApi.v1_testRule(req, res);
    }
    // 批量上报
    if (req.method === 'POST' && p === '/api/v1/reports/batch') {
      return rulesApi.v1_batchReport(req, res);
    }
    // 统计汇总
    if (req.method === 'GET' && p === '/api/v1/stats/summary') {
      return statsApi.summary(req, res);
    }
    // 健康检查
    if (req.method === 'GET' && p === '/api/v1/health') {
      return healthApi.health(req, res);
    }
    return sendJson(res, 404, { error: 'not found' });
  }

  // ---------- v0 兼容路由 ----------
  if (req.method === 'GET' && p === '/api/rules/latest') {
    return rulesApi.v0_latest(req, res);
  }
  if (req.method === 'PUT' && p === '/api/rules') {
    return rulesApi.v0_publish(req, res);
  }
  if (req.method === 'POST' && p === '/api/skip') {
    return rulesApi.v0_skip(req, res);
  }
  if (req.method === 'GET' && p === '/api/stats/summary') {
    return statsApi.summary(req, res);
  }

  return sendJson(res, 404, { error: 'not found' });
}

module.exports = { handleApi };
