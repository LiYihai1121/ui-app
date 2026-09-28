# 开发环境

本项目采用本机开发方式，不依赖 VS Code Dev Container。Android 客户端和 Bun 服务端可以分别启动，互不要求同时运行。

## 前置条件

- JDK 17 或更高版本，以及 Android SDK（compileSdk 35）。
- Bun 1.1 或更高版本。
- Windows 用户建议使用 PowerShell；macOS/Linux 使用 Bash。
- 使用 VS Code 或 Android Studio 打开项目根目录 `ui-app`。

## 启动

服务端：

```powershell
cd server
bun install
bun test
bun run typecheck
```

Android 客户端：

```powershell
cd client
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

Linux/macOS 将 `.\gradlew.bat` 替换为 `./gradlew`。APK 输出在 `client/app/build/outputs/apk/debug/app-debug.apk`，该目录属于构建产物，不提交到 Git。

## 本地运行

启动服务端：

```powershell
cd server
$env:ADMIN_TOKEN = "your-secret-token"
bun run server.ts
```

服务端默认监听 `http://localhost:3210`。未配置 `ADMIN_TOKEN` 时，公开读取接口仍可用，但管理写接口返回 `503`。端口或数据目录等配置见 `server/src/config.ts`。

## APK 安装排障

手机安装 APK 报「解析软件包时出现问题」时，先确认包本身可用：

```powershell
# 1. 校验签名：未签名包无法安装（apksigner 随 Android SDK build-tools 提供）
& "$env:ANDROID_HOME\build-tools\35.0.0\apksigner.bat" verify --print-certs .\app\build\outputs\apk\release\app-release.apk

# 2. 校验分发文件完整性：与 Release 中 SHA256SUMS 比对
#    分发包不入库，需自行从 GitHub Releases 下载到本地任意目录后再校验：
#    Get-FileHash <下载路径>\AdSkip-latest.apk -Algorithm SHA256
```

> **仓库根目录不保留任何构建产物**（含 `*.apk`）：`assembleRelease` 的输出在
> `client/app/build/outputs/apk/release/`，正式分发以 GitHub Releases + `SHA256SUMS` 为准。

判定要点：

- **未签名包**：`assembleRelease` 在 `client/local.properties` 缺少 `adskip.*` 签名配置时会回退 debug 签名；只有显式设置 `adskip.unsignedRelease=true` 才产出未签名包，而发布流水线会拒绝上传这类产物。
- **`minSdk = 26`**：对应 Android 8.0，低于该版本的设备会直接解析失败。
- **签名冲突**：与手机上已安装版本签名不一致时报「应用未安装」，需先卸载 `com.ldp.adskip`。
- **分发完整性**：APK 经聊天工具转发可能被改名或截断，务必比对 SHA-256；本机分发可用 `bun run start` 起本地服务后按 [APK 安装排障](#apk-安装排障) 的方式下载比对。**仓库根不保留任何构建产物**（含 `*.apk`）：`assembleRelease` 的产物在 `client/app/build/outputs/apk/release/`，正式分发以 GitHub Releases + `SHA256SUMS` 为准。

## CI 构建失败排障

**症状**：CI 报 `A problem occurred configuring root project` → `Could not resolve com.android:...` → `Repository maven is disabled due to earlier error` → `There are 28 more failures with identical causes`，**测试一条都没跑**。

**判定**：这是**镜像故障，不是依赖或代码问题**。典型特征是根因里出现 `Received status code 502 from server: Bad Gateway` 或 `Connection reset`，且同一次推送的 `Android Build & Test` 若通过即可佐证（本仓库的仓库源是阿里云镜像优先，见 `client/settings.gradle.kts`）。

**处理**：直接 **Re-run failed jobs**。502 属瞬时故障，无需改代码。

> ⚠️ `settings.gradle.kts` 注释里的「官方仓库回退」**只对 404 生效**：依赖不存在时 Gradle 会顺延到下一个仓库；
> 而镜像返回 5xx/连接中断时，Gradle 会**直接禁用该仓库并让构建失败**，不会顺延。
> 因此镜像抖动会整体阻断 CI，这是已知取舍（国内直连官方源会读超时），靠重跑恢复。

## 环境清理

- 可安全删除 `client/build/`、`.gradle/`、`.kotlin/` 等构建缓存；Gradle 会自动重新生成。
- 服务端 `server/data/`（`rules.json` / `stats/` / `backups/`）是运行数据，删除前先确认无需保留当前规则、统计和回滚记录；初始规则种子固化在入库的 `server/seed/rules.json`，运行时规则文件缺失或损坏时自动回退种子。
