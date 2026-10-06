package com.ldp.adskip.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 电池优化豁免状态的契约。
 *
 * 背景（实测缺陷）：`SettingsViewModel.queryBatteryExempt()` 返回 `Boolean`，
 * 且 `pm?.isIgnoringBatteryOptimizations(...) ?: false` —— 取不到 `PowerManager`
 * 或查询抛异常时**返回 false**，与「确实没豁免」不可区分。于是已经豁免的用户
 * 会一直看到「允许后台运行」按钮，点进去系统却显示已允许。这与无障碍状态那条
 * 缺陷是同一类错误：**拿不确定冒充确定**。
 *
 * 另据规范，豁免状态还必须在用户从系统设置返回后刷新：跳转走的是系统页，
 * Activity 不重建，`LaunchedEffect(Unit)` 不会重跑。
 */
class BatteryExemptionContractTest {

    private val settingsViewModel =
        "client/app/src/main/java/com/ldp/adskip/ui/settings/SettingsViewModel.kt"

    @Test
    fun `battery exemption is tri-state so a failed query is not reported as not exempt`() {
        val status = File(
            repoRoot(),
            "client/app/src/main/java/com/ldp/adskip/device/BatteryExemption.kt",
        )
        assertTrue(
            "应存在 device/BatteryExemption.kt：把「已豁免 / 未豁免 / 无法确认」收敛成三态，" +
                "并让判定逻辑可被 JVM 单测覆盖。当前缺失：${status.path}",
            status.isFile,
        )
        val text = status.readText()
        assertTrue(
            "电池豁免必须区分「已确认未豁免」与「无法确认」：查询失败时报成未豁免，" +
                "会让已豁免的用户反复被引导去设置页，而系统那边显示已允许。\n  ${status.path}",
            "UNKNOWN" in text,
        )
    }

    @Test
    fun `the settings view model no longer models battery exemption as a plain boolean`() {
        val file = File(repoRoot(), settingsViewModel)
        assertTrue("$settingsViewModel 不存在", file.isFile)
        val code = stripComments(file.readText())

        assertTrue(
            "SettingsViewModel 的电池豁免状态不得再用 `batteryExempt: Boolean`——" +
                "Boolean 承载不了「无法确认」，正是缺陷的根源：\n  $settingsViewModel",
            "batteryExempt: Boolean" !in code,
        )
        assertTrue(
            "查询失败后不得再用 `?: false` 之类的默认值把状态判成未豁免：\n  $settingsViewModel",
            "?: false" !in code,
        )
    }

    @Test
    fun `battery exemption is re-checked when the screen resumes`() {
        // 用户从系统电池设置返回时 Activity 不重建，只在进屏读一次会留下过期状态。
        // 设置内容自己有 ViewModel，因此刷新点落在 SettingsContent 所在的文件里。
        val settings = File(
            repoRoot(),
            "client/app/src/main/java/com/ldp/adskip/ui/settings/SettingsScreen.kt",
        )
        assertTrue("SettingsScreen 不存在：${settings.path}", settings.isFile)
        val code = stripComments(settings.readText())

        assertTrue(
            "电池豁免是系统侧状态：用户点按钮跳去系统设置允许后再返回时，Activity 不重建，" +
                "只在进屏读一次的 LaunchedEffect(Unit) 不会重跑，按钮会一直停在「允许后台运行」。" +
                "必须在 ON_RESUME 时刷新：\n  ${settings.path}",
            "refreshBatteryStatus" in code,
        )
        assertTrue(
            "该刷新必须挂在 LifecycleEventEffect(ON_RESUME) 上，而不是 LaunchedEffect(Unit)：\n" +
                "  ${settings.path}",
            "LifecycleEventEffect" in code,
        )
        assertTrue(
            "不得再用 LaunchedEffect(Unit) 读电池状态（它只在首次进入组合时跑一次）：\n" +
                "  ${settings.path}",
            "LaunchedEffect(Unit) { viewModel.refreshBatteryStatus() }" !in code,
        )
    }

    // ---------- 工具 ----------

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
