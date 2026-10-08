import { describe, it, expect, beforeAll, afterAll } from "bun:test";
import * as fs from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import { config } from "../src/config";
import {
  cleanRules,
  cleanBatchReport,
  cleanLabel,
  isValidKeyword,
  isValidViewIdRule,
  isValidPackage,
  PKG_RE,
  VID_RE,
} from "../src/utils/validate";
import { applyCors } from "../src/utils/httpUtil";
import { checkAdminAuth, requireAdmin, _resetTwofaForTests } from "../src/middleware/auth";
import { totpAt } from "../src/utils/totp";
import { summary as statsSummaryHandler } from "../src/api/statsApi";
import {
  limitRead,
  limitWrite,
  limitReport,
  probeReportIp,
  clientIp,
  stopRateLimitTimers,
  _resetRateLimitForTests,
} from "../src/middleware/rateLimit";
import {
  getRules,
  recordSkip,
  statsSummary,
  stopStoreTimers,
  _resetSummaryCacheForTests,
  _resetStatsCacheForTests,
  _resetRulesCacheForTests,
  _rotateStatsBackupForTests,
} from "../src/storage/store";
import { accessMetrics, recordAccess } from "../src/middleware/accessLog";
import { v1_publish } from "../src/api/rulesApi";

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
    expect(isValidPackage("com.ldp.adskip")).toBe(true);
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

  it("clientIp 默认忽略 x-forwarded-for（客户端可伪造），只用 socket IP", () => {
    // 安全契约：XFF 任由请求方填写，若默认信任则每换一个 XFF 就换一个限流桶，
    // 限流形同虚设。默认必须只认 socket IP。
    expect(clientIp(req({ "x-forwarded-for": "1.2.3.4, 5.6.7.8" }), "9.9.9.9")).toBe("9.9.9.9");
    expect(clientIp(req(), "9.9.9.9")).toBe("9.9.9.9");
  });

  it("显式开启 TRUST_PROXY 后才取 x-forwarded-for 首个地址", () => {
    const saved = config.TRUST_PROXY;
    config.TRUST_PROXY = true;
    try {
      expect(clientIp(req({ "x-forwarded-for": "1.2.3.4, 5.6.7.8" }), "9.9.9.9")).toBe("1.2.3.4");
      expect(clientIp(req(), "9.9.9.9")).toBe("9.9.9.9");
    } finally {
      config.TRUST_PROXY = saved;
    }
  });

  it("probeReportIp 探测不扣减令牌", () => {
    _resetRateLimitForTests();
    expect(probeReportIp(req(), "10.0.0.5")).toBe(true);
    // 耗尽上报桶（首次创建不扣减，容量 30 允许 31 次）
    for (let i = 0; i < 40; i++) limitReport(req(), "10.0.0.5");
    expect(limitReport(req(), "10.0.0.5")).toBe(false);
    // 桶空后 probe 也返回 false，且未额外创建令牌
    expect(probeReportIp(req(), "10.0.0.5")).toBe(false);
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

  it("未配置白名单不发送 Allow-Origin（跨域默认拒绝）", () => {
    config.CORS_ORIGINS = null;
    const h = new Headers();
    applyCors(h, "https://any.example");
    expect(h.get("access-control-allow-origin")).toBeNull();
  });

  it("白名单显式含 * 时才回 *", () => {
    config.CORS_ORIGINS = ["*"];
    const h = new Headers();
    applyCors(h, "https://any.example");
    expect(h.get("access-control-allow-origin")).toBe("*");
  });
});

describe("statsApi 鉴权", () => {
  const statsUrl = new URL("http://localhost/api/v1/stats/summary");

  it("STATS_READ_AUTH 开启后统计汇总需要 admin 令牌", async () => {
    const savedAuth = config.STATS_READ_AUTH;
    const savedToken = config.ADMIN_TOKEN;
    const savedDir = config.STATS_DIR;
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "adskip-stats-auth-"));
    config.STATS_DIR = tmp;
    config.STATS_READ_AUTH = true;
    try {
      // 安全契约：STATS_READ_AUTH 不是摆设——开启即必须挡匿名读。
      config.ADMIN_TOKEN = "";
      let res = await statsSummaryHandler(req(), statsUrl, { ip: "10.0.0.9" });
      expect(res.status).toBe(503);

      config.ADMIN_TOKEN = "secret";
      res = await statsSummaryHandler(req(), statsUrl, { ip: "10.0.0.9" });
      expect(res.status).toBe(401);

      res = await statsSummaryHandler(req({ authorization: "Bearer wrong" }), statsUrl, { ip: "10.0.0.9" });
      expect(res.status).toBe(401);

      res = await statsSummaryHandler(req({ authorization: "Bearer secret" }), statsUrl, { ip: "10.0.0.9" });
      expect(res.status).toBe(200);
    } finally {
      config.STATS_READ_AUTH = savedAuth;
      config.ADMIN_TOKEN = savedToken;
      config.STATS_DIR = savedDir;
      fs.rmSync(tmp, { recursive: true, force: true });
      _resetSummaryCacheForTests();
    }
  });

  it("STATS_READ_AUTH 关闭时匿名可读（默认行为不变）", async () => {
    const savedAuth = config.STATS_READ_AUTH;
    const savedDir = config.STATS_DIR;
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "adskip-stats-anon-"));
    config.STATS_DIR = tmp;
    config.STATS_READ_AUTH = false;
    try {
      const res = await statsSummaryHandler(req(), statsUrl, { ip: "10.0.0.10" });
      expect(res.status).toBe(200);
    } finally {
      config.STATS_READ_AUTH = savedAuth;
      config.STATS_DIR = savedDir;
      fs.rmSync(tmp, { recursive: true, force: true });
      _resetSummaryCacheForTests();
    }
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

