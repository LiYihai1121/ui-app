package com.ldp.adskip.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 配色单一事实源契约。
 *
 * 背景（实测缺陷）：`Theme.kt` 的 `lightColorScheme/darkColorScheme` 只显式覆盖了部分
 * M3 角色，而代码里实际使用了 `surfaceContainer*` 三个角色——它们**从未被声明**，
 * 一直走 M3 内置默认值。默认值是为通用场景调的中性紫调，与 `Color.kt` 里维护的品牌蓝
 * 不是同一套色相，于是应用卡片底色与品牌色系悄悄脱节。
 *
 * 这类缺口不会报错、不会崩、审查时也看不出来（代码读起来完全正常），只有把
 * 「用到的角色必须显式声明」写成测试才拦得住。
 *
 * 第二条守护的是单一事实源原则：文件头注释宣称 dynamicColor 默认开启，
 * 而参数实际默认 `false`——同一事实两个说法，读者会信错那个。
 */
class ColorSchemeContractTest {

    private val themeFile = "client/app/src/main/java/com/ldp/adskip/ui/theme/Theme.kt"

    @Test
    fun `every color role used by the app is declared in the brand scheme`() {
        val theme = File(repoRoot(), themeFile)
        assertTrue("$themeFile 不存在", theme.isFile)
        val themeText = theme.readText()

        val declared = Regex("""^\s+(\w+)\s*=\s*[A-Z]""", RegexOption.MULTILINE)
            .findAll(themeText)
            .map { it.groupValues[1] }
            .toSet()

        val used = mainSources()
            .flatMap { file ->
                Regex("""colorScheme\.(\w+)""")
                    .findAll(stripComments(file.readText()))
                    .map { it.groupValues[1] }
            }
            .toSet()

        val undeclared = (used - declared).sorted()
        assertTrue(
            "以下 M3 颜色角色被代码使用，却未在 Theme.kt 的品牌色板里声明——它们会走 M3 内置默认值，" +
                "而那套中性色与 Color.kt 的品牌蓝不是同一色相，卡片底色会与应用配色悄悄脱节。\n" +
                "请在 lightColorScheme / darkColorScheme 中显式赋值：\n" +
                undeclared.joinToString("\n") { "  colorScheme.$it" },
            undeclared.isEmpty(),
        )
    }

    @Test
    fun `the dynamic color doc matches the actual default`() {
        val themeText = File(repoRoot(), themeFile).readText()
        val on = Regex("""dynamicColor:\s*Boolean\s*=\s*true""").containsMatchIn(themeText)
        val off = Regex("""dynamicColor:\s*Boolean\s*=\s*false""").containsMatchIn(themeText)

        assertTrue(
            "无法从 Theme.kt 解析 dynamicColor 的默认值：$themeFile",
            on || off,
        )

        // 文件头注释里的「默认开启 / 默认关闭」必须与实际参数一致。
        val docLine = themeText.lines().firstOrNull { "[dynamicColor]" in it && "默认" in it }
        assertTrue(
            "Theme.kt 文件头注释应说明 dynamicColor 的默认行为（单一事实源：默认值在参数上，" +
                "注释必须与之一致）：\n  $themeFile",
            docLine != null,
        )
        val docSaysOn = docLine!!.contains("默认开启")
        val docSaysOff = docLine.contains("默认 **false**") || docLine.contains("默认关闭")

        val consistent = (on && docSaysOn) || (off && docSaysOff)
        assertTrue(
            "Theme.kt 的动态取色说明与实际默认值矛盾——文档写「默认开启/关闭」而参数是另一个值，" +
                "读者会信错那个。实际默认 ${if (on) "true" else "false"}。\n" +
                "  注释: ${docLine.trim()}\n  $themeFile",
            consistent,
        )
    }

