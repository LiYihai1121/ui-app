package com.ldp.adskip.core

import com.ldp.adskip.service.SkipAdService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 进程内事件总线：Service 层 → UI 层的状态桥。
 *
 * 以 [StateFlow]/[SharedFlow] 取代旧架构中 UI 直接注册 BroadcastReceiver 的方式，
 * ViewModel 只依赖本对象，不感知 Android 广播 API。
 * （SkipAdService 仍同时发送广播，保持对外调试行为不变。）
 */
object AppEvents {

    private val _serviceRunning = MutableStateFlow(SkipAdService.running)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning

    private val _skipped = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val skipped: SharedFlow<String> = _skipped

    /** 无障碍服务连接/断开时由 Service 调用 */
    fun setServiceRunning(running: Boolean) {
        _serviceRunning.value = running
    }

    /** 每次自动跳过成功后由 Service 调用（payload 为应用标签） */
    fun emitSkipped(label: String) {
        _skipped.tryEmit(label)
    }

    /** 同步快照，供无法挂起协程的场景使用 */
    val serviceRunningSnapshot: Boolean get() = _serviceRunning.value
}
