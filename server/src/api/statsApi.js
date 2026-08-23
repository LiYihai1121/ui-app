'use strict';

/**
 * 统计 API：v0 兼容路由 + v1 新路由。
 *
 * v0：
 *   GET /api/stats/summary   统计汇总
 *
 * v1：
 *   GET /api/v1/stats/summary  统计汇总（同 v0，路径别名）
 */
const store = require('../store');
const rateLimit = require('../rateLimit');
const { sendJson } = require('../httpUtil');

function summary(req, res) {
  if (!rateLimit.limitRead(req)) {
    return sendJson(res, 429, { error: 'rate limited' });
  }
  return sendJson(res, 200, store.statsSummary());
}

module.exports = { summary };
