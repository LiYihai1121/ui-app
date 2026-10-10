package com.qingqi.adskip

import android.app.Application
import android.content.Context
import com.qingqi.adskip.core.LogRing
import com.qingqi.adskip.data.Prefs
import com.qingqi.adskip.sync.SyncJobService

/**
 * Application 入口：初始化 [AppContainer] 手动 DI 容器（组合根）。
 *
 * UI / Service 层通过 `(context as AdSkipApp).container` 获取依赖。
 * 组合根同时负责进程级兜底（如周期同步 Job 重注册），
 * 使 ui 层不再直接依赖 data.Prefs / sync.SyncJobService。
 */
class AdSkipApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, this)
        // 兜底：持久化 Job 因厂商 ROM 清理丢失时重新注册
        if (Prefs.isAutoSyncEnabled(this)) SyncJobService.schedule(this)
        LogRing.d("App", "AdSkipApp initialized")
    }

    companion object {
        fun get(context: Context): AppContainer = (context.applicationContext as AdSkipApp).container
    }
}
