# 构建框架工程化设计（version catalog / build-logic / 模块拆分）

> **状态**：设计中，未实施。阶段 A（目录结构契约）已落地，见
> [ARCHITECTURE.md 第 2.2 节](../architecture/ARCHITECTURE.md#22-目录结构契约)。
> **最后更新**：2026-09-28
> **归属版本**：未分配。实施前需按 [ROADMAP.md](ROADMAP.md) 的「候选池」取版本号。

## 1. 目标与非目标

当前客户端是**单模块 `:app`**：版本硬编码在构建脚本里、模块边界靠 import 扫描「自觉」守护。
本文给出三步演进（阶段 A 已完成，B/C 待排期），把工程骨架对齐成熟的 Android 工程约定。

| | 事项 | 状态 |
| --- | --- | --- |
| A | 目录结构契约（白名单 + 产物 + 模块 + 包结构） | ✅ 已落地 |
| B | version catalog + build-logic 约定插件 + Gradle 硬化 | 📋 本文档第 3 节 |
| C | 模块拆分（`core:common` / `core:engine`） | 📋 本文档第 4 节 |

**非目标**：不拆分 `:feature:*` 功能模块（理由见 4.3）；不引入 Hilt/KSP 等注解处理器（当前手动 DI 足够，
注解处理器会拖慢构建且与「零外部代码生成」现状冲突）；不做多 flavor 拆分（单一产品线不需要）。

## 2. 现状事实

- Gradle **9.7.0**（wrapper 走腾讯云镜像），AGP **8.7.3**，Kotlin **2.0.21**，JDK 17，compileSdk/targetSdk 35，minSdk 26。
- 依赖版本全部硬编码：`client/build.gradle.kts` 三处插件版本 + `client/app/build.gradle.kts` 的 Compose BOM 与 5 个 AndroidX 坐标。
- 仓库源全部使用**阿里云镜像优先**（注释明确：本机直连 `dl.google.com`/`mavenCentral` 会读超时）。
- `repositoriesMode = FAIL_ON_PROJECT_REPOS` 已开启（正确，勿在模块内写仓库）。
- `lint { checkReleaseBuilds = false }`：因 JDK 25 + AGP 8.7 内置 lint 的 ASM 不识别新 class 版本而**故意关闭**。
- release 签名读 `client/local.properties`（`adskip.storeFile` 等 4 个键），缺失时回退 debug 签名（Issue #23 修复）。
- 纯 JVM 逻辑已天然分层：`engine/`（零 Android 依赖）与 `core/`（零业务依赖），是拆分模块的前提。

## 3. 阶段 B：版本目录 + 约定插件 + 构建硬化

### 3.1 Version catalog

新建 `client/gradle/libs.versions.toml`，收编当前散落的全部版本：

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
composeBom = "2024.12.01"
activityCompose = "1.9.3"
lifecycle = "2.8.7"
navigationCompose = "2.8.5"
junit = "4.13.2"

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }

[libraries]
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
junit = { module = "junit:junit", version.ref = "junit" }
```

约定：**无版本号的库不写死版本**——Compose 系一律由 BOM 约束（`compose-bom` 作为 `platform()` 引入），
不要给 `compose-ui` 单独写 `version.ref`，否则会绕过 BOM 统一升级。

### 3.2 build-logic 约定插件

```text
client/
├── settings.gradle.kts      # 增 includeBuild("build-logic")
└── build-logic/
    ├── settings.gradle.kts  # 必须自带阿里云镜像（见 3.4 坑 1）
    └── convention/
        ├── build.gradle.kts          # 声明依赖、apply false
        └── src/main/kotlin/
            ├── AdskipApplicationPlugin.kt   # id("adskip.android.application")
            ├── AdskipLibraryPlugin.kt       # id("adskip.android.library")
            └── AdskipJvmPlugin.kt           # id("adskip.jvm")（阶段 C 用）
