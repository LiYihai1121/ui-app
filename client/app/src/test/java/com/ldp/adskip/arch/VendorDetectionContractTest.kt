package com.ldp.adskip.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 厂商（ROM）识别的单一入口契约。
 *
 * 背景（实测缺陷）：识别有两条路径且容错能力不同——
 * - `KeepAliveNavigator.detectVendor()`：包了 try/catch，失败记 [LogRing] 并兜底 GENERIC；
 * - `ProfileViewModel` 直调 `VendorKeepAlive.detect()`：无异常保护、无日志。
 *
 * 于是同一个「当前设备是什么 ROM」的问题，在设置页与「我的」页可能得到不同结果，
 * 且从「我的」页那条路失败时**没有任何痕迹**可查。与无障碍状态那条缺陷同源：
 * 同一事实存在多份实现，早晚不一致。
 */
class VendorDetectionContractTest {

    private val uiRoot = "client/app/src/main/java/com/ldp/adskip/ui"

    @Test
    fun `ui layer reads the vendor through the guarded navigator entry point`() {
        val offenders = uiSources().filter { file ->
            val code = stripComments(file.readText())
            // ui 层不得直调裸识别函数：它没有异常保护，也没走统一日志。
            Regex("""VendorKeepAlive\.detect\s*\(""").containsMatchIn(code)
        }

        assertTrue(
            "ui/ 层必须经 KeepAliveNavigator.detectVendor() 取厂商（含 try/catch 与日志兜底），" +
                "不得直调 VendorKeepAlive.detect()。两条路径的容错能力不同，同一个问题会在" +
                "不同页面得到不同答案，且失败时无迹可查：\n" +
                offenders.joinToString("\n") { "  ${it.relativeTo(repoRoot()).path.replace('\\', '/')}" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the guarded entry point keeps its exception handling`() {
        val navigator = File(
            repoRoot(),
            "client/app/src/main/java/com/ldp/adskip/device/KeepAliveNavigator.kt",
        )
        assertTrue("KeepAliveNavigator 不存在：${navigator.path}", navigator.isFile)
        val code = stripComments(navigator.readText())

        val index = code.indexOf("fun detectVendor")
        assertTrue("KeepAliveNavigator 必须提供 detectVendor()：${navigator.path}", index >= 0)
        val body = code.substring(index, minOf(code.length, index + 400))

        assertTrue(
            "detectVendor() 必须保留异常保护并兜底到 GENERIC：Build 读取在个别 ROM 上可能失败，" +
                "无保护会让「我的」页整页崩掉：\n  ${navigator.path}",
            "try" in body && "GENERIC" in body && "catch" in body,
        )
        assertTrue(
            "识别失败必须留下日志，否则线上无法判断用户设备为何走了通用兜底：\n  ${navigator.path}",
            "LogRing" in body,
        )
    }

    // ---------- 工具 ----------

    private fun uiSources(): List<File> = File(repoRoot(), uiRoot)
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
