---
name: release
description: 发布与回滚自检技能。触发场景：创建 release/vX.Y.Z、打版本 tag、发布验收、hotfix 回滚复盘。日常开发提交与建分支不触发（那是 branch-guard）。
metadata:
  audience: 发布执行者
  workflow: 冻结 → 三处版本号同步 → 全门禁 → 合入 main → annotated tag → 台账回填
---

# 发布与回滚（release）

> **规则指针（唯一事实源，本文不复制正文）**
> - 版本与发布规则：[CONTRIBUTING.md](../../CONTRIBUTING.md)「版本发布」「发布验收」
> - 版本序列与出口条件：[docs/planning/ROADMAP.md](../../docs/planning/ROADMAP.md)、[ROADMAP-ADS.md](../../docs/planning/ROADMAP-ADS.md) 第 6 节
> - 发布链路与台账维护：[docs/planning/RELEASE-HISTORY.md](../../docs/planning/RELEASE-HISTORY.md)
> - 变更记录规范：[CHANGELOG.md](../../CHANGELOG.md)

## 自检动作

1. [ ] 版本号符合 SemVer，且版本归属与 ROADMAP-ADS 里程碑表一致
2. [ ] 三处版本号同步：Android `versionName`、全局单调递增 `versionCode`、`server/package.json`
3. [ ] `release/vX.Y.Z` 分支过全部门禁后以 Squash 合入 `main`
4. [ ] annotated tag `vX.Y.Z` 已创建；制品可追溯到 commit/tag 并记录校验和
5. [ ] CHANGELOG 已新增版本段（行为收紧/破坏性变更带升级注意事项）
6. [ ] RELEASE-HISTORY 台账已回填；未完成项如实标「待发布 / 未创建 / 制品待补传」
7. [ ] 回滚预案明确：优先回滚已验证制品或 `git revert`
8. [ ] 紧急修复走 `hotfix/<id>-<slug>`，保留事故记录与回滚点

## 自检：确认未出现以下情况（判据以上方指针文件为准）

- 未过完整门禁就打 tag
- 删除或移动已推送的版本标签
- 版本号或 `versionCode` 复用
