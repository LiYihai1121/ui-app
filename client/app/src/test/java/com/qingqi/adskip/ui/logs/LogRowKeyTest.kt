package com.qingqi.adskip.ui.logs

import com.qingqi.adskip.data.StatsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * [logRowKey] 注入性守护。
 *
 * 对应真实崩溃路径：`Prefs.addLog` 以毫秒时间戳作 ts，同一应用同一标签在同一毫秒内
 * 被跳过两次时内容三元组完全相同——若 key 只用内容三元组，LazyColumn 会在布局期
 * 抛 `IllegalArgumentException: Key ... was already used`。位序参与后 key 必然互异。
 */
class LogRowKeyTest {

    private fun entry(ts: Long = 1_700_000_000_000L, pkg: String = "com.example.app", label: String = "跳过") =
        StatsRepository.LogEntry(ts = ts, pkg = pkg, label = label)

    @Test
    fun `identical adjacent entries get distinct keys`() {
        // 复现崩溃场景：同毫秒、同包名、同标签连续两条
        val same = entry()
        val keys = List(5) { index -> logRowKey(index, same) }
        assertEquals("相同内容的相邻条目必须产生互异 key（否则 LazyColumn 布局期崩溃）", 5, keys.toSet().size)
    }

    @Test
    fun `same entry at different positions differs`() {
        val e = entry()
        assertNotEquals(logRowKey(0, e), logRowKey(1, e))
    }

    @Test
    fun `distinct entries at distinct positions keep distinct keys`() {
        val entries = listOf(
            entry(ts = 3, pkg = "com.a", label = "跳过"),
            entry(ts = 2, pkg = "com.b", label = "跳过"),
            entry(ts = 1, pkg = "com.a", label = "跳过广告"),
        )
        val keys = entries.mapIndexed { index, e -> logRowKey(index, e) }
        assertEquals(3, keys.toSet().size)
    }

    @Test
    fun `keys encode content so equal content at equal position is stable`() {
        // 同一数据重复渲染（如撤销清空后原样恢复）时，同位序同内容必须得到同一 key，
        // 否则 LazyColumn 会把所有行当作新 item 丢弃复用状态
        val a = entry()
        val b = entry()
        assertEquals(logRowKey(7, a), logRowKey(7, b))
    }
}
