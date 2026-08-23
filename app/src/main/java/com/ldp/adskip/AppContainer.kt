package com.ldp.adskip

import android.app.Application
import android.content.Context
import com.ldp.adskip.core.AppExecutors
import com.ldp.adskip.core.Clock
import com.ldp.adskip.core.SystemClockAdapter
import com.ldp.adskip.data.Prefs
import com.ldp.adskip.data.RulesRepository
import com.ldp.adskip.data.StatsRepository
import com.ldp.adskip.net.SyncClient

/**
 * 手动 DI 容器：收口所有依赖，不引入任何第三方 DI 框架。
 *
 * UI 层经 `(application as AdskipApp).container` 取依赖；
 * Service 层同理。ViewModel 经容器取仓库并暴露 StateFlow。
 */
class AppContainer(val app: Application, context: Context) {
    val clock: Clock = SystemClockAdapter
    val executors = AppExecutors()
    val prefs = Prefs  // object 单例，不需构造
    val rulesRepo = RulesRepository(context.applicationContext)
    val statsRepo = StatsRepository(context.applicationContext, executors.io)
    val syncClient = SyncClient  // object 单例
}
