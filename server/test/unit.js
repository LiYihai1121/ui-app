#!/usr/bin/env node
'use strict';

/**
 * 单元测试：validate / auth / rateLimit / store
 * 无需启动服务，直接测试模块逻辑。
 * 运行：node test/unit.js
 */

const assert = require('assert');
const validate = require('../src/validate');
const auth = require('../src/auth');
const rateLimit = require('../src/rateLimit');

let passed = 0;
let failed = 0;

function test(name, fn) {
  try {
    fn();
    passed++;
    console.log(`  \u2714 ${name}`);
  } catch (e) {
    failed++;
    console.log(`  \u2718 ${name}: ${e.message}`);
  }
}

async function testAsync(name, fn) {
  try {
    await fn();
    passed++;
    console.log(`  \u2714 ${name}`);
  } catch (e) {
    failed++;
    console.log(`  \u2718 ${name}: ${e.message}`);
  }
}

// ---------- validate ----------
console.log('\n--- validate ---');

test('cleanRules: 合法规则通过', () => {
  const r = validate.cleanRules({
    keywords: ['跳过', 'skip'],
    viewIds: ['skip'],
    packages: {
      'com.example.app': { keywords: ['跳过'], viewIds: ['skip'], disabled: false },
    },
  });
  assert.ok(r, 'should not be null');
  assert.strictEqual(r.keywords.length, 2);
  assert.ok(r.packages['com.example.app']);
});

test('cleanRules: 非法 packages 被拒', () => {
  const r = validate.cleanRules({ keywords: [], viewIds: [], packages: null });
  assert.strictEqual(r, null);
});

test('cleanRules: 关键词超长被截断', () => {
  const longKw = 'a'.repeat(20);
  const r = validate.cleanRules({ keywords: [longKw], viewIds: [], packages: {} });
  assert.ok(r);
  assert.strictEqual(r.keywords[0].length, 12); // MAX_KEYWORD_LEN
});

test('cleanRules: 重复关键词去重', () => {
  const r = validate.cleanRules({ keywords: ['跳过', '跳过', 'skip', 'SKIP'], viewIds: [], packages: {} });
  assert.strictEqual(r.keywords.length, 2); // 跳过 + skip (大小写去重)
});

test('cleanRules: 非法包名被跳过', () => {
  const r = validate.cleanRules({
    keywords: [], viewIds: [],
    packages: {
      'invalid': { keywords: [], viewIds: [], disabled: false },
      'com.valid.app': { keywords: [], viewIds: [], disabled: false },
    },
  });
  assert.ok(r);
  assert.ok(!r.packages['invalid']);
  assert.ok(r.packages['com.valid.app']);
});

test('cleanRules: 非 plain object 被拒', () => {
  assert.strictEqual(validate.cleanRules(null), null);
  assert.strictEqual(validate.cleanRules('string'), null);
  assert.strictEqual(validate.cleanRules([]), null);
});

test('isValidKeyword: 边界', () => {
  assert.ok(validate.isValidKeyword('跳过'));
  assert.ok(!validate.isValidKeyword(''));
  assert.ok(!validate.isValidKeyword('a'.repeat(13)));
});

test('isValidViewIdRule: 边界', () => {
  assert.ok(validate.isValidViewIdRule('skip'));
  assert.ok(!validate.isValidViewIdRule('ab')); // < 3
  assert.ok(!validate.isValidViewIdRule(''));
});

test('isValidPackage: 正则约束', () => {
  assert.ok(validate.isValidPackage('com.example.app'));
  assert.ok(validate.isValidPackage('com.ldp.adskip'));
  assert.ok(!validate.isValidPackage('invalid'));
  assert.ok(!validate.isValidPackage('.com.example'));
  assert.ok(!validate.isValidPackage('com..app'));
});

test('cleanBatchReport: 合法批量', () => {
  const r = validate.cleanBatchReport({
    deviceId: 'device-1234567890',
    events: [
      { pkg: 'com.example.app', channel: 'text', ts: 1234567890 },
      { pkg: 'com.example.app2', channel: 'viewId', ts: 1234567891 },
    ],
  });
  assert.ok(r);
  assert.strictEqual(r.events.length, 2);
});

