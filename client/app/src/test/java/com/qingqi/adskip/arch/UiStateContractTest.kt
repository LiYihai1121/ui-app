package com.qingqi.adskip.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 页面状态呈现契约守护（三态：空 / 加载 / 错误）。
 *
 * 背景：`UiContractTest` 管的是「间距标度、反馈通道、组件唯一实现」这类**写法**规则，
 * 但一个页面「该不该有错误态」它管不到。而实测现状是：
 * - `AppsViewModel` 的 `load()` 里 `queryIntentActivities` 无异常保护，抛异常会让协程
 *   中断、`loading` 永远停在 `true` —— 用户看到永久骨架屏，且没有任何提示；
 * - 全仓 UI 层**没有任何错误态组件**，`ErrorState` 零处，`isLoading` 零处。
 *
 * 于是本文件把「三态必须齐全」固化为可机器检查的契约。它是**负向测试**：
 * 断言「存在某个东西」，失败信息会指出是哪个文件缺了什么，便于照着补。
 *
 * 与 [UiContractTest] 相同的取舍：能查的部分交给机器，视觉层级仍靠评审。
 */
class UiStateContractTest {

    @Test
    fun `screens handling failures present them as a retryable error state`() {
        // 这里刻意读**原始文本**而不是 codeOf()：codeOf 的块注释剥离是懒惰匹配，
        // 当中文块注释与代码行相邻时可能把紧随其后的那一行一起吃掉，
        // 于是「明明有 ErrorState( 却判定为没有」——诊断时实测到过这个偏差。
        // 本契约关心的是「该文件是否引用了错误态」，注释里提到它并不构成假阳性风险。
        val withError = screens().filter { "ErrorState(" in File(repoRoot(), it).readText() }
        val noRetry = withError
            .map { File(repoRoot(), it) }
            .filter { "R.string.btn_retry" !in it.readText() }

        assertTrue(
            "以下页面渲染了错误态却没有提供重试动作。失败必须是可恢复的，只告知不给出路" +
                "等于把用户逼回上一屏：\n" + noRetry.joinToString("\n") { "  ${it.name}" },
            noRetry.isEmpty(),
        )
        assertTrue(
            "本契约的触发条件是「某个页面渲染了错误态」，当前没有任何页面渲染，断言会退化为恒真。" +
                "若确实移除了全部错误态，请一并删除本测试，而不是留一条永远通过的守护：\n" +
                "  repoRoot=${repoRoot()}",
            withError.isNotEmpty(),
        )
    }

