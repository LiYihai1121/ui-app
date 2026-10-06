package com.ldp.adskip.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ldp.adskip.AdskipApp
import com.ldp.adskip.AppContainer
import com.ldp.adskip.R
import com.ldp.adskip.device.BatteryExemption
import com.ldp.adskip.device.KeepAliveNavigator
import com.ldp.adskip.device.TileAddResult
import com.ldp.adskip.device.Vendor
import com.ldp.adskip.ui.UiEffect
import com.ldp.adskip.ui.vendorLabelRes
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
        val batteryExemption: BatteryExemption = BatteryExemption.UNKNOWN,
        /** 当前设备所属 ROM，用于保活引导文案与手动路径提示（device/VendorKeepAlive） */
        val keepAliveVendor: Vendor = Vendor.GENERIC,
    ) {
        /**
         * 已确认豁免电池优化。
         *
         * [BatteryExemption.UNKNOWN] 不算已豁免：不确定时不该对用户承诺后台不会被杀。
         */
        val isBatteryExempt: Boolean get() = batteryExemption == BatteryExemption.EXEMPT
    }

    private val _uiState = MutableStateFlow(
        UiState(
            serverUrlInput = container.settingsRepo.serverUrl(),
            lastSyncAt = container.settingsRepo.lastSyncAt(),
            autoSync = container.settingsRepo.isAutoSyncEnabled(),
            dndEnabled = container.settingsRepo.isDoNotDisturbEnabled(),
            dndStartMinute = container.settingsRepo.getDoNotDisturbStart(),
            dndEndMinute = container.settingsRepo.getDoNotDisturbEnd(),
            batteryExemption = queryBatteryExemption(),
            keepAliveVendor = KeepAliveNavigator.detectVendor(),
        ),
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
        _uiState.value = _uiState.value.copy(batteryExemption = queryBatteryExemption())
    }

    // ---------- 厂商保活引导与快捷磁贴 ----------

    /** 打开厂商「自启动 / 后台管理」设置页；候选入口均不可用时提示手动路径。 */
    fun openKeepAliveSettings() {
        val vendorName = vendorName(_uiState.value.keepAliveVendor)
        val opened = KeepAliveNavigator.openKeepAliveSettings(container.app) != null
        val messageRes = if (opened) {
            R.string.settings_keepalive_opened
        } else {
            R.string.settings_keepalive_failed
        }
        send(container.app.getString(messageRes, vendorName))
    }

    /** Android 13+ 请求系统弹出「添加到快捷设置」；低版本或系统服务缺失时提示手动添加。 */
    fun requestAddTile() {
        val tileLabel = container.app.getString(R.string.tile_label)
        if (!KeepAliveNavigator.canRequestAddTile()) {
            send(container.app.getString(R.string.settings_tile_manual, tileLabel))
            return
        }
        val started = KeepAliveNavigator.requestAddTile(
            context = container.app,
            label = tileLabel,
        ) { result -> send(tileAddMessage(result)) }
        if (!started) {
            send(container.app.getString(R.string.settings_tile_manual, tileLabel))
        }
    }

    private fun tileAddMessage(result: TileAddResult): String {
        val messageRes = when (result) {
            TileAddResult.ADDED -> R.string.settings_tile_added
            TileAddResult.ALREADY_ADDED -> R.string.settings_tile_exists
            TileAddResult.NOT_FOREGROUND -> R.string.settings_tile_not_foreground
            TileAddResult.FAILED -> R.string.settings_tile_manual
        }
        return if (messageRes == R.string.settings_tile_manual) {
            container.app.getString(messageRes, container.app.getString(R.string.tile_label))
        } else {
            container.app.getString(messageRes)
        }
    }

    private fun vendorName(vendor: Vendor): String = container.app.getString(vendorLabelRes(vendor))

    fun formatLastSync(ts: Long): String = if (ts > 0L) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
    } else {
        ""
    }

    private fun refreshLastSync() {
        _uiState.value = _uiState.value.copy(lastSyncAt = container.settingsRepo.lastSyncAt())
    }

    /** 查询电池优化豁免；失败时交由 [BatteryExemption] 落成 UNKNOWN，不冒充「未豁免」。 */
    private fun queryBatteryExemption(): BatteryExemption = BatteryExemption.detect(container.app)

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
