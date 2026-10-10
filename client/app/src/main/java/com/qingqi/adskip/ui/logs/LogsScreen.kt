package com.qingqi.adskip.ui.logs

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingqi.adskip.R
import com.qingqi.adskip.ui.Messenger
import com.qingqi.adskip.ui.components.ConfirmDialog
import com.qingqi.adskip.ui.components.EmptyState
import com.qingqi.adskip.ui.components.PageHeader
import com.qingqi.adskip.ui.components.TwoLineRow
import com.qingqi.adskip.ui.theme.Spacing
import com.qingqi.adskip.ui.theme.UiSizes

/**
 * 跳过日志：最近 200 条自动跳过记录，支持清空与分享。
 *
 * 清空 / 分享从顶栏迁到标题下方的操作行：顶栏已由外壳统一管理，
 * 把动作留在内容流里更符合「标题 → 动作 → 内容」的阅读顺序。
 *
 * UX 约定（组件库补齐批次）：
 * - 空态走 [EmptyState]：图标 + 结论 + 下一步引导，替代旧版单行灰字；
 * - 清空是破坏性操作，必须经 [ConfirmDialog] 二次确认，防止误触一键清空；
 * - 条目行统一走 [TwoLineRow]，与应用管理页共享同一套行高与省略策略。
 */
@Composable
fun LogsScreen(messenger: Messenger, viewModel: LogsViewModel = viewModel(factory = LogsViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.reload() }

    if (showClearConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.logs_clear),
            message = stringResource(R.string.logs_clear_confirm),
            confirmText = stringResource(R.string.logs_clear),
            onConfirm = {
                showClearConfirm = false
                viewModel.clear()
                // 二次确认只是最后一道防线，这里再给一条可撤销的消息：
                // 「删除全部记录」是本应用唯一完全不可逆的操作，只靠确认弹窗
                // 意味着用户误点后没有任何退路。
                messenger.showUndoable(
                    message = context.getString(R.string.logs_cleared),
                    undoLabel = context.getString(R.string.action_undo),
                    onUndo = viewModel::undoClear,
                )
            },
            onDismiss = { showClearConfirm = false },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.logs_title),
            subtitle = pluralStringResource(R.plurals.logs_subtitle, state.logs.size, state.logs.size),
        )

        Row(
            modifier = Modifier.padding(horizontal = Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // 破坏性操作降级到最轻的 TextButton：清空不可逆，且无频率价值，
            // 不该和「分享」这种日常操作同权重并列在操作行里（视觉权重 = 出错概率）。
            TextButton(
                onClick = { showClearConfirm = true },
                enabled = state.logs.isNotEmpty(),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(UiSizes.inlineIcon),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.logs_clear))
            }
            FilledTonalButton(
                onClick = {
                    val text = viewModel.shareText()
                    if (text == null) {
                        messenger.show(context.getString(R.string.logs_empty))
                    } else {
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_SUBJECT,
                                        context.getString(R.string.logs_title),
                                    )
                                    putExtra(Intent.EXTRA_TEXT, text)
                                },
                                context.getString(R.string.logs_share),
                            ),
                        )
                    }
                },
                enabled = state.logs.isNotEmpty(),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(UiSizes.inlineIcon),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text(stringResource(R.string.logs_share))
            }
        }

        if (state.logs.isEmpty()) {
            EmptyState(
                // 此前用 Check（勾号）表示「暂无记录」：勾号在语义上是「已完成」，
                // 与「还没有记录」正好相反，会让用户怀疑自己看错了。改用中性列表符号。
                icon = Icons.AutoMirrored.Filled.List,
                title = stringResource(R.string.logs_empty),
                subtitle = stringResource(R.string.logs_empty_hint),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl)) {
                items(state.logs, key = { "${it.ts}:${it.pkg}:${it.label}" }) { entry ->
                    TwoLineRow(
                        title = entry.label,
                        // 走 stringResource 而非硬编码「 ｜ 」，否则英文环境仍显示中文分隔符，
                        // 且分隔符改动要同时改代码与译文两处
                        subtitle = stringResource(
                            R.string.logs_item_subtitle,
                            viewModel.formatTimestamp(entry.ts),
                            entry.pkg,
                        ),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
