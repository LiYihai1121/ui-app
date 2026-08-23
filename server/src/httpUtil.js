'use strict';

/**
 * HTTP 工具：CORS 白名单、JSON 响应、请求体读取。
 */
const config = require('./config');

/** CORS：按白名单收紧，开发模式（无白名单）回退到 * */
function setCors(res, origin) {
  const allowed = config.CORS_ORIGINS;
  if (allowed && allowed.length > 0) {
    if (origin && allowed.includes(origin)) {
      res.setHeader('Access-Control-Allow-Origin', origin);
      res.setHeader('Vary', 'Origin');
    } else {
      // 不匹配白名单时不设置 ACAO，浏览器会阻止跨域
      res.setHeader('Access-Control-Allow-Origin', 'null');
    }
  } else {
    // 开发模式
    res.setHeader('Access-Control-Allow-Origin', '*');
  }
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, OPTIONS');
  res.setHeader(
    'Access-Control-Allow-Headers',
    'Content-Type, Authorization, If-None-Match'
  );
}

function sendJson(res, code, obj, extraHeaders) {
  const headers = { 'Content-Type': 'application/json; charset=utf-8' };
  if (extraHeaders) Object.assign(headers, extraHeaders);
  res.writeHead(code, headers);
  res.end(JSON.stringify(obj));
}

function sendHtml(res, html) {
  res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end(html);
}

function sendStatus(res, code) {
  res.writeHead(code);
  res.end();
}

/** 读取请求体，带大小上限和键数/深度保护 */
function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (c) => {
      size += c.length;
      if (size > config.MAX_BODY) {
        const err = new Error('body too large');
        err.statusCode = 413;
        reject(err);
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

/** 安全 JSON 解析：校验键数和深度上限，防构造性攻击 */
function safeJsonParse(raw) {
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch {
    const err = new Error('invalid json');
    err.statusCode = 400;
    throw err;
  }
  if (!validateDepth(parsed, 0)) {
    const err = new Error('body nesting too deep');
    err.statusCode = 400;
    throw err;
  }
  if (!validateKeyCount(parsed, config.MAX_BODY_KEYS)) {
    const err = new Error('too many keys in body');
    err.statusCode = 400;
    throw err;
  }
  return parsed;
}

function validateDepth(obj, depth) {
  if (depth > config.MAX_BODY_DEPTH) return false;
  if (obj !== null && typeof obj === 'object') {
    if (Array.isArray(obj)) {
      for (const item of obj) {
        if (!validateDepth(item, depth + 1)) return false;
      }
    } else {
      for (const key of Object.keys(obj)) {
        if (!validateDepth(obj[key], depth + 1)) return false;
      }
    }
  }
  return true;
}

function countKeys(obj, acc) {
  if (obj !== null && typeof obj === 'object') {
    if (Array.isArray(obj)) {
      for (const item of obj) countKeys(item, acc);
    } else {
      for (const key of Object.keys(obj)) {
        acc.count++;
        countKeys(obj[key], acc);
      }
    }
  }
  return acc;
}

function validateKeyCount(obj, max) {
  const acc = countKeys(obj, { count: 0 });
  return acc.count <= max;
}

module.exports = {
  setCors,
  sendJson,
  sendHtml,
  sendStatus,
  readBody,
  safeJsonParse,
};
