package com.qingqi.adskip.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qingqi.adskip.AdskipApp
import com.qingqi.adskip.AppContainer
import com.qingqi.adskip.R
import com.qingqi.adskip.core.AppEvents
import com.qingqi.adskip.data.ServerEndpoint
import com.qingqi.adskip.device.BatteryExemption
import com.qingqi.adskip.device.KeepAliveNavigator
import com.qingqi.adskip.device.LanguageMode
import com.qingqi.adskip.device.LocaleApplier
import com.qingqi.adskip.device.TileAddResult
import com.qingqi.adskip.device.Vendor
import com.qingqi.adskip.ui.UiEffect
import com.qingqi.adskip.ui.vendorLabelRes
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
 * 持久化 / 网络 / 后台调度一律经 [com.qingqi.adskip.data.SettingsRepository] 访问
 * （边界契约：ui 层不直读 Prefs、不碰 SyncClient / SyncJobService）。
 * 单向数据流：状态入 [UiState]，一次性提示经 [effects] 下发。
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val serverUrlInput: String,
        val signingKeyInput: String = "",
        val certPinsInput: String = "",
        val syncing: Boolean = false,
        val syncResult: String? = null,
        val lastSyncAt: Long = 0L,
        val autoSync: Boolean = false,
        val dndEnabled: Boolean = false,
        val dndStartMinute: Int = 23 * 60,
        val dndEndMinute: Int = 7 * 60,
        val batteryExemption: BatteryExemption = BatteryExemption.UNKNOWN,
        /** 界面语言选择。 */
        val language: LanguageMode = LanguageMode.DEFAULT,
        /** 当前设备所属 ROM，用于保活引导文案与手动路径提示（device/VendorKeepAlive） */
        val keepAliveVendor: Vendor = Vendor.GENERIC,
    ) {
        /**
         * 已确认豁免电池优化。
         *
         * [BatteryExemption.UNKNOWN] 不算已豁免：不确定时不该对用户承诺后台不会被杀。
         */
        val isBatteryExempt: Boolean get() = batteryExemption == BatteryExemption.EXEMPT

        /**
         * 服务器地址为明文 http 时为 true（A 链告警：明文下规则可被 LAN 内改写，
         * 防篡改靠签名密钥而非传输保密；仍应提示用户尽快迁 HTTPS）。
         */
        val isCleartextServer: Boolean
            get() = serverUrlInput.trim().startsWith("http://", ignoreCase = true)
    }

    private val _uiState = MutableStateFlow(
        UiState(
            serverUrlInput = container.settingsRepo.serverUrl(),
            signingKeyInput = container.settingsRepo.rulesSigningKey(),
            certPinsInput = container.settingsRepo.certPins().joinToString("\n"),
            lastSyncAt = container.settingsRepo.lastSyncAt(),
            autoSync = container.settingsRepo.isAutoSyncEnabled(),
            dndEnabled = container.settingsRepo.isDoNotDisturbEnabled(),
            dndStartMinute = container.settingsRepo.getDoNotDisturbStart(),
            dndEndMinute = container.settingsRepo.getDoNotDisturbEnd(),
            batteryExemption = queryBatteryExemption(),
            language = LanguageMode.fromTag(container.settingsRepo.languageTag()),
            keepAliveVendor = KeepAliveNavigator.detectVendor(),
        ),
    )
    val uiState: StateFlow<UiState> = _uiState

    private val _effects = MutableSharedFlow<UiEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<UiEffect> = _effects

    fun onServerUrlChanged(value: String) {
        _uiState.value = _uiState.value.copy(serverUrlInput = value)
    }

    fun onSigningKeyChanged(value: String) {
        _uiState.value = _uiState.value.copy(signingKeyInput = value)
    }

    fun onCertPinsChanged(value: String) {
        _uiState.value = _uiState.value.copy(certPinsInput = value)
    }

    fun isValidServerUrl(url: String): Boolean = ServerEndpoint.isValid(url)

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

    /**
     * 保存规则签名密钥与证书指纹（安全段，A 链配套）。
     *
     * 证书指纹逐条校验 `sha256/` 前缀 + Base64 长度，防止把无关文本存进去
     * 后到同步时才以「无 pins」形式悄悄退化。
     */
    fun saveSecurityConfig(rawKey: String, rawPins: String): Boolean {
        val key = rawKey.trim()
        if (key.length < 16) {
            send(container.app.getString(R.string.settings_signing_key_too_short))
            return false
        }
        val pins = rawPins.split('\n', ',').map { it.trim() }.filter { it.isNotEmpty() }
        val bad = pins.filterNot(::isValidCertificatePin)
        if (bad.isNotEmpty()) {
            send(container.app.getString(R.string.settings_cert_pin_invalid, bad.first()))
            return false
        }
        container.settingsRepo.saveRulesSigningKey(key)
        container.settingsRepo.saveCertPins(pins)
        _uiState.value = _uiState.value.copy(signingKeyInput = key, certPinsInput = pins.joinToString("\n"))
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

    /**
     * 切换界面语言。
     *
     * 先落偏好再应用：API 26–32 没有平台 API，`LocaleApplier.apply` 只是把
     * 生效时机推迟到 Activity 重建，偏好必须先写，重建时才能读到。
     *
     * 平台路径（API 33+）会立即改所有 Activity 的资源，故返回 `true` 时
     * **不需要**重建，避免多余的闪烁。
     */
    fun setLanguage(mode: LanguageMode) {
        container.settingsRepo.setLanguageTag(mode.tag)
        val appliedImmediately = LocaleApplier.apply(container.app, mode)
        _uiState.value = _uiState.value.copy(language = mode)
        if (!appliedImmediately) {
            // 低版本需要重建 Activity 才能让新语言作用于资源解析。
            _effects.tryEmit(UiEffect.ShowMessage(container.app.getString(R.string.settings_saved)))
            _effects.tryEmit(UiEffect.RecreateActivity)
        }
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

    fun exportNodeSnapshot() {
        if (!AppEvents.serviceRunningSnapshot) {
            send(container.app.getString(R.string.settings_snapshot_empty))
            return
        }
        val context = container.app
        // ui/ 层禁止 import service/（ArchitectureBoundaryTest）：action 用字面量，
        // 与 SkipAdService.ACTION_EXPORT_SNAPSHOT 的一致性由 ServiceIntentContractTest 守护。
        val intent = Intent("com.qingqi.adskip.EXPORT_SNAPSHOT").setPackage(context.packageName)
        context.sendBroadcast(intent)
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

        internal fun isValidCertificatePin(pin: String): Boolean = certificatePinPattern.matches(pin)

        private val certificatePinPattern = Regex("sha256/[A-Za-z0-9+/]{42}[AEIMQUYcgkosw048]=")
    }
}