    @Test
    fun `data loading view models do not leave a pending load unhandled`() {
        val offenders = viewModels().filter { relative ->
            val code = codeOf(relative).joinToString("\n")
            val loads = "load(" in code || "refresh" in code
            loads && "loading" in code && "error" !in code && "failed" !in code
        }

        assertTrue(
            "以下 ViewModel 会发起加载并暴露 loading，却没有任何加载失败的落点。" +
                "失败时它只能把 loading 永远留在 true，用户看到永远不消失的骨架屏，" +
                "既不知道出错也没有重试入口（这正是 AppsViewModel 曾经的缺陷）：\n" +
                offenders.joinToString("\n") { "  $it" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `state components live in the shared component library`() {
        val expected = listOf("EmptyState", "ErrorState")
        val missing = expected.filterNot { name ->
            File(appRoot(), "src/main/java/com/qingqi/adskip/ui/components")
                .listFiles()
                ?.any { it.isFile && "fun $name(" in it.readText() }
                ?: false
        }

        assertTrue(
            "状态类组件必须收口在 ui/components/ 下，否则各页面会各写一份，手感与文案都难以统一。" +
                "缺失：\n" + missing.joinToString("\n") { "  fun $it(" },
            missing.isEmpty(),
        )
    }

    @Test
    fun `error copy is declared in every locale`() {
        val resRoot = File(appRoot(), "src/main/res")
        val locales = resRoot.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("values") }
            ?.map { it.name }
            .orEmpty()
        assertTrue("未找到任何 values*/ 资源目录", locales.isNotEmpty())

        val required = listOf("error_generic_title", "btn_retry")
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
            "错误态文案必须在每个 locale 都存在（品牌与提示语只改一种语言是既有的返工来源）。缺失：\n" +
                missing.joinToString("\n") { "  $it" },
            missing.isEmpty(),
        )
    }

    @Test
    fun `title components expose heading semantics`() {
        val common = File(appRoot(), "src/main/java/com/qingqi/adskip/ui/components/Common.kt")
        assertTrue("页面组件文件不存在：${common.path}", common.isFile)
        val text = common.readText()

        // 用「函数名」而不是「函数名(」定位：签名可能被换行。
        val missing = listOf("fun PageHeader", "fun SectionTitle").filter { name ->
            val index = text.indexOf(name)
            if (index < 0) return@filter true
            // 取该函数声明到下一个顶层 fun 之间的片段，避免把别的函数的语义算进来。
            val rest = text.substring(index)
            val nextFun = rest.indexOf("\nfun ", startIndex = 1)
            val body = if (nextFun > 0) rest.substring(0, nextFun) else rest
            "heading()" !in body
        }

        assertTrue(
            "标题组件必须声明 heading() 语义，否则读屏无法按标题跳读，视障用户只能线性听完一屏：" +
                "本应用的关键操作（关键词开关、打开无障碍）都在长列表之后。缺失：\n" +
                missing.joinToString("\n") { "  $it" },
            missing.isEmpty(),
        )
    }

    @Test
    fun `accessible names are not announced twice`() {
        val main = File(appRoot(), "src/main/java/com/qingqi/adskip/ui/MainActivity.kt")
        assertTrue("MainActivity 不存在：${main.path}", main.isFile)
        val mainText = main.readText()
        val navStart = mainText.indexOf("NavigationBarItem(")
        assertTrue("未找到 NavigationBarItem(，契约失效：${main.path}", navStart >= 0)
        val navBody = mainText.substring(navStart)

        assertTrue(
            "底部导航的图标不得再挂与 label 相同的 contentDescription：下方 label 已渲染同一文案，" +
                "两边都挂会让读屏把每个 tab 名念两遍。图标应为 contentDescription = null：\n" +
                "  ${main.path} 的 NavigationBarItem 内出现了 contentDescription = stringResource",
            "contentDescription = stringResource" !in navBody,
        )

        val orb = File(appRoot(), "src/main/java/com/qingqi/adskip/ui/components/StatusOrb.kt")
        assertTrue("StatusOrb 不存在：${orb.path}", orb.isFile)
        assertTrue(
            "状态环是纯装饰，语义必须由相邻的状态文字承担。若在这里重新挂 contentDescription，" +
                "同一句「服务未开启 / 服务运行中」会在语义树里出现两次：\n  ${orb.path}",
            "clearAndSetSemantics" in orb.readText(),
        )
    }

    @Test
    fun `the wide screen width cap has a single implementation`() {
        val token = "UiSizes.contentMaxWidth"
        val canonical = "client/app/src/main/java/com/qingqi/adskip/ui/theme/Spacing.kt"
        val uiRoot = File(appRoot(), "src/main/java/com/qingqi/adskip/ui")
        val consumers = uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { token in it.readText() }
            .map { it.relativeTo(repoRoot()).path.replace('\\', '/') }
            .toList()

        assertTrue(
            "大屏内容封顶（$token）应只在 $canonical 定义一次，页面通过 " +
                "`Modifier.screenContentWidth()` 取用。多处写死会让平板/折叠屏下的版心" +
                "各自漂移。发现重复引用：\n" + consumers.joinToString("\n") { "  $it" },
            consumers.size == 1 && consumers.first() == canonical,
        )
    }

    // ---------- 工具 ----------

    /** 四个页面级 Composable 的源码路径（相对仓库根）。 */
    private fun screens(): List<String> = listOf(
        "client/app/src/main/java/com/qingqi/adskip/ui/home/HomeScreen.kt",
        "client/app/src/main/java/com/qingqi/adskip/ui/apps/AppsScreen.kt",
        "client/app/src/main/java/com/qingqi/adskip/ui/logs/LogsScreen.kt",
        "client/app/src/main/java/com/qingqi/adskip/ui/settings/SettingsScreen.kt",
    ).filter { File(repoRoot(), it).isFile }

    private fun viewModels(): List<String> {
        val dir = File(appRoot(), "src/main/java/com/qingqi/adskip/ui")
        return dir.walkTopDown()
            .filter { it.isFile && it.name.endsWith("ViewModel.kt") }
            .map { it.relativeTo(repoRoot()).path.replace('\\', '/') }
            .toList()
    }

    /** 剥掉注释后的代码行：契约不该被注释里的示例文本触发。 */
    private fun codeOf(relativePath: String): List<String> {
        val text = File(repoRoot(), relativePath).readText()
        return text
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .lines()
            .map { it.substringBefore("//") }
            .filter { it.isNotBlank() }
    }

    private fun appRoot(): File = File(repoRoot(), "client/app")

    private fun repoRoot(): File {
        var dir: File? = System.getProperty("user.dir")?.let { File(it) }
        while (dir != null) {
            if (File(dir, ".gitignore").isFile && File(dir, "docs/README.md").isFile) return dir
            dir = dir.parentFile
        }
        error("未定位到仓库根（需同时含 .gitignore 与 docs/README.md）")
    }
}
