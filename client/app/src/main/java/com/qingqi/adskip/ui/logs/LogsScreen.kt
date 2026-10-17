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
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.qingqi.adskip.data.StatsRepository
import com.qingqi.adskip.ui.Messenger
import com.qingqi.adskip.ui.components.ConfirmDialog
import com.qingqi.adskip.ui.components.EmptyState
import com.qingqi.adskip.ui.components.PageHeader
import com.qingqi.adskip.ui.components.TwoLineRow
import com.qingqi.adskip.ui.theme.Spacing
import com.qingqi.adskip.ui.theme.UiSizes
import com.qingqi.adskip.ui.theme.icons.AdSkipIcons

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
                    imageVector = AdSkipIcons.Delete,
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
                    imageVector = AdSkipIcons.Share,
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
                // 与「还没有记录」正好相反。EmptyLogs 用品牌 HUD 取景框承载「记录区为空」。
                icon = AdSkipIcons.EmptyLogs,
                title = stringResource(R.string.logs_empty),
                subtitle = stringResource(R.string.logs_empty_hint),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl)) {
                // key 必须含位序：ts 是毫秒粒度（Prefs.addLog 用 System.currentTimeMillis），
                // 同一应用同一标签在同一毫秒内被跳过两次时「ts:pkg:label」完全相同——
                // LazyColumn 遇到重复 key 会在布局期直接抛 IllegalArgumentException 崩溃，
                // 而日志页是底部导航的主 tab。key 只需在同一帧内唯一即可：本列表是
                // 「头部前插、不改序」的追加型列表，位序在同一份数据内自洽。
                itemsIndexed(state.logs, key = { index, entry -> logRowKey(index, entry) }) { _, entry ->
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

/**
 * 日志行的 LazyColumn key：内容三元组 + 位序，**提取成纯函数**以便单测守护注入性。
 *
 * 背景（崩溃路径）：`Prefs.addLog` 用 `System.currentTimeMillis()` 作 ts（毫秒粒度），
 * 同一应用同一标签在同一毫秒内被跳过两次（无障碍事件风暴下真实可达）时
 * 「ts:pkg:label」完全相同；LazyColumn 对重复 key 抛
 * `IllegalArgumentException: Key ... was already used`，直接崩在布局期。
 *
 * 为什么加位序就够：key 的契约只是「同一帧内唯一」。本列表是头部前插的追加型
 * 列表（`addLog` 永远插到最前、从不改序），位序在同一份数据内自洽；
 * 新记录前插时既有行 key 整体后移一位，代价仅是全部行重新组合（≤200 行，
 * 且 LazyColumn 只为可见行创建组合），远小于崩溃风险。
 *
 * 配套测试：`ui/logs/LogRowKeyTest`（构造内容完全相同的相邻条目断言 key 互异）。
 */
internal fun logRowKey(index: Int, entry: StatsRepository.LogEntry): String =
    "${entry.ts}:${entry.pkg}:${entry.label}:$index"
