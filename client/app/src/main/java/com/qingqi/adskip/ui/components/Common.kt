package com.qingqi.adskip.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.qingqi.adskip.ui.theme.Spacing
import com.qingqi.adskip.ui.theme.UiSizes
import com.qingqi.adskip.ui.theme.icons.AdSkipIcons

/*
 * v4 设计稿的组件库。
 *
 * 与 v3 的关系：v3 的「分组列表卡 + 区块标签」体系被整体替换。v4 设计稿走的是
 * 「白卡浮在浅灰蓝底上 + 卡片内部自成一组的列表」，分组靠**卡片边界**而不是
 * 靠间距差，因此 GroupedListCard / SectionLabel 那套（用间距表达从属关系）
 * 在本版里不再需要——同一屏只有一种卡片容器，行与行之间用 [RowDivider]。
 *
 * 保留的建筑原则（这两条不受设计稿影响，是工程约束）：
 *  1. 颜色一律走 `MaterialTheme.colorScheme` 或 `StatusColors`，组件内不出现色值字面量；
 *  2. 尺寸一律走 [Spacing] / [UiSizes]，不出现标度外的 dp 字面量（UiContractTest 强制）。
 */

// ==================== 容器 ====================

/**
 * 统一卡片容器：白底 + 16dp 圆角 + 极淡投影。
 *
 * 与 v3 的差别只有两处——容器色由「容器层色阶」改为**纯白**、并恢复 2dp 投影。
 * 设计稿的层次是「浅灰蓝页面 + 白卡片」，靠明度对比而不是靠 M3 的逐级抬亮；
 * 投影则是设计稿明确画出来的（v3 按 M3 2023 规范去掉了阴影）。
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = UiSizes.cardElevation),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** 卡片内的行分隔线：内缩到与文本左边界对齐（Material 列表规范）。 */
@Composable
fun RowDivider(modifier: Modifier = Modifier, startIndent: Dp = Spacing.lg) {
    Box(modifier = modifier.fillMaxWidth().padding(start = startIndent)) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = UiSizes.hairline,
        )
    }
}

// ==================== 标题 ====================

/**
 * 首页头部：品牌图标 + 应用名 + 一句说明 + 可选右上动作。
 *
 * 为什么首页用带品牌图标的头部而不是普通页面标题：设计稿把首页当成产品的
 * 「门面」——用户每次打开先看到的不是「首页」两个字，而是品牌标识与它承诺的效果。
 * 其余三页仍用普通标题（见 [PageHeader]）。
 */
@Composable
fun AppHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
    trailingLabel: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 品牌标识：蓝底圆角方块 + 盾形。
        // 见交付说明的「图标替换差异」：设计稿此处是闪电，项目图标库无闪电语素，
        // 改用表意「防护」的 Shield，避免为一个装饰图形引入新语素。
        Box(
            modifier = Modifier
                .size(UiSizes.appIcon)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AdSkipIcons.Shield,
                contentDescription = null,
                modifier = Modifier.size(UiSizes.rowIcon),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingIcon != null && onTrailingClick != null) {
            IconButton(onClick = onTrailingClick) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = trailingLabel,
                    modifier = Modifier.size(UiSizes.rowIcon),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 页面标题（内容流内的第一项，无顶栏）。
 *
 * [onBack] 非空时在标题左侧渲染返回按钮：设计稿的「关键词管理」「应用管理」
 * 是从首页进入的**二级页**，需要显式返回（系统返回键在，但可见入口不能省）。
 * [onAction] 用于设计稿里标题右侧的「＋ 添加关键词」这类页面级动作。
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionLabel: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = AdSkipIcons.ChevronLeft,
                    contentDescription = backLabel,
                    modifier = Modifier.size(UiSizes.rowIcon),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        if (onAction != null && actionLabel != null) {
            Spacer(Modifier.width(Spacing.sm))
            Button(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs),
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            }
        }
    }
}

