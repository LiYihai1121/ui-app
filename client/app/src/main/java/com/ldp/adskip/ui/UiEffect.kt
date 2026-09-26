package com.ldp.adskip.ui

/**
 * UI 层一次性事件（单向数据流的 Effect 侧）。
 *
 * 持久可回退的状态放 [UiState]（StateFlow，Screen 用 `collectAsStateWithLifecycle` 收集）；
 * Toast 等一次性副作用走本类型（SharedFlow，Screen 在 `LaunchedEffect` 中收集并消费）。
 * 四个页面统一经 [UiEffect.ShowMessage] 下发提示，避免各页自定义消息类型/裸 String。
 */
sealed interface UiEffect {

    /** 展示一条一次性文案（Toast） */
    data class ShowMessage(val message: String) : UiEffect
}