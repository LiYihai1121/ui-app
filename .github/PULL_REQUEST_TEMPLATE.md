# Pull Request

## 变更说明

<!-- 说明做了什么，以及为什么需要这项变更。 -->

关联 Issue：

<!-- 填写 #Issue 编号；紧急变更填写事故编号。 -->

## 变更类型

- [ ] 新功能
- [ ] Bug 修复
- [ ] 重构
- [ ] 文档或配置
- [ ] CI/构建
- [ ] 安全、协议或数据变更

## 兼容性与影响

<!-- 说明 API、协议、数据、权限、性能和用户体验影响；无影响请写“无”。 -->

## 验证结果

- [ ] `cd client && ./gradlew ktlintCheck`
- [ ] `cd client && ./gradlew assembleDebug`
- [ ] `cd client && ./gradlew testDebugUnitTest`
- [ ] `cd server && bun test`
- [ ] `cd server && bun run typecheck`

## 风险与回滚

<!-- 说明风险、监控指标、回滚版本/制品和执行步骤；无风险请写“无”。 -->

## 审查重点

<!-- 请指出希望审查者重点关注的文件或行为。 -->

## 发布检查

- [ ] 未修改已发布版本标签，未复用 Android `versionCode`
- [ ] 需要发布时已创建 `release/vX.Y.Z`，并同步 Android 与服务端版本
- [ ] 涉及安全、协议或数据变更时已请求领域负责人审查

<!-- 规则（分支模型、提交格式、审批与发布要求）见 CONTRIBUTING.md；此处不复制，避免两份真相。 -->
- [ ] 已确认本次变更未违反 [CONTRIBUTING.md](../CONTRIBUTING.md) 的分支与合并规则

## 多 Agent 协作检查

- [ ] 开发在独立 worktree（`.worktrees/<agent>-<slug>/`）内进行，未在主检出直接提交（主检出提交会被 `githooks/pre-commit` 拦截）
- [ ] 认领板（[AGENT-WORKFLOW.md](../docs/development/AGENT-WORKFLOW.md) 3.2）已登记本任务的工作区、分支与认领路径；跨入他人认领路径前已发起交接请求
- [ ] 涉及协议/契约/规划文档的改动已按文档地图同步对应事实源与契约测试