```

插件编号建议 `adskip.*` 前缀，便于与其他插件区分。`build-logic` 内部通过 `libs` 访问器直接引用主工程的
version catalog（`dependencyResolutionManagement { versionCatalogs { create("libs") { from(files("../gradle/libs.versions.toml")) } } }`）。

**职责划分**：`adskip.android.application` 收敛 `namespace`/`compileSdk`/`minSdk`/`targetSdk`/`jvmTarget=17`/
`buildFeatures.compose`/`testOptions.isReturnDefaultValues`；`adskip.android.library` 收敛除 `applicationId`/`versionCode` 外的公共部分。

### 3.3 gradle.properties 硬化

```properties
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configuration-cache=true
android.nonFinalResClass=true
kotlin.code.style=official
```

### 3.4 已知坑位（务必逐条核对）

| # | 坑 | 表现 | 处理 |
| --- | --- | --- | --- |
| 1 | included build 不继承主工程仓库 | `build-logic` 解析插件时直连 `dl.google.com` 超时（本机网络环境） | `build-logic/settings.gradle.kts` **独立**声明阿里云镜像 |
| 2 | CI 缓存键不覆盖 `.toml` | 现为 `hashFiles('**/*.gradle.kts')`，改版本号不失效缓存 → 用旧依赖构建 | 改用 `gradle/actions/setup-gradle@v4`（自带正确缓存键） |
| 3 | wrapper 缺校验和 | `distributionUrl` 走腾讯镜像，无 `distributionSha256Sum` → 供应链无完整性校验 | 从该镜像取官方 SHA-256 写入 `gradle-wrapper.properties` |
| 4 | `checkReleaseBuilds=false` 被继承 | 误放进 library 约定插件后，库模块的 lint 静默失效 | 该配置**只**在 `adskip.android.application` 中设置 |
| 5 | 签名逻辑位置漂移 | 迁移到约定插件时 `rootProject.file("local.properties")` 相对路径变化 | 保留在 `:app`，或在约定插件中改用 `rootProject.file(...)` 并验证四种签名分支 |

### 3.5 验收门禁

- `./gradlew assembleDebug`、`testDebugUnitTest`（含签名回退逻辑）**行为完全不变**；
- 故意把 catalog 中一个版本改错 → 必须编译失败（证明版本真被引用，而非遗留硬编码）；
- CI 三个 job 全绿，且改 `.toml` 后缓存 key 确实变化。

## 4. 阶段 C：模块拆分

### 4.1 目标结构

```text
client/
├── app/                 应用模块：service/ device/ ui/ net/ sync/ data/ 组合根
├── core/
│   ├── common/          Kotlin JVM：AppEvents/AppExecutors/Clock/LogRing
│   └── engine/          Kotlin JVM：SkipRuleEngine/RuleSet/AdNode/SafetyGuard/selector
└── data/                （可选，第二步再拆）Android library：Prefs/各 Repository
```

### 4.2 迁移顺序（每步独立可验证、可回滚）

1. **`:core:common`**：`core/` 源码与其测试一并迁出，声明 `kotlinx-coroutines` 依赖；`ArchitectureBoundaryTest` 的 `core` 规则保留（仍有价值），另加模块级断言。
2. **`:core:engine`**：`engine/` 迁出，零 Android 依赖；此时 `engine` 规则从「扫 import」升级为「编译器直接拒绝」。
3. **（可选）`:data`**：`data/` 含 `SharedPreferences`，必须是 Android library 而非 JVM 模块。

> 每步都要**移动对应测试**并在该模块内声明 `junit` 依赖，否则会出现「源码迁走、测试还引用旧包」的半迁移态。

### 4.3 为什么不拆 `:feature:*`

本项目是**单 Activity + Navigation Compose + 4 个页面**的规模。feature 模块的收益（并行开发隔离、
按需资源收缩）在这种体量下几乎为零，代价却是每个 feature 都要声明导航、DI、资源归属，
并让「一个功能改三个模块」的提交变得常见。当前 `ui/` 内部按 `home/apps/logs/settings` 分包，
已能提供足够的导航边界。

### 4.4 与 2.1 边界契约的关系

拆分后 2.1 的部分约束由 Gradle 模块边界**编译期强制**，`ArchitectureBoundaryTest` 退化为
「模块级 `dependencies` 断言 + 包级扫描」双重保险。**不要在拆分后删除该测试**——同模块内的包边界
（如 `ui/` 不碰 `data.Prefs`）仍需它守护。

## 5. 风险与回滚

| 风险 | 缓解 | 回滚 |
| --- | --- | --- |
| 阶段 B 破坏签名行为 | 四种签名分支（正式 / 未配置回退 debug / 显式 unsigned）逐个验证 | 单一提交，`git revert` 即可 |
| 配置缓存与签名读取冲突 | 3.4 坑 5 单独验证 | 关闭该开关，不影响其余硬化项 |
| 阶段 C 机械改 import 出错 | 每步一个提交，逐步跑全量单测 | 按步 `git revert`，步骤间无耦合 |
| 模块拆分影响发版 | 拆分本身不改 `applicationId`/`versionCode`/签名配置 | 版本冻结期间不执行阶段 C |

> 阶段 B/C 均不触碰 `applicationId`、`versionCode`、签名配置与发布流水线，
> 因此**不需要重新走发版验证**；但阶段 B 会改 `ci.yml` 的缓存方式，需确认 Release workflow 同步。


⚠️ **`configuration-cache=true` 必须最后单独提交验证**：它与「构建期读取 `local.properties` 做签名决策」这种
配置期文件系统读取可能冲突。若报冲突，改为在 `settings.gradle.kts` 用 `providers.fileContents` 惰性读取，
或对该任务关闭配置缓存，**不要**直接删掉这条。
