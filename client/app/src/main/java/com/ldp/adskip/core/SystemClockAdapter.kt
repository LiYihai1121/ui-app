package com.ldp.adskip.core

import android.os.SystemClock

/**
 * 生产用时钟实现：直接委托框架 API。
 */
object SystemClockAdapter : Clock {
    override fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
