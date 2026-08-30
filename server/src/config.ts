import * as path from "node:path";

// config.ts 位于 src/ 目录，向上一级才是 server 根目录
export const ROOT = path.join(import.meta.dir, "..");

export interface Config {
  PORT: number;
  HOST: string;
  ROOT: string;
  DATA_DIR: string;
  RULES_FILE: string;
  STATS_DIR: string;
  PUBLIC_DIR: string;
  APK_FILE: string;
  MAX_BODY: number;
  RECENT_CAP: number;
  STATS_RETENTION_DAYS: number;
  STATS_DIR_CLEANUP_ON_START: boolean;
  BACKUP_DIR: string;
  BACKUP_COUNT: number;
  ADMIN_TOKEN: string;
  STATS_READ_AUTH: boolean;
  CORS_ORIGINS: string[] | null;
  RATE_LIMIT_READ_PER_MIN: number;
  RATE_LIMIT_REPORT_PER_MIN: number;
  RATE_LIMIT_WRITE_PER_MIN: number;
  SCHEMA_VERSION: number;
  SCHEMA_VERSION_MIN: number;
  MAX_KEYWORD_LEN: number;
  MAX_VIEWID_LEN: number;
  MAX_VIEWID_RULE_LEN: number;
  MAX_RULES_PER_APP: number;
  MAX_APPS: number;
  MAX_BATCH_EVENTS: number;
  MAX_BODY_KEYS: number;
  MAX_BODY_DEPTH: number;
}

export const config: Config = {
  PORT: Number(process.env.PORT ?? 3210),
  HOST: process.env.HOST ?? "0.0.0.0",
  ROOT,
  DATA_DIR: path.join(ROOT, "data"),
  RULES_FILE: path.join(ROOT, "data", "rules.json"),
  STATS_DIR: path.join(ROOT, "data", "stats"),
  PUBLIC_DIR: path.join(ROOT, "public"),
  APK_FILE: path.join(ROOT, "..", "AdSkip-latest.apk"),
  MAX_BODY: 1024 * 1024,
  RECENT_CAP: 500,
  STATS_RETENTION_DAYS: 90,
  STATS_DIR_CLEANUP_ON_START: true,
  BACKUP_DIR: path.join(ROOT, "data", "backups"),
  BACKUP_COUNT: 5,
  ADMIN_TOKEN: process.env.ADMIN_TOKEN ?? "",
  STATS_READ_AUTH: false,
  CORS_ORIGINS: process.env.CORS_ORIGINS
    ? process.env.CORS_ORIGINS.split(",").map((s) => s.trim())
    : null,
  RATE_LIMIT_READ_PER_MIN: 120,
  RATE_LIMIT_REPORT_PER_MIN: 30,
  RATE_LIMIT_WRITE_PER_MIN: 10,
  SCHEMA_VERSION: 1,
  SCHEMA_VERSION_MIN: 1,
  MAX_KEYWORD_LEN: 12,
  MAX_VIEWID_LEN: 256,
  MAX_VIEWID_RULE_LEN: 256,
  MAX_RULES_PER_APP: 512,
  MAX_APPS: 2000,
  MAX_BATCH_EVENTS: 50,
  MAX_BODY_KEYS: 100,
  MAX_BODY_DEPTH: 5,
};
