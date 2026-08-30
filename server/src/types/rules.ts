/** 规则域模型：v0/v1 共享形状，与 cleanRules / ensureCompatShape 一一对应 */

export interface AppRule {
  keywords: string[];
  viewIds: string[];
  disabled: boolean;
}

/** v1 规则体（下发到客户端的核心结构） */
export interface RuleSetV1 {
  globalKeywords: string[];
  globalViewIds: string[];
  apps: Record<string, AppRule>;
  disabled: string[];
}

/** 完整规则包：v1 体 + v0 兼容字段（keywords/viewIds/packages 与 rules.* 同引用） */
export interface RulesPackage {
  schemaVersion: number;
  version: number;
  updatedAt: string;
  hash: string;
  rules: RuleSetV1;
  /** v0 兼容：与 rules.globalKeywords 同引用 */
  keywords: string[];
  /** v0 兼容：与 rules.globalViewIds 同引用 */
  viewIds: string[];
  /** v0 兼容：与 rules.apps 同引用 */
  packages: Record<string, AppRule>;
}

/** cleanRules 清洗后的载荷形状（v0/v1 发布共用的输入） */
export interface CleanedRules {
  keywords: string[];
  viewIds: string[];
  packages: Record<string, AppRule>;
}

/** 单条跳过记录（统计事件） */
export interface SkipEvent {
  ts: string;
  pkg: string;
  label: string;
  channel: string;
}

export interface AppStat {
  label: string;
  count: number;
  byChannel: Record<string, number>;
}

/** 按天分片的统计文件结构 */
export interface StatsDay {
  day: string;
  byApp: Record<string, AppStat>;
  events: SkipEvent[];
}

/** GET /api/v1/stats/summary 响应 */
export interface StatsSummary {
  total: number;
  today: number;
  byDay: Array<{ day: string; count: number }>;
  byApp: Array<{ pkg: string; label: string; count: number }>;
  recent: SkipEvent[];
}
