---
name: branch-guard
description: Enforce enterprise branch governance — every change uses a traceable branch, passes CI and review, then enters protected main or a release branch.
metadata:
  audience: all-contributors
  workflow: github-flow
---

## Branch Workflow Rules

You MUST follow this workflow for ALL code changes in this project. No exceptions.

### 1. Always create a new branch before editing

Before making ANY code change, you MUST:

```bash
# Check current branch (must be on main to start)
git branch --show-current

# Create a new branch from the latest main
git switch main
git pull --ff-only origin main
git switch -c <branch-name>
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
| 发布 | `release/vX.Y.Z` | `release/v3.1.0` |
| 紧急修复 | `hotfix/<id>-<简述>` | `hotfix/456-crash-on-start` |

### 2. Make changes on the branch

Work ONLY on the new branch. Commit with descriptive messages:

```bash
git add -A
git commit -m "type(scope): description"
```

### 3. Test before merging

You MUST verify the code works before merging:

**Android 客户端：**
```bash
cd client
./gradlew assembleDebug        # 编译通过
./gradlew testDebugUnitTest    # 单测通过
```

**服务端（Bun）：**
```bash
cd server && bun install && bun test  # 当前测试全部通过，0 fail
```

If ANY test fails, fix the issue on the same branch before proceeding.

### 4. Pull Request and merge

Only after ALL tests pass and required reviewers approve:

```bash
gh pr create --base main --head <branch-name>
# Merge through the protected repository UI using Squash Merge.
git switch main
git pull --ff-only origin main
git branch -d <branch-name>
```

### 5. Main branch protection rules

**NEVER do these on main directly:**

- `git commit` directly on main
- `git push origin main` without going through a branch
- Merging code that has not passed required CI or review
- Skipping the branch workflow for "small" changes

**"Small" changes still require a branch:**
- Typo fixes
- Comment additions
- Config tweaks
- Documentation updates

The ONLY exception is merging a completed feature branch back to main (step 4).

### 6. Workflow summary

```
main → create traceable branch → edit → test → PR review → squash merge → delete branch → main
```

Every cycle follows this pattern. No shortcuts.
