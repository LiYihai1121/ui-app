package com.ldp.adskip.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * UI 层契约守护。
 *
 * 背景：现有的 `ProjectStructureTest` / `ArchitectureBoundaryTest` /
 * `ManifestContractTest` / `RepoHygieneTest` 覆盖的是清单、目录与包依赖，
 * **UI 规则一条都没有**。而本项目最有效的做法恰恰是「把约定写成测试」——
 * 品牌名一致性那条守护上线后，改名不再可能只改一种语言。
 *
 * 本文件把设计规范审计得出的四条可机器检查的结论固化为门禁：
 * 1. 间距只能取 [com.ldp.adskip.ui.theme.Spacing] 标度内的值；
 * 2. 反馈类操作不得使用 `Toast`（必须走 Snackbar，才能带「撤销」）；
 * 3. 开关行只能有一处实现（`LabeledSwitch`），不得再有 `fun SwitchRow`；
 * 4. 每个 locale 的 `strings.xml` 不得存在未被引用的文案。
 *
 * 与 [ProjectStructureTest] 同样的取舍：这些规则是「机器能查的部分」。
 * 视觉层级、注意力分配这类判断仍然只能靠评审——但凡能查的，就不该留给人记。
 */
class UiContractTest {

    @Test
    fun `spacing literals stay on the declared scale`() {
        val allowed = setOf("4", "8", "12", "16", "24", "32", "40")
        // 圆角半径属于 shape 体系（6/12/16/20/28），不是间距标度，单独豁免该文件
        val offenders = mutableListOf<String>()

        uiSources()
            .filterNot { it.endsWith("theme/Spacing.kt") || it.endsWith("theme/Theme.kt") }
            .forEach { relative ->
                codeOf(relative).forEachIndexed { index, line ->
                    Regex("""(\d+(?:\.\d+)?)dp""").findAll(line).forEach { m ->
                        if (!allowed.contains(m.groupValues[1])) {
                            offenders += "$relative:${index + 1}  ${m.groupValues[1]}dp  ${line.trim()}"
                        }
                    }
                }
            }

        assertTrue(
            "间距字面量必须取自 ui/theme/Spacing.kt 的标度（${allowed.sorted().joinToString("/")}dp）；" +
                "尺寸类请用 UiSizes，圆角属 shape 体系。以下位置用了标度外的值：\n" +
                offenders.joinToString("\n") { "  $it" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `feedback goes through snackbar not toast`() {
        val offenders = uiSources()
            .filterNot { it.endsWith("ui/Messenger.kt") } // Messenger 内部保留 Toast 作为无宿主回退
            .filter { codeOf(it).any { line -> "Toast.makeText" in line } }

        assertTrue(
            "反馈必须走 Messenger（Snackbar）以便携带「撤销」动作；" +
                "Toast 不可交互、会被后一条顶掉。以下文件仍在直接调用 Toast：\n" +
                offenders.joinToString("\n") { "  $it" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `switch rows have exactly one implementation`() {
        val offenders = uiSources().filter { "fun SwitchRow" in codeOf(it).joinToString("\n") }
        assertTrue(
            "开关行只能有一处实现（components/LabeledSwitch，整行可点）；" +
                "出现第二份实现会让同一功能出现两种手感，用户会把其中一个当 bug：" +
                offenders.joinToString("\n") { " $it" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `every declared string is referenced by code`() {
        val resRoot = File(repoRoot(), "client/app/src/main/res")
        val locales = resRoot.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values") }
            ?.map { it.name }
            .orEmpty()
        assertTrue("未找到任何 values*/ 资源目录", locales.isNotEmpty())

        // 引用方不止 ui/：device/（磁贴）、service/（无障碍）、core/ 都会取文案；
        // AndroidManifest 的 android:label 指向 @string/；无障碍服务的说明文案
        // 由 res/xml/skip_service_config.xml 的 @string/ 引用。只扫 Kotlin 会把
        // 这些在用文案误判为死文案——那不是护栏，是噪音。
        val references = StringBuilder()
        kotlinSources().forEach { references.append(codeOf(it)).append('\n') }
        val manifest = File(repoRoot(), "client/app/src/main/AndroidManifest.xml")
        if (manifest.isFile) references.append(manifest.readText())
        resourceXmls().forEach { references.append(it.readText()).append('\n') }

        val orphans = mutableListOf<String>()
        locales.forEach { locale ->
            val xml = File(resRoot, "$locale/strings.xml")
            if (!xml.isFile) return@forEach
            Regex("""<string name="([\w]+)">""")
                .findAll(xml.readText())
                .map { it.groupValues[1] }
                // string-array 不参与本检查（default_keywords / default_view_ids 由代码按名读取）
                .forEach { name ->
                    val referenced = references.contains("R.string.$name") ||
                        references.contains("R.string.array.$name") ||
                        references.contains("@string/$name")
                    if (!referenced) orphans += "$locale/$name"
                }
        }

        assertTrue(
            "以下文案没有任何引用方（Compose 化之前的 View 层遗留）：\n" +
                orphans.joinToString("\n") { "  $it" } +
                "\n删掉它们；确需保留的应注明为何暂无引用。",
            orphans.isEmpty(),
        )
    }

    // ---------- 工具 ----------

    /** 相对 repoRoot 的全部主源码（Kotlin），不限 ui/ ——文案引用方遍布各层。 */
    private fun kotlinSources(): List<String> {
        val root = File(repoRoot(), "client/app/src/main/java/com/ldp/adskip")
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(repoRoot()).path.replace('\\', '/') }
            .toList()
    }

    /** 仅 ui/ 下的源码：间距与组件重复这两条规则只约束 UI 层。 */
    private fun uiSources(): List<String> {
        val root = File(repoRoot(), "client/app/src/main/java/com/ldp/adskip/ui")
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(repoRoot()).path.replace('\\', '/') }
            .toList()
    }

    /** 资源 XML：布局与无障碍服务配置里同样会用 `@string/` 引用文案。 */
    private fun resourceXmls(): List<File> = File(repoRoot(), "client/app/src/main/res")
        .walkTopDown()
        .filter { it.isFile && it.extension == "xml" && it.readText().contains("@string/") }
        .toList()

    /**
     * 读出源码的**代码部分**：剥掉行注释与块注释。
     *
     * 必须剥注释——KDoc 里满是「约 300dp」「环 56dp」这类尺寸描述，
     * 它们是给人读的说明，不是可执行的间距，写进去会让间距门禁失去意义。
     */
    private fun codeOf(relativePath: String): List<String> {
        val text = File(repoRoot(), relativePath).readText()
        return text
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .lines()
            .map { it.substringBefore("//") }
            .filter { it.isNotBlank() }
    }
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            if (File(dir, ".gitignore").isFile && File(dir, "docs/README.md").isFile) return dir
            dir = dir.parentFile
        }
        error("未定位到仓库根（需同时含 .gitignore 与 docs/README.md）")
    }
}
