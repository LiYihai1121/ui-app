import { describe, it, expect, beforeAll, afterAll } from "bun:test";
import * as fs from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import * as crypto from "node:crypto";
import { config } from "../src/config";
import {
  cleanRules,
  cleanBatchReport,
  isValidKeyword,
  isValidViewIdRule,
  isValidPackage,
  PKG_RE,
  VID_RE,
} from "../src/utils/validate";
import { applyCors } from "../src/utils/httpUtil";
import {
  RULES_SIGNATURE_HEADER,
  signRulesBody,
  verifyRulesBodySignature,
} from "../src/utils/sign";
import { checkAdminAuth, requireAdmin } from "../src/middleware/auth";
import {
  limitRead,
  limitWrite,
  limitReport,
  probeReportIp,
  clientIp,
  _resetRateLimitForTests,
} from "../src/middleware/rateLimit";
import {
  getRules,
  statsSummary,
  _resetSummaryCacheForTests,
  _resetStatsCacheForTests,
  _resetRulesCacheForTests,
  _rotateStatsBackupForTests,
} from "../src/storage/store";

function req(headers: Record<string, string> = {}): Request {
  return new Request("http://localhost/", { headers });
}

describe("validate", () => {
  it("cleanRules 正常载荷", () => {
    const r = cleanRules({
      keywords: ["跳过"],
      viewIds: ["skip"],
      packages: {
        "com.example.app": { keywords: ["a"], viewIds: ["b"], disabled: false },
      },
    });
    expect(r).not.toBeNull();
    expect(r!.keywords).toEqual(["跳过"]);
    expect(r!.packages["com.example.app"].keywords).toEqual(["a"]);
  });

  it("cleanRules 无 packages 返回 null", () => {
    expect(cleanRules({ keywords: [] })).toBeNull();
    expect(cleanRules(null)).toBeNull();
  });

  it("关键词截断至 12 字", () => {
    const r = cleanRules({
      packages: {},
      keywords: ["x".repeat(20)],
    });
    expect(r!.keywords[0].length).toBe(12);
  });

  it("关键词大小写不敏感去重，保留首现大小写", () => {
    const r = cleanRules({
      packages: {},
      keywords: ["skip", "SKIP", "Skip", "跳过"],
    });
    expect(r!.keywords.length).toBe(2);
    expect(r!.keywords).toContain("skip");
    expect(r!.keywords).toContain("跳过");
  });

  it("非法包名被跳过", () => {
    const r = cleanRules({
      packages: {
        "bad..pkg": { keywords: ["a"] },
        "com.good.app": { keywords: ["a"] },
      },
    });
    expect(Object.keys(r!.packages)).toEqual(["com.good.app"]);
  });

  it("非普通对象被拒绝", () => {
    expect(cleanRules("string")).toBeNull();
    expect(cleanRules([])).toBeNull();
    expect(cleanRules(123)).toBeNull();
  });

  it("isValidKeyword 边界 1..12", () => {
    expect(isValidKeyword("a")).toBe(true);
    expect(isValidKeyword("")).toBe(false);
    expect(isValidKeyword("x".repeat(13))).toBe(false);
    expect(isValidKeyword("x".repeat(12))).toBe(true);
  });

  it("isValidViewIdRule 短于 3 被拒", () => {
    expect(isValidViewIdRule("ab")).toBe(false);
    expect(isValidViewIdRule("skip")).toBe(true);
  });

  it("isValidPackage 正则边界", () => {
    expect(isValidPackage("com.example.app")).toBe(true);
    expect(isValidPackage("com.qingqi.adskip")).toBe(true);
    expect(isValidPackage("invalid")).toBe(false);
    expect(isValidPackage(".com.example")).toBe(false);
    expect(isValidPackage("com..app")).toBe(false);
  });

  it("cleanBatchReport 正常 2 条", () => {
    const r = cleanBatchReport({
      deviceId: "device1234",
      events: [
        { pkg: "com.example.app", channel: "text", ts: 1 },
        { pkg: "com.other.app", channel: "viewId", ts: 2 },
      ],
    });
    expect(r).not.toBeNull();
    expect(r!.events.length).toBe(2);
  });

  it("deviceId 过短被拒", () => {
    const r = cleanBatchReport({ deviceId: "x", events: [{ pkg: "com.example.app" }] });
    expect(r).toBeNull();
  });

  it("超长事件列表截断至 50", () => {
    const events = Array.from({ length: 100 }, (_, i) => ({
      pkg: "com.example.app",
      channel: "text",
      ts: i,
    }));
    const r = cleanBatchReport({ deviceId: "device1234", events });
    expect(r!.events.length).toBe(50);
  });

  it("空事件被拒", () => {
    expect(cleanBatchReport({ deviceId: "device1234", events: [] })).toBeNull();
  });

  it("非法包名事件被跳过", () => {
    const r = cleanBatchReport({
      deviceId: "device1234",
      events: [
        { pkg: "badpkg" },
        { pkg: "com.example.app" },
      ],
    });
    expect(r!.events.length).toBe(1);
  });
});

