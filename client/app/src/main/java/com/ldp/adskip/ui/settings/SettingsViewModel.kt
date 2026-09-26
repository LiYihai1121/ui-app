package com.ldp.adskip.ui.settings

import android.net.Uri
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ldp.adskip.AdskipApp
import com.ldp.adskip.AppContainer
import com.ldp.adskip.R
import com.ldp.adskip.ui.UiEffect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 云端规则同步设置状态。
 *
 * 持久化 / 网络 / 后台调度一律经 [com.ldp.adskip.data.SettingsRepository] 访问
 * （边界契约：ui 层不直读 Prefs、不碰 SyncClient / SyncJobService）。
 * 单向数据流：状态入 [UiState]，一次性提示经 [effects] 下发。
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val serverUrlInput: String,
        val syncing: Boolean = false,
        val syncResult: String? = null,
        val lastSyncAt: Long = 0L,
        val autoSync: Boolean = false,
        val dndEnabled: Boolean = false,
        val dndStartMinute: Int = 23 * 60,
        val dndEndMinute: Int = 7 * 60,
        val batteryExempt: Boolean = false
    )

    private val _uiState = MutableStateFlow(
        UiState(
            serverUrlInput = container.settingsRepo.serverUrl(),
            lastSyncAt = container.settingsRepo.lastSyncAt(),
            autoSync = container.settingsRepo.isAutoSyncEnabled(),
            dndEnabled = container.settingsRepo.isDoNotDisturbEnabled(),
            dndStartMinute = container.settingsRepo.getDoNotDisturbStart(),
            dndEndMinute = container.settingsRepo.getDoNotDisturbEnd(),
            batteryExempt = queryBatteryExempt()
        )
    )
    val uiState: StateFlow<UiState> = _uiState

    private val _effects = MutableSharedFlow<UiEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<UiEffect> = _effects

    fun onServerUrlChanged(value: String) {
        _uiState.value = _uiState.value.copy(serverUrlInput = value)
    }

    fun isValidServerUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        return uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
    }

    /** 校验并保存服务器地址，返回是否成功 */
    fun saveServerUrl(raw: String): Boolean {
        if (!isValidServerUrl(raw.trim())) {
            send(container.app.getString(R.string.settings_url_invalid))
            return false
        }
        container.settingsRepo.saveServerUrl(raw.trim())
        send(container.app.getString(R.string.settings_saved))
        return true
    }

    /** 立即同步云端规则（v1 协议），结果回传到 syncResult */
    fun syncNow() {
        val url = _uiState.value.serverUrlInput.trim()
        if (!saveServerUrl(url)) return
        _uiState.value = _uiState.value.copy(syncing = true)
        container.settingsRepo.syncNow(url) { _, msg ->
            _uiState.value = _uiState.value.copy(syncing = false, syncResult = msg)
            refreshLastSync()
        }
    }

    fun setAutoSync(enabled: Boolean) {
        container.settingsRepo.setAutoSyncEnabled(enabled)
        _uiState.value = _uiState.value.copy(autoSync = enabled)
    }

    fun setDndEnabled(enabled: Boolean) {
        container.settingsRepo.setDoNotDisturbEnabled(enabled)
        _uiState.value = _uiState.value.copy(dndEnabled = enabled)
    }

    fun setDndTimes(startMinute: Int, endMinute: Int) {
        container.settingsRepo.setDoNotDisturbTimes(startMinute, endMinute)
        _uiState.value = _uiState.value.copy(dndStartMinute = startMinute, dndEndMinute = endMinute)
    }

    fun refreshBatteryStatus() {
        _uiState.value = _uiState.value.copy(batteryExempt = queryBatteryExempt())
    }

    fun formatLastSync(ts: Long): String = if (ts > 0L) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
    } else ""

    private fun refreshLastSync() {
        _uiState.value = _uiState.value.copy(lastSyncAt = container.settingsRepo.lastSyncAt())
    }

    private fun queryBatteryExempt(): Boolean {
        val pm = container.app.getSystemService(PowerManager::class.java)
        return pm?.isIgnoringBatteryOptimizations(container.app.packageName) ?: false
    }

    private fun send(message: String) {
        viewModelScope.launch { _effects.emit(UiEffect.ShowMessage(message)) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AdskipApp
                SettingsViewModel(app.container)
            }
        }
    }
}
