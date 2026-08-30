import { describe, it, expect, beforeAll, afterAll } from "bun:test";
import * as fs from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import { startServer } from "../server";

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "adskip-test-"));
const apkSrc = path.join(import.meta.dir, "..", "..", "AdSkip-latest.apk");
const apkTest = path.join(tmp, "AdSkip-latest.apk");
if (fs.existsSync(apkSrc)) fs.copyFileSync(apkSrc, apkTest);

let server: Bun.Server<undefined>;
let base: string;

beforeAll(async () => {
  server = startServer({
    port: 0,
    host: "127.0.0.1",
    adminToken: "test123",
    apkFile: apkTest,
    dataDir: tmp,
  });
  base = `http://127.0.0.1:${server.port}`;
});

afterAll(() => {
  server.stop(true);
  fs.rmSync(tmp, { recursive: true, force: true });
});

const TOKEN = "test123";

describe("smoke", () => {
  it("GET / 返回 HTML", async () => {
    const res = await fetch(`${base}/`);
    expect(res.status).toBe(200);
    expect(await res.text()).toContain("<");
  });

  it("GET /admin 返回 200", async () => {
    const res = await fetch(`${base}/admin`);
    expect(res.status).toBe(200);
  });

  it("health 正常", async () => {
    const res = await fetch(`${base}/api/v1/health`);
    const json = (await res.json()) as any;
    expect(json.status).toBe("ok");
  });

  it("v0 规则形状", async () => {
    const res = await fetch(`${base}/api/rules/latest`);
    const j = (await res.json()) as any;
    expect(Array.isArray(j.keywords)).toBe(true);
    expect(Array.isArray(j.viewIds)).toBe(true);
    expect(typeof j.packages).toBe("object");
  });

  it("v1 规则形状（含 hash）", async () => {
    const res = await fetch(`${base}/api/v1/rules/latest`);
    const j = (await res.json()) as any;
    expect(j.schemaVersion).toBe(1);
    expect(typeof j.rules).toBe("object");
    expect(typeof j.hash).toBe("string");
    expect(j.hash.startsWith("sha256:")).toBe(true);
  });

  it("ETag 与 hash 一致", async () => {
    const res = await fetch(`${base}/api/v1/rules/latest`);
    const j = (await res.json()) as any;
    expect(res.headers.get("etag")).toBe(j.hash);
  });

  it("If-None-Match 命中返回 304", async () => {
    const res = await fetch(`${base}/api/v1/rules/latest`);
    const hash = ((await res.json()) as any).hash;
    const res2 = await fetch(`${base}/api/v1/rules/latest`, {
      headers: { "if-none-match": hash },
    });
    expect(res2.status).toBe(304);
  });

  it("POST /api/skip 成功", async () => {
    const res = await fetch(`${base}/api/skip`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ pkg: "com.example.app", label: "Example" }),
    });
    expect(((await res.json()) as any).ok).toBe(true);
  });

  it("批量上报 accepted===2", async () => {
    const res = await fetch(`${base}/api/v1/reports/batch`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        deviceId: "device1234",
        events: [
          { pkg: "com.example.app", channel: "text", ts: 1 },
          { pkg: "com.other.app", channel: "viewId", ts: 2 },
        ],
      }),
    });
    const j = (await res.json()) as any;
    expect(j.ok).toBe(true);
    expect(j.accepted).toBe(2);
  });

  it("非法批量上报返回 400", async () => {
    const res = await fetch(`${base}/api/v1/reports/batch`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ deviceId: "x", events: [] }),
    });
    expect(res.status).toBe(400);
  });

  it("统计包含上报的应用", async () => {
    const res = await fetch(`${base}/api/v1/stats/summary`);
    const j = (await res.json()) as any;
    const pkgs = j.byApp.map((a: any) => a.pkg);
    expect(pkgs).toContain("com.example.app");
  });

  it("/download 返回 apk 或 404（无 APK 文件时）", async () => {
    const res = await fetch(`${base}/download`);
    if (fs.existsSync(apkTest)) {
      expect(res.status).toBe(200);
      expect(res.headers.get("content-type")).toContain("android.package-archive");
    } else {
      expect(res.status).toBe(404);
    }
  });

  it("未知 api 返回 404", async () => {
    const res = await fetch(`${base}/api/v1/nope`);
    expect(res.status).toBe(404);
  });

  it("无鉴权发布返回 401", async () => {
    const res = await fetch(`${base}/api/v1/rules`, {
      method: "PUT",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ packages: { "com.x": { keywords: [] } } }),
    });
    expect(res.status).toBe(401);
  });

  it("非法 packages 返回 400", async () => {
    const res = await fetch(`${base}/api/v1/rules`, {
      method: "PUT",
      headers: { "content-type": "application/json", authorization: `Bearer ${TOKEN}` },
      body: JSON.stringify({ packages: "not-an-object" }),
    });
    expect(res.status).toBe(400);
  });

  it("带鉴权发布成功", async () => {
    const res = await fetch(`${base}/api/v1/rules`, {
      method: "PUT",
      headers: { "content-type": "application/json", authorization: `Bearer ${TOKEN}` },
      body: JSON.stringify({
        keywords: ["新词"],
        viewIds: ["skip"],
        packages: { "com.published.app": { keywords: ["a"], viewIds: [], disabled: false } },
      }),
    });
    const j = (await res.json()) as any;
    expect(j.ok).toBe(true);
    expect(typeof j.hash).toBe("string");
  });

  it("请求体非 application/json 返回 415", async () => {
    const res = await fetch(`${base}/api/skip`, {
      method: "POST",
      headers: { "content-type": "text/plain" },
      body: JSON.stringify({ pkg: "com.example.app", label: "Example" }),
    });
    expect(res.status).toBe(415);
  });

  it("请求体缺失 content-type 返回 415", async () => {
    const res = await fetch(`${base}/api/skip`, {
      method: "POST",
      body: JSON.stringify({ pkg: "com.example.app" }),
    });
    expect(res.status).toBe(415);
  });

  it("超过 1MB 的请求体在协议层被拒绝（413 或断开连接）", async () => {
    const oversized = fetch(`${base}/api/v1/reports/batch`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        deviceId: "device1234",
        events: [{ pkg: "com.example.app", channel: "text", ts: 1, pad: "x".repeat(1536 * 1024) }],
      }),
    });
    // Bun 的 maxRequestBodySize 在协议层拦截：冷连接直接断 socket，keep-alive 连接回 413；
    // 两种表现均保证超限正文不进入应用内存
    let status = 0;
    let threw = false;
    try {
      status = (await oversized).status;
    } catch {
      threw = true;
    }
    expect(threw || status === 413).toBe(true);
  });

  it("v0 上报非法包名返回 400", async () => {
    const res = await fetch(`${base}/api/skip`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ pkg: "not a pkg!", label: "X" }),
    });
    expect(res.status).toBe(400);
  });

  it("规则模拟器包含应用专属规则与禁用开关", async () => {
    const put = await fetch(`${base}/api/v1/rules`, {
      method: "PUT",
      headers: { "content-type": "application/json", authorization: `Bearer ${TOKEN}` },
      body: JSON.stringify({
        keywords: [],
        viewIds: [],
        packages: {
          "com.app.disabled": { keywords: ["立即购买"], viewIds: [], disabled: true },
          "com.app.enabled": { keywords: ["限时免单"], viewIds: [], disabled: false },
        },
      }),
    });
    expect(put.status).toBe(200);

    const t1 = (await (
      await fetch(`${base}/api/v1/rules/test`, {
        method: "POST",
        headers: { "content-type": "application/json", authorization: `Bearer ${TOKEN}` },
        body: JSON.stringify({ pkg: "com.app.disabled", text: "立即购买" }),
      })
    ).json()) as any;
    expect(t1.disabled).toBe(true);
    expect(t1.hit).toBe(false);

    const t2 = (await (
      await fetch(`${base}/api/v1/rules/test`, {
        method: "POST",
        headers: { "content-type": "application/json", authorization: `Bearer ${TOKEN}` },
        body: JSON.stringify({ pkg: "com.app.enabled", text: "限时免单" }),
      })
    ).json()) as any;
    expect(t2.disabled).toBe(false);
    expect(t2.hit).toBe(true);
  });

  it("admin logs 无令牌 401，带令牌返回访问记录", async () => {
    const r1 = await fetch(`${base}/api/v1/admin/logs`);
    expect(r1.status).toBe(401);
    const r2 = await fetch(`${base}/api/v1/admin/logs`, {
      headers: { authorization: `Bearer ${TOKEN}` },
    });
    expect(r2.status).toBe(200);
    const j = (await r2.json()) as any;
    expect(Array.isArray(j.entries)).toBe(true);
    expect(j.entries.length).toBeGreaterThan(0);
    expect(j.entries[0].path).toBe("/api/v1/admin/logs");
  });
});
