package com.qingqi.adskip.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qingqi.adskip.R
import com.qingqi.adskip.ui.theme.Spacing
import com.qingqi.adskip.ui.theme.UiSizes

/*
 * 通用 UI 组件第二批：**状态类**与**交互类**元素。
 *
 * [Common.kt] 收口了「卡片 / 标题 / 列表行」等静态元素，本文件收口各页面此前
 * 各自手写的状态呈现：
 * - [EmptyState] / [ErrorState]：空数据与失败，两者必须分开（下一步动作完全不同）；
 * - [ConfirmDialog]：破坏性操作的二次确认；
 * - [TwoLineRow]：主标题 + 副标题的两行列表行；
 * - [SkeletonList]：首屏加载骨架，替代孤零零的转圈；
 *
 * 用普通块注释而非 KDoc：这是「整个文件的说明」，写成 KDoc 会成为悬空注释。
 */

/**
 * 空状态占位：图标（可选）+ 标题 + 说明 + 动作（可选）。
 *
 * 旧版日志页空态只有一行灰字，用户分不清「没有记录」和「加载失败」；
 * 统一空态后语义由标题说结论、说明给下一步，动作按钮可选。
 *
 * 重构改动：图标从 48dp 灰线稿改为**托在品牌底衬上的 64dp 图标**。
 * 空态整屏只有一处内容，用 48dp 灰线稿会让页面看起来像「渲染失败的白屏」；
 * 给它品牌底衬后空态成为页面的一部分而不是缺口。这是大厂空态的一致做法
 * （Ant Design、TDesign 的空态插图都带容器底衬）。
 */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxl, vertical = Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(UiSizes.stateIllustration)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(UiSizes.stateIllustrationIcon),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(Spacing.xl))
            action()
        }
    }
}

/**
 * 错误状态占位：图标 + 结论 + 原因/下一步 + 重试动作。
 *
 * 为什么必须与 [EmptyState] 分开：两者对用户意味着完全不同的下一步。
 * 「暂无内容」是终态，用户该去做别的事；「读取失败」是可恢复态，用户该重试。
 * 此前全 app 只有空态，于是加载失败会显示成「暂无内容」——用户既不知道出了错，
 * 也不知道重试有用。
 *
 * 无障碍：`error()` 语义把这一块整体标为错误区域，读屏会先报「错误」再读内容，
 * 不必依赖图形与红色。图标是纯装饰（`contentDescription = null`），
 * 语义由标题承担，避免图标与标题重复播报。
 */
@Composable
fun ErrorState(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { error(title) }
            .padding(horizontal = Spacing.xxl, vertical = Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(UiSizes.stateIllustration)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.errorContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(UiSizes.stateIllustrationIcon),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(Modifier.height(Spacing.lg))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(Spacing.xl))
            action()
        }
    }
}

/**
 * 破坏性操作的二次确认对话框。
 *
 * 确认按钮走 `error` 色：清空日志这类不可逆操作，
 * 视觉上必须与普通的「确定」区分开。文案按钮沿用全局 btn_confirm / btn_cancel。
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = stringResource(R.string.btn_confirm),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
    )
}

/**
 * 两行列表行：可选头像/底衬 + 主标题 + 副标题 + 可选尾随控件。
 *
 * 日志条目与应用行此前各自拼 Row/Column，间距、字号、省略策略都不一致；
 * 收口到这里后，两页的行高节奏与文本降级行为完全相同。
 * 主标题单行省略，副标题最多两行省略——包名与时间戳都可能超长。
 *
 * 重构改动：行内边距从 8dp 垂直、0 水平 改为 12/20，行高从约 60dp 提到约 72dp。
 * 旧行高按 Material 的 dense 规格（56~64dp）取，但本应用的行副标题常是
 * 「08-16 21:03:41 ｜ com.tencent.mm」这种长文本，密排后在中文下几乎没有呼吸空间。
 */
@Composable
fun TwoLineRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.lg2, vertical = Spacing.md),
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Spacing.md))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(Spacing.md))
            trailing()
        }
    }
}

/**
 * 首屏加载骨架：若干条「圆角底衬 + 两行文本」的占位块。
 *
 * 为什么不用 `CircularProgressIndicator`：转圈只传达「在等」，
 * 不传达「等到的东西长什么样」；应用列表结构固定（图标 + 名称 + 状态），
 * 骨架能让用户在加载期就建立布局预期，感知等待时间更短。
 *
 * 实现说明：微光扫过用无限动画驱动 `Brush.linearGradient` 的起点偏移，
 * 纯 DrawScope 渐变，不引入 shimmer 三方库（依赖清单保持零外部依赖）。
 */
@Composable
fun SkeletonList(
    modifier: Modifier = Modifier,
    rows: Int = 8,
    rowHeight: Dp = UiSizes.skeletonRow,
    avatarSize: Dp = UiSizes.listIcon,
    loadingLabel: String = stringResource(R.string.state_loading_announcement),
) {
    // 骨架块本身是无数值的空节点，读屏会把整片骨架读成空白。
    // `progressBarRangeInfo` 让读屏播报「忙碌中」而不是逐个空块；范围取不确定态
    // （0..1 且当前值等于起点），契合「长度未知的加载」这一真实语义。
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "skeleton-shift",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(shift * 400f - 200f, 0f),
        end = Offset(shift * 400f + 200f, rowHeight.value),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = loadingLabel
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
            },
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        repeat(rows) {
            SkeletonRow(brush = brush, height = rowHeight, avatarSize = avatarSize)
        }
    }
}

@Composable
private fun SkeletonRow(brush: Brush, height: Dp, avatarSize: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(avatarSize)
                .clip(MaterialTheme.shapes.small)
                .background(brush),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth(0.55f)
                    .height(UiSizes.skeletonLine)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(brush),
            )
            Spacer(Modifier.height(Spacing.sm))
            Box(
                Modifier
                    .fillMaxWidth(0.35f)
                    .height(UiSizes.skeletonLineCompact)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(brush),
            )
        }
    }
}

// 「标签 — 取值」信息行已迁移到 Common.kt 的 InfoRow（与 v4 行样式统一）。
// RowDivider / FullDivider 同样见 Common.kt。
