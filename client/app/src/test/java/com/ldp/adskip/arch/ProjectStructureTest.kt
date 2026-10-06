package com.ldp.adskip.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 目录结构守护测试：把「仓库长什么样」也升级为 CI 可执行规则。
 *
 * 背景（本仓库实测故障，不是假想风险）：
 * 1. 仓库根目录曾长期躺着构建产物 `AdSkip-latest.apk` 与工具残留目录（`.kilo/`、`.mimosa/`），
 *    它们不影响编译、不影响 `git status`，因此谁也不会注意到，直到人工整理才发现；
 * 2. `docs/`、`client/` 下新增目录没有任何约束，文档与模块会各自无序膨胀，
 *    出现「同一份规则分散在多个目录、无人知道哪个是权威」的第二份真相；
 * 3. `settings.gradle.kts` 里的 `include(":x")` 与磁盘上的模块目录可能不同步
 *    （模块删了仍注册 → 构建迷惑；建了目录忘注册 → 代码不被编译）。
 *
 * 契约细则见 `docs/architecture/ARCHITECTURE.md` 第 2.2 节；
 * 纯 JVM 文件系统扫描，不依赖 Android SDK，随 `testDebugUnitTest` 门禁运行。
 *
 * 修订方式：**白名单是刻意收紧的**。确需新增根级条目时，先更新本测试的
 * `ALLOWED_ROOT_DIRS` / `ALLOWED_ROOT_FILES`，并在 PR 说明里给出理由——
 * 让结构调整成为一次显式决策，而不是一次顺手拖拽。
 */
class ProjectStructureTest {

    // ---------- 1. 仓库根目录：只有白名单内的条目 ----------

    @Test
    fun `repository root contains only the allowed entries`() {
        val root = repoRoot()
        // `.git` 形态随场景不同：普通克隆是**目录**，`git worktree` 关联的工作区是**文件**。
        // 本项目强制 Agent 使用 worktree（AGENT-WORKFLOW 第 2.1 节），两种形态都属正常，
        // 必须同时从目录与文件两侧排除，否则在 worktree 内会误报。
        val entries = root.listFiles().orEmpty().filter { it.name != ".git" }
        val actualDirs = entries.filter { it.isDirectory }.map { it.name }.toSet()
        val actualFiles = entries.filter { it.isFile }.map { it.name }.toSet()

        val unexpectedDirs = (actualDirs - ALLOWED_ROOT_DIRS).sorted()
        val unexpectedFiles = (actualFiles - ALLOWED_ROOT_FILES).sorted()

        assertTrue(
            "仓库根目录出现了未登记的条目（ARCHITECTURE.md 第 2.2 节的白名单）：\n" +
                (unexpectedDirs + unexpectedFiles).joinToString("\n") { "  $it" } +
                "\n新增根级目录/文件会让仓库结构失去边界（历史上根目录就长出过 *.apk 与工具残留目录）。\n" +
                "若确有必要：\n" +
                "  1. 优先放进既有目录（docs/ 子模块、client/ 模块、server/ 内部）；\n" +
                "  2. 确属根级治理文件时，把名字加入本测试的 ALLOWED_ROOT_DIRS / ALLOWED_ROOT_FILES；\n" +
                "  3. 若属于本机生成物或工具产物，应写入根 .gitignore 而不是加白名单。",
            unexpectedDirs.isEmpty() && unexpectedFiles.isEmpty(),
        )
    }

    // ---------- 2. 产物与临时文件不得进入版本控制范围 ----------

    @Test
    fun `no build artifacts or scratch files inside the workspace`() {
        val root = repoRoot()
        val offenders = mutableListOf<String>()

        fun scan(dir: File, depth: Int) {
            if (depth > MAX_SCAN_DEPTH) return
            for (child in dir.listFiles() ?: return) {
                val rel = root.toPath().relativize(child.toPath()).toString().replace('\\', '/')
                if (child.isDirectory) {
                    if (child.name in PRUNED_DIRS) continue
                    scan(child, depth + 1)
                } else {
                    val ext = child.extension.lowercase()
                    val bad = child.name in SCRATCH_NAMES ||
                        (ext in BANNED_EXTENSIONS && rel !in ARTIFACT_EXCEPTIONS)
                    if (bad) offenders += "$rel  (${child.length() / 1024} KB)"
                }
            }
        }
        scan(root, 0)

        assertTrue(
            "仓库内存在构建产物 / 临时文件（应只出现在被忽略的目录里）：\n" +
                offenders.joinToString("\n") { "  $it" } +
                "\n分发以 GitHub Releases + SHA256SUMS 为准，仓库内不得保留任何 *.apk；" +
                "调试日志请写到仓库之外，编辑器/系统残留文件请加入 .gitignore。\n" +
                "详见 ARCHITECTURE.md 第 2.2 节与 docs/development/DEV-ENVIRONMENT.md。",
            offenders.isEmpty(),
        )
    }

