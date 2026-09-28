package com.ldp.adskip.arch

import com.ldp.adskip.device.VendorKeepAlive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 清单契约守护测试：把「代码里的系统入口」与「`AndroidManifest.xml` 的声明」绑在一起校验。
 *
 * 背景：`device/VendorKeepAlive.kt` 的保活入口依赖 `PackageManager` 解析外部包，
 * 而 Android 11（API 30）起未在 `<queries>` 声明的包**不可见**——
 * 漏声明不会编译报错、不会崩，只会让跳转静默失败（最难排查的一类故障）。
 * 同样，磁贴缺少 `BIND_QUICK_SETTINGS_TILE` 权限或 `QS_TILE` intent-filter 时系统根本不会加载它。
 *
 * 本测试把上述两条隐式约定升级为 CI 可执行规则，与 `ArchitectureBoundaryTest` 同为纯 JVM 源码扫描。
 */
class ManifestContractTest {

    private val manifest: String by lazy { readManifest() }

    // ---------- 快捷磁贴 ----------

    @Test
    fun `quick settings tile is declared with binding permission and intent filter`() {
        val block = tileServiceBlock()
        assertTrue(
            "磁贴必须声明 BIND_QUICK_SETTINGS_TILE，否则系统无法绑定",
            block.contains("android.permission.BIND_QUICK_SETTINGS_TILE"),
        )
        assertTrue(
            "磁贴必须声明 QS_TILE intent-filter，否则不会出现在可添加磁贴列表",
            block.contains("android.service.quicksettings.action.QS_TILE"),
        )
        assertTrue(
            "磁贴必须 exported=true（由系统绑定）",
            Regex("""android:exported\s*=\s*"true"""").containsMatchIn(block),
        )
        assertTrue("磁贴需提供图标", block.contains("@drawable/ic_qs_skip"))
        assertTrue("磁贴需提供本地化标题", block.contains("@string/tile_label"))
    }

    // ---------- 包可见性 ----------

    @Test
    fun `every vendor keep-alive package is visible via queries`() {
        val missing = VendorKeepAlive.allPackages().filterNot { manifest.contains("android:name=\"$it\"") }
        assertTrue(
            "以下保活入口包未在 AndroidManifest.xml 的 <queries> 中声明，Android 11+ 将无法解析：\n" +
                missing.joinToString("\n") +
                "\n（入口表见 device/VendorKeepAlive.kt，契约见本测试）",
            missing.isEmpty(),
        )
    }

    @Test
    fun `every vendor keep-alive action is visible via queries`() {
        val missing = VendorKeepAlive.allActions().filterNot { manifest.contains("android:name=\"$it\"") }
        assertTrue(
            "以下保活入口 action 未在 <queries> 中声明，Android 11+ 将无法解析：\n" +
                missing.joinToString("\n"),
            missing.isEmpty(),
        )
    }

    @Test
    fun `queries block exists`() {
        assertTrue("清单缺少 <queries> 声明块", manifest.contains("<queries>"))
    }

    // ---------- 广播不得越权泄露 ----------

    @Test
    fun `every broadcast is restricted to this app`() {
        val service = readMainSource("service/SkipAdService.kt")
        // 按「整段调用」而非按行匹配：sendBroadcast(\n  Intent(...)\n) 会跨行，
        // 逐行判断会把 setPackage 在下一行的合法调用误判为违规。
        val calls = broadcastCallSpans(service)
        assertTrue("未在 SkipAdService 中找到任何 sendBroadcast 调用", calls.isNotEmpty())

        val offenders = calls.filterNot { it.contains("setPackage(") }
        assertTrue(
            "存在未用 setPackage 收窄的 sendBroadcast（${offenders.size}/${calls.size} 处）：\n" +
                offenders.joinToString("\n") { "  ${it.replace(Regex("\\s+"), " ")}" } +
                "\n未收窄的隐式广播可被任意第三方应用注册同名 action 监听。" +
                "ACTION_SKIPPED 携带用户正在使用的应用名，泄露后果最严重。" +
                "\n修法：Intent(...).setPackage(packageName)，与 ACTION_REQUEST_SHUTDOWN 保持一致。",
            offenders.isEmpty(),
        )
    }

    // ---------- setPersisted 依赖 RECEIVE_BOOT_COMPLETED（易被误删） ----------

    @Test
    fun `boot permission is kept while a persisted job is used`() {
        val syncSource = readMainSource("sync/SyncJobService.kt")
        assertTrue(
            "未找到 .setPersisted( 调用；若同步调度已改为其他机制，请同步更新本测试与" +
                "ARCHITECTURE.md 的同步流程图",
            syncSource.contains(".setPersisted("),
        )
        assertTrue(
            "同步任务使用了 setPersisted(true)，但清单缺少 RECEIVE_BOOT_COMPLETED 权限。\n" +
                "JobInfo.Builder.setPersisted 标注了 @RequiresPermission(RECEIVE_BOOT_COMPLETED)，" +
                "缺少该权限时 JobScheduler.schedule() 会**静默失败**（返回 0），" +
                "表现为「设备重启后规则不再自动同步」——没有任何崩溃或日志。\n" +
                "注意：该权限由 JobScheduler 的持久化能力要求，与是否存在 BootReceiver 无关；" +
                "架构文档中「已删除 BootReceiver」不等于可以删除此权限。",
            manifest.contains("android.permission.RECEIVE_BOOT_COMPLETED"),
        )
    }

    // ---------- 工具 ----------

    /**
     * 提取源码中每一处 `sendBroadcast(...)` 的**完整调用片段**（含首尾括号）。
     *
     * 不用正则的原因：正则无法正确处理任意层级的括号嵌套
     * （如 `sendBroadcast(Intent(X).putExtra("k", compute(1)))` 会被漏检），
     * 也不会跳过字符串字面量里的括号。这里用括号配平扫描，支持任意嵌套。
     */
    private fun broadcastCallSpans(source: String): List<String> {
        val spans = mutableListOf<String>()
        var idx = 0
        while (idx < source.length) {
            val at = source.indexOf(CALL_NAME, idx)
            if (at < 0) break

            // 避免把 `mySendBroadcast` / `sendBroadcastXxx` 误判为调用
            val precededByIdent = at > 0 &&
                (source[at - 1].isLetterOrDigit() || source[at - 1] == '_')

            var open = at + CALL_NAME.length
            while (open < source.length && source[open].isWhitespace()) open++

            if (precededByIdent || open >= source.length || source[open] != '(') {
                idx = at + 1
                continue
            }
            val end = matchingParenEnd(source, open)
            if (end < 0) { // 未闭合：视为异常源码，继续向后扫描
                idx = at + 1
                continue
            }
            spans.add(source.substring(at, end))
            idx = end
        }
        return spans
    }

    /** 从 `openIndex`（指向 `(`）起做括号配平，返回匹配的 `)` 之后一位；未闭合返回 -1。 */
    private fun matchingParenEnd(source: String, openIndex: Int): Int {
        var depth = 0
        var i = openIndex
        while (i < source.length) {
            when (source[i]) {
                '(' -> depth++

                ')' -> {
                    depth--
                    if (depth == 0) return i + 1
                }

                // 跳过字符串字面量，避免其中的括号干扰配平（含反斜杠转义）
                '"', '\'' -> {
                    val quote = source[i]
                    i++
                    while (i < source.length && source[i] != quote) {
                        if (source[i] == '\\') i++
                        i++
                    }
                }
            }
            i++
        }
        return -1
    }

    private fun readMainSource(relative: String): String {
        val dir = File(System.getProperty("user.dir"), "src/main/java/com/ldp/adskip")
        assertTrue("未定位到 src/main/java/com/ldp/adskip：无法读取 $relative", dir.isDirectory)
        val file = File(dir, relative)
        assertTrue("未找到源文件 $relative", file.isFile)
        return file.readText()
    }

    private fun tileServiceBlock(): String {
        val start = manifest.indexOf(TILE_SERVICE)
        assertTrue(
            "清单未注册快捷磁贴服务 $TILE_SERVICE（device/SkipTileService.kt）",
            start >= 0,
        )
        val end = manifest.indexOf("</service>", start)
        assertTrue("磁贴 <service> 标签未闭合", end > start)
        return manifest.substring(start, end)
    }

    private fun readManifest(): String {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val candidate = File(dir, MANIFEST_RELATIVE_PATH)
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("未定位到 $MANIFEST_RELATIVE_PATH：无法执行清单契约守护")
    }

    private companion object {
        const val TILE_SERVICE = ".device.SkipTileService"
        const val MANIFEST_RELATIVE_PATH = "src/main/AndroidManifest.xml"
        // 匹配完整的 sendBroadcast(...) 调用体（允许一层嵌套括号），
        // 以便判断 setPackage 是否真的出现在这处调用里。

        /** sendBroadcast 调用名（配合括号配平扫描使用）。 */
        const val CALL_NAME = "sendBroadcast"
    }
}
