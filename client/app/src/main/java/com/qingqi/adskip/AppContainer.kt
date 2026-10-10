package com.qingqi.adskip

import android.app.Application
import android.content.Context
import com.qingqi.adskip.core.AppExecutors
import com.qingqi.adskip.core.Clock
import com.qingqi.adskip.core.SystemClockAdapter
import com.qingqi.adskip.data.Prefs
import com.qingqi.adskip.data.RulesRepository
import com.qingqi.adskip.data.SettingsRepository
import com.qingqi.adskip.data.StatsRepository
import com.qingqi.adskip.net.SyncClient

/**
 * 手动 DI 容器：收口所有依赖，不引入任何第三方 DI 框架。
 *
 * UI 层经 `(application as AdskipApp).container` 取依赖；
 * Service 层同理。ViewModel 经容器取仓库并暴露 StateFlow。
 */
class AppContainer(val app: Application, context: Context) {
    val clock: Clock = SystemClockAdapter
    val executors = AppExecutors()
    val prefs = Prefs // object 单例，不需构造
    val rulesRepo = RulesRepository(context.applicationContext)
    val statsRepo = StatsRepository(context.applicationContext, executors.io)
    val settingsRepo = SettingsRepository(context.applicationContext, rulesRepo)
    val syncClient = SyncClient // object 单例
}
