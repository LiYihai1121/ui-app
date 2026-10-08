package com.ldp.adskip.engine

import com.ldp.adskip.engine.selector.SelectorParser
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 选择器双端契约的客户端一侧（DESIGN-PHASE1 §6.2）。
 *
 * 消费 `client/app/src/test/resources/selectors.contract.json`：同一批向量
 * 必须给出一致的「接受 / 拒绝」判定。夹具原随 `server/` 一同维护，服务端移除后
 * 迁入客户端侧由本测试单独守护；解析器收紧或文法变更时未同步夹具，第三通道
 * 会看起来「配了没生效」，而没有任何报错。
 *
 * 判定口径：客户端的「接受」= [SelectorParser.parse] 编译成功。
 * 夹具的 `divergences` 段刻意记录两端判定不同的向量（服务端只做快检，
 * 语法权威在本文件这一侧），本测试只断言其中的 `client` 声明。
 *
 * 纯 JVM：只读文件系统 + 调用解析器，不依赖 Android SDK，随 `testDebugUnitTest` 运行。
 */
class SelectorContractTest {

    private data class Vector(val id: String, val expr: String, val client: String? = null, val why: String? = null)

    // ---------- 1. 夹具本身必须存在且达到设计下限 ----------

    @Test
    fun `fixture exists and meets the design minimum`() {
        val f = fixture()
        assertTrue(
            "选择器契约夹具结构不完整：accepted=${f.accepted.size} rejected=${f.rejected.size} " +
                "divergences=${f.divergences.size}（设计要求合法/非法各 ≥12 条）",
            f.accepted.size >= 12 && f.rejected.size >= 12 && f.divergences.isNotEmpty(),
        )
    }

    // ---------- 2. 合法向量：客户端必须能编译 ----------

    @Test
    fun `accepted vectors compile on the client`() {
        val failed = mutableListOf<String>()
        for (v in fixture().accepted) {
            if (SelectorParser.parse(v.expr) == null) failed += "${v.id} ${v.expr}"
        }
        assertTrue(
            "以下向量在服务端被接受，客户端却编译失败（两端判定漂移）：\n" +
                failed.joinToString("\n") { "  $it" } +
                "\n客户端以「编译成功」对应「接受」，解析器收紧或文法变更时必须同步夹具。",
            failed.isEmpty(),
        )
    }

    // ---------- 3. 非法向量：客户端必须拒收 ----------

    @Test
    fun `rejected vectors are refused on the client`() {
        val leaked = mutableListOf<String>()
        for (v in fixture().rejected) {
            if (SelectorParser.parse(v.expr) != null) leaked += "${v.id} ${v.expr}"
        }
        assertTrue(
            "以下向量在服务端被拒绝，客户端却编译通过（两端判定漂移）：\n" +
                leaked.joinToString("\n") { "  $it" },
            leaked.isEmpty(),
        )
    }

    // ---------- 4. 有意差异：客户端判定必须与夹具声明相符 ----------

    @Test
    fun `divergence vectors match the declared client verdict`() {
        val f = fixture()
        val mismatched = mutableListOf<String>()
        for (v in f.divergences) {
            val actual = if (SelectorParser.parse(v.expr) == null) "reject" else "accept"
            if (actual != v.client) {
                mismatched += "${v.id} 期望 ${v.client} 实际 $actual（${v.why}）"
            }
        }
        assertTrue(
            "divergences 段的客户端判定与实际不符：\n" +
                mismatched.joinToString("\n") { "  $it" } +
                "\n这些向量记录的是「服务端快检放行、语法由客户端兜底」的有意差异，" +
                "改动任一侧都必须同步夹具，否则差异会从「有意」变成「失控」。",
            mismatched.isEmpty(),
        )
    }

    // ---------- 5. 夹具覆盖客户端独有的拒绝理由 ----------