test('cleanBatchReport: deviceId 过短被拒', () => {
  assert.strictEqual(validate.cleanBatchReport({ deviceId: 'short', events: [{ pkg: 'com.x.y' }] }), null);
});

test('cleanBatchReport: 超过上限截断', () => {
  const events = Array.from({ length: 100 }, (_, i) => ({ pkg: `com.app${i}.test`, channel: 'text', ts: i }));
  const r = validate.cleanBatchReport({ deviceId: 'device-1234567890', events });
  assert.ok(r);
  assert.ok(r.events.length <= 50);
});

test('cleanBatchReport: 空 events 被拒', () => {
  assert.strictEqual(validate.cleanBatchReport({ deviceId: 'device-1234567890', events: [] }), null);
});

test('cleanBatchReport: 非法包名事件被跳过', () => {
  const r = validate.cleanBatchReport({
    deviceId: 'device-1234567890',
    events: [
      { pkg: 'invalid', channel: 'text', ts: 0 },
      { pkg: 'com.valid.app', channel: 'text', ts: 0 },
    ],
  });
  assert.ok(r);
  assert.strictEqual(r.events.length, 1);
});

// ---------- auth ----------
console.log('\n--- auth ---');

test('requireAdmin: 未配置 token → 503', () => {
  // 默认未设置 ADMIN_TOKEN
  const result = auth.requireAdmin({ headers: {} });
  assert.ok(!result.ok);
  assert.strictEqual(result.status, 503);
});

test('requireAdmin: 带 Bearer token 通过', () => {
  // 临时设置
  const config = require('../src/config');
  const old = config.ADMIN_TOKEN;
  config.ADMIN_TOKEN = 'test-token-123';
  const result = auth.requireAdmin({ headers: { authorization: 'Bearer test-token-123' } });
  assert.ok(result.ok);
  config.ADMIN_TOKEN = old;
});

test('requireAdmin: 错误 token → 401', () => {
  const config = require('../src/config');
  const old = config.ADMIN_TOKEN;
  config.ADMIN_TOKEN = 'test-token-123';
  const result = auth.requireAdmin({ headers: { authorization: 'Bearer wrong-token' } });
  assert.ok(!result.ok);
  assert.strictEqual(result.status, 401);
  config.ADMIN_TOKEN = old;
});

test('requireAdmin: 无 Authorization 头 → 401', () => {
  const config = require('../src/config');
  const old = config.ADMIN_TOKEN;
  config.ADMIN_TOKEN = 'test-token-123';
  const result = auth.requireAdmin({ headers: {} });
  assert.ok(!result.ok);
  assert.strictEqual(result.status, 401);
  config.ADMIN_TOKEN = old;
});

test('checkAdminAuth: 非 Bearer 前缀', () => {
  const config = require('../src/config');
  const old = config.ADMIN_TOKEN;
  config.ADMIN_TOKEN = 'test-token-123';
  assert.ok(!auth.checkAdminAuth({ headers: { authorization: 'Basic abc' } }));
  config.ADMIN_TOKEN = old;
});

// ---------- rateLimit ----------
console.log('\n--- rateLimit ---');

test('allow: 首次请求允许', () => {
  assert.ok(rateLimit.allow('test-key-1', 10));
});

test('allow: 超限后拒绝', () => {
  const key = 'test-key-2';
  for (let i = 0; i < 10; i++) rateLimit.allow(key, 10);
  assert.ok(!rateLimit.allow(key, 10));
});

test('limitRead: 正常请求允许', () => {
  assert.ok(rateLimit.limitRead({ socket: { remoteAddress: '1.2.3.4' }, headers: {} }));
});

test('limitReport: 正常请求允许', () => {
  assert.ok(rateLimit.limitReport({ socket: { remoteAddress: '1.2.3.5' }, headers: {} }, 'device-1234567890'));
});

test('clientIp: 提取 x-forwarded-for', () => {
  assert.strictEqual(rateLimit.clientIp({ headers: { 'x-forwarded-for': '1.2.3.4, 5.6.7.8' }, socket: {} }), '1.2.3.4');
  assert.strictEqual(rateLimit.clientIp({ headers: {}, socket: { remoteAddress: '9.9.9.9' } }), '9.9.9.9');
});

// ---------- 总结 ----------
console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
