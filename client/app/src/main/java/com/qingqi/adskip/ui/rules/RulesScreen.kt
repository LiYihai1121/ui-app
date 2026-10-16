package com.qingqi.adskip.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingqi.adskip.R
import com.qingqi.adskip.ui.Messenger
import com.qingqi.adskip.ui.UiEffect
import com.qingqi.adskip.ui.components.EmptyState
import com.qingqi.adskip.ui.components.InfoBanner
import com.qingqi.adskip.ui.components.KeywordRow
import com.qingqi.adskip.ui.components.PageHeader
import com.qingqi.adskip.ui.components.RowDivider
import com.qingqi.adskip.ui.components.SearchField
import com.qingqi.adskip.ui.components.SectionCard
import com.qingqi.adskip.ui.components.SectionTitle
import com.qingqi.adskip.ui.theme.Spacing
import com.qingqi.adskip.ui.theme.icons.AdSkipIcons

/**
 * 关键词管理（底部导航「规则」）。
 *
 * 这是 v4 设计稿里**新独立出来的一级页**：v3 把关键词编辑塞在首页卡片里，
 * 结果是「看状态」与「改配置」两件事共用一屏，首屏既不够干净、关键词多了又难管。
 *
 * 页面结构：标题（含「添加关键词」动作）→ 搜索 → 提示条 → 关键词列表。
 * 列表**整组一张卡**、行间一条分隔线——这是设计稿的做法，也是移动端管理列表的通用形态。
 *
 * 「系统」标签的判定：关键词若同时出现在 `R.array.default_keywords` 里，
 * 就标为系统内置。这样标签表达的是**数据的来源**而不是装饰——
 * 用户能一眼看出哪些词是自己加的、哪些是应用预置的。
 */
@Composable
fun RulesScreen(messenger: Messenger, viewModel: RulesViewModel = viewModel(factory = RulesViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 系统内置关键词：用于给行加「系统」标签。
    // 先取数组再 remember 成 Set：`stringArrayResource` 每次重组都返回新数组，
    // 直接 `.toSet()` 会让 Set 每帧都是新实例，进而让下游的成员判断无法复用。
    val systemArray = stringArrayResource(R.array.default_keywords)
    val systemKeywords = remember(systemArray) { systemArray.toSet() }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(messenger) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is UiEffect.ShowMessage -> messenger.show(effect.message)

                is UiEffect.KeywordRemoved -> messenger.showUndoable(
                    message = context.getString(R.string.keyword_removed, effect.keyword),
                    undoLabel = context.getString(R.string.action_undo),
                    onUndo = viewModel::undoRemoveKeyword,
                )

                is UiEffect.RecreateActivity -> Unit
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.rules_title),
            subtitle = stringResource(R.string.rules_subtitle),
            actionLabel = stringResource(R.string.rules_add),
            onAction = { showAddDialog = true },
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                SearchField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    placeholder = stringResource(R.string.rules_search_hint),
                )
            }

            item { InfoBanner(text = stringResource(R.string.rules_banner)) }

            item {
                SectionTitle(stringResource(R.string.rules_count, state.keywords.size))
            }

            if (state.visible.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(
                            if (state.keywords.isEmpty()) R.string.rules_empty_title else R.string.rules_no_match_title,
                        ),
                        subtitle = stringResource(R.string.rules_empty_hint),
                        icon = AdSkipIcons.Tag,
                        action = {
                            Button(onClick = { showAddDialog = true }) {
                                Text(stringResource(R.string.rules_add))
                            }
                        },
                    )
                }
            } else {
                item {
                    SectionCard(contentPadding = PaddingValues(vertical = Spacing.xs)) {
                        state.visible.forEachIndexed { index, keyword ->
                            KeywordRow(
                                keyword = keyword,
                                systemTag = if (keyword in systemKeywords) {
                                    stringResource(R.string.rules_system_tag)
                                } else {
                                    null
                                },
                                deleteLabel = stringResource(R.string.keyword_remove_cd, keyword),
                                onDelete = { viewModel.removeKeyword(keyword) },
                            )
                            if (index != state.visible.lastIndex) {
                                RowDivider()
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.rules_footer_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
            }
        }
    }

    if (showAddDialog) {
        AddKeywordDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { keyword ->
                viewModel.addKeyword(keyword)
                showAddDialog = false
            },
        )
    }
}

/** 添加关键词对话框：单行输入 + 取消 / 添加。 */
@Composable
private fun AddKeywordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rules_add)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.keyword_input_hint)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(input) },
                enabled = input.isNotBlank(),
            ) {
                Text(stringResource(R.string.btn_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
    )
}