describe("鉴权失败也计限流（防 admin token 暴力猜测）", () => {
  it("v1_publish 连续错误令牌会先被限流", async () => {
    const saved = config.ADMIN_TOKEN;
    _resetRateLimitForTests();
    config.ADMIN_TOKEN = "secret";
    try {
      // 写限频容量 10（首次创建不扣减 → 放行 11 次）：错误令牌尝试必须消耗令牌桶，
      // 否则鉴权失败零节流，token 可被无限次离线猜测。
      for (let i = 0; i < 11; i++) {
        const res = await v1_publish(
          req({ authorization: "Bearer wrong" }),
          new URL("http://localhost/api/v1/rules"),
          { ip: "10.8.8.8" }
        );
        expect(res.status).toBe(401);
      }
      const res = await v1_publish(
        req({ authorization: "Bearer wrong" }),
        new URL("http://localhost/api/v1/rules"),
        { ip: "10.8.8.8" }
      );
      expect(res.status).toBe(429);
    } finally {
      config.ADMIN_TOKEN = saved;
      _resetRateLimitForTests();
    }
  });
});

describe("stats byApp 上限（防伪造包名灌爆统计）", () => {
  it("超出当日条目上限的包名聚合进 _other", () => {
    const savedDir = config.STATS_DIR;
    const savedMax = config.MAX_STATS_APPS_PER_DAY;
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "adskip-byapp-"));
    config.STATS_DIR = tmp;
    config.MAX_STATS_APPS_PER_DAY = 2;
    _resetStatsCacheForTests();
    try {
      recordSkip("com.a.app", "A", "text");
      recordSkip("com.b.app", "B", "text");
      recordSkip("com.c.app", "C", "text");
      recordSkip("com.c.app", "C", "text");
      recordSkip("com.d.app", "D", "viewId");
      const s = statsSummary();
      expect(s.byApp.map((x) => x.pkg).sort()).toEqual(["_other", "com.a.app", "com.b.app"]);
      const other = s.byApp.find((x) => x.pkg === "_other")!;
      expect(other.count).toBe(3); // c×2 + d×1
      expect(s.total).toBe(5);
    } finally {
      _resetStatsCacheForTests();
      config.STATS_DIR = savedDir;
      config.MAX_STATS_APPS_PER_DAY = savedMax;
      fs.rmSync(tmp, { recursive: true, force: true });
    }
  });
});

describe("cleanLabel（上报 label 数据卫生）", () => {
  it("去掉控制字符并截断到 256", () => {
    expect(cleanLabel("正常标签")).toBe("正常标签");
    expect(cleanLabel("a" + String.fromCharCode(7) + "b" + String.fromCharCode(31) + "c")).toBe("a b c");
    expect(cleanLabel("x".repeat(300)).length).toBe(256);
    expect(cleanLabel(null)).toBe("");
  });
});

