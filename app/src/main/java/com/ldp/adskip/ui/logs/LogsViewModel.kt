package com.ldp.adskip.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ldp.adskip.AdskipApp
import com.ldp.adskip.AppContainer
import com.ldp.adskip.data.StatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 跳过日志页状态：最近 200 笔自动跳过记录。
 */
class LogsViewModel(private val container: AppContainer) : ViewModel() {

    private val _logs = MutableStateFlow<List<StatsRepository.LogEntry>>(emptyList())
    val logs: StateFlow<List<StatsRepository.LogEntry>> = _logs

    init {
        reload()
    }

    fun reload() {
        _logs.value = container.statsRepo.logs()
    }

    fun clear() {
        container.statsRepo.clearLogs()
        reload()
    }

    /** 生成分享文本；无记录时返回 null（由界面提示） */
    fun shareText(): String? {
        val text = container.statsRepo.logs().joinToString("\n") { entry ->
            "${formatTimestamp(entry.ts)}\t${entry.label}\t${entry.pkg}"
        }
        return text.ifBlank { null }
    }

    fun formatTimestamp(ts: Long): String =
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ts))

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AdskipApp
                LogsViewModel(app.container)
            }
        }
    }
}
