package com.qingqi.adskip.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qingqi.adskip.AdskipApp
import com.qingqi.adskip.AppContainer
import com.qingqi.adskip.data.StatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 跳过日志页状态：最近 200 笔自动跳过记录。
 *
 * 单向数据流：列表聚合为一个 [UiState]（本页无一次性 Effect）。
 */
class LogsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(val logs: List<StatsRepository.LogEntry> = emptyList())

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    /** 最近一次 [clear] 前的完整快照，供撤销使用；只保留一份，后一次清空覆盖前一次。 */
    private var clearedSnapshot: List<StatsRepository.LogEntry>? = null

    init {
        reload()
    }

    fun reload() {
        _uiState.value = UiState(container.statsRepo.logs())
    }

    fun clear() {
        // 清空前留一份快照：清空是本应用唯一完全不可逆的操作，只有给出「撤销」
        // 才配得上「删除全部记录」这种后果。快照仅存在内存，进程结束即失效，
        // 因此撤销窗口就是 Snackbar 展示的那几秒——这与 Material 对可撤销操作的预期一致。
        clearedSnapshot = container.statsRepo.logs()
        container.statsRepo.clearLogs()
        reload()
    }

    /** 撤销上一次 [clear]；无快照或快照已过期时为空操作。 */
    fun undoClear() {
        val snapshot = clearedSnapshot ?: return
        clearedSnapshot = null
        container.statsRepo.restoreLogs(snapshot)
        reload()
    }

    /** 生成分享文本；无记录时返回 null（由界面提示） */
    fun shareText(): String? {
        val text = container.statsRepo.logs().joinToString("\n") { entry ->
            "${formatTimestamp(entry.ts)}\t${entry.label}\t${entry.pkg}"
        }
        return text.ifBlank { null }
    }

    fun formatTimestamp(ts: Long): String = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ts))

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AdskipApp
                LogsViewModel(app.container)
            }
        }
    }
}