describe("auth", () => {
  const saved = config.ADMIN_TOKEN;

  afterAll(() => {
    config.ADMIN_TOKEN = saved;
  });

  it("无令牌时 requireAdmin 返回 503", () => {
    config.ADMIN_TOKEN = "";
    const res = requireAdmin(req());
    expect(res.ok).toBe(false);
    if (!res.ok) expect(res.status).toBe(503);
  });

  it("Bearer 正确令牌通过", () => {
    config.ADMIN_TOKEN = "secret";
    expect(checkAdminAuth(req({ authorization: "Bearer secret" }))).toBe(true);
  });

  it("错误令牌返回 401", () => {
    config.ADMIN_TOKEN = "secret";
    const res = requireAdmin(req({ authorization: "Bearer wrong" }));
    expect(res.ok).toBe(false);
    if (!res.ok) expect(res.status).toBe(401);
  });

  it("缺少 Authorization 头返回 401", () => {
    config.ADMIN_TOKEN = "secret";
    const res = requireAdmin(req());
    expect(res.ok).toBe(false);
    if (!res.ok) expect(res.status).toBe(401);
  });

  it("非 Bearer 方案被拒", () => {
    config.ADMIN_TOKEN = "secret";
    const res = requireAdmin(req({ authorization: "Basic secret" }));
    expect(res.ok).toBe(false);
    if (!res.ok) expect(res.status).toBe(401);
  });
});

describe("rateLimit", () => {
  beforeAll(() => {
    _resetRateLimitForTests();
  });

  it("首次请求放行", () => {
    expect(limitWrite(req(), "10.0.0.1")).toBe(true);
  });

  it("写限频耗尽（首次创建不扣减，容量 10 允许 11 次）", () => {
    _resetRateLimitForTests();
    for (let i = 0; i < 11; i++) expect(limitWrite(req(), "10.0.0.2")).toBe(true);
    expect(limitWrite(req(), "10.0.0.2")).toBe(false);
  });

  it("读限频放行", () => {
    _resetRateLimitForTests();
    expect(limitRead(req(), "10.0.0.3")).toBe(true);
  });

  it("上报限频放行", () => {
    _resetRateLimitForTests();
    expect(limitReport(req(), "10.0.0.4", "dev12345678")).toBe(true);
  });

  it("clientIp 未配置受信代理时忽略 X-Forwarded-For", () => {
    const saved = config.TRUSTED_PROXIES;
    config.TRUSTED_PROXIES = [];
    try {
      // 伪造的 XFF 不得成为限流键，否则任何人换头部即可重生令牌桶
      expect(clientIp(req({ "x-forwarded-for": "1.2.3.4" }), "9.9.9.9")).toBe("9.9.9.9");
      expect(clientIp(req(), "9.9.9.9")).toBe("9.9.9.9");
    } finally {
      config.TRUSTED_PROXIES = saved;
    }
  });

  it("clientIp 对端是受信代理时采信 X-Forwarded-For", () => {
    const saved = config.TRUSTED_PROXIES;
    config.TRUSTED_PROXIES = ["10.1.1.1"];
    try {
      expect(
        clientIp(req({ "x-forwarded-for": "1.2.3.4, 5.6.7.8" }), "10.1.1.1")
      ).toBe("1.2.3.4");
      // 代理未带头时回退 socket IP
      expect(clientIp(req(), "10.1.1.1")).toBe("10.1.1.1");
      // 非受信对端即使带头也不采信
      expect(clientIp(req({ "x-forwarded-for": "1.2.3.4" }), "9.9.9.9")).toBe("9.9.9.9");
    } finally {
      config.TRUSTED_PROXIES = saved;
    }
  });

  it("probeReportIp 探测不扣减令牌", () => {
    _resetRateLimitForTests();
    expect(probeReportIp(req(), "10.0.0.5")).toBe(true);
    // 耗尽 IP 上报桶（首次创建不扣减，容量 30 允许 31 次）
    for (let i = 0; i < 40; i++) limitReport(req(), "10.0.0.5");
    expect(limitReport(req(), "10.0.0.5")).toBe(false);
    // 桶空后 probe 也返回 false，且未额外创建令牌
    expect(probeReportIp(req(), "10.0.0.5")).toBe(false);
  });

  it("伪造 deviceId 不能绕过上报限额（IP 桶兜底）", () => {
    _resetRateLimitForTests();
    // 同一 IP 每次换一个设备号：设备桶各满，但 IP 桶 31 次后必须拒绝
    for (let i = 0; i < 31; i++) {
      expect(limitReport(req(), "10.0.0.6", `device-${i}`)).toBe(true);
    }
    expect(limitReport(req(), "10.0.0.6", "device-fresh")).toBe(false);
  });
});

