package com.ldp.adskip.arch

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 仓库卫生守护测试：把多 Agent 协作中最容易「静默失控」的约定升级为 CI 可执行规则。
 *
 * 背景（均为本仓库实测故障，不是假想风险）：
 * 1. 工作区内曾存在**没有 `.git` 元数据的整仓副本**（`.kilo/worktrees/<name>/`），
 *    导致文件检索/读取出现两份结果、Agent 改到副本里而「成果丢失」；
 * 2. 该副本之所以对 `git status` 隐形，是因为忽略规则只写在本机 `.git/info/exclude`
 *    而未进入共享 `.gitignore`——形成「git 看不见、工具看得见」的双重真相；
 * 3. 规范类文档若未登记到 `docs/README.md`，流程会出现第二份真相；
 * 4. 分支名不受约束时，多个 Agent 会混用同一分支，变更无法追踪到任务/人。
 *
 * 约定细则见 `docs/development/AGENT-WORKFLOW.md`；本测试只负责让违反约定时 CI 立刻失败。
 * 纯 JVM 文件系统扫描，不依赖 Android SDK，随 `testDebugUnitTest` 门禁运行。
 */
class RepoHygieneTest {

    // ---------- 1. 忽略规则必须共享，不能只藏在本机 ----------

    @Test
    fun `gitignore covers agent artifact and workspace directories`() {
        val patterns = gitignorePatterns()
        val required = listOf(
            ".mimosa",    // AI 助手会话产物
            ".workbuddy", // 本地工具状态
            ".kilo",      // Agent 工作区（本次故障来源）
            ".kilocode",  // 同上（legacy 目录名）
            ".worktrees", // 本项目约定的 worktree 落点
            ".agents"     // 通用 Agent 产物目录
        )
        val missing = required.filterNot { dir -> patterns.any { it == dir } }
        assertTrue(
            "根 .gitignore 必须覆盖以下目录（否则忽略规则只存在于本机 .git/info/exclude，" +
                "会造出「git status 干净但文件检索仍命中」的双重真相）：\n" +
                missing.joinToString("\n") { "  $it" } +
                "\n详见 docs/development/AGENT-WORKFLOW.md 第 2.2 节。",
            missing.isEmpty()
        )
    }

    // ---------- 2. 不得存在没有 VCS 元数据的整仓副本 ----------

    @Test
    fun `no repository copy without git metadata inside the workspace`() {
        val root = repoRoot()
        val offenders = mutableListOf<String>()

        // 广度优先、限定深度；剪掉构建产物与工具目录，避免无谓遍历
        var frontier = listOf(root)
        repeat(MAX_SCAN_DEPTH) {
            val next = mutableListOf<File>()
            for (dir in frontier) {
                val children = dir.listFiles()?.filter { it.isDirectory } ?: continue
                for (child in children) {
                    if (child.name in PRUNED_DIRS) continue
                    if (looksLikeRepoCopy(child) && !File(child, ".git").exists()) {
                        offenders += root.toPath().relativize(child.toPath()).toString()
                    }
                    if (child.name != ".git" && child.name != "src") next += child
                }
            }
            frontier = next
        }

        assertTrue(
            "仓库内存在「像仓库但没有 .git 元数据」的目录（多半是某个 Agent 复制出来的整仓副本）：\n" +
                offenders.joinToString("\n") { "  $it" } +
                "\n这类目录会让文件检索返回重复结果，并可能让 Agent 修改到副本而丢失成果。" +
                "请改用真正的 worktree（git worktree add .worktrees/<agent>-<slug> -b <branch> origin/main），" +
                "并删除该副本；细则见 docs/development/AGENT-WORKFLOW.md 第 2.1 节。",
            offenders.isEmpty()
        )
    }

    // ---------- 3. 协作规范必须在文档地图登记 ----------

    @Test
    fun `agent workflow doc is registered in the doc map`() {
        val docMap = File(repoRoot(), "docs/README.md")
        assertTrue("未找到文档地图 docs/README.md", docMap.isFile)
        assertTrue(
            "docs/README.md 必须登记 $WORKFLOW_DOC（AGENTS.md 要求新增文档同 PR 登记文档地图；" +
                "否则协作规范会成为第二份流程真相）",
            docMap.readText().contains(WORKFLOW_DOC)
        )
    }

    // ---------- 4. 分支可追踪 ----------

    @Test
    fun `current branch follows the naming convention`() {
        val head = readHeadRef(repoRoot()) ?: return  // 非 git 环境：放行
        if (head.isBlank()) return                    // detached HEAD（CI 常见）：放行
        val allowed = Regex("^(main|master)$").matches(head) ||
            Regex("^(feature|fix|docs|ci|test|refactor|release|hotfix)/[a-z0-9][a-z0-9._-]*$")
                .matches(head)
        assertTrue(
            "当前分支「$head」不符合约定：应为 type/<id>-<slug>" +
                "（type ∈ feature/fix/docs/ci/test/refactor/release/hotfix），或 main/master。" +
                "分支不可追踪会让多 Agent 的变更无法归属到任务/人；" +
                "细则见 docs/development/AGENT-WORKFLOW.md 第 4 节。",
            allowed
        )
    }

    // ---------- 工具 ----------

    /** 读取归一化后的 .gitignore 模式：去注释、去空行，并剥离路径glob 前后缀。 */
    private fun gitignorePatterns(): Set<String> {
        val file = File(repoRoot(), ".gitignore")
        assertTrue("未找到根 .gitignore", file.isFile)
        return file.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .map { pattern ->
                pattern.removePrefix("**/").removeSuffix("/**").removeSuffix("/")
            }
            .toSet()
    }

    /** 「像仓库」的判据：含根级标记文件与本项目的源码骨架。 */
    private fun looksLikeRepoCopy(dir: File): Boolean =
        File(dir, "AGENTS.md").isFile &&
            File(dir, "client/app/src/main/java/com/ldp/adskip").isDirectory

    /** 解析当前分支名；非 git 环境返回 null、detached HEAD 返回空串（均放行）。 */
    private fun readHeadRef(root: File): String? {
        val dotGit = File(root, ".git")
        val gitDir = when {
            dotGit.isDirectory -> dotGit
            dotGit.isFile -> {
                val pointer = dotGit.readText().trim().removePrefix("gitdir:").trim()
                val resolved = if (File(pointer).isAbsolute) File(pointer) else File(root, pointer)
                if (resolved.isDirectory) resolved else null
            }
            else -> null
        } ?: return null
        val head = File(gitDir, "HEAD")
        if (!head.isFile) return null
        val content = head.readText().trim()
        if (!content.startsWith("ref:")) return ""   // detached HEAD
        return content.removePrefix("ref:").trim().removePrefix("refs/heads/")
    }

    /** 从当前工作目录向上定位仓库根（需同时包含 .gitignore 与 docs/README.md）。 */
    private fun repoRoot(): File {
        var dir: File? = System.getProperty("user.dir")?.let { File(it) }
        while (dir != null) {
            if (File(dir, ".gitignore").isFile && File(dir, "docs/README.md").isFile) return dir
            dir = dir.parentFile
        }
        error("未定位到仓库根（需同时包含 .gitignore 与 docs/README.md）：无法执行仓库卫生守护")
    }

    private companion object {
        const val MAX_SCAN_DEPTH = 4
        const val WORKFLOW_DOC = "AGENT-WORKFLOW.md"
        val PRUNED_DIRS = setOf(
            ".git", "node_modules", "build", ".gradle", ".kotlin", ".idea", "captures", ".tools"
        )
    }
}


