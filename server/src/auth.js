'use strict';

/**
 * 鉴权中间件：Bearer token 校验。
 *
 * - 未配置 ADMIN_TOKEN 时，所有写接口返回 503（拒绝服务而非裸奔）
 * - 统计读接口默认公开（config.STATS_READ_AUTH 可改）
 * - 规则下发（GET rules/latest）始终公开（客户端拉取无需鉴权）
 */
const config = require('./config');

/**
 * 校验请求是否携带合法的 admin token。
 * @returns {boolean} true=鉴权通过
 */
function checkAdminAuth(req) {
  if (!config.ADMIN_TOKEN) return false;
  const auth = req.headers['authorization'] || '';
  if (!auth.startsWith('Bearer ')) return false;
  const token = auth.slice(7).trim();
  return token === config.ADMIN_TOKEN;
}

/**
 * 中间件式：保护写接口。未配置 token → 503；token 不匹配 → 401。
 */
function requireAdmin(req, res) {
  if (!config.ADMIN_TOKEN) {
    return { ok: false, status: 503, error: 'ADMIN_TOKEN not configured' };
  }
  if (!checkAdminAuth(req)) {
    return { ok: false, status: 401, error: 'unauthorized' };
  }
  return { ok: true };
}

module.exports = { checkAdminAuth, requireAdmin };
