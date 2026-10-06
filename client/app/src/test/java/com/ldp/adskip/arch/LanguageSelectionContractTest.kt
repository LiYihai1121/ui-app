package com.ldp.adskip.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 应用内语言选择契约。
 *
 * 本项目此前没有语言开关：minSdk 26 / targetSdk 35，且宿主是纯 `ComponentActivity`
 * （无 AppCompat），所以「固定语言」必须自己实现，且有两条路径——
 * API 33+ 用平台的 `LocaleManager.setApplicationLocales`，API 26–32 只能靠
 * 在 `attachBaseContext` 里包一层 Context。两条路径都容易只做一条。
 *
 * 本契约固化三件容易做错的事：
 * 1. **「跟随系统」必须映射为空 LocaleList**——传具体 locale 会把它钉死，
 *    用户再选「跟随系统」也不会复原；
 * 2. 用平台 API 必须带版本判断，否则 API 32 及以下直接崩；
 * 3. 语言文案必须在每个 locale 齐备。
 */
class LanguageSelectionContractTest {

    private val applier = "client/app/src/main/java/com/ldp/adskip/device/LocaleApplier.kt"
    private val modeFile = "client/app/src/main/java/com/ldp/adskip/device/LanguageMode.kt"

    @Test
    fun `language mode covers system chinese and english`() {
        val file = File(repoRoot(), modeFile)
        assertTrue(
            "应存在 device/LanguageMode.kt：把语言选择建模成可单测的枚举，并给出到 LocaleList 的纯映射。\n" +
                "当前缺失：${file.path}",
            file.isFile,
        )
        val text = file.readText()

        assertTrue(
            "语言模式必须含「跟随系统」与两种具体语言（中文 / 英文）：\n  ${file.path}",
            "SYSTEM" in text && "CHINESE" in text && "ENGLISH" in text,
        )
        assertTrue(
            "「跟随系统」必须不携带语言标签（`SYSTEM(null)`），调用方据此清除应用级覆盖。" +
                "给它一个具体 tag 会把语言钉死，用户再选「跟随系统」时不会复原：\n  ${file.path}",
            "SYSTEM(null)" in text,
        )
        assertTrue(
            "语言标签必须显式声明（zh-CN / en），不要在调用处拼字符串：\n  ${file.path}",
            "\"zh-CN\"" in text && "\"en\"" in text,
        )
    }

    @Test
    fun `the platform locale api is guarded by a version check`() {
        val file = File(repoRoot(), applier)
        assertTrue(
            "应存在 device/LocaleApplier.kt：负责把语言选择落到运行时。\n当前缺失：${file.path}",
            file.isFile,
        )
        val text = stripComments(file.readText())

        assertTrue(
            "必须使用平台 LocaleManager.setApplicationLocales 作为主路径：\n  ${file.path}",
            "setApplicationLocales" in text,
        )
        assertTrue(
            "「跟随系统」必须走空 LocaleList（平台用它表示清除应用级覆盖），" +
                "否则用户切回「跟随系统」不会复原：\n  ${file.path}",
            "getEmptyLocaleList" in text,
        )
        assertTrue(
            "平台 API 需要 API 33（TIRAMISU）。缺少版本判断会让 API 32 及以下直接崩——" +
                "本项目 minSdk 26，这是必须覆盖的区间：\n  ${file.path}",
            "TIRAMISU" in text && ("SDK_INT" in text),
        )
        assertTrue(
            "低版本必须回落到 attachBaseContext 包装 Context，否则 API 26–32 上选语言无效：\n  ${file.path}",
            "wrapContext" in text || "attachBaseContext" in text,
        )
    }

    @Test
    fun `language copy is declared in every locale`() {
        val resRoot = File(repoRoot(), "client/app/src/main/res")
        val locales = resRoot.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values") }
            ?.filter { File(it, "strings.xml").isFile }
            ?.map { it.name }
            .orEmpty()
        assertTrue("未找到任何声明了 strings.xml 的 values*/ 目录", locales.isNotEmpty())

        val required = listOf(
            "settings_language_section",
            "settings_language_label",
            "settings_language_hint",
            "language_system",
            "language_zh",
            "language_en",
        )
        val missing = mutableListOf<String>()
        locales.forEach { locale ->
            val text = File(File(resRoot, locale), "strings.xml").readText()
            required.forEach { key ->
                if ("<string name=\"$key\">" !in text) missing += "$locale/$key"
            }
        }

        assertTrue(
            "语言相关文案必须在每个 locale 齐备：\n" + missing.joinToString("\n") { "  $it" },
            missing.isEmpty(),
        )
    }

    @Test
    fun `the ui reads and writes the language through the settings facade`() {
        val vm = File(
            repoRoot(),
            "client/app/src/main/java/com/ldp/adskip/ui/settings/SettingsViewModel.kt",
        )
        assertTrue("SettingsViewModel 不存在：${vm.path}", vm.isFile)
        val code = stripComments(vm.readText())

        assertTrue(
            "设置页必须能读写语言选择：\n  ${vm.path}",
            "LanguageMode" in code && ("setLanguage" in code || "language" in code),
        )
        // ui 层禁止直接 import data.Prefs（边界契约），语言选择同样要走门面。
        assertTrue(
            "ui 层不得直接读写 Prefs，语言选择也要经 SettingsRepository 门面：\n  ${vm.path}",
            "data.Prefs" !in code,
        )
    }

    // ---------- 工具 ----------

    private fun stripComments(text: String): String =
        text.replace(Regex("""/\*[\s\S]*?\*/"""), "").lines().joinToString("\n") { it.substringBefore("//") }

    private fun repoRoot(): File {
        var dir: File? = System.getProperty("user.dir")?.let { File(it) }
        while (dir != null) {
            if (File(dir, ".gitignore").isFile && File(dir, "docs/README.md").isFile) return dir
            dir = dir.parentFile
        }
        error("未定位到仓库根（需同时含 .gitignore 与 docs/README.md）")
    }
}
