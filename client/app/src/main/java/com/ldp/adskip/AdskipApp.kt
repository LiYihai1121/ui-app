package com.ldp.adskip

import android.app.Application
import android.content.Context
import com.ldp.adskip.core.LogRing

/**
 * Application 入口：初始化 [AppContainer] 手动 DI 容器（组合根）。
 *
 * UI / Service 层通过 `(context as AdskipApp).container` 获取依赖。
 * 本应用为纯本地架构：无云端规则同步、无后台同步调度、无跳过上报，
 * 进程级初始化仅组装依赖。
 */
class AdskipApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, this)
        LogRing.d("App", "AdskipApp initialized")
    }

    companion object {
        fun get(context: Context): AppContainer = (context.applicationContext as AdskipApp).container
    }
}
