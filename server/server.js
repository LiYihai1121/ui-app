#!/usr/bin/env node
/**
 * 净启动 AdSkip 后端服务（零第三方依赖，Node.js 原生 http）
 *
 * API:
 *   GET  /api/rules/latest   获取最新规则包（客户端同步）
 *   PUT  /api/rules          更新规则包（管理后台）
 *   POST /api/skip           客户端上报一次跳过 { pkg, label }
 *   GET  /api/stats/summary  统计汇总（管理后台）
 *   GET  / 或 /admin         管理后台页面
 *
 * 数据文件：data/rules.json、data/stats.json
 */
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = process.env.PORT || 3210;
const HOST = '0.0.0.0';
const DATA_DIR = path.join(__dirname, 'data');
const RULES_FILE = path.join(DATA_DIR, 'rules.json');
const STATS_FILE = path.join(DATA_DIR, 'stats.json');
const ADMIN_HTML = path.join(__dirname, 'public', 'admin.html');
const MAX_BODY = 1024 * 1024; // 1MB
const RECENT_CAP = 500;

// ---------- 数据读写 ----------
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

function getRules() {
  return readJson(RULES_FILE, { version: 1, updatedAt: new Date().toISOString(), keywords: [], viewIds: [], packages: {} });
}

function getStats() {
  return readJson(STATS_FILE, { total: 0, byDay: {}, byApp: {}, recent: [] });
}

// ---------- 请求工具 ----------
function setCors(res) {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
}

function sendJson(res, code, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (c) => {
      size += c.length;
      if (size > MAX_BODY) {
        reject(new Error('body too large'));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

// ---------- 路由处理 ----------
async function handleApi(req, res, url) {
  // 规则下发（客户端）
  if (req.method === 'GET' && url.pathname === '/api/rules/latest') {
    return sendJson(res, 200, getRules());
  }

  // 更新规则（管理后台）
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
    rules.version = (getRules().version || 1) + 1;
    rules.updatedAt = new Date().toISOString();
    writeJson(RULES_FILE, rules);
    return sendJson(res, 200, { ok: true, version: rules.version });
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
    const pkg = String(data.pkg || 'unknown');
    const label = String(data.label || pkg);
    const now = new Date();
    const day = now.toISOString().slice(0, 10);

    const stats = getStats();
    stats.total += 1;
    stats.byDay[day] = (stats.byDay[day] || 0) + 1;
    if (!stats.byApp[pkg]) stats.byApp[pkg] = { label, count: 0 };
    stats.byApp[pkg].label = label;
    stats.byApp[pkg].count += 1;
    stats.recent.unshift({ ts: now.toISOString(), pkg, label });
    if (stats.recent.length > RECENT_CAP) stats.recent.length = RECENT_CAP;
    writeJson(STATS_FILE, stats);
    return sendJson(res, 200, { ok: true });
  }

  // 统计汇总（管理后台）
  if (req.method === 'GET' && url.pathname === '/api/stats/summary') {
    const stats = getStats();
    const today = new Date().toISOString().slice(0, 10);
    const byApp = Object.entries(stats.byApp)
      .map(([pkg, v]) => ({ pkg, label: v.label, count: v.count }))
      .sort((a, b) => b.count - a.count);
    const days = Object.keys(stats.byDay).sort().slice(-14);
    return sendJson(res, 200, {
      total: stats.total,
      today: stats.byDay[today] || 0,
      byDay: days.map((d) => ({ day: d, count: stats.byDay[d] })),
      byApp,
      recent: stats.recent.slice(0, 50),
    });
  }

  return sendJson(res, 404, { error: 'not found' });
}

// ---------- 服务器 ----------
const server = http.createServer(async (req, res) => {
  setCors(res);
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    return res.end();
  }

  const url = new URL(req.url, `http://localhost:${PORT}`);

  try {
    if (url.pathname.startsWith('/api/')) {
      return await handleApi(req, res, url);
    }

    if (url.pathname === '/' && req.method === 'GET') {
      const html = fs.readFileSync(path.join(__dirname, 'public', 'index.html'));
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      return res.end(html);
    }

    if (url.pathname === '/admin' && req.method === 'GET') {
      const html = fs.readFileSync(ADMIN_HTML);
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      return res.end(html);
    }

    if (url.pathname === '/download' && req.method === 'GET') {
      const apk = path.join(__dirname, '..', 'AdSkip-v2.0.apk');
      if (!fs.existsSync(apk)) return sendJson(res, 404, { error: 'apk not found' });
      res.writeHead(200, {
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Disposition': 'attachment; filename="AdSkip-v2.0.apk"',
        'Content-Length': fs.statSync(apk).size,
      });
      return fs.createReadStream(apk).pipe(res);
    }

    sendJson(res, 404, { error: 'not found' });
  } catch (e) {
    sendJson(res, 500, { error: String(e && e.message ? e.message : e) });
  }
});

server.listen(PORT, HOST, () => {
  const os = require('os');
  const nets = os.networkInterfaces();
  const ips = [];
  for (const name of Object.keys(nets)) {
    for (const net of nets[name] || []) {
      if (net.family === 'IPv4' && !net.internal) ips.push(net.address);
    }
  }
  console.log(`[AdSkip Server] 已启动，端口 ${PORT}`);
  console.log(`[AdSkip Server] 管理后台: http://localhost:${PORT}/admin`);
  if (ips.length) {
    console.log(`[AdSkip Server] 局域网地址（手机端填写此地址）:`);
    ips.forEach((ip) => console.log(`  http://${ip}:${PORT}`));
  }
});
