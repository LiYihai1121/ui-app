package com.ldp.adskip.core

/**
 * 时钟抽象：节流/去抖不再硬依赖 [SystemClock.elapsedRealtime]。
 * 测试时可注入假时钟控制时间推进。
 */
interface Clock {
    /** 单调递增时间（毫秒），对应 SystemClock.elapsedRealtime() */
    fun elapsedRealtime(): Long

    /** 墙钟时间（毫秒），对应 System.currentTimeMillis() */
    fun currentTimeMillis(): Long
}
