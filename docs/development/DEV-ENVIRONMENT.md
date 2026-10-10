# 开发环境（DEV-ENVIRONMENT）

本项目采用本机开发方式，不依赖 VS Code Dev Container。Android 客户端和 Bun 服务端可以分别启动，互不要求同时运行。工具版本以 Gradle Wrapper、Version Catalog 和 CI 为准；不要为适配本机而改动这些项目级版本。

## 前置条件

- **JDK 17**：与 CI 和客户端 Java/Kotlin 字节码目标保持一致；不建议用更高版本替代已验证的工具链。
- **Android SDK**：平台 `android-37.0`（Beta channel，`compileSdk = 37`）、Build Tools `36.0.0` 和 Platform Tools。`targetSdk = 35`、`minSdk = 26`；三者用途不同，不要把 target/min 误当成构建所需平台版本。
- **Bun 1.1 或更高版本**：须满足 `server/package.json` 的 engines 要求；使用仓库 `server/bun.lock` 锁定依赖。
- Windows 用户建议使用 PowerShell；macOS/Linux 使用 Bash。
- **VS Code（推荐）或 Android Studio**：VS Code 打开仓库根目录；Android Studio 也可用于 SDK Manager、模拟器和设备管理。推荐扩展见 `.vscode/extensions.json`，不是构建依赖。
- `GRADLE_USER_HOME` 是可选的机器级缓存位置，不是项目必须配置的路径。未设置时 Gradle 使用默认用户缓存目录；需要使用自定义缓存时，在启动 IDE/终端前设置该变量，且勿将个人绝对路径提交到仓库。
- Gradle wrapper 已固化 `distributionSha256Sum`（Gradle 9.7.0 官方校验和），下载或镜像被篡改时 Gradle 会拒绝启动而非静默换源。

### Android SDK 安装

先安装 Android SDK Command-line Tools，并设置 `ANDROID_HOME`（或 `ANDROID_SDK_ROOT`）指向 SDK 根目录。也可以使用 Android Studio 的 SDK Manager 安装稳定通道的 Build Tools 和 Platform Tools。Android 17 平台仍在 Beta 通道，需要显式选择该通道：

```powershell
sdkmanager --install "platform-tools" "build-tools;36.0.0"
sdkmanager --install "platforms;android-37.0" --channel=1
```

macOS/Linux 使用相同的 `sdkmanager` 参数。仅在本机需要固定 SDK 路径时，在 `client/local.properties` 写入 `sdk.dir`；此文件已忽略，不要提交本机 SDK 路径。

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

在 VS Code 打开仓库根目录后，可先按扩展提示安装推荐项。然后在各自终端中启动/检查需要的模块。

服务端（依赖安装后可在 `server/` 单独开发和测试）：

```powershell
cd server
bun install --frozen-lockfile
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

完整 CI 还会运行 `assembleRelease` 并验证签名；日常开发通常只需运行格式检查、Debug 构建和 JVM 单测。跨模块变更或提交 PR 前，按 [CI 工作流](../../.github/workflows/ci.yml) 验证 Android 与服务端两侧。

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
& "$env:ANDROID_HOME\build-tools\36.0.0\apksigner.bat" verify --print-certs .\app\build\outputs\apk\release\app-release.apk

# 2. 校验分发文件完整性：与 Release 中 SHA256SUMS 比对
#    分发包不入库，需自行从 GitHub Releases 下载到本地任意目录后再校验：
#    Get-FileHash <下载路径>\AdSkip-latest.apk -Algorithm SHA256
```

> **仓库根目录不保留任何构建产物**（含 `*.apk`）：`assembleRelease` 的输出在
> `client/app/build/outputs/apk/release/`，正式分发以 GitHub Releases + `SHA256SUMS` 为准。
> 服务端 `/download` 路由固定读 `server/` 上一级的 `AdSkip-latest.apk`，需要时在**部署目录**放该文件，
> 不要放进开发检出——根目录白名单（`ProjectStructureTest`）不含 `*.apk`，会让 `testDebugUnitTest` 判红。

判定要点：