describe("admin 2FA（TOTP 第二因子）", () => {
  // RFC 6238 测试密钥（仅测试用；生产密钥由 ADMIN_TOTP_SECRET 提供，不入库）
  const SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

  function with2fa(fn: () => void): void {
    const savedSecret = config.ADMIN_TOTP_SECRET;
    const savedToken = config.ADMIN_TOKEN;
    config.ADMIN_TOTP_SECRET = SECRET;
    config.ADMIN_TOKEN = "secret";
    try {
      fn();
    } finally {
      config.ADMIN_TOTP_SECRET = savedSecret;
      config.ADMIN_TOKEN = savedToken;
    }
  }

  beforeAll(() => {
    _resetTwofaForTests();
  });

  it("未启用 2FA 时行为不变（只需 Bearer token）", () => {
    with2fa(() => {
      config.ADMIN_TOTP_SECRET = "";
      expect(requireAdmin(req({ authorization: "Bearer secret" }), "10.1.1.1").ok).toBe(true);
    });
  });

  it("启用后缺少 X-2FA-Code 返回 401（第二因子不可省）", () => {
    with2fa(() => {
      const res = requireAdmin(req({ authorization: "Bearer secret" }), "10.1.1.2");
      expect(res.ok).toBe(false);
      if (!res.ok) {
        expect(res.status).toBe(401);
        expect(res.error).toBe("2fa code required");
      }
    });
  });

  it("启用后错误 code 返回 401", () => {
    with2fa(() => {
      const res = requireAdmin(req({ authorization: "Bearer secret", "x-2fa-code": "000000" }), "10.1.1.3");
      expect(res.ok).toBe(false);
      if (!res.ok) {
        expect(res.status).toBe(401);
        expect(res.error).toBe("invalid 2fa code");
      }
    });
  });

  it("正确 code 通过", () => {
    with2fa(() => {
      const code = totpAt(SECRET, Date.now(), 6);
      const res = requireAdmin(req({ authorization: "Bearer secret", "x-2fa-code": code }), "10.1.1.4");
      expect(res.ok).toBe(true);
    });
  });

  it("连续 5 次失败锁定该 IP（429），此后正确 code 也拒绝", () => {
    with2fa(() => {
      const ip = "10.1.1.5";
      for (let i = 0; i < 5; i++) {
        const res = requireAdmin(req({ authorization: "Bearer secret", "x-2fa-code": "000000" }), ip);
        expect(res.ok).toBe(false);
      }
      const locked = requireAdmin(
        req({ authorization: "Bearer secret", "x-2fa-code": totpAt(SECRET, Date.now(), 6) }),
        ip
      );
      expect(locked.ok).toBe(false);
      if (!locked.ok) expect(locked.status).toBe(429);
    });
  });

  it("失败计数按 IP 隔离", () => {
    with2fa(() => {
      for (let i = 0; i < 5; i++) {
        requireAdmin(req({ authorization: "Bearer secret", "x-2fa-code": "000000" }), "10.1.1.6");
      }
      // 另一个 IP 不受影响
      const res = requireAdmin(
        req({ authorization: "Bearer secret", "x-2fa-code": totpAt(SECRET, Date.now(), 6) }),
        "10.1.1.7"
      );
      expect(res.ok).toBe(true);
    });
  });

  it("验证成功会清除该 IP 的失败计数", () => {
    with2fa(() => {
      const ip = "10.1.1.8";
      for (let i = 0; i < 3; i++) {
        requireAdmin(req({ authorization: "Bearer secret", "x-2fa-code": "000000" }), ip);
      }
      expect(
        requireAdmin(req({ authorization: "Bearer secret", "x-2fa-code": totpAt(SECRET, Date.now(), 6) }), ip).ok
      ).toBe(true);
      // 再错 3 次不应触发锁定（计数已被成功验证清零）
      for (let i = 0; i < 3; i++) {
        expect(
          requireAdmin(req({ authorization: "Bearer secret", "x-2fa-code": "000000" }), ip).ok
        ).toBe(false);
      }
      expect(
        requireAdmin(req({ authorization: "Bearer secret", "x-2fa-code": totpAt(SECRET, Date.now(), 6) }), ip).ok
      ).toBe(true);
    });
  });

  it("token 错误时不消耗 2FA 失败计数（token 先校验）", () => {
    with2fa(() => {
      const ip = "10.1.1.9";
      for (let i = 0; i < 5; i++) {
        const res = requireAdmin(req({ authorization: "Bearer wrong" }), ip);
        expect(res.ok).toBe(false);
        if (!res.ok) expect(res.error).toBe("unauthorized");
      }
      expect(
        requireAdmin(req({ authorization: "Bearer secret", "x-2fa-code": totpAt(SECRET, Date.now(), 6) }), ip).ok
      ).toBe(true);
    });
  });
});

describe("运维收尾（计数器与定时器清理）", () => {
  it("accessMetrics 按状态类聚合计数（/metrics 数据源）", () => {
    recordAccess({ method: "GET", path: "/x", status: 200, ip: "1.1.1.1", ms: 1 });
    recordAccess({ method: "GET", path: "/x", status: 404, ip: "1.1.1.1", ms: 1 });
    recordAccess({ method: "GET", path: "/x", status: 500, ip: "1.1.1.1", ms: 1 });
    const m = accessMetrics();
    expect(m.byStatusClass["2xx"]).toBeGreaterThanOrEqual(1);
    expect(m.byStatusClass["4xx"]).toBeGreaterThanOrEqual(1);
    expect(m.byStatusClass["5xx"]).toBeGreaterThanOrEqual(1);
    expect(m.total).toBeGreaterThanOrEqual(3);
    expect(m.uptimeSec).toBeGreaterThanOrEqual(0);
  });

  it("定时器清理钩子可重复调用，且不影响限流工作", () => {
    stopRateLimitTimers();
    stopStoreTimers();
    stopRateLimitTimers();
    stopStoreTimers();
    _resetRateLimitForTests();
    expect(limitRead(req(), "10.9.9.20")).toBe(true);
  });
});
