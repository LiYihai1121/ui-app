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
import com.ldp.adskip.core.LogRing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 应用管理页状态：可启动应用列表 + 每应用的跳过开关与次数。
 *
 * 单向数据流：列表与加载态聚合为一个 [UiState]（本页无一次性 Effect）。
 */
class AppsViewModel(private val container: AppContainer) : ViewModel() {

    data class AppRow(val pkg: String, val label: String, val icon: Drawable?, val disabled: Boolean, val count: Int)

    data class UiState(
        val items: List<AppRow> = emptyList(),
        val loading: Boolean = true,
        val failed: Boolean = false,
        val query: String = "",
        val onlyEnabled: Boolean = false,
    ) {
        /** 搜索 + 筛选后的可见列表；匹配包名与应用名，大小写不敏感。 */
        val visibleItems: List<AppRow>
            get() {
                val q = query.trim().lowercase()
                return items.filter { row ->
                    (onlyEnabled.not() || row.disabled.not()) &&
                        (
                            q.isEmpty() ||
                                row.label.lowercase().contains(q) ||
                                row.pkg.lowercase().contains(q)
                            )
                }
            }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun setOnlyEnabled(onlyEnabled: Boolean) {
        _uiState.update { it.copy(onlyEnabled = onlyEnabled) }
    }

    /**
     * 离开本页时清掉临时筛选态。
     *
     * 底部导航用了 `saveState/restoreState`，因此页面 ViewModel 实例会跨 tab 存活：
     * 用户搜过某个应用、切去别的页再回来，搜索框里还留着上次的关键词，
     * 「只看已启用」也还开着——列表凭空少了一大半而没有任何提示，
     * 绝大多数用户会当成 bug。搜索与筛选属于**视图级临时状态**，不是会话级偏好，
     * 离开就该重置；真正需要跨会话保留的是「跳过开关」这类真实配置，
     * 它们存在仓库层，不受此影响。
     *
     * 由页面在离开时调用（见 [com.ldp.adskip.ui.apps.AppsScreen] 的 DisposableEffect）。
     */
    fun clearTransientFilters() {
        _uiState.update { if (it.query.isEmpty() && !it.onlyEnabled) it else it.copy(query = "", onlyEnabled = false) }
    }

    init {
        load()
    }

    /** 在 IO 线程枚举启动器应用并读取规则/统计，回主线程提交。 */
    fun load() {
        _uiState.value = _uiState.value.copy(loading = true, failed = false)
        viewModelScope.launch {
            val rows = try {
                withContext(Dispatchers.IO) {
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
                                icon = try {
                                    it.loadIcon(pm)
                                } catch (_: Exception) {
                                    null
                                },
                                disabled = container.rulesRepo.isDisabled(pkg),
                                count = container.statsRepo.countFor(pkg),
                            )
                        }
                        .sortedBy { it.label.lowercase() }
                }
            } catch (e: Exception) {
                // 查询失败必须落地成一个**可呈现的错误态**，而不是让协程静默中断：
                // 否则 loading 永远停在 true，用户只看到不会消失的骨架屏，
                // 既不知道出了错，也没有重试入口。
                LogRing.e("AppsViewModel", "枚举启动器应用失败: ${e.message}")
                _uiState.value = _uiState.value.copy(loading = false, failed = true)
                return@launch
            }
            _uiState.value = _uiState.value.copy(items = rows, loading = false, failed = false)
        }
    }

    fun setEnabled(pkg: String, enabled: Boolean) {
        container.rulesRepo.setDisabled(pkg, !enabled)
        _uiState.value = _uiState.value.copy(
            items = _uiState.value.items.map {
                if (it.pkg == pkg) it.copy(disabled = !enabled) else it
            },
        )
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
