#!/usr/bin/env node
'use strict';

/**
 * 冒烟测试：对运行中的服务依次验证全部路由。
 * 用法：
 *   ADMIN_TOKEN=test123 node server.js   # 启动服务（带 token）
 *   ADMIN_TOKEN=test123 node test/smoke.js  # 运行测试
 *
 * 若不设置 ADMIN_TOKEN，写接口测试将验证 503（拒绝服务）。
 */

const BASE = process.env.BASE_URL || 'http://localhost:3210';
const TOKEN = process.env.ADMIN_TOKEN || '';
const authHeader = TOKEN ? { Authorization: `Bearer ${TOKEN}` } : {};

let passed = 0;
let failed = 0;

function check(name, cond, extra) {
  if (cond) {
    passed++;
    console.log(`  \u2714 ${name}`);
  } else {
    failed++;
    console.log(`  \u2718 ${name} ${extra || ''}`);
  }
}

async function main() {
  console.log(`Smoke test against ${BASE}\n`);

  // 1. 落地页
  const home = await fetch(BASE + '/');
  check('GET / 返回 200 且为 HTML', home.status === 200 && (home.headers.get('content-type') || '').includes('text/html'));

  // 2. 管理后台
  const admin = await fetch(BASE + '/admin');
  check('GET /admin 返回 200', admin.status === 200);

  // 3. 健康检查（v1）
  const healthRes = await fetch(BASE + '/api/v1/health');
  const health = await healthRes.json();
  check('GET /api/v1/health 返回 ok', healthRes.status === 200 && health.status === 'ok');

  // 4. 规则下发（v0 兼容）
  const rulesRes = await fetch(BASE + '/api/rules/latest');
  const rules = await rulesRes.json();
  check(
    'GET /api/rules/latest 结构完整（v0）',
    rulesRes.status === 200 && Array.isArray(rules.keywords) && Array.isArray(rules.viewIds) && typeof rules.packages === 'object'
  );

  // 5. 规则下发（v1，含 ETag）
  const v1RulesRes1 = await fetch(BASE + '/api/v1/rules/latest');
  const v1Rules = await v1RulesRes1.json();
  check(
    'GET /api/v1/rules/latest 结构完整（v1）',
    v1RulesRes1.status === 200 && v1Rules.schemaVersion === 1 && typeof v1Rules.rules === 'object' && typeof v1Rules.hash === 'string'
  );
  check('v1 规则响应含 ETag 头', v1RulesRes1.headers.get('etag') === v1Rules.hash);

  // 6. ETag 304
  if (v1Rules.hash) {
    const notModRes = await fetch(BASE + '/api/v1/rules/latest', {
      headers: { 'If-None-Match': v1Rules.hash },
    });
    check('GET /api/v1/rules/latest + If-None-Match → 304', notModRes.status === 304);
  }

  // 7. 跳过上报（v0 兼容）
  const skipRes = await fetch(BASE + '/api/skip', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ pkg: 'com.smoke.test', label: '冒烟测试' }),
  });
  check('POST /api/skip 返回 ok（v0）', skipRes.status === 200 && (await skipRes.json()).ok === true);

  // 8. 批量上报（v1）
  const batchRes = await fetch(BASE + '/api/v1/reports/batch', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      deviceId: 'smoke-test-device-1234',
      events: [
        { pkg: 'com.smoke.batch', channel: 'text', ts: Date.now() },
        { pkg: 'com.smoke.batch2', channel: 'viewId', ts: Date.now() },
      ],
    }),
  });
  const batch = await batchRes.json();
  check('POST /api/v1/reports/batch 返回 ok', batchRes.status === 200 && batch.ok === true && batch.accepted === 2);

  // 9. 批量上报校验：非法载荷拒绝
  const badBatchRes = await fetch(BASE + '/api/v1/reports/batch', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ deviceId: 'x', events: [] }),
  });
  check('POST /api/v1/reports/batch 拒绝非法载荷', badBatchRes.status === 400);

  // 10. 统计汇总含刚上报的记录
  const sumRes = await fetch(BASE + '/api/stats/summary');
  const sum = await sumRes.json();
  check(
    'GET /api/stats/summary 含上报记录',
    sumRes.status === 200 && sum.total >= 1 && sum.byApp.some((a) => a.pkg === 'com.smoke.test')
  );

  // 11. APK 下载
  const dl = await fetch(BASE + '/download');
  check('GET /download 返回 200 且为 APK', dl.status === 200 && (dl.headers.get('content-type') || '').includes('android.package-archive'));

  // 12. 404
  const nf = await fetch(BASE + '/api/nope');
  check('未知 API 返回 404', nf.status === 404);

  // 13. 非法规则包应被拒绝
  const badRulesRes = await fetch(BASE + '/api/rules', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeader },
    body: JSON.stringify({ keywords: 'not-array', viewIds: [1], packages: null }),
  });
  check('PUT /api/rules 拒绝非法 packages', badRulesRes.status === 400);

  // 14. 鉴权：无 token 时规则发布应被拒
  const noTokenRes = await fetch(BASE + '/api/rules', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ keywords: ['跳过'], viewIds: [], packages: {} }),
  });
  if (TOKEN) {
    check('PUT /api/rules 无 token → 401', noTokenRes.status === 401);
  } else {
    check('PUT /api/rules 无 ADMIN_TOKEN → 503', noTokenRes.status === 503);
  }

  // 15. 带 token 的合法规则发布
  if (TOKEN) {
    const okRulesRes = await fetch(BASE + '/api/rules', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json', ...authHeader },
      body: JSON.stringify({ keywords: ['跳过', 'skip'], viewIds: ['skip'], packages: {} }),
    });
    const okRules = await okRulesRes.json();
    check('PUT /api/rules 带 token 发布成功', okRulesRes.status === 200 && okRules.ok === true);
  }

  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed ? 1 : 0);
}

main().catch((e) => {
  console.error('Smoke test 执行失败（服务是否已启动？）:', e.message);
  process.exit(1);
});
