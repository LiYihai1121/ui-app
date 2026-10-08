package com.ldp.adskip.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 安全存储契约：把「存储收口」与「迁移期依赖隔离」从文档升级为门禁。
 *
 * 背景（两起真实事故）：
 * 1. `LanguagePreferences` 与 `data/Prefs` 曾共用同一个明文 SharedPreferences
 *    文件名，Prefs 迁移清源时把语言设置连带销毁（同名互踩、数据丢失）；
 * 2. `androidx.security:security-crypto` 已废弃，若散落引用会让迁移期依赖
 *    永远清不掉。
 *
 * 因此：
 * - **存储收口**：除 SecureStore 与迁移模块外，任何代码不得直接开
 *   `getSharedPreferences`——所有偏好经单一加密存储，杜绝第二份真相与同名互踩；
 * - **迁移期依赖隔离**：`androidx.security` 只允许出现在 `core/LegacyEncryptedPrefsMigration.kt`
 *   （一次性读取旧 Tink 格式数据），下个版本删除该依赖时只需动一个文件。
 *
 * 扫描口径：只看**代码行**（注释里提到旧库名字是合法的因果说明，不算引用）；
 * 路径用 `/` 统一（Windows 的 File.path 会返回反斜杠，直接比较会全量误判）。
 */
class SecureStorageContractTest {

    @Test
    fun `legacy crypto dependency is confined to the migration module`() {
        val allowed = setOf("core/LegacyEncryptedPrefsMigration.kt")
        val offenders = sourceFiles()
            .filter { file -> codeLines(file).any { it.contains("androidx.security") } }
            .map { relative(it) }
            .filterNot { it in allowed }
        assertTrue(
            "androidx.security（已废弃库）只允许出现在 core/LegacyEncryptedPrefsMigration.kt（迁移期依赖，" +
                "下版本移除）；以下文件仍在引用：\n" + offenders.joinToString("\n") { "  $it" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun `all preference storage goes through SecureStore`() {
        val allowed = setOf(
            "core/SecureStore.kt",
            "core/LegacyEncryptedPrefsMigration.kt",
        )
        val offenders = sourceFiles()
            .filter { file -> codeLines(file).any { it.contains("getSharedPreferences(") } }
            .map { relative(it) }
            .filterNot { it in allowed }
        assertTrue(
            "偏好存储必须收口到 core/SecureStore（其余文件直接开 SharedPreferences 会造成同名互踩/" +
                "明文回潮）；以下文件违规：\n" + offenders.joinToString("\n") { "  $it" },
            offenders.isEmpty(),
        )
    }

    // ---------- 工具 ----------

    private fun sourceFiles(): List<File> {
        val root = File(mainSourceRoot(), "com/ldp/adskip")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /** 代码行 = 全文去掉注释行（KDoc 星号行、双斜线行注释、块注释起始行均不计） */
    private fun codeLines(file: File): List<String> = file.readLines().filter { line ->
        val trimmed = line.trim()
        trimmed.isNotEmpty() &&
            !trimmed.startsWith("*") &&
            !trimmed.startsWith("//") &&
            !trimmed.startsWith("/*")
    }

    private fun relative(file: File): String =
        file.relativeTo(File(mainSourceRoot(), "com/ldp/adskip")).invariantSeparatorsPath

    private fun mainSourceRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val candidate = File(dir, "src/main/java")
            if (candidate.isDirectory && File(dir, "src/main/java/com/ldp/adskip").isDirectory) return candidate
            dir = dir.parentFile
        }
        error("未定位到 src/main/java/com/ldp/adskip：无法执行安全存储契约守护")
    }
}