/** 卡片内的小节标题（带 `heading()`，读屏可逐段跳读）。 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics { heading() },
    )
}

/** 卡片内的说明性正文。 */
@Composable
fun SectionHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

// ==================== 控件 ====================

/**
 * 分段选择（SegmentedTabs）。
 *
 * 取代 v3 的 `SegmentedFilter`：设计稿的分段控件是**实心胶囊**（选中项蓝底白字），
 * 不是下划线式 tab。实心胶囊在深色页上同样成立，这是它能同时服务
 * 「应用管理」（浅色）与「跳过记录」（深色）两页的原因。
 *
 * 颜色参数显式暴露而不是内部写死：深色页需要一套反色取值，
 * 内部写死会让调用方只能靠 `Modifier.background` 覆盖，反而更脏。
 */
@Composable
fun <T> SegmentedTabs(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    labelOf: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    selectedContainerColor: Color = MaterialTheme.colorScheme.primary,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(containerColor)
            .padding(Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(UiSizes.segmentHeight)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (isSelected) selectedContainerColor else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = labelOf(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) selectedContentColor else contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 搜索框（填充式，无边框，高 44dp）。
 *
 * 用 [BasicTextField] 而不是 `TextField`：M3 的填充式输入框最小高度是 56dp，
 * 强制压到设计稿的 44dp 会裁掉文字基线。自己搭装饰盒才能既拿到准确高度、
 * 又保住「左放大镜 + 占位文案」的正确对齐。
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    hintColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(UiSizes.searchField)
            .clip(MaterialTheme.shapes.small)
            .background(containerColor)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = AdSkipIcons.Search,
            contentDescription = null,
            modifier = Modifier.size(UiSizes.inlineIcon),
            tint = hintColor,
        )
        Spacer(Modifier.width(Spacing.sm))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = hintColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = contentColor,
                    fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                ),
                cursorBrush = SolidColor(cursorColor),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 提示条：浅底 + 信息图标 + 说明。 */
@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(containerColor)
            .padding(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = AdSkipIcons.Info,
            contentDescription = null,
            modifier = Modifier.size(UiSizes.inlineIcon),
            tint = contentColor,
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
        )
    }
}

// ==================== 图标底衬与徽章 ====================

/** 圆角方形图标底衬（列表行 / 快捷瓦片共用）。 */
@Composable
fun IconPlate(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = UiSizes.iconPlate,
    glyphSize: Dp = UiSizes.iconPlateGlyph,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(glyphSize),
            tint = contentColor,
        )
    }
}

/** 胶囊标签（设计稿的「系统」tag、状态徽章等）。 */
@Composable
fun TagBadge(text: String, modifier: Modifier = Modifier, containerColor: Color, contentColor: Color) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(containerColor)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
        )
    }
}

// ==================== 行 ====================

/**
 * 设置行：图标 + 标题（+ 副标题）+ 可选尾部内容 + 右向指示符。整行可点。
 *
 * 无障碍：整行是**一个** `Role.Button` 节点，读屏一次念完「标题，副标题，按钮」。
 */
