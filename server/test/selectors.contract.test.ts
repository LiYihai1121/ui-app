import { describe, expect, it } from "bun:test";
import fs from "node:fs";
import path from "node:path";
import { config } from "../src/config";
import { cleanRules, isValidSelector } from "../src/utils/validate";

/**
 * 选择器双端契约的服务端一侧（DESIGN-PHASE1 §6.2）。
 *
 * 与 `client/app/src/test/java/com/qingqi/adskip/engine/SelectorContractTest.kt`
 * 消费同一份 `test/fixtures/selectors.contract.json`：同一批向量两端必须给出
 * 一致的「接受 / 拒绝」判定，防止两套校验随时间漂移。
 *
 * 有意的不一致记在夹具的 `divergences` 里：服务端只做快检（长度 + 字符白名单
 * + 括号引号配平），语法权威在客户端 `SelectorParser`。本文件断言服务端那一侧，
 * 客户端测试断言客户端那一侧，两边都必须与夹具声明相符。
 */

interface Vector {
  id: string;
  expr: string;
  server?: string;
  client?: string;
  why?: string;
}

const FIXTURE_PATH = path.join(import.meta.dir, "fixtures", "selectors.contract.json");
const fixture = JSON.parse(fs.readFileSync(FIXTURE_PATH, "utf8")) as {
  version: number;
  accepted: Vector[];
  rejected: Vector[];
  divergences: Vector[];
};

const accepted: Vector[] = fixture.accepted ?? [];
const rejected: Vector[] = fixture.rejected ?? [];
const divergences: Vector[] = fixture.divergences ?? [];

describe("selectors contract fixture (server side)", () => {
  it("夹具结构完整且向量数量满足设计下限", () => {
    expect(fixture.version).toBe(1);
    expect(accepted.length).toBeGreaterThanOrEqual(12);
    expect(rejected.length).toBeGreaterThanOrEqual(12);
    expect(divergences.length).toBeGreaterThanOrEqual(1);
  });

  it("accepted 向量：服务端全部接受", () => {
    for (const v of accepted) {
      expect(`${v.id} ${v.expr}: ${isValidSelector(v.expr)}`).toBe(`${v.id} ${v.expr}: true`);
    }
  });

  it("rejected 向量：服务端全部拒绝", () => {
    for (const v of rejected) {
      expect(`${v.id} ${isValidSelector(v.expr)}`).toBe(`${v.id} false`);
    }
  });

  it("divergences 向量：服务端判定与夹具声明一致", () => {
    for (const v of divergences) {
      const actual = isValidSelector(v.expr) ? "accept" : "reject";
      expect(`${v.id}: ${actual}`).toBe(`${v.id}: ${v.server}`);
    }
  });

  it("accepted 向量在 cleanSelectorList 中原样保留（含中文与去重）", () => {
    const cleaned = cleanRules({
      keywords: ["跳过"],
      viewIds: ["skip"],
      selectors: accepted.map((v) => v.expr),
      packages: {},
    });
    // accepted 里没有重复项，长度应一一对应
    expect(cleaned?.selectors).toEqual(accepted.map((v) => v.expr));
  });
});

describe("cleanSelectorList 边界", () => {
  const clean = (list: unknown) =>
    cleanRules({ keywords: [], viewIds: [], selectors: list, packages: {} })?.selectors;

  it("非数组输入返回空列表", () => {
    expect(clean("不是数组")).toEqual([]);
    expect(clean(null)).toEqual([]);
    expect(clean(123)).toEqual([]);
  });

  it("非字符串元素被丢弃，其余保留", () => {
    expect(clean([42, null, "[text=\"a\"]", { a: 1 }])).toEqual(["[text=\"a\"]"]);
  });

  it("首尾空白被裁剪", () => {
    expect(clean(["  [text=\"a\"]  "])).toEqual(["[text=\"a\"]"]);
  });

  it("大小写不敏感去重，保留首次出现", () => {
    expect(clean(['[TEXT="a"]', '[text="A"]', '[text="b"]'])).toEqual([
      '[TEXT="a"]',
      '[text="b"]',
    ]);
  });

  it("超过 MAX_SELECTORS_PER_LIST 的部分被截断", () => {
    const many = Array.from({ length: config.MAX_SELECTORS_PER_LIST + 7 }, (_, i) => `[text="k${i}"]`);
    const out = clean(many) ?? [];
    expect(out).toHaveLength(config.MAX_SELECTORS_PER_LIST);
    expect(out[0]).toBe('[text="k0"]');
  });

  it("超过 MAX_SELECTOR_LEN 的整条被丢弃（不是截断）", () => {
    const tooLong = `[text="${"a".repeat(config.MAX_SELECTOR_LEN)}"]`;
    expect(tooLong.length).toBeGreaterThan(config.MAX_SELECTOR_LEN);
    expect(clean([tooLong, '[text="ok"]'])).toEqual(['[text="ok"]']);
  });

  it("恰好等于长度上限的条目保留", () => {
    const head = '[text="';
    const tail = '"]';
    const exact = head + "a".repeat(config.MAX_SELECTOR_LEN - head.length - tail.length) + tail;
    expect(exact).toHaveLength(config.MAX_SELECTOR_LEN);
    expect(isValidSelector(exact)).toBe(true);
    expect(clean([exact])).toEqual([exact]);
  });

  it("中文选择器不被丢弃（ASCII \\w 白名单曾导致整通道静默失效）", () => {
    expect(clean(['[text="跳过"]', '[desc*="跳过广告"]'])).toEqual([
      '[text="跳过"]',
      '[desc*="跳过广告"]',
    ]);
  });

  it("应用级规则同样走清洗，未提供 selectors 时为默认空数组", () => {
    const cleaned = cleanRules({
      keywords: [],
      viewIds: [],
      selectors: [],
      packages: {
        "com.example.app": { keywords: ["a"], viewIds: ["b"], selectors: ['[click="true"]'] },
        "com.example.two": { keywords: ["c"], viewIds: [], disabled: false },
      },
    });
    expect(cleaned?.packages["com.example.app"].selectors).toEqual(['[click="true"]']);
    expect(cleaned?.packages["com.example.two"].selectors).toEqual([]);
  });

  it("顶层同时给出 selectors 与 globalSelectors 时以 selectors 为准", () => {
    const cleaned = cleanRules({
      keywords: [],
      viewIds: [],
      selectors: ['[text="a"]'],
      globalSelectors: ['[text="b"]'],
      packages: {},
    });
    expect(cleaned?.selectors).toEqual(['[text="a"]']);
  });
});
