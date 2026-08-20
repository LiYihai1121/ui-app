#!/usr/bin/env node
'use strict';

/**
 * 净启动 AdSkip 后端入口。
 *
 * 结构：
 *   src/config.js   配置
 *   src/httpUtil.js HTTP 工具
 *   src/store.js    存储层（JSON 文件原子读写）
 *   src/api.js      /api/* 路由
 *   public/         落地页 index.html + 管理后台 admin.html
 *
 * 路由：
 *   GET  /                    产品落地页
 *   GET  /admin               管理后台
 *   GET  /download            APK 下载
 *   GET  /api/rules/latest    规则下发（客户端）
 *   PUT  /api/rules           规则发布（管理后台）
 *   POST /api/skip            跳过上报（客户端）
 *   GET  /api/stats/summary   统计汇总（管理后台）
 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const os = require('os');
const config = require('./src/config');
const { setCors, sendJson, sendHtml } = require('./src/httpUtil');
const { handleApi } = require('./src/api');

const server = http.createServer(async (req, res) => {
  setCors(res);
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
        'Content-Disposition': 'attachment; filename="AdSkip-v2.0.apk"',
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
  for (const [name, list] of Object.entries(os.networkInterfaces())) {
    for (const net of list || []) {
      if (net.family === 'IPv4' && !net.internal) {
        console.log(`[AdSkip Server] 局域网: http://${net.address}:${config.PORT}`);
      }
    }
  }
});