@Composable
fun SettingRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconPlate(icon = icon)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Spacer(Modifier.width(Spacing.xs))
        }
        if (onClick != null) {
            Icon(
                imageVector = AdSkipIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(UiSizes.chevron),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/**
 * 开关行：图标 + 标题（+ 副标题）+ 开关。整行可点。
 *
 * 无障碍：整行是**一个** `Role.Switch` 节点（`toggleable` + 合并子节点），
 * 标题与状态一次读完。`Switch` 自身置为不可交互（`onCheckedChange = null`），
 * 避免出现第二个可点区域。
 *
 * 这是全项目**唯一**的开关行实现（`UiContractTest` 禁止出现第二个 `fun SwitchRow`）。
 */
@Composable
fun LabeledSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconPlate(icon = icon)
            Spacer(Modifier.width(Spacing.md))
        }
        Column(Modifier.weight(1f).padding(end = Spacing.md)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

/**
 * 关键词行：关键词 + 「系统」标签 + 删除按钮。
 *
 * 与 v3 的 InputChip 平铺相比，设计稿用的是**纵向列表行**：关键词数量到几十条时，
 * Chip 平铺会把卡片撑得很高且难以扫读；列表行每行一个词、左对齐，
 * 扫读效率与「删除」的命中区域都更好。
 */
@Composable
fun KeywordRow(
    keyword: String,
    deleteLabel: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    systemTag: String? = null,
    systemContainerColor: Color = MaterialTheme.colorScheme.errorContainer,
    systemContentColor: Color = MaterialTheme.colorScheme.error,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = keyword,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (systemTag != null) {
            TagBadge(
                text = systemTag,
                containerColor = systemContainerColor,
                contentColor = systemContentColor,
            )
            Spacer(Modifier.width(Spacing.xs))
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = AdSkipIcons.Delete,
                contentDescription = deleteLabel,
                modifier = Modifier.size(UiSizes.inlineIcon),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** 统计数字块：数值 + 标签。 */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier, emphasis: Boolean = false) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = if (emphasis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 占满整宽的分隔线（不缩进）。 */
@Composable
fun FullDivider(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.outlineVariant) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        color = color,
        thickness = UiSizes.hairline,
    )
}

/** 卡片内的信息行：左标签 / 右取值（用于「关于」区块）。 */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 设置项：标题（+ 副标题）+ 当前取值 + 右向指示符，点击进入选择。
 *
 * 与 [LabeledSwitch] 的分工：那是布尔开/关，这是**从若干取值中选一个**。
 * 单独成组件而不是复用开关：语言这类多选一用开关表达会丢信息
 * （用户看不到当前选的是哪个），且「点整行」的语义是「打开选择」，不是「切换」。
 *
 * 与 [SettingRow] 的分工：那个带图标底衬、用于「我的」页的入口行；
 * 这个不带图标、用于卡片内部的取值行（例如设置页的语言行）。
 */
@Composable
fun LabeledChoiceRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconPlate(icon = icon)
            Spacer(Modifier.width(Spacing.md))
        }
        Column(Modifier.weight(1f).padding(end = Spacing.md)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(Spacing.xs))
        Icon(
            imageVector = AdSkipIcons.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(UiSizes.chevron),
            tint = MaterialTheme.colorScheme.outline,
        )
    }
}

// ==================== 应用名解析 ====================

/**
 * 把包名解析成用户可读的应用名（带缓存）。
 *
 * 为什么需要缓存：包名 → 应用名要走 PackageManager 查询，首页与记录页会逐行调用，
 * 单个应用列表有上百行；不加缓存时每次重组都会触发一轮查询（实测在低端机上可见卡顿）。
 * 缓存用 `synchronized` 而不是 ConcurrentHashMap：查询本身也会调 PackageManager，
 * 并发查同一包名会重复做同样的 Binder 调用，锁的代价远小于重复查询。
 */
@Composable
fun rememberAppLabel(pkg: String): String {
    val context = LocalContext.current
    return remember(pkg) { resolveAppLabel(context, pkg) }
}

private val labelCache = HashMap<String, String>()

/** 解析包名对应的应用名；查询失败时回退为包名本身（不抛异常）。 */
fun resolveAppLabel(context: Context, pkg: String): String {
    if (pkg.isBlank()) return pkg
    synchronized(labelCache) {
        labelCache[pkg]?.let { return it }
    }
    val pm = context.packageManager
    val label = runCatching {
        pm.queryIntentActivities(
            android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER),
            0,
        )
            .firstOrNull { it.activityInfo?.packageName == pkg }
            ?.let { it.loadLabel(pm).toString() }
    }.getOrNull()
        ?: runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull()

    val result = label ?: pkg
    synchronized(labelCache) { labelCache[pkg] = result }
    return result
}