describe("cors", () => {
  const saved = config.CORS_ORIGINS;

  afterAll(() => {
    config.CORS_ORIGINS = saved;
  });

  it("白名单命中回显 Origin", () => {
    config.CORS_ORIGINS = ["https://good.example"];
    const h = new Headers();
    applyCors(h, "https://good.example");
    expect(h.get("access-control-allow-origin")).toBe("https://good.example");
  });

  it("白名单不匹配不发送 Allow-Origin（浏览器侧直接拦截）", () => {
    config.CORS_ORIGINS = ["https://good.example"];
    const h = new Headers();
    applyCors(h, "https://evil.example");
    expect(h.get("access-control-allow-origin")).toBeNull();
  });

  it("未配置白名单默认 *", () => {
    config.CORS_ORIGINS = null;
    const h = new Headers();
    applyCors(h, "https://any.example");
    expect(h.get("access-control-allow-origin")).toBe("*");
  });
});

describe("rules signature", () => {
  const saved = config.RULES_SIGNING_KEY;

  afterAll(() => {
    config.RULES_SIGNING_KEY = saved;
  });

  it("密钥为空时不下发签名、验签一律拒绝", () => {
    config.RULES_SIGNING_KEY = "";
    const body = JSON.stringify({ version: 1 });
    expect(signRulesBody(body)).toBe("");
    // 未配置密钥的服务端发出的载荷，客户端不得采信
    expect(verifyRulesBodySignature(body, "deadbeef")).toBe(false);
    expect(verifyRulesBodySignature(body, null)).toBe(false);
  });

  it("签名与独立复算的 HMAC 一致，且按原始字节串校验", () => {
    config.RULES_SIGNING_KEY = "unit-test-key";
    const body = JSON.stringify({ schemaVersion: 2, rules: { globalKeywords: ["跳过"] } });
    const sig = signRulesBody(body);
    expect(sig).not.toBe("");
    const expected = crypto
      .createHmac("sha256", "unit-test-key")
      .update(body, "utf8")
      .digest("hex");
    expect(sig).toBe(expected);
    expect(verifyRulesBodySignature(body, sig)).toBe(true);
    expect(verifyRulesBodySignature(body, `sha256=${sig}`)).toBe(true);
  });

  it("载荷被篡改一个字节，验签失败", () => {
    config.RULES_SIGNING_KEY = "unit-test-key";
    const body = JSON.stringify({ version: 1, rules: {} });
    const sig = signRulesBody(body);
    const tampered = JSON.stringify({ version: 2, rules: {} });
    expect(verifyRulesBodySignature(tampered, sig)).toBe(false);
  });

  it("错误密钥签发的载荷验签失败", () => {
    config.RULES_SIGNING_KEY = "key-a";
    const body = JSON.stringify({ v: 1 });
    const sigFromB = crypto.createHmac("sha256", "key-b").update(body, "utf8").digest("hex");
    expect(verifyRulesBodySignature(body, sigFromB)).toBe(false);
  });

  it("签名头常量稳定（客户端按此名字读取）", () => {
    expect(RULES_SIGNATURE_HEADER).toBe("X-Rules-Signature");
  });
});

describe("statsSummary", () => {
  it("recent 跨天取最近记录，汇总走缓存", () => {
    const savedDir = config.STATS_DIR;
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "adskip-stats-"));
    const yesterday = new Date(Date.now() - 86400000).toISOString().slice(0, 10);
    const dayBefore = new Date(Date.now() - 2 * 86400000).toISOString().slice(0, 10);
    fs.writeFileSync(
      path.join(tmp, `${yesterday}.json`),
      JSON.stringify({
        day: yesterday,
        byApp: { "com.yday.app": { label: "Y", count: 2, byChannel: { text: 2 } } },
        events: [
          { ts: "y2", pkg: "com.yday.app", label: "Y", channel: "text" },
          { ts: "y1", pkg: "com.yday.app", label: "Y", channel: "text" },
        ],
      })
    );
    fs.writeFileSync(
      path.join(tmp, `${dayBefore}.json`),
      JSON.stringify({
        day: dayBefore,
        byApp: { "com.old.app": { label: "O", count: 1, byChannel: { viewId: 1 } } },
        events: [{ ts: "o1", pkg: "com.old.app", label: "O", channel: "viewId" }],
      })
    );
    // 清掉其他测试（smoke）残留在 statsCache 里的真实「今天」，保证隔离
    _resetStatsCacheForTests();
    config.STATS_DIR = tmp;
    try {
      const s = statsSummary();
      expect(s.today).toBe(0); // 两天文件都不是今天
      expect(s.total).toBe(3);
      expect(s.recent.length).toBe(3); // 跨天合并
      expect(s.recent[0].ts).toBe("y2"); // 最新一天的记录在前
      expect(s.byDay.length).toBe(2);
      // 第二次调用命中缓存（引用相同）
      expect(statsSummary()).toBe(s);
    } finally {
      config.STATS_DIR = savedDir;
      fs.rmSync(tmp, { recursive: true, force: true });
      _resetStatsCacheForTests();
    }
  });
});

