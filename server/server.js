#!/usr/bin/env node
'use strict';

/**
 * 净启动 AdSkip 后端入口。
 *
 * 结构：
 *   src/config.js       配置（env 覆盖）
 *   src/httpUtil.js     HTTP 工具（CORS 白名单 / JSON / body 安全解析）
 *   src/auth.js         Bearer token 鉴权
 *   src/rateLimit.js     令牌桶限频
 *   src/validate.js     载荷校验（与客户端同源约束）
 *   src/store.js        存储层（JSON 原子读写 + 备份轮转 + 分日统计）
 *   src/api/index.js    路由分发 → rulesApi / statsApi / healthApi
 *   public/             落地页 + 管理后台
 *
 * 路由：
 *   GET  /                     产品落地页
 *   GET  /admin                管理后台
 *   GET  /download             APK 下载
 *   GET  /api/rules/latest     规则下发（v0 兼容）
 *   PUT  /api/rules            规则发布（v0，需 admin token）
 *   POST /api/skip             跳过上报（v0 兼容）
 *   GET  /api/stats/summary    统计汇总（v0 兼容）
 *   GET  /api/v1/rules/latest  规则下发（v1，ETag/304）
 *   PUT  /api/v1/rules         规则发布（v1，需 admin token）
 *   POST /api/v1/rules/test    规则模拟器（需 admin token）
 *   POST /api/v1/reports/batch 批量上报（v1）
 *   GET  /api/v1/stats/summary 统计汇总（v1）
 *   GET  /api/v1/health        健康检查
 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const config = require('./src/config');
const { setCors, sendJson, sendHtml } = require('./src/httpUtil');
const { handleApi } = require('./src/api');
const store = require('./src/store');

// 启动时清理过期统计分片
if (config.STATS_DIR_CLEANUP_ON_START) {
  store.cleanupOldStats();
}

const server = http.createServer(async (req, res) => {
  setCors(res, req.headers.origin);
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    return res.end();
  }

  const url = new URL(req.url, `http://localhost:${config.PORT}`);

  try {
    if (url.pathname.startsWith('/api/')) {
      return await handleApi(req, res, url);
    }

    if (url.pathname === '/' && req.method === 'GET') {
      return sendHtml(res, fs.readFileSync(path.join(config.PUBLIC_DIR, 'index.html')));
    }

    if (url.pathname === '/admin' && req.method === 'GET') {
      return sendHtml(res, fs.readFileSync(path.join(config.PUBLIC_DIR, 'admin.html')));
    }

    if (url.pathname === '/download' && req.method === 'GET') {
      if (!fs.existsSync(config.APK_FILE)) return sendJson(res, 404, { error: 'apk not found' });
      res.writeHead(200, {
        'Content-Type': 'application/vnd.android.package-archive',
        'Content-Disposition': 'attachment; filename="AdSkip-v2.1.apk"',
        'Content-Length': fs.statSync(config.APK_FILE).size,
      });
      return fs.createReadStream(config.APK_FILE).pipe(res);
    }

    sendJson(res, 404, { error: 'not found' });
  } catch (e) {
    const status = e && Number.isInteger(e.statusCode) ? e.statusCode : 500;
    sendJson(res, status, { error: String(e && e.message ? e.message : e) });
  }
});

server.listen(config.PORT, config.HOST, () => {
  console.log(`[AdSkip Server] 已启动，端口 ${config.PORT}`);
  console.log(`[AdSkip Server] 落地页:   http://localhost:${config.PORT}/`);
  console.log(`[AdSkip Server] 管理后台: http://localhost:${config.PORT}/admin`);
  if (!config.ADMIN_TOKEN) {
    console.log('[AdSkip Server] ⚠️  ADMIN_TOKEN 未配置，写接口将返回 503');
  }
  for (const [name, list] of Object.entries(os.networkInterfaces())) {
    for (const net of list || []) {
      if (net.family === 'IPv4' && !net.internal) {
        console.log(`[AdSkip Server] 局域网: http://${net.address}:${config.PORT}`);
      }
    }
  }
});

// ---------- 优雅停机 ----------
function shutdown(signal) {
  console.log(`\n[AdSkip Server] 收到 ${signal}，正在优雅停机…`);
  store.flush();
  server.close(() => {
    console.log('[AdSkip Server] 已停止');
    process.exit(0);
  });
  // 兜底：5 秒后强制退出
  setTimeout(() => process.exit(1), 5000).unref();
}

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));
