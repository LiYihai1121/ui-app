/** 轻量结构化日志工具（零运行时依赖）。
 *
 * 使用方式：
 *   import { logger } from "../utils/logger";
 *   logger.info("msg", { key: "value" });
 *   logger.warn("msg", { key: "value" });
 *   logger.error("msg", { key: "value" });
 *
 * 输出格式（JSONL，便于 grep/jq 分析）：
 *   {"level":"info","ts":"2026-10-07T05:35:00.000Z","msg":"...","key":"value"}
 */

type Level = "debug" | "info" | "warn" | "error";

interface LogEntry {
  level: Level;
  ts: string;
  msg: string;
  [key: string]: unknown;
}

const LEVEL_ORDER: Record<Level, number> = {
  debug: 0,
  info: 1,
  warn: 2,
  error: 3,
};

let currentLevel: Level = "info";

export function setLogLevel(level: Level): void {
  currentLevel = level;
}

export function getLogLevel(): Level {
  return currentLevel;
}

function formatEntry(level: Level, msg: string, meta?: Record<string, unknown>): LogEntry {
  const entry: LogEntry = {
    level,
    ts: new Date().toISOString(),
    msg,
  };
  if (meta) {
    Object.assign(entry, meta);
  }
  return entry;
}

function print(level: Level, msg: string, meta?: Record<string, unknown>): void {
  if (LEVEL_ORDER[level] < LEVEL_ORDER[currentLevel]) return;
  const entry = formatEntry(level, msg, meta);
  const line = JSON.stringify(entry);
  switch (level) {
    case "error":
      console.error(line);
      break;
    case "warn":
      console.warn(line);
      break;
    case "debug":
      console.debug(line);
      break;
    default:
      console.log(line);
  }
}

export const logger = {
  debug: (msg: string, meta?: Record<string, unknown>) => print("debug", msg, meta),
  info: (msg: string, meta?: Record<string, unknown>) => print("info", msg, meta),
  warn: (msg: string, meta?: Record<string, unknown>) => print("warn", msg, meta),
  error: (msg: string, meta?: Record<string, unknown>) => print("error", msg, meta),
};
