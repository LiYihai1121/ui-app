import * as path from "node:path";

// config.ts 位于 src/ 目录，向上一级才是 server 根目录
export const ROOT = path.join(import.meta.dir, "..");

export interface Config {
  PORT: number;
  HOST: string;
  ROOT: string;
  DATA_DIR: string;
  RULES_FILE: string;
  /** 入库的初始规则种子；运行时 RULES_FILE 缺失时兜底（不随 dataDir 注入改变） */
  SEED_RULES_FILE: string;
  STATS_DIR: string;
  PUBLIC_DIR: string;
  APK_FILE: string;
  MAX_BODY: number;
  RECENT_CAP: number;
  STATS_RETENTION_DAYS: number;
  STATS_DIR_CLEANUP_ON_START: boolean;
  BACKUP_DIR: string;
  BACKUP_COUNT: number;
  /** 统计分片保留的备份份数（rotateStatsBackup 轮转） */
  STATS_BACKUP_COUNT: number;
  ADMIN_TOKEN: string;
  /** 统计汇总是否要求 admin 鉴权（STATS_READ_AUTH=1 开启；默认匿名可读，行为与旧版一致） */
  STATS_READ_AUTH: boolean;
  /**
   * 是否信任反向代理注入的 X-Forwarded-For 来判定客户端 IP。
   * 默认 false：XFF 由请求方任意填写，若默认信任则限流桶可被随意换新（限流绕过）。
   * 仅当服务端确实部署在可信反向代理之后时才置为 true（TRUST_PROXY=1）。
   */
  TRUST_PROXY: boolean;
  CORS_ORIGINS: string[] | null;
  RATE_LIMIT_READ_PER_MIN: number;
  RATE_LIMIT_REPORT_PER_MIN: number;
  RATE_LIMIT_WRITE_PER_MIN: number;
  SCHEMA_VERSION: number;
  SCHEMA_VERSION_MIN: number;
  MAX_KEYWORD_LEN: number;
  MAX_VIEWID_LEN: number;
  MAX_VIEWID_RULE_LEN: number;
  MAX_SELECTOR_LEN: number;
  MAX_SELECTORS_PER_LIST: number;
  MAX_RULES_PER_APP: number;
  MAX_APPS: number;
  /** 当日统计 byApp 条目数上限（未认证上报可伪造任意包名，超限聚合进 "_other"） */
  MAX_STATS_APPS_PER_DAY: number;
  MAX_BATCH_EVENTS: number;
  MAX_BODY_KEYS: number;
  MAX_BODY_DEPTH: number;
  /** TLS 证书路径（空字符串表示禁用 HTTPS）。 */
  TLS_CERT: string;
  /** TLS 私钥路径（空字符串表示禁用 HTTPS）。 */
  TLS_KEY: string;
}

export const config: Config = {
  PORT: Number(process.env.PORT ?? 3210),
  HOST: process.env.HOST ?? "0.0.0.0",
  ROOT,
  DATA_DIR: path.join(ROOT, "data"),
  RULES_FILE: path.join(ROOT, "data", "rules.json"),
  SEED_RULES_FILE: path.join(ROOT, "seed", "rules.json"),
  STATS_DIR: path.join(ROOT, "data", "stats"),
  PUBLIC_DIR: path.join(ROOT, "public"),
  APK_FILE: path.join(ROOT, "..", "AdSkip-latest.apk"),
  MAX_BODY: 1024 * 1024,
  RECENT_CAP: 500,
  STATS_RETENTION_DAYS: 90,
  STATS_DIR_CLEANUP_ON_START: true,
  BACKUP_DIR: path.join(ROOT, "data", "backups"),
  BACKUP_COUNT: 5,
  STATS_BACKUP_COUNT: 5,
  ADMIN_TOKEN: process.env.ADMIN_TOKEN ?? "",
  STATS_READ_AUTH: ["1", "true", "yes"].includes(
    (process.env.STATS_READ_AUTH ?? "").toLowerCase()
  ),
  TRUST_PROXY: ["1", "true", "yes"].includes(
    (process.env.TRUST_PROXY ?? "").toLowerCase()
  ),
  CORS_ORIGINS: process.env.CORS_ORIGINS
    ? process.env.CORS_ORIGINS.split(",").map((s) => s.trim())
    : null,
  RATE_LIMIT_READ_PER_MIN: 120,
  RATE_LIMIT_REPORT_PER_MIN: 30,
  RATE_LIMIT_WRITE_PER_MIN: 10,
  // 2 = 载荷含选择器通道（rules.globalSelectors / apps.*.selectors）；
  // MIN 保持 1，旧客户端（不识 selectors）对 schema 2 载荷行为不变。
  SCHEMA_VERSION: 2,
  SCHEMA_VERSION_MIN: 1,
  MAX_KEYWORD_LEN: 12,
  MAX_VIEWID_LEN: 256,
  MAX_VIEWID_RULE_LEN: 256,
  MAX_SELECTOR_LEN: 256,
  MAX_SELECTORS_PER_LIST: 128,
  MAX_RULES_PER_APP: 512,
  MAX_APPS: 2000,
  MAX_STATS_APPS_PER_DAY: 2000,
  MAX_BATCH_EVENTS: 50,
  MAX_BODY_KEYS: 100,
  MAX_BODY_DEPTH: 5,
  TLS_CERT: process.env.TLS_CERT ?? "",
  TLS_KEY: process.env.TLS_KEY ?? "",
};
