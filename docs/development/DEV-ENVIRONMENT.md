# 开发环境

本项目采用本机开发方式，不依赖 VS Code Dev Container。Android 客户端和 Bun 服务端可以分别启动，互不要求同时运行。

## 前置条件

- JDK 17 或更高版本，以及 Android SDK（compileSdk 35）。
- Bun 1.1 或更高版本。
- Windows 用户建议使用 PowerShell；macOS/Linux 使用 Bash。
- 使用 VS Code 或 Android Studio 打开项目根目录 `ui-app`。
- **`GRADLE_USER_HOME` 必须指向已缓存 Gradle 发行版的目录**（本机为 `F:\All_Data\gradle`）。
  该变量是**机器级**配置而非项目配置，命令行、IDE、CI 与各类构建工具都必须继承它——
  缺失时 Gradle 会回落到 `C:\Users\<用户>\.gradle`，那里没有缓存副本，
  于是每次构建都要重新下载发行版；在本网络下会因 `services.gradle.org` / `dl.google.com`
  不可达而直接失败。详见 [构建启动失败排障](#构建启动失败排障)。
- Gradle wrapper 已固化 `distributionSha256Sum`（Gradle 9.7.0 官方校验和），下载或镜像被篡改时 Gradle 会拒绝启动而非静默换源。

## 构建体系

- **版本唯一事实源 `client/gradle/libs.versions.toml`**：升级 AGP / Kotlin / Compose BOM / AndroidX 时只改这个文件，
  不要在任意 `.gradle.kts` 里写死版本号（`ProjectStructureTest` 不检查版本号，但 DESIGN-BUILD-FRAMEWORK 约定如此）。
  升级前先核对阿里云镜像的 `maven-metadata.xml`（本网络直连官方仓库会超时）。
- **公共构建配置在 `client/build-logic/convention/`**（`adskip.android.application` 约定插件）：
  `:app` 只保留它自己的 `applicationId` / `versionCode` / `versionName` / 签名 / 依赖。改动公共配置后，
  先跑 `client/build-logic` 的约定插件编译再整体验证（`./gradlew testDebugUnitTest assembleDebug`）。
- **格式与静态检查 `ktlint`**：规则集与行宽的**唯一事实源是仓库根 `.editorconfig`**（ktlint 从被检查文件逐级向上查找），
  插件由约定插件应用，ktlint 本体版本在 `client/build.gradle.kts` 的 `subprojects` 块里锁定（用 `plugins.withId` 触发，
  因为约定插件是在子项目求值阶段才应用它）。两个易踩的坑：
  - ktlint 本体版本必须显式锁定。插件版本虽已固定，但其默认内含的 ktlint 版本会随补丁版变动，规则集随之变化，
    会让「同一份代码在不同时刻跑出不同结论」；
  - Gradle 插件本体（`org.jlleitschuh.gradle:ktlint-gradle`）发布在插件仓库而非 Maven Central，
    `build-logic/settings.gradle.kts` 的 `dependencyResolutionManagement` 因此额外挂了阿里云
    `gradle-plugin` 镜像；`*.gradle.plugin` marker 只在插件门户，解析普通依赖时不可用。

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
.\gradlew.bat ktlintCheck
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

## 构建启动失败排障

**症状一：发行版下载失败（`BUILD FAILED in 1s`，一个任务都没跑）**

```text
[error] [gradle-server] Could not run build action using connection to
        Gradle distribution 'https://services.gradle.org/distributions/gradle-X.Y.Z-bin.zip'.
[error] Error getting build for <路径>/client: Could not run build action ...
```

**判定**：这是 `GRADLE_USER_HOME` 未被该进程继承，**不是**代码或依赖问题。两个判据：

1. 请求的 `distributionUrl` 指向 `services.gradle.org`——本项目 wrapper 固定使用
   `mirrors.cloud.tencent.com`，**任何提交里都没有出现过 `services.gradle.org`**；
   若日志里的 URL 与仓库中的不一致，说明构建的**不是当前这份代码**（陈旧副本或错误目录）。
2. `BUILD FAILED in 1s` 且无任务执行记录——失败发生在下载发行版阶段，早于依赖解析与编译。

**处理**：

```powershell
# 确认变量已设置，且目标目录内确实有已解压的发行版
$env:GRADLE_USER_HOME                       # 应为 F:\All_Data\gradle
Get-ChildItem "$env:GRADLE_USER_HOME\wrapper\dists" -Directory
```

若变量缺失就补上并**重启调用它的工具**（IDE、构建服务、Agent 宿主进程不继承新设的环境变量，
必须重启才会生效）。若 URL 指向 `services.gradle.org`，先确认当前目录是不是仓库的
`client/`，以及是否存在陈旧副本——见下方。

**症状二：整仓副本导致的「双重真相」**

`RepoHygieneTest` 会直接判失败，提示仓库内出现了「像仓库但没有 `.git` 元数据」的目录。
这类副本（例如工具在 `.kilo/worktrees/`、`.worktrees/` 等处生成的）会读到过期的构建配置，
于是出现「代码是旧的、Gradle 版本也是旧的」这类难以定位的失败。

正确做法：`git worktree add .worktrees/<agent>-<slug> -b <branch> origin/main`（落点在
`.worktrees/`，含 `.git` 元数据），不要在仓库内复制整仓。细则见
[AGENT-WORKFLOW.md](AGENT-WORKFLOW.md) 第 2.1 节。

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
