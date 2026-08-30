package com.ldp.adskip.ui.apps

import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.ldp.adskip.AdskipApp
import com.ldp.adskip.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 应用管理页状态：可启动应用列表 + 每应用的跳过开关与次数。
 */
class AppsViewModel(private val container: AppContainer) : ViewModel() {

    data class AppRow(
        val pkg: String,
        val label: String,
        val icon: Drawable?,
        val disabled: Boolean,
        val count: Int
    )

    private val _items = MutableStateFlow<List<AppRow>>(emptyList())
    val items: StateFlow<List<AppRow>> = _items

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading

    init {
        load()
    }

    /** 在 IO 线程枚举启动器应用并读取规则/统计，回主线程提交。 */
    fun load() {
        _loading.value = true
        viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) {
                val pm = container.app.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(intent, 0)
                    .distinctBy { it.activityInfo.packageName }
                    .filter { it.activityInfo.packageName != container.app.packageName }
                    .map {
                        val pkg = it.activityInfo.packageName
                        AppRow(
                            pkg = pkg,
                            label = it.loadLabel(pm).toString(),
                            icon = try { it.loadIcon(pm) } catch (_: Exception) { null },
                            disabled = container.rulesRepo.isDisabled(pkg),
                            count = container.statsRepo.countFor(pkg)
                        )
                    }
                    .sortedBy { it.label.lowercase() }
            }
            _items.value = rows
            _loading.value = false
        }
    }

    fun setEnabled(pkg: String, enabled: Boolean) {
        container.rulesRepo.setDisabled(pkg, !enabled)
        _items.value = _items.value.map {
            if (it.pkg == pkg) it.copy(disabled = !enabled) else it
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AdskipApp
                AppsViewModel(app.container)
            }
        }
    }
}
