import { describe, it, expect, beforeAll, afterAll } from "bun:test";
import { config } from "../src/config";
import {
  cleanRules,
  cleanBatchReport,
  isValidKeyword,
  isValidViewIdRule,
  isValidPackage,
  PKG_RE,
  VID_RE,
} from "../src/validate";
import { checkAdminAuth, requireAdmin } from "../src/auth";
import {
  limitRead,
  limitWrite,
  limitReport,
  clientIp,
  _resetRateLimitForTests,
} from "../src/rateLimit";

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

  it("clientIp 优先取 x-forwarded-for，回退 remoteIp", () => {
    expect(clientIp(req({ "x-forwarded-for": "1.2.3.4, 5.6.7.8" }), "9.9.9.9")).toBe("1.2.3.4");
    expect(clientIp(req(), "9.9.9.9")).toBe("9.9.9.9");
  });
});
