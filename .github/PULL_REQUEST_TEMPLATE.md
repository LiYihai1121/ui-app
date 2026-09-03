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