- **无签名不再回退 debug**：`assembleRelease` 在 `client/local.properties` 缺少 `adskip.*` 签名配置时**直接失败**（M2 起三态策略，见 `client/app/build.gradle.kts`）；只有显式设置 `adskip.unsignedRelease=true` 才产出未签名包（仅供受信任环境自行签名），而发布流水线会拒绝上传这类产物。
- **`minSdk = 26`**：对应 Android 8.0，低于该版本的设备会直接解析失败。
- **签名冲突**：与手机上已安装版本签名不一致时报「应用未安装」，需先卸载 `com.qingqi.adskip`。
- **分发完整性**：APK 经聊天工具转发可能被改名或截断，务必比对 SHA-256；本机分发可用 `bun run start` 起本地服务后按 [APK 安装排障](#apk-安装排障) 的方式下载比对。**仓库根不保留任何构建产物**（含 `*.apk`）：`assembleRelease` 的产物在 `client/app/build/outputs/apk/release/`，正式分发以 GitHub Releases + `SHA256SUMS` 为准。

## 构建启动失败排障

**症状一：Gradle 发行版下载或启动失败（构建任务尚未执行）**

```text
[error] [gradle-server] Could not run build action using connection to
        Gradle distribution 'https://services.gradle.org/distributions/gradle-X.Y.Z-bin.zip'.
[error] Error getting build for <路径>/client: Could not run build action ...
```

**判定**：发行版下载发生在 Gradle 执行构建任务之前，通常与网络、wrapper 配置或缓存目录有关，不足以单独判断为代码/依赖错误，也不一定是 `GRADLE_USER_HOME` 未设置。先检查 `client/gradle/wrapper/gradle-wrapper.properties` 中的发行版 URL 和 Gradle 用户缓存；IDE 使用的环境变量可能与终端不同。

**处理**：

```powershell
# 查看 Gradle 实际使用的用户缓存目录；未设置时使用 Gradle 默认目录
if ($env:GRADLE_USER_HOME) { $gradleHome = $env:GRADLE_USER_HOME } else { $gradleHome = Join-Path $HOME ".gradle" }
$gradleHome
$distributionCache = Join-Path $gradleHome "wrapper\dists"
if (Test-Path $distributionCache) { Get-ChildItem $distributionCache -Directory } else { "Gradle 尚未在此用户目录缓存发行版" }
```

若团队或本机网络需要自定义缓存/镜像，按机器实际路径设置 `GRADLE_USER_HOME`，并**重启调用它的工具**（IDE、构建服务、Agent 宿主进程不继承新设的环境变量，必须重启才会生效）。遇到下载失败时，确认正在使用本仓库的 `client/`、URL 与 Wrapper 配置一致，并检查网络代理/镜像状态；若只是首次构建且缓存为空，Gradle 需要先下载发行版。

**症状二：整仓副本导致的「双重真相」**

`RepoHygieneTest` 会直接判失败，提示仓库内出现了「像仓库但没有 `.git` 元数据」的目录。
这类副本（例如工具在 `.kilo/worktrees/`、`.worktrees/` 等处生成的）会读到过期的构建配置，
于是出现「代码是旧的、Gradle 版本也是旧的」这类难以定位的失败。

正确做法：`git worktree add .worktrees/<agent>-<slug> -b <branch> origin/main`（落点在
`.worktrees/`，含 `.git` 元数据），不要在仓库内复制整仓。细则见
[AGENT-WORKFLOW.md](AGENT-WORKFLOW.md) 第 2.1 节。

## IDE 语言服务诊断排障

**症状**：VS Code 里 `client/` 下所有 `.kt` 文件一片红——`Unresolved reference: junit` / `mutableListOf` / `it` / `to` / `error`，或 `Class 'kotlin.Unit' was compiled with an incompatible version of Kotlin. The actual metadata version is 2.4.0, but the compiler version 2.1.0 can read versions up to 2.2.0`（`INCOMPATIBLE_CLASS`），个别文件还报 `Redeclaration: <类名>`。

**判定**：这是**编辑器扩展与构建工具链的结构性版本不兼容，100% 是误报**，不是代码问题。三条判据：

1. 命令行门禁全绿：`cd client && ./gradlew ktlintCheck testDebugUnitTest`——真实编译信号以此为准；
2. 报错指向 `gradle-9.7.0/lib/kotlin-stdlib-2.4.0.jar`，而报错的编译器是 2.1.0：两个 Kotlin 版本对不上，版本低的编译器读不了版本高的 stdlib metadata（2.1.0 只认 ≤ 2.2.0）；
3. 触发前提是 AGP 9 起 Kotlin 由 AGP 内置、本工程不再声明 `kotlin-android` 插件（见 `client/gradle/libs.versions.toml` 注释），语言服务器取不到工具链版本只能回落自带编译器。

历史根因是社区扩展 `fwcd.kotlin`（0.2.36 即 Marketplace 最新版，已冻结，内置 Kotlin 2.1.0，「升级扩展」这条路走不通）。本仓库的 `.vscode/extensions.json` 已改推 JetBrains 官方扩展 `JetBrains.kotlin-server`（基于 IntelliJ IDEA 的 Kotlin 插件实现，支持最新 Kotlin 语言版本），不存在该结构性不兼容。

**处理**：

1. 扩展面板确认装的是 **Kotlin by JetBrains**（`JetBrains.kotlin-server`）；若同时装有 `fwcd.kotlin`，先卸载旧的再装官方扩展——两个语言服务抢注 `.kt` 文件会互相干扰，装完重载窗口；
2. `Redeclaration` 误报来自多 Agent 的 `.worktrees/<name>/` 副本（每个 worktree 内含同一份 `client/` 工程，`fwcd.kotlin` 无目录排除配置项会一并导入）：协作收尾后按 [AGENT-WORKFLOW.md](AGENT-WORKFLOW.md) 删掉 worktree（`git worktree remove .worktrees/<name>`，分支与提交不受影响）即可消除；
3. **不要为迁就语言服务器而改写已通过构建的代码**——那会污染正确的源码。

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
