package com.ldp.adskip.data

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 统计计数的**回归保护**：锁死「一次跳过 = 计数 +1」这条最基本的不变式。
 *
 * 背景（本仓库已发生的真实缺陷，非假想）：`StatsRepository` 曾同时维护
 * `pendingTotal` / `pendingByPkg` 内存增量，而 `Prefs.recordSkip` 早已同步把
 * `total_skips` 与 `pkg_count:*` 各 +1 落盘——两者是同一批 key 的**第二个写入方**，
 * 且 `flush()` 从不将增量清零。于是每跳一次、每 5 秒一次的 flush 都会把累计增量
 * 再次叠加：
 *
 *     跳过 1 次   -> SP=1，pending=1
 *     flush       -> SP = 1 + 1 = 2   （真实值应为 1）
 *     跳过再 1 次 -> SP=2，pending=2
 *     flush       -> SP = 2 + 2 = 4   （真实值应为 2）
 *
 * 用户看到的跳过数因此约为真实值的 2 倍起跳并持续膨胀。
 *
 * 为什么要 Robolectric：这段逻辑写在依赖 `Context` / `SharedPreferences` /
 * `Handler` 的类里，纯 JVM 单测跑不到——而它恰恰是本仓库唯一一类
 * 「两个写入方静默互相覆盖、不崩溃不报错」的故障。没有 Robolectric 时本缺陷
 * 在 CI 上完全不可见，只能靠用户报障发现。
 *
 * 本测试的核心断言刻意写成**循环 + 中途 flush**，而不是「跳一次等于一」：
 * 单次用例在旧实现下也会通过（1 -> 2 之前的那一步），只有多次叠加才会暴露发散。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StatsRepositoryCountingTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = org.robolectric.RuntimeEnvironment.getApplication()
        // 每个用例从干净的 SP 开始：SharedPreferences 在 Robolectric 下跨用例共享同一文件
        Prefs.sp(context).edit().clear().commit()
    }

    @After
    fun tearDown() {
        Prefs.sp(context).edit().clear().commit()
    }

    @Test
    fun `repeated skips across flushes count exactly once each`() {
        val repo = StatsRepository(context)
        repeat(5) { i ->
            repo.recordSkip(PKG, "示例应用")
            // 每跳一次就 flush，复现旧实现里 5 秒定时落盘的累积叠加行为
            repo.flush()
            assertEquals(
                "第 ${i + 1} 次跳过后计数应恰为 ${i + 1}（flush 不得重复累加）",
                i + 1,
                repo.total(),
            )
        }
        assertEquals("累计 5 次跳过后总数应为 5", 5, repo.total())
        assertEquals("按应用计数同样不应被重复累加", 5, repo.countFor(PKG))
    }

    @Test
    fun `flush without new skips is idempotent`() {
        val repo = StatsRepository(context)
        repo.recordSkip(PKG, "示例应用")
        val afterFirst = repo.total()

        // 连续多次 flush 不得改变已落盘的计数（这是旧实现最直观的症状）
        repeat(5) { repo.flush() }

        assertEquals("重复 flush 不得改变计数", afterFirst, repo.total())
        assertEquals("重复 flush 后计数仍应为 1", 1, repo.total())
    }

    @Test
    fun `counting survives reads between flushes`() {
        val repo = StatsRepository(context)
        repo.recordSkip(PKG, "示例应用")
        repo.recordSkip(PKG, "示例应用")
        // 读接口在旧实现里是「SP + pending」，与 flush 的叠加共同导致翻倍
        assertEquals(2, repo.total())
        repo.flush()
        assertEquals("flush 前后读数必须一致", 2, repo.total())
        repo.flush()
        assertEquals("多次 flush 后读数仍须一致", 2, repo.total())
    }

    @Test
    fun `counts are isolated per package`() {
        val repo = StatsRepository(context)
        repo.recordSkip("com.example.a", "应用 A")
        repo.recordSkip("com.example.b", "应用 B")
        repo.recordSkip("com.example.a", "应用 A")
        repo.flush()

        assertEquals(3, repo.total())
        assertEquals("按应用计数不应互相污染", 2, repo.countFor("com.example.a"))
        assertEquals(1, repo.countFor("com.example.b"))
    }

    @Test
    fun `every skip is recorded in the log`() {
        val repo = StatsRepository(context)
        repo.recordSkip(PKG, "示例应用")
        repo.recordSkip(PKG, "示例应用")

        val logs = repo.logs()
        assertEquals("每次跳过都应留下一条日志", 2, logs.size)
        assertTrue("日志应记录对应包名", logs.all { it.pkg == PKG })
    }

    private companion object {
        const val PKG = "com.example.demo"
    }
}
