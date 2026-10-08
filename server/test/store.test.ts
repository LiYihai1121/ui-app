import { describe, it, expect, beforeAll, afterAll } from "bun:test";
import * as fs from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import { config } from "../src/config";
import {
  getRules,
  saveRules,
  recordSkip,
  statsSummary,
  cleanupOldStats,
  flush,
  _resetSummaryCacheForTests,
  _resetStatsCacheForTests,
  _resetRulesCacheForTests,
} from "../src/storage/store";
import type { CleanedRules } from "../src/types/rules";

/**
 * 存储层单测（FOLLOW-UP P1：store.ts 无存储层单测）。
 *
 * 覆盖：规则写入 + 备份轮转数量、统计记录/跨天汇总、损坏文件回退、写入目标路径不可写
 * 时不崩溃（异常被吞并返回兜底）、过期分片清理。
 * 所有用例使用独立临时目录，互不污染；用例间重置内部缓存。
 */

let workDir: string;
const saved = {
  rulesFile: config.RULES_FILE,
  statsDir: config.STATS_DIR,
  backupDir: config.BACKUP_DIR,
};

beforeAll(() => {
  workDir = fs.mkdtempSync(path.join(os.tmpdir(), "adskip-store-"));
  config.RULES_FILE = path.join(workDir, "rules.json");
  config.STATS_DIR = path.join(workDir, "stats");
  config.BACKUP_DIR = path.join(workDir, "backups");
  fs.mkdirSync(config.STATS_DIR, { recursive: true });
  fs.mkdirSync(config.BACKUP_DIR, { recursive: true });
});

afterAll(() => {
  config.RULES_FILE = saved.rulesFile;
  config.STATS_DIR = saved.statsDir;
  config.BACKUP_DIR = saved.backupDir;
  fs.rmSync(workDir, { recursive: true, force: true });
});

function resetCaches(): void {
  _resetStatsCacheForTests();
  _resetSummaryCacheForTests();
  _resetRulesCacheForTests();
}

function sampleRules(): CleanedRules {
  return {
    keywords: ["跳过"],
    viewIds: ["skip"],
    selectors: [],
    packages: {
      "com.example.app": { keywords: ["立即购买"], viewIds: [], selectors: [], disabled: false },
    },
  };
}

describe("store rules", () => {
  it("saveRules 写入文件并返回版本号", () => {
    resetCaches();
    const version = saveRules(sampleRules());
    expect(typeof version).toBe("number");
    const rules = getRules();
    expect(rules.keywords).toEqual(["跳过"]);
    expect(rules.schemaVersion).toBeGreaterThanOrEqual(config.SCHEMA_VERSION_MIN);
  });

  it("备份轮转：超过 BACKUP_COUNT 份的旧备份被删除", () => {
    resetCaches();
    saveRules(sampleRules());
    // 连续保存多次，触发 rotateBackup；stamp 以毫秒为单位，人工凑不到同一毫秒，
    // 所以至少能生成若干份备份。
    for (let i = 0; i < config.BACKUP_COUNT + 3; i++) {
      saveRules(sampleRules());
    }
    const backups = fs
      .readdirSync(config.BACKUP_DIR)
      .filter((f) => f.startsWith("rules-") && f.endsWith(".json"));
    expect(backups.length).toBeLessThanOrEqual(config.BACKUP_COUNT);
  });

  it("规则文件损坏时 getRules 回退种子/默认，不抛异常", () => {
    resetCaches();
    fs.writeFileSync(config.RULES_FILE, "{ broken json");
    // 种子文件也是坏文件 → 走内置默认
    const rules = getRules();
    expect(Array.isArray(rules.keywords)).toBe(true);
    expect(rules.keywords.length).toBeGreaterThan(0);
  });
});

describe("store stats", () => {
  it("recordSkip 后 flush 落盘，统计可被汇总读取", () => {
    resetCaches();
    const day = new Date().toISOString().slice(0, 10);
    recordSkip("com.example.app", "示例", "text");
    recordSkip("com.example.app", "示例", "viewId");
    flush();

    const file = path.join(config.STATS_DIR, `${day}.json`);
    expect(fs.existsSync(file)).toBe(true);
    const s = statsSummary();
    expect(s.total).toBe(2);
    expect(s.byApp.find((a) => a.pkg === "com.example.app")?.count).toBe(2);
  });

  it("统计文件损坏/缺失时 summary 不抛异常", () => {
    resetCaches();
    // 用全新独立目录：无文件场景
    const emptyDir = path.join(workDir, "stats-empty");
    fs.mkdirSync(emptyDir, { recursive: true });
    const savedDir = config.STATS_DIR;
    config.STATS_DIR = emptyDir;
    try {
      const s1 = statsSummary();
      expect(s1.total).toBe(0);

      // 损坏文件场景
      const day = new Date().toISOString().slice(0, 10);
      fs.writeFileSync(path.join(emptyDir, `${day}.json`), "{ broken");
      resetCaches();
      const s2 = statsSummary();
      expect(s2.total).toBe(0);
    } finally {
      config.STATS_DIR = savedDir;
      resetCaches();
      fs.rmSync(emptyDir, { recursive: true, force: true });
    }
  });

  it("过期分片被 cleanupOldStats 清理", () => {
    resetCaches();
    const oldDay = "2000-01-01";
    fs.writeFileSync(
      path.join(config.STATS_DIR, `${oldDay}.json`),
      JSON.stringify({ day: oldDay, byApp: {}, events: [] })
    );
    cleanupOldStats();
    expect(fs.existsSync(path.join(config.STATS_DIR, `${oldDay}.json`))).toBe(false);
  });
});

describe("store failure tolerance", () => {
  it("统计目录不可写时 recordSkip/flush 不抛（异常被存储层吞掉）", () => {
    resetCaches();
    const brokenDir = path.join(workDir, "stats-readonly");
    fs.mkdirSync(brokenDir, { recursive: true });
    // 用一个指向普通文件的 STATS_DIR，让其不可作为目录写入 → writeJson 抛错被 catch
    const fileBlock = path.join(workDir, "blocker");
    fs.writeFileSync(fileBlock, "i am a file, not a dir");
    const savedStatsDir = config.STATS_DIR;
    config.STATS_DIR = fileBlock;
    try {
      recordSkip("com.other.app", "其他", "text");
      flush();
    } finally {
      config.STATS_DIR = savedStatsDir;
      resetCaches();
    }
  });
});