package com.ldp.adskip

import android.app.Application
import android.content.Context
import com.ldp.adskip.core.LogRing

/**
 * Application 入口：初始化 [AppContainer] 手动 DI 容器。
 *
 * UI / Service 层通过 `(context as AdskipApp).container` 获取依赖。
 */
class AdskipApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        LogRing.d("App", "AdskipApp initialized")
    }

    companion object {
        fun get(context: Context): AppContainer =
            (context.applicationContext as AdskipApp).container
    }
}
