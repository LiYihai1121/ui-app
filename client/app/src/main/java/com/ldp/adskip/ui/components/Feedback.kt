package com.ldp.adskip.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ldp.adskip.R
import com.ldp.adskip.ui.theme.Spacing
import com.ldp.adskip.ui.theme.UiSizes

/*
 * 通用 UI 组件第二批（v3.1 重设计后续）。
 *
 * [Common.kt] 收口了「卡片 / 标题 / 统计块」等静态元素，本文件收口
 * 各页面此前各自手写的**状态类**与**交互类**元素：
 * - [EmptyState]：空数据 / 搜索无结果的统一空态；
 * - [ConfirmDialog]：破坏性操作（清空日志等）的二次确认；
 * - [TwoLineRow]：主标题 + 副标题的两行列表行（日志条目 / 应用行共用）；
 * - [SkeletonList]：首屏加载骨架，替代孤零零的转圈；
 * - [InfoRow]：「标签 — 取值」信息行（原为「我的」页私有实现）。
 *
 * 用普通块注释而非 KDoc：这是「整个文件的说明」，写成 KDoc 会成为悬空注释。
 */

/**
 * 空状态占位：图标（可选）+ 标题 + 说明 + 动作（可选）。
 *
 * 旧版日志页空态只有一行灰字，用户分不清「没有记录」和「加载失败」；
 * 统一空态后语义由标题说结论、说明给下一步，动作按钮可选。
 * 图标允许为 null——core 图标集里没有贴切的「搜索无结果」图标，
 * 与其硬凑一个语义错误的图标，不如纯文字。
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(UiSizes.emptyStateIcon),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.lg))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(Spacing.lg))
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
 * 两行列表行：可选头像 + 主标题 + 副标题 + 可选尾随控件。
 *
 * 日志条目与应用行此前各自拼 Row/Column，间距、字号、省略策略都不一致；
 * 收口到这里后，两页的行高节奏与文本降级行为完全相同。
 * 主标题单行省略，副标题最多两行省略——包名与时间戳都可能超长。
 */
@Composable
fun TwoLineRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(vertical = Spacing.sm),
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
 * 首屏加载骨架：若干条「圆点头像 + 两行文本」的占位块。
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
) {
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
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    val brush = Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(shift * 400f - 200f, 0f),
        end = Offset(shift * 400f + 200f, rowHeight.value),
    )

    Column(
        modifier = modifier.fillMaxWidth(),
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
                .clip(CircleShape)
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

/** 「标签 — 取值」信息行；取值过长时省略而非撑破布局。 */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Spacing.lg),
        )
    }
}

/** [InfoRow] 之间的分隔线，颜色与卡片底色协调。 */
@Composable
fun InfoDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
