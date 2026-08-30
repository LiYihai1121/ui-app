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
import com.ldp.adskip.service.SkipAdService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 主页一次性提示事件（对应旧架构的 Toast） */
sealed class HomeMessage {
    data class Text(val value: String) : HomeMessage()
}

/**
 * 主页状态：
 * 服务开关状态 / 跳过统计 / 全局关键词 / 模拟开屏广告测试。
 */
class HomeViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val serviceRunning: Boolean = false,
        val totalSkips: Int = 0,
        val lastApp: String = "",
        val keywords: List<String> = emptyList(),
        val fakeAdVisible: Boolean = false,
        val countdown: Int = FAKE_AD_SECONDS
    ) {
        val testActive: Boolean get() = fakeAdVisible
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private val _messages = MutableSharedFlow<HomeMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<HomeMessage> = _messages

    private var countdownJob: Job? = null

    init {
        refreshAll()

        viewModelScope.launch {
            AppEvents.serviceRunning.collect { running ->
                _uiState.value = _uiState.value.copy(serviceRunning = running)
            }
        }
        viewModelScope.launch {
            AppEvents.skipped.collect { label ->
                if (_uiState.value.testActive) {
                    endTest()
                    _messages.emit(
                        HomeMessage.Text(container.app.getString(R.string.test_success, label))
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
        refreshKeywords()
        send(R.string.keyword_removed, removed)
    }

    /** 模拟一个带「跳过」按钮的开屏广告，验证无障碍链路。 */
    fun startFakeAdTest() {
        if (!AppEvents.serviceRunningSnapshot) {
            send(R.string.test_need_service)
            return
        }
        SkipAdService.testActive = true
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
        SkipAdService.testActive = false
        _uiState.value = _uiState.value.copy(fakeAdVisible = false)
    }

    private fun refreshAll() {
        refreshStats()
        refreshKeywords()
    }

    private fun refreshStats() {
        _uiState.value = _uiState.value.copy(
            totalSkips = container.statsRepo.total(),
            lastApp = container.statsRepo.lastApp()
        )
    }

    private fun refreshKeywords() {
        _uiState.value = _uiState.value.copy(keywords = container.rulesRepo.keywords())
    }

    private fun send(resId: Int, vararg args: Any) {
        viewModelScope.launch {
            _messages.emit(HomeMessage.Text(container.app.getString(resId, *args)))
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
