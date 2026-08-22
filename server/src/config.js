'use strict';

/**
 * 全局配置：端口、路径、限制参数集中管理。
 * 支持 env 覆盖，方便部署环境差异化配置。
 */
const path = require('path');

const ROOT = path.join(__dirname, '..');

module.exports = {
  PORT: process.env.PORT || 3210,
  HOST: process.env.HOST || '0.0.0.0',
  ROOT,
  DATA_DIR: path.join(ROOT, 'data'),
  RULES_FILE: path.join(ROOT, 'data', 'rules.json'),
  STATS_DIR: path.join(ROOT, 'data', 'stats'),
  PUBLIC_DIR: path.join(ROOT, 'public'),
  APK_FILE: path.join(ROOT, '..', 'AdSkip-v2.1.apk'),

  // 请求体上限
  MAX_BODY: 1024 * 1024, // 1MB

  // 统计
  RECENT_CAP: 500,           // 最近跳过记录保留条数
  STATS_RETENTION_DAYS: 90,  // 分日统计文件保留天数
  STATS_DIR_CLEANUP_ON_START: true,

  // 备份轮转
  BACKUP_DIR: path.join(ROOT, 'data', 'backups'),
  BACKUP_COUNT: 5,           // 发布规则前轮转保留的备份数

  // 鉴权：未设置 ADMIN_TOKEN 时写接口返回 503（拒绝服务而非裸奔）
  ADMIN_TOKEN: process.env.ADMIN_TOKEN || '',
  // 统计读接口是否需要鉴权（默认公开，只有 summary 需要）
  STATS_READ_AUTH: false,

  // CORS 白名单：未配置时回退到 *（开发模式）
  // 生产环境通过环境变量 CORS_ORIGINS 设置，逗号分隔
  CORS_ORIGINS: process.env.CORS_ORIGINS
    ? process.env.CORS_ORIGINS.split(',').map((s) => s.trim())
    : null, // null = 允许全部（开发模式）

  // 限频
  RATE_LIMIT_READ_PER_MIN: 120,    // per-IP 读接口
  RATE_LIMIT_REPORT_PER_MIN: 30,   // per-deviceId 上报
  RATE_LIMIT_WRITE_PER_MIN: 10,    // per-IP 写接口（规则发布）

  // 协议
  SCHEMA_VERSION: 1,
  SCHEMA_VERSION_MIN: 1, // 客户端最低支持的 schemaVersion

  // 校验常量（与客户端同源）
  MAX_KEYWORD_LEN: 12,
  MAX_VIEWID_LEN: 256,
  MAX_VIEWID_RULE_LEN: 256,
  MAX_RULES_PER_APP: 512,
  MAX_APPS: 2000,
  MAX_BATCH_EVENTS: 50,
  MAX_BODY_KEYS: 100,
  MAX_BODY_DEPTH: 5,
};
