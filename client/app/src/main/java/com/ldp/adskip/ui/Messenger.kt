package com.ldp.adskip.ui

import android.widget.Toast
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 统一消息出口：Snackbar 取代 Toast。
 *
 * 为什么不继续用 Toast（设计规范审计结论）：
 * - Toast **不可交互**，放不下「撤销」动作，而本应用有两处不可逆操作
 *   （删除关键词、清空日志）——没有退路的删除只剩二次确认这一道保险；
 * - Toast 会被后一条顶掉、时长由系统决定、深色背景上容易被忽略；
 *   Snackbar 可排队、可关闭、可带 action。
 *
 * 外壳在 `AdskipShell` 里创建 [SnackbarHostState] 并作为 `Scaffold.snackbarHost`，
 * 再把 [Messenger] **显式传参**给需要发消息的页面。这里刻意不用 CompositionLocal：
 * 依赖显式可见，页面签名即它的能力清单，Preview 与单测也能直接注入假实现。
 */
class Messenger internal constructor(
    private val hostState: SnackbarHostState?,
    private val scope: CoroutineScope,
    private val fallback: (String) -> Unit,
) {
    /** 普通提示：短时展示，无 action。 */
    fun show(message: String) {
        val host = hostState
        if (host == null) {
            fallback(message)
            return
        }
        scope.launch { host.showSnackbar(message) }
    }

    /**
     * 可撤销提示：带 action 按钮并延长展示时长。
     *
     * 用 [SnackbarDuration.Long] 是必要的：带 action 的 Snackbar 需要给用户
     * 读完文案并决定是否点击的时间，默认时长通常来不及。
     */
    fun showUndoable(message: String, undoLabel: String, onUndo: () -> Unit) {
        val host = hostState
        if (host == null) {
            // 无宿主时无法提供撤销，退回普通提示：宁可少一个按钮，
            // 也不能让「撤销」变成一个点了没反应的死按钮
            fallback(message)
            return
        }
        scope.launch {
            val result = host.showSnackbar(
                message = message,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }
}

/**
 * 取一个 [Messenger]。
 *
 * [hostState] 为 null 时自动回退到 Toast，保证页面在 Preview / 单测里也能调用
 * 而不抛异常。
 */
@Composable
fun rememberMessenger(hostState: SnackbarHostState?): Messenger {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(hostState, context, scope) {
        Messenger(
            hostState = hostState,
            scope = scope,
        ) { message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show() }
    }
}
