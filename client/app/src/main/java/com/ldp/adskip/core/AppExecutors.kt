package com.ldp.adskip.core

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * 线程域收口：IO 单线程 + Main 单线程。
 * 避免 `Thread { }.start()` 散落各处。
 */
class AppExecutors {
    val io = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun main(runnable: Runnable) = mainHandler.post(runnable)
    fun main(action: () -> Unit) = mainHandler.post(action)
}
