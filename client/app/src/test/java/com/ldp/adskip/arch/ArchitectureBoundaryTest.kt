package com.ldp.adskip.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 架构边界守护测试：扫描 `src/main/java` 源码的 import 关系，
 * 强制执行 ARCHITECTURE.md 第 2.1 节「边界契约」，违反即测试失败。
 *
 * 纯 JVM 源码扫描（不依赖 Android SDK），随 `testDebugUnitTest` 门禁运行——
 * 把边界从文档约定升级为 CI 可执行规则。
 */
class ArchitectureBoundaryTest {

    // ---------- engine：纯 JVM，保证引擎测试可在纯 JVM 运行、可拆分 module ----------

    @Test
    fun `engine depends only on itself and the JVM`() = assertNoBannedImports(
        pkgDir = "engine",
        banned = listOf("android.", "androidx.", "com.ldp.adskip."),
        allowed = listOf("com.ldp.adskip.engine.")
    )

    // ---------- core：零业务包依赖（事件总线/时钟/日志均为横切设施） ----------

    @Test
    fun `core depends on no other business package`() = assertNoBannedImports(
        pkgDir = "core",
        banned = listOf("com.ldp.adskip."),
        allowed = listOf("com.ldp.adskip.core.")
    )

    // ---------- ui：不直连 service / net / sync / 原始偏好 Prefs ----------

    @Test
    fun `ui never imports service net sync or raw prefs`() = assertNoBannedImports(
        pkgDir = "ui",
        banned = listOf(
            "com.ldp.adskip.service.",
            "com.ldp.adskip.net.",
            "com.ldp.adskip.sync.",
            "com.ldp.adskip.data.Prefs"
        )
    )

    // ---------- 下层永不反向依赖 ui（依赖倒置：经 StateFlow/组合根向上供值） ----------

    @Test
    fun `data never imports ui or service`() = assertNoBannedImports(
        pkgDir = "data",
        banned = listOf("com.ldp.adskip.ui.", "com.ldp.adskip.service.")
    )

    @Test
    fun `net never imports ui or service`() = assertNoBannedImports(
        pkgDir = "net",
        banned = listOf("com.ldp.adskip.ui.", "com.ldp.adskip.service.")
    )

    @Test
    fun `sync never imports ui or service`() = assertNoBannedImports(
        pkgDir = "sync",
        banned = listOf("com.ldp.adskip.ui.", "com.ldp.adskip.service.")
    )

    @Test
    fun `service never imports ui`() = assertNoBannedImports(
        pkgDir = "service",
        banned = listOf("com.ldp.adskip.ui.")
    )

    // ---------- 工具 ----------

    private fun assertNoBannedImports(pkgDir: String, banned: List<String>, allowed: List<String> = emptyList()) {
        val root = sourceRoot()
        val dir = File(root, pkgDir)
        assertTrue(
            "边界契约违规：源码目录不存在 $dir（目录结构变化需同步更新本测试与 ARCHITECTURE.md 2.1）",
            dir.isDirectory
        )

        val violations = mutableListOf<String>()
        dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                file.readLines().forEach { line ->
                    val imp = IMPORT_REGEX.find(line)?.groupValues?.get(1) ?: return@forEach
                    val bannedHit = banned.any { imp.startsWith(it) } &&
                        allowed.none { imp.startsWith(it) }
                    if (bannedHit) violations += "${file.nameWithoutExtension} → import $imp"
                }
            }

        assertTrue(
            "边界契约违规（ARCHITECTURE.md 第 2.1 节，包 $pkgDir/ 禁止以下依赖）：\n" +
                violations.joinToString("\n") + "\n" +
                "若确为合理演进，请先更新 ARCHITECTURE.md 边界契约与本测试规则。",
            violations.isEmpty()
        )
    }

    private fun sourceRoot(): File {
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val candidate = File(dir, "src/main/java/com/ldp/adskip")
            if (candidate.isDirectory) return candidate
            dir = dir.parentFile
        }
        error("未定位到 src/main/java/com/ldp/adskip：无法执行架构边界守护")
    }

    private companion object {
        val IMPORT_REGEX = Regex("""^import\s+([\w.]+)""")
    }
}