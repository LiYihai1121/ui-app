#!/usr/bin/env python3
"""新技能脚手架：按 skills/README.md「SKILL.md 格式」生成合规骨架。

用法：
    python skills/scripts/new_skill.py <skill-name> "<触发描述>" [--third-party]

生成 skills/<skill-name>/SKILL.md（name 与目录名一致、小写连字符、
description 为必填触发描述）；--third-party 时同时生成 NOTICE.md 骨架并
提示随包携带上游 LICENSE。生成物必须通过 SkillContractTest
（testDebugUnitTest 门禁）的格式与登记校验。
"""
import argparse
import re
import sys
from pathlib import Path

NAME_RE = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")
MIN_DESCRIPTION_LENGTH = 30

SKILL_TEMPLATE = """---
name: {name}
description: {description}
metadata:
  audience: ...
  workflow: ...
---

# {name}

> **规则指针（唯一事实源，本文不复制正文）**
> - ……（指向 CONTRIBUTING.md / AGENTS.md / docs/ 下的规范文件）

## 自检动作

1. [ ] ……

## 自检：确认未出现以下情况

- ……
"""

NOTICE_TEMPLATE = """# 来源与署名（NOTICE）

- **技能**：`{name}`
- **来源**：第三方上游（引入时补全来源与作者信息）
- **许可**：随包携带上游 LICENSE（引入时补全）；对外分发前须确认授权
"""


def main() -> int:
    parser = argparse.ArgumentParser(description="按 skills/README.md 规范生成新技能骨架")
    parser.add_argument("name", help="技能名：小写连字符，且与目录名一致")
    parser.add_argument("description", help="触发描述：何时该被自动发现、何时不该误触发（≥30 字符）")
    parser.add_argument("--third-party", action="store_true", help="第三方技能：生成 NOTICE 骨架并提示携带 LICENSE")
    parser.add_argument("--force", action="store_true", help="覆盖已存在的 SKILL.md")
    args = parser.parse_args()

    if not NAME_RE.match(args.name):
        print(f"[拒绝] 技能名不合法（应为小写连字符）：{args.name}", file=sys.stderr)
        return 1
    if len(args.description) < MIN_DESCRIPTION_LENGTH:
        print(f"[拒绝] description 至少 {MIN_DESCRIPTION_LENGTH} 字符（需写明适用场景与边界）", file=sys.stderr)
        return 1

    root = Path(__file__).resolve().parents[2]
    skill_dir = root / "skills" / args.name
    skill_file = skill_dir / "SKILL.md"
    if skill_file.exists() and not args.force:
        print(f"[拒绝] 已存在：{skill_file}（--force 覆盖）", file=sys.stderr)
        return 1

    skill_dir.mkdir(parents=True, exist_ok=True)
    skill_file.write_text(
        SKILL_TEMPLATE.format(name=args.name, description=args.description), encoding="utf-8"
    )
    print(f"[已生成] {skill_file}")

    if args.third_party:
        notice = skill_dir / "NOTICE.md"
        if not notice.exists():
            notice.write_text(NOTICE_TEMPLATE.format(name=args.name), encoding="utf-8")
            print(f"[已生成] {notice}（第三方技能：请随包携带上游 LICENSE 并补全署名）")

    readme_text = (root / "skills" / "README.md").read_text(encoding="utf-8")
    if f"{args.name}/" not in readme_text:
        print(f"[下一步] 把 {args.name}/ 登记进 skills/README.md 目录结构（SkillContractTest 强制）")
    print("[下一步] 编写正文（规则指针 + 自检动作，不复制规则正文）后跑 "
          "cd client && ./gradlew testDebugUnitTest 验证")
    return 0


if __name__ == "__main__":
    sys.exit(main())
