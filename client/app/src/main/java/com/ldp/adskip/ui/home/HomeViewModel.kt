package com.ldp.adskip.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ldp.adskip.AdskipApp
import com.ldp.adskip.AppContainer
import com.ldp.adskip.R
import com.ldp.adskip.core.AppEvents
import com.ldp.adskip.device.AccessibilityStatus
import com.ldp.adskip.ui.UiEffect
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 主页状态：
 * 服务开关状态 / 跳过统计 / 全局关键词 / 模拟开屏广告测试。
 *
 * 单向数据流：可回退状态入 [UiState]（StateFlow），
 * 一次性提示经 [effects]（[UiEffect] SharedFlow）下发，由 Screen 消费。
 */
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val accessibilityStatus: AccessibilityStatus = AccessibilityStatus.UNKNOWN,
        val totalSkips: Int = 0,
        val lastApp: String = "",
        val keywords: List<String> = emptyList(),
        val fakeAdVisible: Boolean = false,
        val countdown: Int = FAKE_AD_SECONDS,
    ) {
        val testActive: Boolean get() = fakeAdVisible

        /** 服务已确认开启。UNKNOWN 不算开启，避免拿不确定的状态去承诺功能可用。 */
        val serviceRunning: Boolean get() = accessibilityStatus == AccessibilityStatus.ON
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private val _effects = MutableSharedFlow<UiEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<UiEffect> = _effects

    private var countdownJob: Job? = null

    /** 最近一次删除的关键词与其原索引，供 [undoRemoveKeyword] 撤销。只保留一份。 */
    private var lastRemoved: Pair<Int, String>? = null

    init {
        refreshAll()

        viewModelScope.launch {
            // 进程信号只作实时提示：用户在系统设置里关掉服务时，回调可能迟迟不到，
            // 因此回屏时会用系统真值覆盖（见 refreshAccessibilityStatus）。
            AppEvents.serviceRunning.collect { running ->
                _uiState.value = _uiState.value.copy(
                    accessibilityStatus = AccessibilityStatus.decide(
                        systemEnabled = null,
                        processSignal = running,
                    ),
                )
            }
        }
        viewModelScope.launch {
            AppEvents.skipped.collect { label ->
                if (_uiState.value.testActive) {
                    endTest()
                    _effects.emit(
                        UiEffect.ShowMessage(container.app.getString(R.string.test_success, label)),
                    )
                }
                refreshStats()
            }
        }
    }

    fun addKeyword(raw: String) {
        val kw = raw.trim()
        if (kw.isEmpty()) {
            send(R.string.keyword_empty)
            return
        }
        val current = container.rulesRepo.keywords()
        if (current.any { it.equals(kw, ignoreCase = true) }) {
            send(R.string.keyword_exists)
            return
        }
        container.rulesRepo.saveKeywords(current + kw)
        refreshKeywords()
        send(R.string.keyword_added, kw)
    }

    fun removeKeyword(index: Int) {
        val current = container.rulesRepo.keywords()
        if (index >= current.size) return
        val removed = current.removeAt(index)
        container.rulesRepo.saveKeywords(current)
        lastRemoved = index to removed
        refreshKeywords()
        // 文案由界面侧生成（需带「撤销」），这里只报告发生了什么。
        // 用 tryEmit 而非 emit：本函数是普通（非 suspend）回调，界面此时
        // 尚未保证有收集者，emit 会在无订阅者时直接挂起。
        _effects.tryEmit(UiEffect.KeywordRemoved(removed))
    }

    /** 撤销上一次 [removeKeyword]：按原索引插回，避免顺序变化。 */
    fun undoRemoveKeyword() {
        val (index, keyword) = lastRemoved ?: return
        lastRemoved = null
        val current = container.rulesRepo.keywords()
        if (current.any { it.equals(keyword, ignoreCase = true) }) return
        val restored = current.toMutableList()
        restored.add(index.coerceIn(0, restored.size), keyword)
        container.rulesRepo.saveKeywords(restored)
        refreshKeywords()
    }

    /** 模拟一个带「跳过」按钮的开屏广告，验证无障碍链路。 */
    fun startFakeAdTest() {
        // 用有效状态而不是进程信号：服务已被用户在系统里关掉、但回调未到时，
        // 进程信号仍为 true，会让用户以为链路可用，实际点了没反应。
        refreshAccessibilityStatus()
        if (!_uiState.value.serviceRunning) {
            send(R.string.test_need_service)
            return
        }
        AppEvents.setTestActive(true)
        _uiState.value = _uiState.value.copy(fakeAdVisible = true, countdown = FAKE_AD_SECONDS)
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var left = FAKE_AD_SECONDS
            while (left > 0) {
                _uiState.value = _uiState.value.copy(countdown = left)
                delay(1000)
                left--
            }
            endTest()
            send(R.string.test_timeout)
        }
    }

    /** 手动点击跳过按钮视为服务未生效 */
    fun onManualSkipClicked() {
        endTest()
        send(R.string.test_manual)
    }

    private fun endTest() {
        countdownJob?.cancel()
        countdownJob = null
        AppEvents.setTestActive(false)
        _uiState.value = _uiState.value.copy(fakeAdVisible = false)
    }

    /**
     * 回屏时重查无障碍状态。
     *
     * 必须走系统真值：用户去系统设置里改开关后返回，Service 的 `onDestroy` 可能还没回调，
     * 只订阅进程信号会把过期的「运行中」一直显示下去——而快捷磁贴读的是系统真值，
     * 于是同一台设备出现两个说法。由 Screen 在进入本页时调用。
     */
    fun onScreenResumed() {
        refreshAccessibilityStatus()
        refreshStats()
    }

    private fun refreshAll() {
        refreshAccessibilityStatus()
        refreshStats()
        refreshKeywords()
    }

    private fun refreshAccessibilityStatus() {
        _uiState.value = _uiState.value.copy(
            accessibilityStatus = AccessibilityStatus.detect(
                context = container.app,
                serviceClassName = AccessibilityStatus.SKIP_AD_SERVICE_CLASS_NAME,
                processSignal = AppEvents.serviceRunningSnapshot,
            ),
        )
    }

    private fun refreshStats() {
        _uiState.value = _uiState.value.copy(
            totalSkips = container.statsRepo.total(),
            lastApp = container.statsRepo.lastApp(),
        )
    }

    private fun refreshKeywords() {
        _uiState.value = _uiState.value.copy(keywords = container.rulesRepo.keywords())
    }

    private fun send(resId: Int, vararg args: Any) {
        viewModelScope.launch {
            _effects.emit(UiEffect.ShowMessage(container.app.getString(resId, *args)))
        }
    }

    companion object {
        const val FAKE_AD_SECONDS = 5

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AdskipApp
                HomeViewModel(app.container)
            }
        }
    }
}
