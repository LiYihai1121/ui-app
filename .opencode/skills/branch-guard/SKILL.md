---
name: branch-guard
description: Enforce branch-per-change workflow — every code change must use a new branch, pass tests, then merge back. Protects main from direct commits and untested code.
metadata:
  audience: all-contributors
  workflow: git-flow
---

## Branch Workflow Rules

You MUST follow this workflow for ALL code changes in this project. No exceptions.

### 1. Always create a new branch before editing

Before making ANY code change, you MUST:

```bash
# Check current branch (must be on main to start)
git branch --show-current

# Create a new branch from main (or master)
git checkout main
git pull
git checkout -b <branch-name>
```

**Branch naming convention:**

| 类型 | 格式 | 示例 |
|---|---|---|
| 新功能 | `feature/<简述>` | `feature/compose-migration`, `feature/bun-server` |
| 修复 Bug | `fix/<简述>` | `fix/rate-limit-off-by-one`, `fix/cors-null-header` |
| 重构 | `refactor/<简述>` | `refactor/server-ts`, `refactor/ui-layer` |
| 文档 | `docs/<简述>` | `docs/architecture-update` |
| CI/构建 | `ci/<简述>` | `ci/bun-workflow` |
| 测试 | `test/<简述>` | `test/smoke-in-process` |

### 2. Make changes on the branch

Work ONLY on the new branch. Commit with descriptive messages:

```bash
git add -A
git commit -m "type: description"
```

### 3. Test before merging

You MUST verify the code works before merging:

**Android 客户端：**
```bash
./gradlew assembleDebug        # 编译通过
./gradlew testDebugUnitTest    # 单测通过
```

**服务端（Bun）：**
```bash
cd server && bun install && bun test  # 40 tests pass, 0 fail
```

If ANY test fails, fix the issue on the same branch before proceeding.

### 4. Merge back to main

Only after ALL tests pass:

```bash
git checkout main
git merge --no-ff <branch-name> -m "merge: <branch-name> — <summary>"
git push origin main
git branch -d <branch-name>  # clean up local branch
git push origin --delete <branch-name>  # clean up remote branch (if pushed)
```

### 5. Main branch protection rules

**NEVER do these on main directly:**

- `git commit` directly on main
- `git push origin main` without going through a branch
- Merging code that hasn't been tested
- Skipping the branch workflow for "small" changes

**"Small" changes still require a branch:**
- Typo fixes
- Comment additions
- Config tweaks
- Documentation updates

The ONLY exception is merging a completed feature branch back to main (step 4).

### 6. Workflow summary

```
main → create branch → edit → commit → test → merge back → delete branch → main
```

Every cycle follows this pattern. No shortcuts.
