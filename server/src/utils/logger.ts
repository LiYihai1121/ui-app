/**
 * 结构化日志（JSON Lines 单行输出）。
 *
 * 技术选型：Bun 原生实现，不引入 pino —— 服务端保持「零运行时依赖」的设计承诺
 * （README / ARCHITECTURE / package.json 均以此为卖点）；改用 pino 会引入线程级依赖，
 * 对单进程 LAN 服务收益低于维护成本。输出格式与 pino 对齐（level / msg / time），
 * 便于以后无论是否有日志采集器都能 grep / jq 消费。
 */

type LogLevel = "info" | "warn" | "error";

interface LogFields {
  [key: string]: unknown;
}

const LEVEL_ORDER: Record<LogLevel, number> = { info: 30, warn: 40, error: 50 };
const CONSOLE: Record<LogLevel, (line: string) => void> = {
  info: (l) => console.log(l),
  warn: (l) => console.warn(l),
  error: (l) => console.error(l),
};

function write(level: LogLevel, msg: string, fields?: LogFields): void {
  const line = JSON.stringify({
    time: new Date().toISOString(),
    level: LEVEL_ORDER[level],
    msg,
    ...fields,
  });
  CONSOLE[level](line);
}

export const logger = {
  info(msg: string, fields?: LogFields): void {
    write("info", msg, fields);
  },
  warn(msg: string, fields?: LogFields): void {
    write("warn", msg, fields);
  },
  error(msg: string, fields?: LogFields): void {
    write("error", msg, fields);
  },
};

/** 仅供测试：确认输出串是合法 JSON 行（结构化不破坏可解析性） */
export function _isStructuredLine(line: string): boolean {
  try {
    const parsed = JSON.parse(line);
    return (
      typeof parsed === "object" &&
      parsed !== null &&
      typeof parsed.msg === "string" &&
      typeof parsed.level === "number"
    );
  } catch {
    return false;
  }
}