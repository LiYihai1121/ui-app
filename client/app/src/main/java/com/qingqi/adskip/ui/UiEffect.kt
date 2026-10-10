package com.qingqi.adskip.ui

/**
 * UI 层一次性事件（单向数据流的 Effect 侧）。
 *
 * 持久可回退的状态放 [UiState]（StateFlow，Screen 用 `collectAsStateWithLifecycle` 收集）；
 * Toast 等一次性副作用走本类型（SharedFlow，Screen 在 `LaunchedEffect` 中收集并消费）。
 * 四个页面统一经 [UiEffect.ShowMessage] 下发提示，避免各页自定义消息类型/裸 String。
 */
sealed interface UiEffect {

    /** 展示一条一次性文案（Snackbar，无 action） */
    data class ShowMessage(val message: String) : UiEffect

    /**
     * 关键词被删除，**可撤销**。
     *
     * 单独成类型而不是复用 [ShowMessage]：删除是不可逆操作，界面必须能拿到
     * 「撤销」入口；把撤销能力混进通用消息里，调用方就会退化成只弹一条无退路的提示。
     * 文案由界面侧生成（需要拼接与本地化资源），ViewModel 只报告事实。
     */
    data class KeywordRemoved(val keyword: String) : UiEffect

    /**
     * 需要重建当前 Activity 才能让改动生效。
     *
     * 目前唯一来源是**低版本（API < 33）切换界面语言**：那里没有平台级的
     * `LocaleManager`，新语言只能靠 Activity 重建时在 `attachBaseContext`
     * 里包一层 Context 才作用于资源解析。
     *
     * 单独成类型而不是让 ViewModel 持有 Activity：UI 层之外无法安全地操作
     * Activity 生命周期，这类「请宿主做一件事」的请求必须经 Effect 上报。
     */
    data object RecreateActivity : UiEffect
}
