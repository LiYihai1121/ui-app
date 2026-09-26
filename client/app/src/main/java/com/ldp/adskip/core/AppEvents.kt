package com.ldp.adskip.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 进程内事件总线：Service 层 → UI 层的状态桥。
 *
 * 以 [StateFlow]/[SharedFlow] 取代旧架构中 UI 直接注册 BroadcastReceiver 的方式，
 * ViewModel 只依赖本对象，不感知 Android 广播 API，也不必依赖 [com.ldp.adskip.service] 类型。
 * （SkipAdService 仍同时发送广播，保持对外调试行为不变。）
 *
 * 边界契约：core 层不引用任何其他业务包；[serviceRunning] 初值为 false，
 * 由 Service 在 `onServiceConnected` 中以 [setServiceRunning] 写入真实状态。
 */
object AppEvents {

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning

    private val _skipped = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val skipped: SharedFlow<String> = _skipped

    /**
     * 模拟开屏广告测试进行中标记。
     * Service 读取它以放行本包事件并抑制跳过 Toast；UI 通过 [setTestActive] 维护。
     */
    @Volatile
    var testActive: Boolean = false
        private set

    /** 无障碍服务连接/断开时由 Service 调用 */
    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    /** 每次自动跳过成功后由 Service 调用（payload 为应用标签） */
    fun emitSkipped(label: String) {
        _skipped.tryEmit(label)
    }

    /** 模拟测试开始/结束时由 UI 调用（见 HomeViewModel.startFakeAdTest） */
    fun setTestActive(active: Boolean) {
        testActive = active
    }

    /** 同步快照，供无法挂起协程的场景使用 */
    val serviceRunningSnapshot: Boolean get() = _serviceRunning.value
}
