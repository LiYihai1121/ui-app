package com.ldp.adskip.arch

import com.ldp.adskip.device.PermissionInspector
import com.ldp.adskip.device.PermissionKeys
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 权限状态中心的**接线契约**守护测试。
 *
 * 背景：本应用的权限状态分散在四个互不相关的系统机制里，而它们都**不能被应用收到变更通知**——
 * 用户在系统设置里改完，本进程一无所知，只能靠「回到前台就重查」。
 * 这条约定一旦被破坏，症状是「改了设置但界面不变」，**不崩溃、不报错、只是静默错误**，
 * 是最难在评审中被发现的一类回归。因此升级为 CI 可执行规则。
 */
class PermissionContractTest {

    // ---------- 回到前台必须重查 ----------

    @Test
    fun `profile page refreshes permissions on resume`() {
        val source = readMainSource("ui/profile/ProfileScreen.kt")
        assertTrue(
            "「我的」页未使用 LifecycleResumeEffect 重查权限状态。\n" +
                "权限只能在系统设置页被修改，应用收不到通知；返回前台时若不重查，\n" +
                "权限卡片会一直显示改动前的状态，用户反复跳转却看不到任何变化。\n" +
                "修法：LifecycleResumeEffect(Unit) { viewModel.refreshPermissions() }",
            source.contains("LifecycleResumeEffect"),
        )
        assertTrue(
            "「我的」页的重查回调未调用 refreshPermissions（可能重查了别的状态）",
            source.contains("viewModel.refreshPermissions()"),
        )
    }

    @Test
    fun `settings page refreshes battery status on resume`() {
        val source = readMainSource("ui/settings/SettingsScreen.kt")
        assertTrue(
            "设置页未使用 LifecycleResumeEffect 重查电池优化状态。\n" +
                "原先用 LaunchedEffect(Unit) 只在首次组合时读一次，而本内容内嵌在「我的」页的\n" +
                "LazyColumn 中、item 不会因跳转系统页而回收重组，导致用户改完返回后\n" +
                "按钮文案仍停在改动前。修法：与权限卡片统一改用 LifecycleResumeEffect。",
            source.contains("LifecycleResumeEffect"),
        )
        assertTrue(
            "设置页的重查回调未调用 refreshBatteryStatus",
            source.contains("viewModel.refreshBatteryStatus()"),
        )
    }

    // ---------- 跳转必须经 KeepAliveNavigator ----------

    @Test
    fun `profile page never assembles intents directly`() {
        val source = readMainSource("ui/profile/ProfileScreen.kt")
        val banned = listOf(
            "Intent(",
            "Settings.ACTION_",
            "startActivity",
            "startActivityForResult",
        )
        val hits = banned.filter { source.contains(it) }
        assertTrue(
            "「我的」页直接组装了系统跳转（${hits.joinToString()}）。\n" +
                "边界契约（ARCHITECTURE.md 2.1）：所有系统入口跳转统一经 device/KeepAliveNavigator，\n" +
                "由它负责包可见性探测与降级重试；UI 自行拼 Intent 会绕过降级，\n" +
                "在国产 ROM 上表现为「点了没反应」。",
            hits.isEmpty(),
        )
    }

    // ---------- 探测失败不得谎报 DENIED ----------

    @Test
    fun `inspector never hardcodes a denied verdict`() {
        val source = readMainSource("device/PermissionInspector.kt")
        // inspectVendorKeepAlive 恒返回 UNKNOWN：各厂商 ROM 均无公开的自启动查询 API，
        // 报 DENIED 会诱使用户在已开启的系统上反复跳转。
        val vendorFn = source.substringAfter("fun inspectVendorKeepAlive")
            .substringBefore("\n    }")
        assertTrue(
            "inspectVendorKeepAlive 必须返回 UNKNOWN（无公开查询 API，详见该函数注释）",
            vendorFn.contains("PermissionState.UNKNOWN"),
        )
        assertTrue(
            "inspectVendorKeepAlive 不得返回 DENIED",
            !vendorFn.contains("PermissionState.DENIED"),
        )
    }

    @Test
    fun `tile probe does not use hidden api`() {
        // 只扫代码、剔除注释后再判定：本测试与实现都在注释里提到这个 API 名，
        // 按原文 contains 会把「说明为什么不用它」也算成违规，测试随即自我否定。
        val code = stripComments(readMainSource("device/PermissionInspector.kt"))
        assertTrue(
            "TileService.queryTileServices 在 AOSP 中是 @hide、未进入公开 SDK，" +
                "用它无法通过编译。磁贴状态须走 Settings.Secure 的 sysui_qs_tiles。\n" +
                "若确需改用其他方式，请同步更新本测试与该方法的注释。",
            !code.contains("queryTileServices"),
        )
    }

    /** 去掉块注释与行注释，只留可编译代码。 */
    private fun stripComments(source: String): String {
        val noBlock = source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        return noBlock.lines().joinToString("\n") { it.substringBefore("//") }
    }

    // ---------- 权限项 key 集合稳定 ----------

    @Test
    fun `permission keys are unique and non empty`() {
        val keys = listOf(
            PermissionKeys.ACCESSIBILITY,
            PermissionKeys.BATTERY,
            PermissionKeys.VENDOR_KEEPALIVE,
            PermissionKeys.QUICK_TILE,
        )
        assertTrue("权限 key 不得为空", keys.none { it.isBlank() })
        assertTrue("权限 key 不得重复：${keys.groupingBy { it }.eachCount()}", keys.size == keys.toSet().size)
    }

    @Test
    fun `inspector covers every declared permission key`() {
        val source = readMainSource("device/PermissionInspector.kt")
        listOf(
            PermissionKeys.ACCESSIBILITY to "inspectAccessibility",
            PermissionKeys.BATTERY to "inspectBatteryExempt",
            PermissionKeys.VENDOR_KEEPALIVE to "inspectVendorKeepAlive",
            PermissionKeys.QUICK_TILE to "inspectQuickTileAdded",
        ).forEach { (key, probe) ->
            assertTrue(
                "inspectAll 未覆盖权限项 $key（缺少 $probe 的结果）",
                source.contains("$probe(context)"),
            )
        }
    }

    // ---------- 工具 ----------

    private fun readMainSource(relative: String): String {
        val dir = File(System.getProperty("user.dir"), "src/main/java/com/ldp/adskip")
        assertTrue("未定位到 src/main/java/com/ldp/adskip：无法读取 $relative", dir.isDirectory)
        val file = File(dir, relative)
        assertTrue("未找到源文件 $relative", file.isFile)
        return file.readText()
    }
}