describe("seed rules fallback", () => {
  it("运行时规则文件缺失时回退到入库种子", () => {
    const savedRulesFile = config.RULES_FILE;
    const savedSeedFile = config.SEED_RULES_FILE;
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "adskip-seed-"));
    fs.writeFileSync(
      path.join(tmp, "seed.json"),
      JSON.stringify({ keywords: ["种子词"], viewIds: ["seed_view"], packages: {}, version: 7 })
    );
    config.RULES_FILE = path.join(tmp, "missing-rules.json");
    config.SEED_RULES_FILE = path.join(tmp, "seed.json");
    try {
      _resetRulesCacheForTests();
      const r = getRules();
      expect(r.keywords).toEqual(["种子词"]);
      expect(r.version).toBe(7);
      expect(r.schemaVersion).toBe(2); // v0 形状经兼容层补齐到当前 schema（selectors 通道）
      expect(r.hash.startsWith("sha256:")).toBe(true);
      expect(r.packages).toEqual({}); // legacy packages 指向 v1 apps
      expect(fs.existsSync(config.RULES_FILE)).toBe(false); // 读路径无副作用
    } finally {
      config.RULES_FILE = savedRulesFile;
      config.SEED_RULES_FILE = savedSeedFile;
      _resetRulesCacheForTests();
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("种子也缺失时使用内置默认规则", () => {
    const savedRulesFile = config.RULES_FILE;
    const savedSeedFile = config.SEED_RULES_FILE;
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "adskip-seed-"));
    config.RULES_FILE = path.join(tmp, "missing-rules.json");
    config.SEED_RULES_FILE = path.join(tmp, "missing-seed.json");
    try {
      _resetRulesCacheForTests();
      const r = getRules();
      expect(r.keywords).toContain("跳过");
      expect(r.version).toBe(1);
      expect(r.hash.startsWith("sha256:")).toBe(true);
    } finally {
      config.RULES_FILE = savedRulesFile;
      config.SEED_RULES_FILE = savedSeedFile;
      _resetRulesCacheForTests();
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });

  it("运行时文件损坏时回退种子", () => {
    const savedRulesFile = config.RULES_FILE;
    const savedSeedFile = config.SEED_RULES_FILE;
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "adskip-seed-"));
    fs.writeFileSync(path.join(tmp, "rules.json"), "{ not valid json");
    fs.writeFileSync(
      path.join(tmp, "seed.json"),
      JSON.stringify({ keywords: ["兜底"], viewIds: [], packages: {}, version: 3 })
    );
    config.RULES_FILE = path.join(tmp, "rules.json");
    config.SEED_RULES_FILE = path.join(tmp, "seed.json");
    try {
      _resetRulesCacheForTests();
      const r = getRules();
      expect(r.keywords).toEqual(["兜底"]);
    } finally {
      config.RULES_FILE = savedRulesFile;
      config.SEED_RULES_FILE = savedSeedFile;
      _resetRulesCacheForTests();
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });
});

describe("stats backup rotation", () => {
  it("rotateStatsBackup creates backup and keeps only STATS_BACKUP_COUNT", () => {
    const savedDir = config.STATS_DIR;
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "adskip-stats-backup-"));
    config.STATS_DIR = tmp;
    const day = "2026-10-07";
    const statsFile = path.join(tmp, `${day}.json`);
    fs.writeFileSync(statsFile, JSON.stringify({ skip: 1 }));
    const backupDir = path.join(tmp, "backups");

    try {
      _rotateStatsBackupForTests(day);
      const backups = fs.readdirSync(backupDir).filter((f) => f.startsWith(`stats-${day}-`));
      expect(backups.length).toBe(1);

      for (let i = 0; i < config.STATS_BACKUP_COUNT + 2; i++) {
        _rotateStatsBackupForTests(day);
      }
      const remaining = fs.readdirSync(backupDir).filter((f) => f.startsWith(`stats-${day}-`));
      expect(remaining.length).toBe(config.STATS_BACKUP_COUNT);
    } finally {
      config.STATS_DIR = savedDir;
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });
});
