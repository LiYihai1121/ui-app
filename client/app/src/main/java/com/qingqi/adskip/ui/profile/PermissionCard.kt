package com.qingqi.adskip.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.qingqi.adskip.R
import com.qingqi.adskip.device.PermissionItem
import com.qingqi.adskip.device.PermissionKeys
import com.qingqi.adskip.device.PermissionStatus
import com.qingqi.adskip.ui.components.SectionCard
import com.qingqi.adskip.ui.components.SectionHint
import com.qingqi.adskip.ui.components.SectionTitle
import com.qingqi.adskip.ui.components.StatusDot
import com.qingqi.adskip.ui.components.TwoLineRow
import com.qingqi.adskip.ui.theme.Spacing

/**
 * 权限与系统开关清单卡。
 *
 * 存在的理由：四类开关（无障碍 / 电池优化 / 厂商自启动 / 快捷磁贴）此前分散在首页状态环、
 * 本页无障碍卡片与设置页三处，用户无法一眼看出「到底还差什么才能正常工作」。
 * 这里把它们收成一张**可核对清单**：每项给出真实状态与直达入口。
 *
 * 两条刻意的设计约束（与 device/PermissionCenter 的判定一致）：
 *
 * 1. **UNKNOWN 不谎报为「需要处理」**。厂商自启动没有公开查询 API，磁贴也无法反查
 *    是否已添加；谎报会让用户在系统页反复来回而状态永远不变。故这类项显示
 *    「无法自动确认，请手动核对」并且**仍然给出入口**——否则「无法确认」会变成死路。
 *
 * 2. **本文件不做任何状态判定**，只按 [PermissionItem.status] 取文案与渲染；
 *    判定全在 `device/PermissionCenter`（纯函数，有 JVM 单测）。
 *
 * @param onOpen 各项目的跳转回调，由宿主页注入；失败提示也由宿主负责，卡片自身无副作用。
 */
@Composable
fun PermissionCard(
    items: List<PermissionItem>,
    readyCount: Int,
    onOpen: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    SectionCard(modifier = modifier) {
        SectionTitle(stringResource(R.string.permission_section))
        Spacer(Modifier.height(Spacing.sm))
        SectionHint(stringResource(R.string.permission_summary, readyCount, items.size))
        Spacer(Modifier.height(Spacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            items.forEachIndexed { index, item ->
                PermissionRow(item = item, onOpen = onOpen)
                if (index != items.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(item: PermissionItem, onOpen: (key: String) -> Unit) {
    // 无障碍行不给行内按钮：宿主页紧随其后就是无障碍卡片，它带同一跳转且有更完整的
    // 说明文案。同一屏里两个同样标的按钮会让用户以为是两件事。
    // 行本身仍完整呈现在清单里——状态是清单的职责，入口归无障碍卡片。
    val showAction = item.actionable && item.key != PermissionKeys.ACCESSIBILITY
    TwoLineRow(
        title = stringResource(item.titleRes()),
        subtitle = stringResource(item.statusTextRes()),
        leading = { StatusDot(ready = item.status == PermissionStatus.READY) },
        trailing = {
            if (showAction) {
                TextButton(onClick = { onOpen(item.key) }) {
                    // 刻意用短文案（permission_open）：本行给标题列 weight(1f)，
                    // 按钮过长会把标题挤成单字符——实测 "Open Auto-start / Background
                    // Manager" 让标题只剩约 210px（1080px 屏），读起来是 "B..."。
                    Text(stringResource(R.string.permission_open))
                }
            }
        },
    )
}

/** 稳定 key → 本地化标题。判定层不持有资源 id，故映射放在 UI 侧。 */
private fun PermissionItem.titleRes(): Int = when (key) {
    PermissionKeys.ACCESSIBILITY -> R.string.permission_accessibility
    PermissionKeys.BATTERY -> R.string.permission_battery
    PermissionKeys.VENDOR_KEEPALIVE -> R.string.permission_vendor_keepalive
    PermissionKeys.QUICK_TILE -> R.string.permission_quick_tile
    else -> R.string.permission_section
}

/** 状态 → 本地化描述。UNKNOWN 明确说「无法自动确认」，不冒充「需要处理」。 */
private fun PermissionItem.statusTextRes(): Int = when (status) {
    PermissionStatus.READY -> R.string.permission_state_ready
    PermissionStatus.NEEDS_ACTION -> R.string.permission_state_needs_action
    PermissionStatus.UNKNOWN -> R.string.permission_state_unknown
}
