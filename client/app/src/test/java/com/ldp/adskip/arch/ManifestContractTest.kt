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
            block.contains("android.permission.BIND_QUICK_SETTINGS_TILE")
        )
        assertTrue(
            "磁贴必须声明 QS_TILE intent-filter，否则不会出现在可添加磁贴列表",
            block.contains("android.service.quicksettings.action.QS_TILE")
        )
        assertTrue(
            "磁贴必须 exported=true（由系统绑定）",
            Regex("""android:exported\s*=\s*"true"""").containsMatchIn(block)
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
            missing.isEmpty()
        )
    }

    @Test
    fun `every vendor keep-alive action is visible via queries`() {
        val missing = VendorKeepAlive.allActions().filterNot { manifest.contains("android:name=\"$it\"") }
        assertTrue(
            "以下保活入口 action 未在 <queries> 中声明，Android 11+ 将无法解析：\n" +
                missing.joinToString("\n"),
            missing.isEmpty()
        )
    }

    @Test
    fun `queries block exists`() {
        assertTrue("清单缺少 <queries> 声明块", manifest.contains("<queries>"))
    }

    // ---------- 工具 ----------

    private fun tileServiceBlock(): String {
        val start = manifest.indexOf(TILE_SERVICE)
        assertTrue(
            "清单未注册快捷磁贴服务 $TILE_SERVICE（device/SkipTileService.kt）",
            start >= 0
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
    }
}