    // ---------- 3. settings.gradle.kts 与磁盘模块双向一致 ----------

    @Test
    fun `gradle modules match settings gradle kts`() {
        val client = File(repoRoot(), "client")
        val settings = File(client, "settings.gradle.kts")
        assertTrue("未找到 client/settings.gradle.kts", settings.isFile)

        val registered = INCLUDE_REGEX.findAll(settings.readText())
            .map { it.groupValues[1] }
            .map { it.removePrefix(":").replace(':', '/') }
            .toSet()
        assertTrue(
            "未从 client/settings.gradle.kts 解析出任何 include(\":x\")——" +
                "模块声明格式若变更，必须同步更新本测试的正则。",
            registered.isNotEmpty(),
        )

        val missingOnDisk = registered.filterNot { File(client, it).isDirectory }
        assertTrue(
            "settings.gradle.kts 注册了磁盘上不存在的模块：\n" +
                missingOnDisk.joinToString("\n") { "  include(\":$it\")" } +
                "\n模块目录被删除/改名却未同步注册，Gradle 会以令人费解的方式失败。",
            missingOnDisk.isEmpty(),
        )

        val withoutBuildFile = registered.filterNot { File(client, "$it/build.gradle.kts").isFile }
        assertTrue(
            "以下模块目录缺少 build.gradle.kts：\n" +
                withoutBuildFile.joinToString("\n") { "  $it" },
            withoutBuildFile.isEmpty(),
        )

        val onDiskButNotRegistered = client.listFiles().orEmpty()
            .filter { it.isDirectory && it.name !in NON_MODULE_DIRS && File(it, "build.gradle.kts").isFile }
            .map { it.name }
            .filterNot { it in registered }
            .sorted()
        assertTrue(
            "磁盘上存在 Gradle 模块目录，但没有在 settings.gradle.kts 中 include()：\n" +
                onDiskButNotRegistered.joinToString("\n") { "  client/$it" } +
                "\n未注册的模块不会被编译，代码会「写了但没生效」。\n" +
                "若该目录并非 Gradle 模块，请改名或加入 NON_MODULE_DIRS 白名单。",
            onDiskButNotRegistered.isEmpty(),
        )
    }

    // ---------- 4. client/ 根目录只保留 Gradle 工程必要文件 ----------

    @Test
    fun `client dir contains only the expected gradle project files`() {
        val client = File(repoRoot(), "client")
        val actual = client.listFiles().orEmpty().map { it.name }.toSet()
        val unexpected = (actual - ALLOWED_CLIENT_ENTRIES).sorted()

        assertTrue(
            "client/ 根目录出现了预期之外的文件/目录：\n" +
                unexpected.joinToString("\n") { "  $it" } +
                "\nclient/ 是 Gradle 工程根，只应保留 wrapper、构建脚本与模块目录；" +
                "脚本、文档、工具产物请放到各自归属目录。\n" +
                "如需长期保留，请加入本测试的 ALLOWED_CLIENT_ENTRIES。",
            unexpected.isEmpty(),
        )
    }

    // ---------- 5. 每个模块遵循标准 Android 源集布局 ----------

    @Test
    fun `android modules follow the standard source layout`() {
        val client = File(repoRoot(), "client")
        val problems = mutableListOf<String>()

        for (module in registeredModules(client)) {
            val main = File(module, "src/main")
            if (!main.isDirectory) {
                problems += "${rel(module)}：缺少 src/main 目录"
                continue
            }
            val isApplication = File(main, "AndroidManifest.xml").isFile
            if (isApplication && !File(main, "res").isDirectory) {
                problems += "${rel(module)}：应用模块缺少 src/main/res"
            }
            val test = File(module, "src/test")
            if (test.isDirectory && !File(test, "java").isDirectory) {
                problems += "${rel(module)}：存在 src/test 但缺少 src/test/java"
            }

            // 非常规源集目录必须显式声明，否则 AGP 不会把它当源目录编译
            val buildFile = File(module, "build.gradle.kts")
            val buildText = if (buildFile.isFile) buildFile.readText() else ""
            main.listFiles().orEmpty()
                .filter { it.isDirectory && it.name !in STANDARD_MAIN_DIRS }
                .forEach { extra ->
                    if (extra.name !in buildText) {
                        problems += "${rel(module)}：src/main/${extra.name} 未在 build.gradle.kts 中声明" +
                            "（非常规源集目录默认不参与编译）"
                    }
                }
        }

        assertTrue(
            "模块源码布局不符合标准（ARCHITECTURE.md 第 2.2 节）：\n" +
                problems.joinToString("\n") { "  $it" } +
                "\n标准布局：src/main/{java,res} + src/main/AndroidManifest.xml（应用模块）、src/test/java。",
            problems.isEmpty(),
        )
    }

