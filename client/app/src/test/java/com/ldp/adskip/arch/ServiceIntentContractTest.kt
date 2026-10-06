package com.ldp.adskip.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Service 广播 action 字面量的单一真值源守护。
 *
 * 背景：`ui/` 层被 [ArchitectureBoundaryTest] 禁止 import `service/`（依赖倒置），
 * 因此 SettingsViewModel 触发「导出界面快照」时只能用 action 字符串字面量，
 * 与 `service/SkipAdService.kt` 声明的常量分处两处——字面量一旦漂移，
 * 按钮点击后广播无人接收、用户毫无反馈。本契约把「两处必须一致」固化为可执行规则。
 */
class ServiceIntentContractTest {

    @Test
    fun `snapshot export action matches the service declaration`() {
        val serviceFile = File(
            repoRoot(),
            "client/app/src/main/java/com/ldp/adskip/service/SkipAdService.kt",
        )
        val declared = Regex("""ACTION_EXPORT_SNAPSHOT\s*=\s*"([^"]+)"""")
            .find(serviceFile.readText())
            ?.groupValues
            ?.get(1)

        assertTrue(
            "SkipAdService 必须声明 ACTION_EXPORT_SNAPSHOT（快照导出广播的唯一真值源）：\n" +
                "  ${serviceFile.path}",
            declared != null,
        )
        val action = declared ?: return

        val vm = File(
            repoRoot(),
            "client/app/src/main/java/com/ldp/adskip/ui/settings/SettingsViewModel.kt",
        ).readText()
        assertTrue(
            "SettingsViewModel 触发快照导出时必须使用与 service 声明的常量相同的字面量。\n" +
                "service 声明的 action = $action，而 SettingsViewModel 中未找到该字面量。\n" +
                "漂移的后果：按钮点击后广播无人接收，用户毫无反馈。",
            vm.contains("\"$action\""),
        )
    }

    // ---------- 工具 ----------

    private fun repoRoot(): File {
        var dir: File? = System.getProperty("user.dir")?.let { File(it) }
        while (dir != null) {
            if (File(dir, ".gitignore").isFile && File(dir, "docs/README.md").isFile) return dir
            dir = dir.parentFile
        }
        error("未定位到仓库根（需同时含 .gitignore 与 docs/README.md）")
    }
}
