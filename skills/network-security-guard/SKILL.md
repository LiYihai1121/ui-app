---
name: network-security-guard
description: 网络层安全护栏技能。触发场景：net/ 层改动、SyncClient 或 OkHttp 迁移、TLS/证书 pinning、HMAC 签名、HTTPS/限频/鉴权策略变更。证书 pin 不可被配置绕过，服务端请求必须走签名与限频基建；不触发引擎/UI/服务端路由改动。
metadata:
  audience: android-network-contributors
  workflow: feature-branch
---

# 网络层安全护栏（network-security-guard）

> **规则指针（唯一事实源，本文不复制正文）**
> - 网络层实现（唯一出口）：`client/app/src/main/java/com/qingqi/adskip/net/SyncClient.kt`
> - 网络与 TLS 契约测试：`net/SyncClientTest`、`ui/settings/CertificatePinTest`
> - 安全模型与组件暴露：[ARCHITECTURE.md](../../docs/architecture/ARCHITECTURE.md) 第 5 节
> - 协议 / 鉴权 / 限频契约：[API.md](../../docs/api/API.md)
> - 安全策略与密钥纪律：[SECURITY.md](../../SECURITY.md)、[AGENTS.md](../../AGENTS.md)「提交」

## 开工前自检

1. 网络出口收敛：应用内请求一律走 `SyncClient`，禁止在 `net/` 之外散落 OkHttp / HttpURLConnection 直连；
2. 涉及 TLS/证书校验的改动先看 `SyncClientTest` 与 `CertificatePinTest` 固化的握手场景，改完契约测试先红再实现；
3. 带签名头的请求：签名算法与密钥注入路径变更必须先更新 [API.md](../../docs/api/API.md) 契约；
4. 新增网络行为（重试 / 超时 / 缓存 / 降级）先定位既有降级路径，不复写第二套策略。

## 硬护栏

1. **证书校验不可被配置绕过**——不允许「调试开关 / 配置项」关闭 pinning 或信任任意证书；安全用例由 `CertificatePinTest` 固化；
2. **服务端请求必须复用签名与限频基建**——`sync` 类请求保持既有签名头与限频行为，不另起鉴权通道；
3. **不引入明文 HTTP 出口**——除非已在 [API.md](../../docs/api/API.md) 登记并注释理由，新端点一律 HTTPS。

## 提交前自检

1. `cd client && ./gradlew testDebugUnitTest --tests "*SyncClient*" --tests "*CertificatePin*"` 全绿；
2. 协议字段变更同步 [API.md](../../docs/api/API.md) 与双端夹具（契约先行）；
3. 门禁命令全集见 [AGENTS.md](../../AGENTS.md)「验证与合并」——自行跑通再提交。

## 自检：确认未出现以下情况

- 绕过 pinning 的调试后门或配置开关
- 明文 HTTP 出口 / 未登记的新网络端点
- 在 `net/` 之外新增网络依赖却未走 `build-logic` 声明
