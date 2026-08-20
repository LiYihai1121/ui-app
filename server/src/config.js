'use strict';

/**
 * 全局配置：端口、路径、限制参数集中管理。
 */
const path = require('path');

const ROOT = path.join(__dirname, '..');

module.exports = {
  PORT: process.env.PORT || 3210,
  HOST: '0.0.0.0',
  ROOT,
  DATA_DIR: path.join(ROOT, 'data'),
  RULES_FILE: path.join(ROOT, 'data', 'rules.json'),
  STATS_FILE: path.join(ROOT, 'data', 'stats.json'),
  PUBLIC_DIR: path.join(ROOT, 'public'),
  APK_FILE: path.join(ROOT, '..', 'AdSkip-v2.0.apk'),
  MAX_BODY: 1024 * 1024, // 请求体上限 1MB
  RECENT_CAP: 500,       // 最近跳过记录保留条数
};
