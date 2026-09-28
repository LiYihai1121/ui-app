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
            unexpectedDirs.isEmpty() && unexpectedFiles.isEmpty()
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
            offenders.isEmpty()
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
            registered.isNotEmpty()
        )

        val missingOnDisk = registered.filterNot { File(client, it).isDirectory }
        assertTrue(
            "settings.gradle.kts 注册了磁盘上不存在的模块：\n" +
                missingOnDisk.joinToString("\n") { "  include(\":$it\")" } +
                "\n模块目录被删除/改名却未同步注册，Gradle 会以令人费解的方式失败。",
            missingOnDisk.isEmpty()
        )

        val withoutBuildFile = registered.filterNot { File(client, "$it/build.gradle.kts").isFile }
        assertTrue(
            "以下模块目录缺少 build.gradle.kts：\n" +
                withoutBuildFile.joinToString("\n") { "  $it" },
            withoutBuildFile.isEmpty()
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
            onDiskButNotRegistered.isEmpty()
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
            unexpected.isEmpty()
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
            problems.isEmpty()
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
            mismatches.isEmpty()
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
            unexpectedDirs.isEmpty()
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
            unregistered.isEmpty()
        )
    }

    // ---------- 工具 ----------

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
    private fun appModule(): File =
        registeredModules(File(repoRoot(), "client"))
            .firstOrNull { File(it, "src/main/AndroidManifest.xml").isFile }
            ?: File(repoRoot(), "client/app")

    private fun rel(file: File): String =
        repoRoot().toPath().relativize(file.toPath()).toString().replace('\\', '/')

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

        val INCLUDE_REGEX = Regex("""include\s*\(\s*"(:[^"]+)"\s*\)""")
        val PACKAGE_REGEX = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)

        /** Markdown 行内链接的目标，用于精确判定文档是否已登记到文档地图。 */
        val MarkdownLinkRegex = Regex("""\]\(([^)\s]+)\)""")

        /** sendBroadcast 调用名（配合括号配平扫描使用）。 */
        const val CALL_NAME = "sendBroadcast"

        // 仓库根白名单：目录
        val ALLOWED_ROOT_DIRS = setOf(
            ".github",   // CI 工作流
            ".opencode", // Agent 技能（skills/ 受版本控制，node_modules 等被忽略）
            "client",    // Android 工程根
            "docs",      // 文档
            "server"     // Bun + TypeScript 服务端
        )

        // 仓库根白名单：文件（治理类，全部受版本控制）
        val ALLOWED_ROOT_FILES = setOf(
            ".editorconfig", ".gitattributes", ".gitignore",
            "AGENTS.md", "CHANGELOG.md", "CONTRIBUTING.md", "LICENSE", "README.md",
            "opencode.json"
        )

        // client/ 白名单：末尾几项为本机生成/被忽略的目录，允许存在但不强制
        val ALLOWED_CLIENT_ENTRIES = setOf(
            "app", "gradle",
            "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
            "gradlew", "gradlew.bat", "local.properties",
            "build", ".gradle", ".kotlin", "signing"
        )

        /** client/ 下不是 Gradle 模块的目录（不参与「未注册模块」检查）。 */
        val NON_MODULE_DIRS = setOf("gradle", "build", ".gradle", ".kotlin", "signing")

        /** 扫描产物时剪掉的目录：构建输出、依赖与工具/本机生成物。 */
        val PRUNED_DIRS = setOf(
            ".git", "node_modules", "build", ".gradle", ".kotlin", ".idea", ".vscode",
            "captures", ".tools", "signing", "dist", "out", "target", "release",
            ".mimosa", ".workbuddy", ".kilo", ".kilocode", ".worktrees", ".agents",
            ".cxx", ".externalNativeBuild", "data"
        )

        /** 禁止进入版本控制范围的文件扩展名。 */
        val BANNED_EXTENSIONS = setOf(
            "apk", "aab", "apks", "aar",                 // Android 制品
            "log", "tmp", "bak", "orig", "rej", "swp", "hprof", // 调试与编辑残留
            "zip", "tar", "gz", "7z", "rar", "jar",      // 打包产物
            "keystore", "jks", "p12", "kdb", "pem",      // 密钥
            "iml", "exe", "dll", "so", "dylib"           // IDE 与本地二进制
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
