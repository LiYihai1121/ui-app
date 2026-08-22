package com.ldp.adskip.core

/**
 * 环形日志：内存中保留最近 N 条日志，替代散落的 Log.d。
 * 设置页可导出分享，用户反馈问题有据可查。
 */
object LogRing {
    private const val CAPACITY = 500

    private data class Entry(val time: Long, val level: String, val tag: String, val msg: String)

    private val ring = ArrayDeque<Entry>()

    @Synchronized
    fun d(tag: String, msg: String) {
        add("D", tag, msg)
    }

    @Synchronized
    fun w(tag: String, msg: String) {
        add("W", tag, msg)
    }

    @Synchronized
    fun e(tag: String, msg: String) {
        add("E", tag, msg)
    }

    private fun add(level: String, tag: String, msg: String) {
        ring.addLast(Entry(System.currentTimeMillis(), level, tag, msg))
        while (ring.size > CAPACITY) ring.removeFirst()
    }

    /** 导出全部日志为可分享文本。 */
    @Synchronized
    fun export(): String {
        val sb = StringBuilder()
        val sdf = java.text.SimpleDateFormat("MM-dd HH:mm:ss.SSS", java.util.Locale.US)
        for (e in ring) {
            sb.append(sdf.format(java.util.Date(e.time)))
                .append(' ').append(e.level).append('/').append(e.tag)
                .append(": ").append(e.msg).append('\n')
        }
        return sb.toString()
    }

    @Synchronized
    fun clear() {
        ring.clear()
    }
}
