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
import com.ldp.adskip.data.Prefs
import com.ldp.adskip.net.SyncClient
import com.ldp.adskip.sync.SyncJobService
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
            serverUrlInput = SyncClient.serverUrl(container.app),
            lastSyncAt = SyncClient.lastSyncAt(container.app),
            autoSync = Prefs.isAutoSyncEnabled(container.app),
            dndEnabled = Prefs.isDoNotDisturbEnabled(container.app),
            dndStartMinute = Prefs.getDoNotDisturbStart(container.app),
            dndEndMinute = Prefs.getDoNotDisturbEnd(container.app),
            batteryExempt = queryBatteryExempt()
        )
    )
    val uiState: StateFlow<UiState> = _uiState

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

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
        SyncClient.saveServerUrl(container.app, raw.trim())
        send(container.app.getString(R.string.settings_saved))
        return true
    }

    /** 立即同步云端规则（v1 协议），结果回传到 syncResult */
    fun syncNow() {
        val url = _uiState.value.serverUrlInput.trim()
        if (!saveServerUrl(url)) return
        _uiState.value = _uiState.value.copy(syncing = true)
        SyncClient.syncRules(container.app, url, container.rulesRepo) { _, msg ->
            _uiState.value = _uiState.value.copy(syncing = false, syncResult = msg)
            refreshLastSync()
        }
    }

    fun setAutoSync(enabled: Boolean) {
        SyncJobService.setEnabled(container.app, enabled)
        _uiState.value = _uiState.value.copy(autoSync = enabled)
    }

    fun setDndEnabled(enabled: Boolean) {
        Prefs.setDoNotDisturbEnabled(container.app, enabled)
        _uiState.value = _uiState.value.copy(dndEnabled = enabled)
    }

    fun setDndTimes(startMinute: Int, endMinute: Int) {
        Prefs.setDoNotDisturbTimes(container.app, startMinute, endMinute)
        _uiState.value = _uiState.value.copy(dndStartMinute = startMinute, dndEndMinute = endMinute)
    }

    fun refreshBatteryStatus() {
        _uiState.value = _uiState.value.copy(batteryExempt = queryBatteryExempt())
    }

    fun formatLastSync(ts: Long): String = if (ts > 0L) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
    } else ""

    private fun refreshLastSync() {
        _uiState.value = _uiState.value.copy(lastSyncAt = SyncClient.lastSyncAt(container.app))
    }

    private fun queryBatteryExempt(): Boolean {
        val pm = container.app.getSystemService(PowerManager::class.java)
        return pm?.isIgnoringBatteryOptimizations(container.app.packageName) ?: false
    }

    private fun send(message: String) {
        viewModelScope.launch { _messages.emit(message) }
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