    // ---------- 6. Kotlin 文件的包声明必须与目录一致 ----------

    @Test
    fun `kotlin package declaration matches its directory`() {
        val mismatches = mutableListOf<String>()

        for (sourceSet in listOf("src/main/java", "src/test/java")) {
            val sourceRoot = File(appModule(), "$sourceSet/$BASE_PACKAGE_PATH")
            if (!sourceRoot.isDirectory) continue

            sourceRoot.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val subPath = sourceRoot.toPath().relativize(file.parentFile.toPath())
                        .toString().replace('\\', '/').replace('/', '.')
                        .let { if (it == ".") "" else it }
                    val expected = if (subPath.isEmpty()) BASE_PACKAGE else "$BASE_PACKAGE.$subPath"
                    val declared = PACKAGE_REGEX.find(file.readText().removePrefix("\uFEFF"))
                        ?.groupValues?.get(1)
                    if (declared != expected) {
                        val where = sourceRoot.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                        mismatches += "$sourceSet/$where：声明 package ${declared ?: "<缺失>"}，按目录应为 $expected"
                    }
                }
        }

        assertTrue(
            "Kotlin 文件的 package 声明与其目录不一致：\n" +
                mismatches.joinToString("\n") { "  $it" } +
                "\n包路径错位会让 ArchitectureBoundaryTest 的包级扫描失效（它按目录定位包边界），" +
                "也破坏 IDE 的包结构视图。请移动文件到正确目录，或修正 package 声明。",
            mismatches.isEmpty(),
        )
    }

    // ---------- 7. 文档必须登记到文档地图 ----------

    @Test
    fun `docs are registered in the doc map`() {
        val docs = File(repoRoot(), "docs")
        val unexpectedDirs = docs.listFiles().orEmpty()
            .filter { it.isDirectory && it.name !in ALLOWED_DOC_DIRS }
            .map { it.name }.sorted()
        assertTrue(
            "docs/ 出现了未登记的子目录：\n" +
                unexpectedDirs.joinToString("\n") { "  docs/$it" } +
                "\n新文档请归入既有分类目录，并把目录登记到 docs/README.md 的「目录结构」。",
            unexpectedDirs.isEmpty(),
        )

        val docMap = File(docs, "README.md").readText()
        // 精确匹配「相对 docs/ 的路径」，而不是子串 contains：
        // 子串匹配会把 `XROADMAP.md` 误判为 `ROADMAP.md` 已登记（假阴性，门禁形同虚设）。
        val registered = MarkdownLinkRegex.findAll(docMap).map { it.groupValues[1] }.toSet()
        val unregistered = docs.walkTopDown()
            .filter { it.isFile && it.extension == "md" && it.name != "README.md" }
            .map { docs.toPath().relativize(it.toPath()).toString().replace('\\', '/') }
            .filterNot { path -> registered.any { it.substringBefore('#') == path } }
            .sorted()
            .toList()
        assertTrue(
            "以下文档未登记到 docs/README.md（文档地图要求登记全部文档，否则会形成第二份真相）：\n" +
                unregistered.joinToString("\n") { "  docs/$it" } +
                "\n请在 docs/README.md 的「目录结构」与「阅读顺序」中登记，" +
                "以相对 docs/ 的路径写成 Markdown 链接（如 [planning/ROADMAP.md](planning/ROADMAP.md)）。",
            unregistered.isEmpty(),
        )
    }

    /** 解析 settings.gradle.kts 中已注册的模块目录列表。 */
    private fun registeredModules(client: File): List<File> {
        val settings = File(client, "settings.gradle.kts")
        if (!settings.isFile) return emptyList()
        return INCLUDE_REGEX.findAll(settings.readText())
            .map { it.groupValues[1].removePrefix(":").replace(':', '/') }
            .map { File(client, it) }
            .toList()
    }

    /** 应用模块：src/main 下带 AndroidManifest.xml 的模块。 */
    private fun appModule(): File = registeredModules(File(repoRoot(), "client"))
        .firstOrNull { File(it, "src/main/AndroidManifest.xml").isFile }
        ?: File(repoRoot(), "client/app")

    private fun rel(file: File): String = repoRoot().toPath().relativize(file.toPath()).toString().replace('\\', '/')

    // ---------- 导航契约：一级路由与页面注册必须一一对应 ----------
    //
    // 背景（本仓库实测缺陷，非假想风险）：`Routes.SETTINGS` 与 `SettingsScreen`
    // 早已存在却无任何入口，用户根本进不去。新增页面若只写 Screen 不接导航，
    // 就是同一类问题的复发，故把「路由 ↔ 底部导航 ↔ NavHost」三方对齐升级为测试。

    @Test
    fun `every route is reachable from the bottom navigation`() {
        // 声明在 Routes.kt，接线在 MainActivity.kt——两侧都要看，才能挡住「声明了但进不去」。
        val routesSource = readUiSource("Routes.kt")
        val navSource = readUiSource("MainActivity.kt")

        val declared = ROUTE_CONST_REGEX.findAll(routesSource)
            .map { it.groupValues[1] }
            .toSet()
        assertTrue(
            "Routes 应恰好登记 4 个一级路由（首页/应用/日志/我的），实际为 ${declared.sorted()}",
            declared.size == 4,
        )
        assertTrue(
            "Routes 缺少 PROFILE（「我的」页路由）",
            declared.contains("PROFILE"),
        )
        // 设置已内嵌进「我的」页，不再是一级路由：若有人再加回 SETTINGS 并挂到底部导航，
        // 就回到了「设置与关于本机分成两页」的老结构。
        assertTrue(
            "Routes 不应再声明 SETTINGS：设置内容已内嵌于「我的」页（v3.2 起底部导航为 4 项）",
            !declared.contains("SETTINGS"),
        )

        val inBottomBar = TOP_LEVEL_ITEM_REGEX.findAll(navSource)
            .map { it.groupValues[1] }
            .toSet()
        val inNavHost = COMPOSABLE_REGEX.findAll(navSource)
            .map { it.groupValues[1] }
            .toSet()

        assertTrue(
            "底部导航缺少一级入口：导航=${inBottomBar.sorted()}，路由=${declared.sorted()}\n" +
                "「页面已存在但用户进不去」是本仓库发生过的真实缺陷，请同步 TopLevelDestinations。",
            inBottomBar.containsAll(declared),
        )
        assertTrue(
            "NavHost 缺少一级路由注册：宿主=${inNavHost.sorted()}，路由=${declared.sorted()}",
            inNavHost.containsAll(declared),
        )
        // 反向检查：出现未在 Routes 登记的导航项会形成第二份真相。
        val unregistered = (inBottomBar + inNavHost).filterNot { it in declared }
        assertTrue(
            "导航/宿主里出现了未在 Routes 登记的路由：${unregistered.sorted()}",
            unregistered.isEmpty(),
        )
    }

    @Test
    fun `java import excludes every agent tool directory`() {
        val settings = File(repoRoot(), ".vscode/settings.json")
        if (!settings.isFile) return
        val text = settings.readText()

        // 从 java.import.exclusions 数组里取 glob；用正则而不是 JSON 解析：
        // settings.json 含注释（JSONC），拿不到标准 JSON 解析器时正则更稳，
        // 且这里要断言的本来就是「出现了哪些 glob」。
        //
        // **必须贪婪**（`]*` 而非 `]*?`）：数组里的 glob 自带 `**`，非贪婪会在第一个
        // `]` 处提前收尾——而第一个元素 `"**/node_modules/**"` 里的 `**` 不含 `]`，
        // 真正截断它的是……正是非贪婪本身：它会停在**最靠前**的 `]`，即数组末尾之前
        // 任何含 `]` 的位置。实测非贪婪写法下只能取到第一个元素，其余全部漏检，
        // 于是断言退化为恒真（移除 `**/.kilo/**` 也不会变红）。
        val arrayBody = Regex(""""java\.import\.exclusions"\s*:\s*\[([\s\S]*)]""")
            .find(text)
            ?.groupValues
            ?.get(1)
        assertTrue(
            ".vscode/settings.json 应声明 java.import.exclusions（Java 语言服务默认会导入工作区内" +
                "所有 gradle 项目，多 Agent worktree 各带一份 client/ 会撞成重复项目名）。\n  ${settings.path}",
            arrayBody != null,
        )
        val globs = Regex(""""([^"]+)"""").findAll(arrayBody!!).map { it.groupValues[1] }.toSet()
        // 数量下限：防止提取再次退化成「只拿到一两条」而让下面的循环空转。
        assertTrue(
            "从 java.import.exclusions 只解析出 ${globs.size} 条 glob，明显少于实际（应含 build/gradle/" +
                "各 Agent 目录等十余条）。断言会因此退化为恒真，请检查提取正则：\n  ${settings.path}",
            globs.size >= 10,
        )

        // 这些目录里放着多 Agent 的 worktree，每个都含一份 client/ 构建。
        // .vscode/ 是版本控制的共享配置、不含构建，无需排除。
        val mustExclude = AGENT_TOOL_DIRS.filter { it != ".vscode" }
        val missing = mustExclude.filter { dir -> globs.none { it.startsWith("**/$dir/") } }.sorted()

        assertTrue(
            "以下多 Agent 产物目录未加入 java.import.exclusions：\n" +
                missing.joinToString("\n") { "  $it" } +
                "\n每个 worktree 都带一份同名的 client/ Gradle 工程，被 Java 语言服务导入后主检出与" +
                "worktree 会撞成重复项目名——实测报错：\n" +
                "  \"A project with the name AdSkip-client already exists.\n" +
                "   Duplicate root element AdSkip-client\"\n" +
                "本清单此前正是**漏了 .kilo/**（它的 worktree 里 rootProject.name 同样是 AdSkip）而复发。" +
                "新增工具目录时请同时更新这里与本清单常量。\n" +
                "  ${settings.path}",
            missing.isEmpty(),
        )
    }

    @Test
    fun `vscode directory only holds the shared config`() {
        val vscode = File(repoRoot(), ".vscode")
        if (!vscode.isDirectory) {
            // 目录不存在是允许的：共享配置对构建与测试都不是必需的，
            // 不该因为它缺席就让守护测试变红。
            return
        }
        val actual = vscode.listFiles()?.map { it.name }?.toSet().orEmpty()
        val unexpected = actual - VSCODE_SHARED_FILES
        assertTrue(
            ".vscode/ 只应包含团队共享配置（${VSCODE_SHARED_FILES.sorted().joinToString()}）；" +
                "以下文件属于本机调试/工作区状态，入库会带进个人路径与断点：" +
                unexpected.sorted().joinToString { " $it" } +
                "（根 .gitignore 已用否定规则放行上述共享文件）",
            unexpected.isEmpty(),
        )
    }

    @Test
    fun `profile page is implemented under ui profile and wired to navigation`() {
        assertTrue(
            "「我的」页实现应位于 ui/profile/（与其余四屏同级）",
            File(File(repoRoot(), "client/app/src/main/java/$BASE_PACKAGE_PATH/ui"), "profile").isDirectory,
        )

        val navSource = readUiSource("MainActivity.kt")
        // 只断言「PROFILE 路由确实映射到 ProfileScreen」，不断言实参为空。
        // 原断言是整串字面量 `composable(Routes.PROFILE) { ProfileScreen() }`，
        // 页面一旦需要任何注入（例如 Messenger）就会红——那种失败与本用例的意图
        // 无关，属于把实现细节写进契约。路由映射错到别的页面仍会失败，护栏没削弱。
        assertTrue(
            "NavHost 缺少 Routes.PROFILE → ProfileScreen 的注册",
            Regex("""composable\(Routes\.PROFILE\)\s*\{\s*ProfileScreen\(""").containsMatchIn(navSource),
        )
    }

    @Test
    fun `profile page embeds the accessibility entry and the settings content`() {
        val profile = readUiSource("profile/ProfileScreen.kt")
        assertTrue(
            "「我的」页应内嵌设置内容 SettingsContent(...)，否则移除独立设置页后设置将无处可达",
            profile.contains("SettingsContent("),
        )
        assertTrue(
            "「我的」页应提供「打开无障碍设置」入口 AccessibilityCard",
            profile.contains("AccessibilityCard("),
        )
        assertTrue(
            "无障碍跳转须经 device/ 层（KeepAliveNavigator），UI 不得自行拼 Intent",
            profile.contains("KeepAliveNavigator.openAccessibilitySettings"),
        )

        val settings = readUiSource("settings/SettingsScreen.kt")
        assertTrue(
            "设置内容应以 SettingsContent 暴露供「我的」页内嵌",
            settings.contains("fun SettingsContent("),
        )
    }

    @Test
    fun `profile strings are declared in every locale`() {
        val required = listOf("nav_profile", "profile_title", "profile_version_label")
        val resRoot = File(repoRoot(), "client/app/src/main/res")
        // 只检查**声明了 strings.xml 的资源目录**（真正的 locale），并在断言里写明数量下限，
        // 避免「过滤太狠导致一个都没查」时静默通过。
        //
        // 为什么不能要求每个 values*/ 都有 strings.xml：`values-night/` 这类是**资源限定符**
        // 目录（只覆盖窗口主题的窗口级颜色），它不声明字符串，靠资源系统从 values/ 回退。
        // 早期的写法把限定符目录当成 locale，于是新增 values-night/ 会让这条测试变红——
        // 那不是「文案缺失」，是把两种目录的语义混为一谈。
        val locales = resRoot.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values") }
            ?.filter { File(it, "strings.xml").isFile }
            ?.map { it.name }
            .orEmpty()

        assertTrue(
            "未找到任何声明了 strings.xml 的 values*/ 目录，本用例会退化为恒真：${resRoot.path}",
            locales.isNotEmpty(),
        )

        locales.forEach { locale ->
            val xml = File(File(resRoot, locale), "strings.xml")
            val text = xml.readText()
            val missing = required.filter { !text.contains("name=\"$it\"") }
            assertTrue(
                "client/app/src/main/res/$locale/strings.xml 缺少文案：${missing.joinToString()}",
                missing.isEmpty(),
            )
        }
    }

    // ---------- 产品品牌：改名必须一次改全，不允许「改一半」 ----------

    @Test
    fun `product brand is declared consistently across every locale`() {
        val resRoot = File(repoRoot(), "client/app/src/main/res")
        // 与上一条同理：只查声明了 strings.xml 的资源目录。
        // `values-night/` 这类**资源限定符**目录不声明字符串，靠资源系统从 values/ 回退，
        // 把它当 locale 会误报「缺少品牌文案」。
        val locales = resRoot.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values") }
            ?.filter { File(it, "strings.xml").isFile }
            ?.map { it.name }
            .orEmpty()
        assertTrue("未找到任何声明了 strings.xml 的 values*/ 资源目录，无法校验产品品牌", locales.isNotEmpty())

        // 用户可见的品牌位：桌面名、无障碍服务名、快捷磁贴名。
        // 三者都会出现在系统设置界面里，漏改任意一项就会出现「同一个应用两个名字」。
        val brandKeys = listOf("app_name", "service_name", "tile_label")
        // 旧品牌（v3.x 及以前）：改名后不得在任何 locale 的用户可见文案里复活。
        val legacyTokens = listOf("净启动", "AdSkip")

        val problems = mutableListOf<String>()
        locales.forEach { locale ->
            val xml = File(File(resRoot, locale), "strings.xml")
            val text = xml.readText()

            brandKeys.forEach { key ->
                val value = stringValue(text, key)
                when {
                    value.isNullOrEmpty() ->
                        problems += "$locale/strings.xml：缺少品牌文案 $key"

                    key == "app_name" && value != BRAND ->
                        problems += "$locale/strings.xml：app_name 应为「$BRAND」，实际为「$value」"

                    LEGACY_BRAND in value ->
                        problems += "$locale/strings.xml：$key 仍含旧品牌「$LEGACY_BRAND」（值：$value）"
                }
                // service_name / tile_label 允许带副标题（如「轻启 · 跳过开屏广告」），
                // 但必须由当前品牌开头，否则系统界面会显示旧名字。
                if (!value.isNullOrEmpty() && key != "app_name" && !value.startsWith(BRAND)) {
                    problems += "$locale/strings.xml：$key 未以「$BRAND」开头（值：$value）"
                }
            }

            // 其余文案里也不应残留旧品牌（如「把净启动 AdSkip 加入白名单」这类提示）。
            (legacyTokens + LEGACY_BRAND).distinct().forEach { token ->
                val hit = Regex("""<string\s+name="(\w+)"\s*>[^<]*${Regex.escape(token)}""")
                    .findAll(text)
                    .map { it.groupValues[1] }
                    .filterNot { it in brandKeys }
                    .toList()
                if (hit.isNotEmpty()) {
                    problems += "$locale/strings.xml：以下文案仍含旧品牌「$token」：${hit.joinToString()}"
                }
            }
        }

        assertTrue(
            "产品品牌改名未改全：\n" + problems.joinToString("\n") { "  $it" } +
                "\n应用曾在 v3.x 期间名为「净启动 AdSkip」，改名为「$BRAND」后须同步所有 locale 的全部用户可见文案；" +
                "\n包名（applicationId）与 Theme.AdSkip 等内部标识刻意不变，以保证覆盖升级兼容性，不属本测试范围。",
            problems.isEmpty(),
        )
    }

    /** 取出 `<string name="key">value</string>` 的值；不存在返回 null。 */
    private fun stringValue(text: String, key: String): String? = Regex("""<string\s+name="$key"\s*>([^<]*)</string>""")
        .find(text)
        ?.groupValues
        ?.get(1)
        ?.trim()

    /** 读取 `ui/` 源目录下的 Kotlin 源文件。 */
    private fun readUiSource(relative: String): String {
        val file = File(repoRoot(), "client/app/src/main/java/$BASE_PACKAGE_PATH/ui/$relative")
        assertTrue("缺少源码 ${file.path}（或包结构已变更，请同步本测试）", file.isFile)
        return file.readText()
    }

    /** 从当前工作目录向上定位仓库根（需同时包含 .gitignore 与 docs/README.md）。 */
    private fun repoRoot(): File {
        var dir: File? = System.getProperty("user.dir")?.let { File(it) }
        while (dir != null) {
            if (File(dir, ".gitignore").isFile && File(dir, "docs/README.md").isFile) return dir
            dir = dir.parentFile
        }
        error("未定位到仓库根（需同时包含 .gitignore 与 docs/README.md）：无法执行目录结构守护")
    }

    private companion object {
        const val MAX_SCAN_DEPTH = 8
        const val BASE_PACKAGE = "com.ldp.adskip"
        const val BASE_PACKAGE_PATH = "com/ldp/adskip"

        /** 当前产品品牌（用户可见名称的唯一事实源）；改名时先改这里，再改全部 locale 文案。 */
        const val BRAND = "轻启"

        /** 本仓库曾长期使用的旧品牌名，改名后不得在任何 locale 的用户可见文案里复活。 */
        const val LEGACY_BRAND = "AdSkip"

        val INCLUDE_REGEX = Regex("""include\s*\(\s*"(:[^"]+)"\s*\)""")
        val PACKAGE_REGEX = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)

        /** Markdown 行内链接的目标，用于精确判定文档是否已登记到文档地图。 */
        val MarkdownLinkRegex = Regex("""\]\(([^)\s]+)\)""")

        /** sendBroadcast 调用名（配合括号配平扫描使用）。 */
        const val CALL_NAME = "sendBroadcast"

        /** `Routes` 中的路由常量声明：`const val PROFILE = "profile"`。 */
        val ROUTE_CONST_REGEX = Regex("""const\s+val\s+(\w+)\s*=\s*""")

        /** 底部导航数据源中的一项：`TopLevelDestination(Routes.PROFILE, ...)`。 */
        val TOP_LEVEL_ITEM_REGEX = Regex("""TopLevelDestination\(\s*Routes\.(\w+)""")

        /** NavHost 中的注册：`composable(Routes.PROFILE) { ... }`。 */
        val COMPOSABLE_REGEX = Regex("""composable\(\s*Routes\.(\w+)""")

        /**
         * 多 Agent 工具的产物目录（**单一事实源**）。
         *
         * 这些目录已被根 `.gitignore` 忽略、不入库，但会真实存在于开发者的工作区。
         * 它们各自可能带着一份 worktree（内含完整的 `client/` Gradle 工程），
         * 因此必须同时登记到两处：
         * 1. [ALLOWED_ROOT_DIRS]——否则仓库根白名单守护测试会在装了工具的机器上误报；
         * 2. `.vscode/settings.json` 的 `java.import.exclusions`——否则 Java 语言服务
         *    会把 worktree 里的 `client/` 也当项目导入，与主检出撞成重复项目名。
         *
         * 集中在此是为了让「新增一个工具目录」变成一处改动：此前两处各写一份清单，
         * 结果是 `.kilo/` 漏进了排除清单，重复项目名的报错因此复发。
         */
        val AGENT_TOOL_DIRS = setOf(
            ".kilo", // Kilo / Agent Manager 状态（agent-manager.json 等本机数据）
            ".kilocode", // 同族工具目录
            ".agents", // 同族工具目录
            ".worktrees", // 多 Agent worktree 落点（AGENT-WORKFLOW 第 2.1 节强制约定）
            ".mimosa", // 同族工具目录
            ".workbuddy", // 同族工具目录
            ".vscode", // 编辑器共享配置（选择性入库，规则见 VSCODE_SHARED_FILES）
        )

        // 仓库根白名单：目录
        // 工具产物目录整族放行：它们已被根 .gitignore 忽略（不入库），
        // 但会真实存在于每个开发者的工作区，缺席白名单会让本守护测试在
        // 「装了 Agent 工具的机器上必然红、CI 上必然绿」——这种双端不一致
        // 的门禁等于没有门禁。与 PRUNED_DIRS 保持同族登记。
        val ALLOWED_ROOT_DIRS = setOf(
            ".github", // CI 工作流
            "skills", // 随仓库版本控制的 Agent 技能（kilo.json 的 skills.paths 挂载点）
            "client", // Android 工程根
            "docs", // 文档
            "server", // Bun + TypeScript 服务端
        ) + AGENT_TOOL_DIRS

        /**
         * `.vscode/` 中允许入库的文件。
         *
         * 根 `.gitignore` 采取选择性入库：共享的编辑器行为与推荐扩展要进版本控制，
         * 否则它们只存在于各自本机，任何一条都可能在某台机器上悄悄失效；
         * 而 launch.json / tasks.json 这类个人调试状态绝不能进——它们带着本机路径、
         * 个人断点与临时任务，进了库就是噪声。
         *
         * 本清单同时是 `.gitignore` 否定规则的**对照表**：两边不一致时以本清单为准，
         * 因为本清单会被 CI 强制执行。
         */
        val VSCODE_SHARED_FILES = setOf("settings.json", "extensions.json")

        // 仓库根白名单：文件（治理类 + 工具配置，全部受版本控制）
        val ALLOWED_ROOT_FILES = setOf(
            ".editorconfig", ".gitattributes", ".gitignore",
            "AGENTS.md", "CHANGELOG.md", "CONTRIBUTING.md", "LICENSE", "README.md",
            "kilo.json", // Agent 工具的项目级配置（技能挂载 + 权限）；原 opencode.json 已是 legacy 路径
        )

        // client/ 白名单：末尾几项为本机生成/被忽略的目录，允许存在但不强制
        val ALLOWED_CLIENT_ENTRIES = setOf(
            "app", "gradle",
            "build-logic", // 复合构建（included build）：托管约定插件，非主构建模块
            "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
            "gradlew", "gradlew.bat", "local.properties",
            "build", ".gradle", ".kotlin", "signing",
        )

        /** client/ 下不是 Gradle 模块的目录（不参与「未注册模块」检查）。 */
        val NON_MODULE_DIRS = setOf("gradle", "build", ".gradle", ".kotlin", "signing", "build-logic")

        /** 扫描产物时剪掉的目录：构建输出、依赖与工具/本机生成物。 */
        val PRUNED_DIRS = setOf(
            ".git", "node_modules", "build", ".gradle", ".kotlin", ".idea", ".vscode",
            "captures", ".tools", "signing", "dist", "out", "target", "release",
            ".mimosa", ".workbuddy", ".kilo", ".kilocode", ".worktrees", ".agents",
            ".cxx", ".externalNativeBuild", "data",
        )

        /** 禁止进入版本控制范围的文件扩展名。 */
        val BANNED_EXTENSIONS = setOf(
            "apk", "aab", "apks", "aar", // Android 制品
            "log", "tmp", "bak", "orig", "rej", "swp", "hprof", // 调试与编辑残留
            "zip", "tar", "gz", "7z", "rar", "jar", // 打包产物
            "keystore", "jks", "p12", "kdb", "pem", // 密钥
            "iml", "exe", "dll", "so", "dylib", // IDE 与本地二进制
        )

        /** 允许存在的打包产物（Gradle wrapper 必须入库，否则无法构建）。 */
        val ARTIFACT_EXCEPTIONS = setOf("client/gradle/wrapper/gradle-wrapper.jar")

        /** 禁止存在的系统/编辑器残留文件名。 */
        val SCRATCH_NAMES = setOf(".DS_Store", "Thumbs.db", "desktop.ini")

        /** docs/ 下允许的分类目录。 */
        val ALLOWED_DOC_DIRS = setOf("api", "architecture", "development", "planning", "diagrams")

        /** src/main 下允许的常规目录。 */
        val STANDARD_MAIN_DIRS = setOf("java", "res", "assets")
    }
}
