package com.ldp.adskip.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ldp.adskip.R
import com.ldp.adskip.device.PermissionItem
import com.ldp.adskip.device.PermissionKeys
import com.ldp.adskip.device.PermissionLogic
import com.ldp.adskip.device.PermissionPresentation
import com.ldp.adskip.device.PermissionState
import com.ldp.adskip.ui.components.SectionCard
import com.ldp.adskip.ui.components.SectionHint
import com.ldp.adskip.ui.components.SectionTitle
import com.ldp.adskip.ui.components.TwoLineRow
import com.ldp.adskip.ui.theme.AdskipTheme
import com.ldp.adskip.ui.theme.Spacing

/**
 * 权限与系统开关状态卡片。
 *
 * 存在的理由：四类开关（无障碍 / 电池优化 / 自启动 / 磁贴）此前分散在首页状态环、
 * 「我的」页无障碍卡片与设置页三处，用户无法一眼看出「到底还差什么才能正常工作」。
 * 这里把它们收成一张**可核对清单**：每项显示真实状态，点右侧按钮直达对应系统页。
 *
 * 两条刻意的设计约束：
 *
 * 1. **UNKNOWN 不谎报为 DENIED**。厂商自启动无公开查询 API（见 [PermissionInspector]），
 *    此处显示「无法检测，请手动确认」而非「未开启」。后者会让用户在已开启的系统上
 *    被反复引导去设置页，而状态永远不变。
 *
 * 2. **跳转统一经 `KeepAliveNavigator`**，本文件不拼任何 `Intent`（边界契约：ui 层不碰系统跳转）。
 *
 * @param onOpenAccessibility / onOpenBattery / onOpenKeepAlive / onRequestTile 各项目的跳转回调；
 *        由宿主页（[ProfileScreen]）注入，失败时的提示也由它负责，保持卡片自身无副作用。
 */
@Composable
fun PermissionCard(
    items: List<PermissionItem>,
    readyCount: Int,
    needAttention: Boolean,
    supportsKeepAlive: Boolean,
    canRequestTile: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenKeepAlive: () -> Unit,
    onRequestTile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vendorSupported = supportsKeepAlive
    SectionCard(modifier = modifier) {
        SectionTitle(stringResource(R.string.perm_section))
        Spacer(Modifier.height(Spacing.sm))
        SectionHint(
            stringResource(
                if (needAttention) R.string.perm_summary_attention else R.string.perm_summary_ready,
                readyCount,
                items.size,
            ),
        )
        Spacer(Modifier.height(Spacing.md))

        // 四项的呈现决策全部走 PermissionLogic.present（纯函数，可 JVM 单测）；
        // 本文件只负责取文案与渲染，不做任何状态判断。
        PermissionRow(
            item = items.firstOrNull { it.key == PermissionKeys.ACCESSIBILITY },
            titleRes = R.string.perm_item_accessibility,
            descRes = R.string.perm_item_accessibility_desc,
            supported = true,
            actionLabel = stringResource(R.string.perm_open),
            onAction = onOpenAccessibility,
        )
        HorizontalDivider(Modifier.padding(vertical = Spacing.xs))

        PermissionRow(
            item = items.firstOrNull { it.key == PermissionKeys.BATTERY },
            titleRes = R.string.perm_item_battery,
            descRes = R.string.perm_item_battery_desc,
            supported = true,
            actionLabel = stringResource(R.string.perm_open),
            onAction = onOpenBattery,
        )
        HorizontalDivider(Modifier.padding(vertical = Spacing.xs))

        PermissionRow(
            item = items.firstOrNull { it.key == PermissionKeys.VENDOR_KEEPALIVE },
            titleRes = R.string.perm_item_keepalive,
            descRes = R.string.perm_item_keepalive_desc,
            supported = vendorSupported,
            actionLabel = stringResource(R.string.perm_open),
            onAction = onOpenKeepAlive,
        )
        HorizontalDivider(Modifier.padding(vertical = Spacing.xs))

        // 磁贴在低版本系统上不能由应用主动添加（仅能手动拖入），
        // 故按钮退化为「手动添加」提示，与 KeepAliveNavigator.canRequestAddTile 同源判定。
        PermissionRow(
            item = items.firstOrNull { it.key == PermissionKeys.QUICK_TILE },
            titleRes = R.string.perm_item_tile,
            descRes = R.string.perm_item_tile_desc,
            supported = true,
            actionLabel = stringResource(
                if (canRequestTile) R.string.perm_tile_request else R.string.perm_open,
            ),
            onAction = onRequestTile,
        )
    }
}

@Composable
private fun PermissionRow(
    item: PermissionItem?,
    titleRes: Int,
    descRes: Int,
    supported: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val state = item?.state ?: PermissionState.UNKNOWN
    val presentation = PermissionLogic.present(
        key = item?.key ?: "",
        state = state,
        supported = supported,
        readyText = stringResource(R.string.perm_state_ready),
        deniedText = stringResource(R.string.perm_state_denied),
        unknownText = stringResource(R.string.perm_state_unknown),
        unsupportedText = stringResource(R.string.perm_state_unsupported),
    )

    TwoLineRow(
        title = stringResource(titleRes),
        subtitle = stringResource(descRes),
        leading = { StatusDot(ready = presentation.ready, statusText = presentation.statusText) },
        trailing = {
            if (presentation.actionable) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        },
    )
}

/**
 * 状态圆点。
 *
 * 用颜色 + 形状（圆 / 空心环）**双重**编码：只靠颜色区分对色觉障碍用户不可读，
 * 而这是判断「功能是否生效」的唯一视觉线索，误判成本很高。
 * 状态文字由 `TwoLineRow` 的尾部按钮与副标题承载，圆点本身对无障碍服务隐藏
 * （`clearAndSetSemantics`），避免读屏念出无意义的「圆点」。
 */
@Composable
private fun StatusDot(ready: Boolean, statusText: String) {
    val color = if (ready) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .then(if (ready) Modifier.background(color) else Modifier.border(1.5.dp, color, CircleShape))
            .clearAndSetSemantics { },
    )
}

@Preview(showBackground = true)
@Composable
private fun PermissionCardPreview() {
    AdskipTheme {
        PermissionCard(
            items = listOf(
                PermissionItem(PermissionKeys.ACCESSIBILITY, PermissionState.GRANTED),
                PermissionItem(PermissionKeys.BATTERY, PermissionState.DENIED),
                PermissionItem(PermissionKeys.VENDOR_KEEPALIVE, PermissionState.UNKNOWN),
                PermissionItem(PermissionKeys.QUICK_TILE, PermissionState.DENIED),
            ),
            readyCount = 1,
            needAttention = true,
            supportsKeepAlive = true,
            canRequestTile = true,
            onOpenAccessibility = {},
            onOpenBattery = {},
            onOpenKeepAlive = {},
            onRequestTile = {},
        )
    }
}
