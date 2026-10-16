package com.qingqi.adskip.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qingqi.adskip.AdSkipApp
import com.qingqi.adskip.AppContainer
import com.qingqi.adskip.R
import com.qingqi.adskip.ui.UiEffect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 关键词管理页（底部导航「规则」）状态。
 *
 * 为什么单独建 ViewModel 而不是复用 [com.qingqi.adskip.ui.home.HomeViewModel]：
 * 两者虽然都读写同一份关键词存储，但**职责边界不同**——首页只把关键词数量当作
 * 一个统计数字，规则页要增删改查。复用会让首页 ViewModel 背上搜索框状态、
 * 删除对话框状态等只属于规则页的字段，最后两边都变成「什么都装一点」。
 *
 * 单一事实源仍在仓库层（`rulesRepo`）：两个 ViewModel 只是同一份数据的两个视图，
 * 不存在各自的副本，因此不会出现「首页删了、规则页还看得到」的不一致。
 */
class RulesViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(val keywords: List<String> = emptyList(), val query: String = "") {
        /** 搜索过滤后的可见列表；匹配关键词本身，大小写不敏感。 */
        val visible: List<String>
            get() {
                val q = query.trim().lowercase()
                return if (q.isEmpty()) keywords else keywords.filter { it.lowercase().contains(q) }
            }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    private val _effects = MutableSharedFlow<UiEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<UiEffect> = _effects

    /** 最近一次删除的关键词与其原索引，供撤销使用。只保留一份。 */
    private var lastRemoved: Pair<Int, String>? = null

    init {
        refresh()
    }

    fun setQuery(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
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
        refresh()
        send(R.string.keyword_added, kw)
    }

    /**
     * 按**值**删除而不是按索引。
     *
     * 列表在界面上是搜索过滤后的视图，其下标与仓库中的下标不是同一套；
     * 传索引会让「搜索后删除第二条」删掉仓库里的第二条（可能完全是另一个词）。
     */
    fun removeKeyword(keyword: String) {
        val current = container.rulesRepo.keywords()
        val index = current.indexOfFirst { it == keyword }
        if (index < 0) return
        val next = current.toMutableList().also { it.removeAt(index) }
        container.rulesRepo.saveKeywords(next)
        lastRemoved = index to keyword
        refresh()
        // 文案由界面侧生成（需带「撤销」），这里只报告发生了什么。
        // 用 tryEmit 而非 emit：本函数是普通（非 suspend）回调，界面此时
        // 尚未保证有收集者，emit 会在无订阅者时直接挂起。
        _effects.tryEmit(UiEffect.KeywordRemoved(keyword))
    }

    /** 撤销上一次删除：按原索引插回，避免顺序变化。 */
    fun undoRemoveKeyword() {
        val (index, keyword) = lastRemoved ?: return
        lastRemoved = null
        val current = container.rulesRepo.keywords()
        if (current.any { it.equals(keyword, ignoreCase = true) }) return
        val restored = current.toMutableList()
        restored.add(index.coerceIn(0, restored.size), keyword)
        container.rulesRepo.saveKeywords(restored)
        refresh()
    }

    private fun refresh() {
        _uiState.value = _uiState.value.copy(keywords = container.rulesRepo.keywords())
    }

    private fun send(resId: Int, vararg args: Any) {
        viewModelScope.launch {
            _effects.emit(UiEffect.ShowMessage(container.app.getString(resId, *args)))
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as AdSkipApp
                RulesViewModel(app.container)
            }
        }
    }
}
