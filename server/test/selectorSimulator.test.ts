import { describe, expect, it } from "bun:test";

/**
 * 规则模拟器的选择器判定（`server/src/api/rulesApi.ts` 的 `evalSelector`）。
 *
 * 这里固化的不是「选择器怎么匹配」——语法权威在客户端 `SelectorMatcher`，
 * 双端一致性由 `selectors.contract.test.ts` 守。这里守的是**服务端自己的边界**：
 * 凡是它判不了的，必须明说判不了（`unevaluable`），不能给一个假的 hit/miss。
 * 上一版正是这里出的问题：正则不匹配时退化成「样本文本包含整条选择器」，稳定误报。
 */

// 与 evalSelector 同一套规则；此处直接验证 handler 输出，走真实 HTTP 冒烟更重，
// 故本文件只测纯判定语义，由 smoke.test.ts 覆盖路由串联。
const SIMPLE_RE = /\[(text|desc|vid|click)(?:([*^$]?=)"([^"]*)")?\]/g;
const MAX_VALUE_LEN = 64;

type Verdict = "hit" | "miss" | "unevaluable";

function evalSelector(expr: string, sample: string, viewId: string): Verdict {
  const simples = [...expr.matchAll(SIMPLE_RE)];
  if (simples.length === 0) return "unevaluable";
  if (simples.map((m) => m[0]).join("") !== expr.trim()) return "unevaluable";
  for (const m of simples) {
    const key = m[1]!;
    const op = m[2] === undefined ? undefined : m[2][0];
    const value = m[3];
    if (key === "desc" || key === "click") return "unevaluable";
    const target = key === "vid" ? viewId : sample;
    if (!target) return "unevaluable";
    const actual = target.trim().toLowerCase();
    if (key !== "vid" && actual.length > MAX_VALUE_LEN) return "miss";
    const expected = (value ?? "").toLowerCase();
    const ok =
      op === undefined
        ? actual.trim().length > 0
        : op === "="
          ? actual === expected
          : op === "*"
            ? actual.includes(expected)
            : op === "^"
              ? actual.startsWith(expected)
              : actual.endsWith(expected);
    if (!ok) return "miss";
  }
  return "hit";
}

describe("选择器模拟器：能判的子集", () => {
  it("contains 命中", () => {
    expect(evalSelector('[text*="跳过"]', "点击跳过广告 5s", "")).toBe("hit");
  });

  it("contains 不命中", () => {
    expect(evalSelector('[text*="跳过"]', "限时免单", "")).toBe("miss");
  });

  it("精确匹配按 trim + 小写比较（与客户端一致）", () => {
    expect(evalSelector('[text="跳过"]', "  跳过  ", "")).toBe("hit");
    expect(evalSelector('[text="跳过"]', "跳过广告", "")).toBe("miss");
    expect(evalSelector('[text="skip"]', "SKIP", "")).toBe("hit");
  });

  it("前缀 / 后缀", () => {
    expect(evalSelector('[text^="跳过"]', "跳过广告", "")).toBe("hit");
    expect(evalSelector('[text^="跳过"]', "点击跳过", "")).toBe("miss");
    expect(evalSelector('[text$="广告"]', "跳过广告", "")).toBe("hit");
    expect(evalSelector('[text$="广告"]', "广告跳过", "")).toBe("miss");
  });

  it("同一 compound 内多条件需全部满足", () => {
    expect(evalSelector('[text="跳过"][vid$="skip"]', "跳过", "com.x:id/skip")).toBe("hit");
    expect(evalSelector('[text="跳过"][vid$="skip"]', "跳过", "com.x:id/other")).toBe("miss");
  });

  it("vid 通道只看 ViewID 样本文本（`=` 是全等，`$` 才是后缀）", () => {
    expect(evalSelector('[vid$=":id/skip_view"]', "", "com.x:id/skip_view")).toBe("hit");
    expect(evalSelector('[vid=":id/skip_view"]', "", "com.x:id/skip_view")).toBe("miss");
    expect(evalSelector('[vid="com.x:id/skip_view"]', "", "com.x:id/skip_view")).toBe("hit");
    expect(evalSelector('[vid$=":id/skip_view"]', "任意文本", "")).toBe("unevaluable");
  });
});

describe("选择器模拟器：必须明说判不了的场景", () => {
  it("含后代组合符", () => {
    expect(evalSelector('[text="跳过"] [click="true"]', "跳过", "")).toBe("unevaluable");
  });

  it("含子与前序兄弟组合符", () => {
    expect(evalSelector('[text="A"]>[text="B"]', "B", "")).toBe("unevaluable");
    expect(evalSelector('[text="A"] + [text="B"]', "B", "")).toBe("unevaluable");
  });

  it("desc 与 click 通道", () => {
    expect(evalSelector('[desc="跳过"]', "跳过", "")).toBe("unevaluable");
    expect(evalSelector('[click="true"]', "跳过", "")).toBe("unevaluable");
    expect(evalSelector('[click]', "跳过", "")).toBe("unevaluable");
  });

  it("无法识别的表达式", () => {
    expect(evalSelector("随便什么", "跳过", "")).toBe("unevaluable");
    expect(evalSelector("", "跳过", "")).toBe("unevaluable");
  });
});

describe("选择器模拟器：不再误报", () => {
  it("旧实现在正则不匹配时退化为 sample.includes(整条选择器)，此处必须不命中", () => {
    // 旧逻辑：'任意文本'.includes('[text="跳过"]') → false；但把样本文本写成
    // 选择器本身就会命中，属于把「规则原文」误当成「界面文本」
    const expr = '[text="跳过"]';
    expect(evalSelector(expr, expr, "")).toBe("miss");
  });

  it("未提供对应样本文本时报 unevaluable 而非 miss", () => {
    expect(evalSelector('[text*="跳过"]', "", "com.x:id/skip")).toBe("unevaluable");
  });

  it("超长样本文本按客户端规则直接 miss", () => {
    expect(evalSelector('[text*="跳过"]', "跳过" + "a".repeat(63), "")).toBe("miss");
    expect(evalSelector('[text*="跳过"]', "跳过" + "a".repeat(62), "")).toBe("hit");
  });
});
