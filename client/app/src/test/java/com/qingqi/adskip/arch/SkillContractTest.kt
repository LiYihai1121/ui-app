package com.qingqi.adskip.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 技能库契约（skills/README.md「SKILL.md 格式」与「校验」两节的机器化）。
 *
 * 背景：技能格式约定此前只活在文档里——frontmatter 缺 description、技能漏登记进
 * 目录结构、第三方技能漏带 NOTICE 都不会被发现（安装/替换技能时全靠人工守约）。
 * 本测试把可机器检查的部分升级为门禁，违反即测试失败，随 testDebugUnitTest 运行。
 *
 * 检查项：
 * 1. 每个技能目录（含 SKILL.md 的子目录）frontmatter 含 name 与 description；
 * 2. name 与目录名一致且为小写连字符命名；description ≥ 30 字符（须写明适用场景与边界）；
 * 3. 每个技能登记进 skills/README.md 目录结构（漏登记 = 第二份真相）；
 * 4. 随包携带 LICENSE 的技能必须同时带 NOTICE.md（来源与署名）。
 */
class SkillContractTest {

    @Test
    fun `every skill has well-formed SKILL frontmatter`() {
        val problems = mutableListOf<String>()
        for (dir in skillDirs()) {
            val skill = File(dir, "SKILL.md")
            val frontmatter = parseFrontmatter(skill.readText())
            if (frontmatter == null) {
                problems += "${dir.name}/SKILL.md：缺少合法 frontmatter（--- ... ---）"
                continue
            }
            val name = frontmatter["name"]
            val description = frontmatter["description"]
            when {
                name.isNullOrBlank() -> problems += "${dir.name}/SKILL.md：frontmatter 缺 name"

                else -> {
                    if (name != dir.name) {
                        problems += "${dir.name}/SKILL.md：name「$name」与目录名不一致"
                    }
                    if (!NAME_REGEX.matches(name)) {
                        problems += "${dir.name}/SKILL.md：name「$name」非小写连字符命名"
                    }
                }
            }
            when {
                description.isNullOrBlank() ->
                    problems += "${dir.name}/SKILL.md：frontmatter 缺 description（触发描述是防误触发的唯一手段）"

                description.length < MIN_DESCRIPTION_LENGTH ->
                    problems += "${dir.name}/SKILL.md：description 过短（< $MIN_DESCRIPTION_LENGTH 字符），须写明适用场景与边界"
            }
        }
        assertTrue(
            "技能格式违反 skills/README.md「SKILL.md 格式」：\n" +
                problems.joinToString("\n") { "  $it" } +
                "\n新技能开发流程见 skills/README.md「开发一个新技能」（脚手架：skills/scripts/new_skill.py）。",
            problems.isEmpty(),
        )
    }

    @Test
    fun `every skill is registered in the skills README tree`() {
        val readme = File(skillsRoot(), "README.md")
        assertTrue("缺少 skills/README.md（技能登记表）", readme.isFile)
        val text = readme.readText()
        val missing = skillDirs().filterNot { text.contains("${it.name}/") }
        assertTrue(
            "以下技能未登记进 skills/README.md 目录结构（漏登记 = 第二份真相）：\n" +
                missing.joinToString("\n") { "  ${it.name}" },
            missing.isEmpty(),
        )
    }

    @Test
    fun `a bundled license always ships its notice`() {
        // 第三方技能随包携带 LICENSE 与 NOTICE.md（来源与署名）；检查方向取
        // 「有 LICENSE 必有 NOTICE」——许可未随附的技能由 NOTICE 记录来源与
        // 许可状态（如 software-development-full），不伪造 LICENSE。
        val problems = skillDirs()
            .filter { File(it, "LICENSE").isFile && !File(it, "NOTICE.md").isFile }
            .map { "${it.name}/：携带 LICENSE 但缺 NOTICE.md（来源与署名）" }
        assertTrue(
            "第三方技能署名不完整：\n" + problems.joinToString("\n") { "  $it" },
            problems.isEmpty(),
        )
    }

    // ---------- 工具 ----------

    /** 解析 frontmatter 顶层键值（忽略 metadata 嵌套项）；无合法 frontmatter 返回 null */
    private fun parseFrontmatter(text: String): Map<String, String>? {
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != "---") return null
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (end < 0) return null
        return lines.subList(1, end + 1)
            .filter { it.isNotBlank() && !it.startsWith(" ") && !it.startsWith("\t") && ":" in it }
            .associate { line ->
                val (key, value) = line.split(":", limit = 2)
                key.trim() to value.trim()
            }
    }

    /** 技能目录 = skills/ 下含 SKILL.md 的子目录（scripts/ 等开发工具目录不参与） */
    private fun skillDirs(): List<File> = skillsRoot().listFiles()
        ?.filter { it.isDirectory && File(it, "SKILL.md").isFile }
        ?.sortedBy { it.name }
        .orEmpty()

    private fun skillsRoot(): File = File(repoRoot(), "skills")

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            if (File(dir, "skills").isDirectory && File(dir, "client").isDirectory) return dir
            dir = dir.parentFile
        }
        error("未定位到仓库根（含 skills/ 与 client/）：无法执行技能库契约守护")
    }

    private companion object {
        val NAME_REGEX = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
        const val MIN_DESCRIPTION_LENGTH = 30
    }
}
