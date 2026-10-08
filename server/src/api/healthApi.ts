import { config } from "../config";
import { accessMetrics } from "../middleware/accessLog";
import { getRules, statsSummary } from "../storage/store";
import { isRulesSigningConfigured } from "../utils/rulesSigner";
import { jsonResponse, type Handler } from "../utils/httpUtil";

/**
 * 健康检查与运行指标。
 *
 * - `health`：探活用，刻意**不读盘**（规则/统计都不碰），失败探针只可能是进程本身；
 * - `metrics`：运维观测用粗粒度指标（请求计数 / 规则版本 / 统计总量 / 内存），
 *   不含任何用户级明细（包名、事件、IP 均不出现在响应里）。
 */
export const health: Handler = () => {
  const m = accessMetrics();
  return jsonResponse({
    status: "ok",
    timestamp: new Date().toISOString(),
    uptimeSec: m.uptimeSec,
  });
};

export const metrics: Handler = () => {
  const m = accessMetrics();
  const rules = getRules();
  const stats = statsSummary();
  return jsonResponse({
    uptimeSec: m.uptimeSec,
    requests: { total: m.total, byStatusClass: m.byStatusClass },
    rules: {
      version: rules.version,
      schemaVersion: rules.schemaVersion,
      hash: rules.hash,
    },
    stats: { total: stats.total, today: stats.today },
    memory: { rssBytes: process.memoryUsage().rss },
    security: {
      tls: Boolean(config.TLS_CERT && config.TLS_KEY),
      twofa: Boolean(config.ADMIN_TOTP_SECRET),
      rulesSigning: isRulesSigningConfigured(),
    },
  });
};