    @Test
    fun `the window theme follows the system dark mode`() {
        val base = File(repoRoot(), "client/app/src/main/res/values/themes.xml")
        val night = File(repoRoot(), "client/app/src/main/res/values-night/themes.xml")
        assertTrue("values/themes.xml 不存在：${base.path}", base.isFile)

        // framework 的 android:Theme.Material 没有 DayNight 变体（那套只在 AppCompat 里），
        // 所以「跟随系统深色」只能靠资源限定符，不能靠换父主题。契约据此检查两类事实。
        assertTrue(
            "缺少 values-night/themes.xml：深色系统下的窗口背景与启动闪屏会沿用浅色主题，" +
                "而 Compose 只在首帧绘制后才接管——中间那一段就是刺眼的白屏。\n" +
                "注意 framework 的 android:Theme.Material 没有 DayNight 变体，" +
                "必须用资源限定符覆盖同名 style。",
            night.isFile,
        )
        assertTrue(
            "values-night/themes.xml 必须真的覆盖窗口背景（android:windowBackground），" +
                "否则深色系统下的闪屏仍是浅色：\n  ${night.path}",
            "android:windowBackground" in night.readText(),
        )
        assertTrue(
            "状态栏颜色不得写死品牌蓝字面量，否则深色系统下仍是那块亮蓝；应走透明，" +
                "让已 enableEdgeToEdge 的内容自己延伸：\n  ${base.path}",
            "#1565C0" !in base.readText(),
        )
    }

    @Test
    fun `typography and shape systems stay complete and consistent`() {
        val type = File(repoRoot(), "client/app/src/main/java/com/ldp/adskip/ui/theme/Type.kt")
        val theme = File(repoRoot(), "client/app/src/main/java/com/ldp/adskip/ui/theme/Theme.kt")
        assertTrue("Type.kt 不存在：${type.path}", type.isFile)

        // M3 的 Typography 要求全部 15 个角色，无法只定义用到的那些；
        // 把「未使用」的角色退回 Material 默认值等于降级（字号/字重/行高都会变），
        // 而不是精简。故契约固化「体系完整」这一有意的选择。
        val roles = listOf(
            "displayLarge", "displayMedium", "displaySmall",
            "headlineLarge", "headlineMedium", "headlineSmall",
            "titleLarge", "titleMedium", "titleSmall",
            "bodyLarge", "bodyMedium", "bodySmall",
            "labelLarge", "labelMedium", "labelSmall",
        )
        val typeText = type.readText()
        // 匹配 `角色名 = TextStyle(` —— 不用 \b（Kotlin 原始字符串里不转义反斜杠）。
        val missingRoles = roles.filterNot { role ->
            Regex("""$role\s*=\s*TextStyle\(""").containsMatchIn(typeText)
        }
        assertTrue(
            "字阶必须显式声明全部 15 个 M3 角色。只声明在用的几个会让其余角色退回 Material " +
                "默认值——同一次运行里出现两套字阶来源，正是本项目反复出现过的「多份真相」问题：\n" +
                missingRoles.joinToString("\n") { "  $it" },
            missingRoles.isEmpty(),
        )

        // 全项目零 <plurals>、也无自定义字体：字阶必须显式给出 lineHeightStyle，
        // 否则中文在多行场景下会因字体度量差异出现视觉重心偏移。
        assertTrue(
            "字阶应统一声明 lineHeightStyle（中文行高对齐是刻意设置，不是默认值）：\n  ${type.path}",
            "lineHeightStyle" in typeText,
        )

        val themeText = theme.readText()
        val slots = listOf("extraSmall", "small", "medium", "large", "extraLarge")
        val missingSlots = slots.filter { !Regex("""$it\s*=\s*RoundedCornerShape""").containsMatchIn(themeText) }
        assertTrue(
            "形状体系必须声明全部 5 个 M3 槽位。刻意只声明用到的几个会开出不一致的口子——" +
                "后续新增组件时，作者无法判断「该用哪个」与「为什么少一个」：\n" +
                missingSlots.joinToString("\n") { "  $it" },
            missingSlots.isEmpty(),
        )
    }

    // ---------- 工具 ----------

    private fun mainSources(): List<File> = File(
        repoRoot(),
        "client/app/src/main/java/com/ldp/adskip",
    )
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    private fun stripComments(text: String): String =
        text.replace(Regex("""/\*[\s\S]*?\*/"""), "").lines().joinToString("\n") { it.substringBefore("//") }

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            if (File(dir, ".gitignore").isFile && File(dir, "docs/README.md").isFile) return dir
            dir = dir.parentFile
        }
        error("未定位到仓库根（需同时含 .gitignore 与 docs/README.md）")
    }
}
