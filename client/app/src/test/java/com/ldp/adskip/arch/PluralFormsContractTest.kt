package com.ldp.adskip.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 数量文案必须支持单复数契约。
 *
 * 背景（实测缺陷）：项目全量为零 `<plurals>`，所有计数文案都是普通 `<string>` + `%1$d`。
 * 英文 locale 因此会产出 `Skipped 1 times`、`1 records`、`Auto-close in 1 seconds`
 * 这类语法错误——中文没有单复数，所以中文侧永远看不出问题，但这是英文用户的可见缺陷。
 *
 * Android 的 `<plurals>` 由系统按当前 locale 的数量规则选择分支；中文只需 `other`，
 * 英文需 `one` / `other`。本契约把「计数文案必须走 plurals」固化为门禁，
 * 防止后续新增计数文案时又退回普通 string。
 */
class PluralFormsContractTest {

    /** 已知的计数文案：key → 它为什么是数量相关的。 */
    private val countedKeys = listOf(
        "apps_count",
        "logs_subtitle",
        "stats_total_short",
        "fake_ad_countdown",
    )

    @Test
    fun `counted copy is declared as plurals in every locale`() {
        val locales = localeDirs()
        assertTrue("未找到任何声明了 strings.xml 的 values*/ 目录", locales.isNotEmpty())

        val problems = mutableListOf<String>()
        locales.forEach { locale ->
            val xml = File(locale, "strings.xml")
            val text = xml.readText()
            countedKeys.forEach { key ->
                if (!Regex("""<plurals\s+name="$key"\s*>""").containsMatchIn(text)) {
                    problems += "${locale.name}/$key 未声明为 <plurals>"
                }
            }
        }

        assertTrue(
            "以下计数文案仍是普通 <string>。中文没有单复数所以看不出问题，但英文会产出 " +
                "`Skipped 1 times` 这类语法错误。请改为 <plurals>（中文只需 other，" +
                "英文需 one/other）：\n" + problems.joinToString("\n") { "  $it" },
            problems.isEmpty(),
        )
    }

    @Test
    fun `english plurals declare both one and other`() {
        val en = File(repoRoot(), "client/app/src/main/res/values-en/strings.xml")
        assertTrue("values-en/strings.xml 不存在：${en.path}", en.isFile)
        val text = en.readText()

        val problems = mutableListOf<String>()
        countedKeys.forEach { key ->
            val block = Regex("""<plurals\s+name="$key"\s*>([\s\S]*?)</plurals>""")
                .find(text)
                ?.groupValues
                ?.get(1)
            if (block == null) {
                problems += "$key：未找到 <plurals> 块"
                return@forEach
            }
            // 英文的数量规则：1 走 one，其余走 other。缺任一条都会让某一侧读到错误文案。
            listOf("one", "other").forEach { q ->
                if (!Regex("""<item\s+quantity="$q"\s*>""").containsMatchIn(block)) {
                    problems += "$key：缺少 quantity=\"$q\""
                }
            }
        }

        assertTrue(
            "英文 plurals 必须同时声明 one 与 other（Android 按数量规则择一，缺哪条哪一侧就会错）：\n" +
                problems.joinToString("\n") { "  $it" },
            problems.isEmpty(),
        )
    }

    @Test
    fun `counted copy is read through pluralStringResource`() {
        val uiRoot = File(repoRoot(), "client/app/src/main/java/com/ldp/adskip/ui")
        val offenders = uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val code = stripComments(file.readText())
                // 仍用 stringResource 读计数文案：数字会按 string 而非 plurals 解析。
                countedKeys.any { Regex("""stringResource\(R\.string\.$it\b""").containsMatchIn(code) }
            }
            .map { it.relativeTo(repoRoot()).path.replace('\\', '/') }
            .toList()

        assertTrue(
            "以下文件仍用 stringResource 读取计数文案，必须改为 pluralStringResource" +
                "（否则 plurals 形同虚设，单复数不会生效）：\n" +
                offenders.joinToString("\n") { "  $it" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `string arrays are localized in every locale`() {
        val locales = localeDirs()
        val arrayNames = listOf("default_keywords", "default_view_ids")

        val problems = mutableListOf<String>()
        locales.forEach { locale ->
            val text = File(locale, "strings.xml").readText()
            arrayNames.forEach { name ->
                if (!Regex("""<string-array\s+name="$name"\s*>""").containsMatchIn(text)) {
                    problems += "${locale.name}/$name 未声明"
                }
            }
        }

        assertTrue(
            "以下 string-array 未在每个 locale 声明。缺失时资源系统回落到默认 locale，" +
                "英文用户会拿到中文关键词（如「跳过」），那是无效的匹配文本：\n" +
                problems.joinToString("\n") { "  $it" },
            problems.isEmpty(),
        )

        // 英文数组不得含 CJK：那是「数组内容没本地化」的直接证据。
        val en = File(repoRoot(), "client/app/src/main/res/values-en/strings.xml")
        if (en.isFile) {
            val enText = en.readText()
            val cjkInArray = arrayNames.filter { name ->
                val block = Regex("""<string-array\s+name="$name"\s*>([\s\S]*?)</string-array>""")
                    .find(enText)
                    ?.groupValues
                    ?.get(1)
                    .orEmpty()
                Regex("""[\u4e00-\u9fff]""").containsMatchIn(block)
            }
            assertTrue(
                "英文 string-array 里出现了中文字符，说明数组只是把默认值复制过去而没有本地化：\n" +
                    cjkInArray.joinToString("\n") { "  values-en/$it" },
                cjkInArray.isEmpty(),
            )
        }
    }

    // ---------- 工具 ----------

    private fun localeDirs(): List<File> {
        val resRoot = File(repoRoot(), "client/app/src/main/res")
        return resRoot.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values") }
            ?.filter { File(it, "strings.xml").isFile }
            .orEmpty()
    }

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
