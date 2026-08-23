'use strict';

/**
 * 规则 API：v0 兼容路由 + v1 新路由。
 *
 * v0（兼容期，旧客户端无感）：
 *   GET  /api/rules/latest     规则下发（旧格式）
 *   PUT  /api/rules            规则发布（需 admin token）
 *
 * v1：
 *   GET  /api/v1/rules/latest  规则下发（新格式 + ETag/304）
 *   PUT  /api/v1/rules         规则发布（需 admin token）
 *   POST /api/v1/rules/test    规则模拟器（需 admin token）
 */
const store = require('../store');
const auth = require('../auth');
const rateLimit = require('../rateLimit');
const validate = require('../validate');
const { sendJson, sendStatus, readBody, safeJsonParse } = require('../httpUtil');

// ---------- v0 兼容路由 ----------

function v0_latest(req, res) {
  const rules = store.getRules();
  // v0 客户端读 keywords/viewIds/packages 兼容字段
  return sendJson(res, 200, {
    version: rules.version,
    updatedAt: rules.updatedAt,
    keywords: rules.keywords,
    viewIds: rules.viewIds,
    packages: rules.packages,
  });
}

async function v0_publish(req, res) {
  const authCheck = auth.requireAdmin(req, res);
  if (!authCheck.ok) return sendJson(res, authCheck.status, { error: authCheck.error });

  if (!rateLimit.limitWrite(req)) {
    return sendJson(res, 429, { error: 'rate limited' });
  }

  const raw = await readBody(req);
  const rules = safeJsonParse(raw);
  const cleaned = validate.cleanRules(rules);
  if (!cleaned) {
    return sendJson(res, 400, { error: 'invalid rules payload' });
  }
  const version = store.saveRules(cleaned);
  return sendJson(res, 200, { ok: true, version });
}

async function v0_skip(req, res) {
  if (!rateLimit.limitReport(req, null)) {
    return sendJson(res, 429, { error: 'rate limited' });
  }
  const raw = await readBody(req);
  const data = safeJsonParse(raw);
  const pkg = String(data.pkg || '').trim().slice(0, 256) || 'unknown';
  const label = String(data.label || data.pkg || '').trim().slice(0, 128) || pkg;
  store.recordSkip(pkg, label, 'text');
  return sendJson(res, 200, { ok: true });
}

// ---------- v1 路由 ----------

function v1_latest(req, res, url) {
  const rules = store.getRules();
  const hash = rules.hash;

  // ETag / 304
  const inm = req.headers['if-none-match'];
  if (inm && inm === hash) {
    return sendStatus(res, 304);
  }

  // v1 完整格式
  return sendJson(res, 200, {
    schemaVersion: rules.schemaVersion,
    version: rules.version,
    hash: hash,
    updatedAt: rules.updatedAt,
    rules: rules.rules,
  }, { ETag: hash });
}

async function v1_publish(req, res) {
  const authCheck = auth.requireAdmin(req, res);
  if (!authCheck.ok) return sendJson(res, authCheck.status, { error: authCheck.error });

  if (!rateLimit.limitWrite(req)) {
    return sendJson(res, 429, { error: 'rate limited' });
  }

  const raw = await readBody(req);
  const rules = safeJsonParse(raw);
  const cleaned = validate.cleanRules(rules);
  if (!cleaned) {
    return sendJson(res, 400, { error: 'invalid rules payload' });
  }
  const version = store.saveRules(cleaned);
  const updated = store.getRules();
  return sendJson(res, 200, { ok: true, version, hash: updated.hash });
}

async function v1_batchReport(req, res) {
  const raw = await readBody(req);
  const data = safeJsonParse(raw);
  const cleaned = validate.cleanBatchReport(data);
  if (!cleaned) {
    return sendJson(res, 400, { error: 'invalid batch report' });
  }

  if (!rateLimit.limitReport(req, cleaned.deviceId)) {
    return sendJson(res, 429, { error: 'rate limited' });
  }

  for (const ev of cleaned.events) {
    store.recordSkip(ev.pkg, ev.pkg, ev.channel);
  }
  return sendJson(res, 200, { ok: true, accepted: cleaned.events.length });
}

/** 规则模拟器：传入样本文本/ViewID，返回是否命中 */
async function v1_testRule(req, res) {
  const authCheck = auth.requireAdmin(req, res);
  if (!authCheck.ok) return sendJson(res, authCheck.status, { error: authCheck.error });

  const raw = await readBody(req);
  const data = safeJsonParse(raw);
  const sampleText = String(data.text || '').trim().slice(0, 256);
  const sampleViewId = String(data.viewId || '').trim().slice(0, 256);
  const keywords = Array.isArray(data.keywords) ? data.keywords : [];
  const viewIds = Array.isArray(data.viewIds) ? data.viewIds : [];

  const results = [];
  if (sampleText) {
    for (const kw of keywords) {
      if (sampleText.includes(String(kw))) {
        results.push({ match: 'keyword', keyword: String(kw), field: 'text' });
      }
    }
  }
  if (sampleViewId) {
    const lower = sampleViewId.toLowerCase();
    for (const vid of viewIds) {
      if (vid.length >= 3 && lower.includes(String(vid).toLowerCase())) {
        results.push({ match: 'viewId', rule: String(vid) });
      }
    }
  }
  return sendJson(res, 200, { hits: results, hit: results.length > 0 });
}

module.exports = {
  v0_latest,
  v0_publish,
  v0_skip,
  v1_latest,
  v1_publish,
  v1_batchReport,
  v1_testRule,
};
