package com.ldp.adskip.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 无障碍服务状态的**单一真值源**守护。
 *
 * 背景（实测缺陷）：无障碍状态在代码里同时存在三份表示——
 * 1. `SkipAdService.isEnabled(context)`：查系统「已启用的无障碍服务」列表，**权威**；
 * 2. `SkipAdService.running`：进程内 `@Volatile` 快照，由 `onServiceConnected` /
 *    `onDestroy` 驱动，进程被杀或用户在系统设置里关掉而回调未到时会**过期**；
 * 3. `AppEvents.serviceRunning`：UI 唯一订阅的那份，由 (2) 派生。
 *
 * 于是出现可复现的不一致：用户在系统设置里关掉无障碍，首页与「我的」页仍显示
 * 「服务运行中」，而快捷磁贴（读 (1)）已经显示「已停止」。同一台设备两个说法。
 *
 * 本契约把「谁是权威」固化为可机器检查的规则，防止修好之后又被改回去。
 */
class AccessibilityStatusContractTest {

    /** 查询系统已启用列表的那个方法名：权威来源的唯一实现点。 */
    private val authoritativeQuery = "getEnabledAccessibilityServiceList"

    @Test
    fun `the system accessibility list is the single authoritative source`() {
        val readers = mainSources().filter { authoritativeQuery in it.readText() }

        assertTrue(
            "查询系统已启用无障碍服务列表的代码必须只有一处。多处查询会让「服务到底开没开」" +
                "在不同页面得到不同答案——这正是本契约要防的缺陷。发现 ${readers.size} 处：\n" +
                readers.joinToString("\n") { "  ${it.relativeTo(repoRoot()).path.replace('\\', '/')}" },
            readers.size == 1,
        )

        // 查询落在哪一层由依赖规则决定：ui/ 不能 import service/（依赖倒置），
        // 因此权威查询只能在 device/（device 可被 ui 与 service 双方使用）。
        // 这里只固化「唯一」，不固化具体路径，避免把架构约束写死成脆弱的路径断言。
        val reader = readers.first().relativeTo(repoRoot()).path.replace('\\', '/')
        assertTrue(
            "权威查询不应出现在 ui/ 层（ui 只呈现状态，不查系统）：$reader",
            !reader.startsWith("client/app/src/main/java/com/ldp/adskip/ui/"),
        )
    }

    @Test
    fun `accessibility status never silently reports off when the query fails`() {
        val status = File(
            repoRoot(),
            "client/app/src/main/java/com/ldp/adskip/device/AccessibilityStatus.kt",
        )
        assertTrue(
            "应存在 device/AccessibilityStatus.kt：把「系统真值 / 进程信号 / 查询失败」" +
                "三种输入收敛成一个纯函数，使其可被 JVM 单测覆盖。当前缺失：${status.path}",
            status.isFile,
        )
        val text = status.readText()

        assertTrue(
            "查询失败（系统异常）时不得把状态直接判为「未开启」——那会让已经开启服务的用户" +
                "被反复引导去设置页，而状态永远不变。必须区分「已确认关闭」与「无法确认」：\n" +
                "  ${status.path}",
            "UNKNOWN" in text,
        )
        assertTrue(
            "进程内信号只能作为**兜底**，不能覆盖系统真值。判定函数必须显式表达这一优先级：\n" +
                "  ${status.path}",
            "systemEnabled" in text && "processSignal" in text,
        )
    }

    @Test
    fun `both status screens read the effective status rather than the raw process signal`() {
        // 首页与「我的」页都渲染服务状态；两处都必须走同一个「有效状态」出口，
        // 否则会出现「首页说开着、我的页说关着」。
        val consumers = listOf(
            "ui/home/HomeViewModel.kt",
            "ui/profile/ProfileViewModel.kt",
        )
        val offenders = consumers.filter { rel ->
            val file = File(repoRoot(), "client/app/src/main/java/com/ldp/adskip/$rel")
            if (!file.isFile) return@filter true
            val code = stripComments(file.readText())
            // 只订阅原始进程信号、从不取系统真值的，即为漏网。
            "AccessibilityStatus" !in code
        }

        assertTrue(
            "以下 ViewModel 仍直接用进程内信号作为服务状态，未取系统真值——" +
                "用户在系统设置里关掉服务后会看到过期的「运行中」：\n" +
                offenders.joinToString("\n") { "  $it" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the declared service class name matches the manifest`() {
        val status = File(
            repoRoot(),
            "client/app/src/main/java/com/ldp/adskip/device/AccessibilityStatus.kt",
        )
        val declared = Regex("""SKIP_AD_SERVICE_CLASS_NAME\s*=\s*"([^"]+)"""")
            .find(status.readText())
            ?.groupValues
            ?.get(1)

        assertTrue(
            "AccessibilityStatus 必须声明 SKIP_AD_SERVICE_CLASS_NAME（ui/ 层不能 import service/，" +
                "只能经此常量告知「查哪个服务」）：\n  ${status.path}",
            declared != null,
        )
        val serviceClassName = declared ?: return

        val manifest = File(repoRoot(), "client/app/src/main/AndroidManifest.xml").readText()
        // Manifest 用相对写法（`.service.SkipAdService`），常量是全限定名，两种形式都要认。
        val suffix = serviceClassName.removePrefix("com.ldp.adskip")
        assertTrue(
            "常量值（$serviceClassName）与 AndroidManifest 中声明的无障碍服务不一致" +
                "（推导出的相对名后缀 = [$suffix]，manifest 含该后缀 = ${manifest.contains(suffix)}）。" +
                "改名而漏改常量，会让状态查询永远落空、服务状态恒为「未开启」：\n" +
                "  client/app/src/main/AndroidManifest.xml",
            manifest.contains(serviceClassName) || manifest.contains(suffix),
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
