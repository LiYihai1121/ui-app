"""Validate this small plugin's distribution shape; standard library only."""

import argparse
import json
from pathlib import Path
import re


def validate(root: Path) -> dict:
    codex = json.loads((root / ".codex-plugin/plugin.json").read_text(encoding="utf-8"))
    claude = json.loads((root / ".claude-plugin/plugin.json").read_text(encoding="utf-8"))
    name = "code-simplifier"
    version = codex.get("version")
    if codex.get("name") != name or claude.get("name") != name:
        raise ValueError("Plugin identities differ")
    if not isinstance(version, str) or not re.fullmatch(
        r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)", version
    ):
        raise ValueError("Expected a semantic plugin version")
    if claude.get("version") != version or codex.get("skills") != "./skills/":
        raise ValueError("Version or skill path differs between manifests")
    if codex.get("license") != "Apache-2.0" or claude.get("license") != "Apache-2.0":
        raise ValueError("License metadata differs")
    skills = sorted((root / "skills").glob("*/SKILL.md"))
    if len(skills) != 1 or skills[0].parent.name != name:
        raise ValueError("Expected exactly one public code-simplifier skill")
    content = skills[0].read_text(encoding="utf-8-sig")
    if not content.startswith("---\n") or "\n---\n" not in content[4:]:
        raise ValueError("Skill frontmatter is missing")
    frontmatter = content.split("\n---\n", 1)[0].removeprefix("---\n")
    if f"name: {name}" not in frontmatter.splitlines() or not any(
        line.startswith("description: ") for line in frontmatter.splitlines()
    ):
        raise ValueError("Skill name or description is missing")
    if not (root / "LICENSE").is_file() or not (root / "NOTICE.md").is_file():
        raise ValueError("License and upstream attribution must travel with the plugin")
    return {"name": name, "version": version, "publicSkills": 1}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    try:
        print(json.dumps(validate(args.root), ensure_ascii=False))
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        parser.exit(1, f"Plugin validation failed: {exc}\n")
