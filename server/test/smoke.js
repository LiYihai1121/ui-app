#!/usr/bin/env node
'use strict';

/**
 * 冒烟测试：对运行中的服务依次验证全部路由。
 * 用法：先启动服务（node server.js），再执行 node test/smoke.js
 */

const BASE = process.env.BASE_URL || 'http://localhost:3210';
let passed = 0;
let failed = 0;

function check(name, cond, extra) {
  if (cond) {
    passed++;
    console.log(`  ✔ ${name}`);
  } else {
    failed++;
    console.log(`  ✘ ${name} ${extra || ''}`);
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

  // 3. 规则下发
  const rulesRes = await fetch(BASE + '/api/rules/latest');
  const rules = await rulesRes.json();
  check(
    'GET /api/rules/latest 结构完整',
    rulesRes.status === 200 && Array.isArray(rules.keywords) && Array.isArray(rules.viewIds) && typeof rules.packages === 'object'
  );

  // 4. 跳过上报
  const skipRes = await fetch(BASE + '/api/skip', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ pkg: 'com.smoke.test', label: '冒烟测试' }),
  });
  check('POST /api/skip 返回 ok', skipRes.status === 200 && (await skipRes.json()).ok === true);

  // 5. 统计汇总含刚上报的记录
  const sumRes = await fetch(BASE + '/api/stats/summary');
  const sum = await sumRes.json();
  check(
    'GET /api/stats/summary 含上报记录',
    sumRes.status === 200 && sum.total >= 1 && sum.byApp.some((a) => a.pkg === 'com.smoke.test')
  );

  // 6. APK 下载
  const dl = await fetch(BASE + '/download');
  check('GET /download 返回 200 且为 APK', dl.status === 200 && (dl.headers.get('content-type') || '').includes('android.package-archive'));

  // 7. 404
  const nf = await fetch(BASE + '/api/nope');
  check('未知 API 返回 404', nf.status === 404);

  // 8. 非法规则包应被拒绝，避免 packages=null 落入存储层
  const badRulesRes = await fetch(BASE + '/api/rules', {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ keywords: 'not-array', viewIds: [1], packages: null }),
  });
  check('PUT /api/rules 拒绝非法 packages', badRulesRes.status === 400);

  console.log(`\n${passed} passed, ${failed} failed`);
  process.exit(failed ? 1 : 0);
}

main().catch((e) => {
  console.error('Smoke test 执行失败（服务是否已启动？）:', e.message);
  process.exit(1);
});