    @Test
    fun `fixture pins the client-only rejection reasons`() {
        val byId = fixture().divergences.associateBy { it.id }
        val cases = mapOf(
            "d01" to "未知属性 key",
            "d02" to "值未加引号",
            "d06" to "值超长",
            "d07" to "组合符链过长",
        )
        val missing = cases.keys.filterNot { byId.containsKey(it) }
        assertTrue("夹具缺少客户端独有拒绝理由的向量：${missing.joinToString()}", missing.isEmpty())

        // 这些理由只在客户端成立（服务端会放行），因此只能靠夹具锁住
        val leaked = cases.keys.filter { SelectorParser.parse(byId.getValue(it).expr) != null }
        assertTrue(
            "以下「客户端独有拒绝」向量被解析器接受了，护栏可能已失效：${leaked.joinToString()}",
            leaked.isEmpty(),
        )
    }

    // ---------- 解析夹具 ----------

    /**
     * 夹具是**逐行手写**的稳定格式（每行一个向量），因此按行扫描而非引入 JSON 库——
     * 客户端源码坚持零第三方依赖，测试也不例外。
     */
    private class Fixture(val accepted: List<Vector>, val rejected: List<Vector>, val divergences: List<Vector>)

    private fun fixture(): Fixture {
        val text = File(repoRoot(), FIXTURE_REL_PATH).let {
            assertTrue("未找到选择器契约夹具 ${it.path}（应位于 client/app/src/test/resources/）", it.isFile)
            it.readText()
        }
        val accepted = mutableListOf<Vector>()
        val rejected = mutableListOf<Vector>()
        val divergences = mutableListOf<Vector>()
        var section = ""
        for (line in text.lines()) {
            when {
                SECTION_ACCEPTED.containsMatchIn(line) -> section = "accepted"
                SECTION_REJECTED.containsMatchIn(line) -> section = "rejected"
                SECTION_DIVERGENCES.containsMatchIn(line) -> section = "divergences"
            }
            val m = VECTOR_REGEX.find(line) ?: continue
            val id = m.groupValues[1]
            val expr = unescape(m.groupValues[2])
            val client = VERDICT_REGEX.find(m.groupValues[3])?.groupValues?.get(1)
            val why = WHY_REGEX.find(m.groupValues[3])?.groupValues?.get(1)
            when (section) {
                "accepted" -> accepted += Vector(id, expr)
                "rejected" -> rejected += Vector(id, expr)
                "divergences" -> divergences += Vector(id, expr, client ?: "accept", why)
            }
            if (section == "divergences" && why == null) {
                assertTrue("$id 缺少 why 说明：有意差异必须写清原因，否则后人无从判断该不该改", false)
            }
        }
        return Fixture(accepted, rejected, divergences)
    }

    /** 只需支持夹具实际用到的转义：引号、反斜杠与换行类。 */
    private fun unescape(raw: String): String {
        if (!raw.contains('\\')) return raw
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (raw[i + 1]) {
                    'n' -> {
                        out.append('\n')
                        i += 2
                    }

                    't' -> {
                        out.append('\t')
                        i += 2
                    }

                    'r' -> {
                        out.append('\r')
                        i += 2
                    }

                    else -> {
                        out.append(raw[i + 1])
                        i += 2
                    }
                }
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    /** 从当前工作目录向上定位仓库根（需同时含 .gitignore 与 docs/README.md）。 */
    private fun repoRoot(): File {
        var dir: File? = System.getProperty("user.dir")?.let { File(it) }
        while (dir != null) {
            if (File(dir, ".gitignore").isFile && File(dir, "docs/README.md").isFile) return dir
            dir = dir.parentFile
        }
        error("未定位到仓库根（需同时含 .gitignore 与 docs/README.md）")
    }

    private companion object {
        const val FIXTURE_REL_PATH = "client/app/src/test/resources/selectors.contract.json"

        val SECTION_ACCEPTED = Regex("\"accepted\"\\s*:")
        val SECTION_REJECTED = Regex("\"rejected\"\\s*:")
        val SECTION_DIVERGENCES = Regex("\"divergences\"\\s*:")

        /** 每个向量固定为 `{ "id": …, "expr": …, … }` 单行，便于按行扫描。 */
        val VECTOR_REGEX = Regex("""\{\s*"id":\s*"([^"]+)",\s*"expr":\s*"((?:[^"\\]|\\.)*)"(.*)\}""")
        val VERDICT_REGEX = Regex(""""client":\s*"(accept|reject)"""")
        val WHY_REGEX = Regex(""""why":\s*"([^"]*)"""")
    }
}
