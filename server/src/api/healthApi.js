'use strict';

/**
 * 健康检查 API（v1）。
 *
 *   GET /api/v1/health  服务健康检查
 */
const { sendJson } = require('../httpUtil');

function health(req, res) {
  return sendJson(res, 200, {
    status: 'ok',
    timestamp: new Date().toISOString(),
  });
}

module.exports = { health };
