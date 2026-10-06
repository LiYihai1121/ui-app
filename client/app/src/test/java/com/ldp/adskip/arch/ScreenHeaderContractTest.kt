package com.ldp.adskip.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 一级页面的信息架构契约。
 *
 * 背景（实测缺陷）：底部导航的四个一级页面里，**只有首页没有页面标题**——
 * 其余三屏都渲染 `PageHeader`。用户从导航切到首页后没有任何「我在哪」的锚点，
 * 而首页恰恰承载了最关键的状态结论（服务开没开），没有标题会让整屏读起来
 * 像一段浮在空中的仪表盘。
 *
 * 本契约把「每个一级页面都要有标题」固化为可机器检查的规则。与
 * `ProjectStructureTest` 的路由三方对齐形成互补：那边保证「页面进得去」，
 * 这边保证「进去之后知道自己在哪里」。
 */
class ScreenHeaderContractTest {

    /** 底部导航的四个一级页面（与 Routes 一致）。 */
    private val screens = listOf(
        "home/HomeScreen.kt",
        "apps/AppsScreen.kt",
        "logs/LogsScreen.kt",
        "profile/ProfileScreen.kt",
    )

    @Test
    fun `every top level screen renders a page header`() {
        val missing = screens.filterNot { relative ->
            val file = File(uiRoot(), relative)
            file.isFile && "PageHeader(" in file.readText()
        }

        assertTrue(
            "以下一级页面没有页面标题 PageHeader。底部导航切过去后用户没有任何位置锚点，" +
                "而首屏本就要靠标题说明「这一屏是什么」：\n" +
                missing.joinToString("\n") { "  ui/$it" },
            missing.isEmpty(),
        )
    }

    @Test
    fun `page titles are declared in every locale`() {
        val resRoot = File(repoRoot(), "client/app/src/main/res")
        val locales = resRoot.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values") }
            ?.map { it.name }
            .orEmpty()
        assertTrue("未找到任何 values*/ 资源目录", locales.isNotEmpty())

        // 每个一级页面一个标题文案，与 nav_*（导航栏短标签）分开：
        // 导航标签受限于栏宽（「首页」两字），页面标题可以更完整。
        val required = listOf("home_title", "apps_title", "logs_title", "profile_title")
        val missing = mutableListOf<String>()
        locales.forEach { locale ->
            val xml = File(resRoot, "$locale/strings.xml")
            if (!xml.isFile) return@forEach
            val text = xml.readText()
            required.forEach { key ->
                if ("<string name=\"$key\">" !in text) missing += "$locale/$key"
            }
        }

        assertTrue(
            "一级页面的标题文案必须在每个 locale 都存在（只改一种语言是本仓库发生过的返工）：\n" +
                missing.joinToString("\n") { "  $it" },
            missing.isEmpty(),
        )
    }

    // ---------- 工具 ----------

    private fun uiRoot(): File = File(repoRoot(), "client/app/src/main/java/com/ldp/adskip/ui")

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            if (File(dir, ".gitignore").isFile && File(dir, "docs/README.md").isFile) return dir
            dir = dir.parentFile
        }
        error("未定位到仓库根（需同时含 .gitignore 与 docs/README.md）")
    }
}
