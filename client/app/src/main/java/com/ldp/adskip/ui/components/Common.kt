package com.ldp.adskip.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ldp.adskip.ui.theme.Spacing
import com.ldp.adskip.ui.theme.UiSizes

/**
 * 统一卡片容器。
 *
 * 重设计前各页面各自 new `Card(...)` 并各自决定内边距与容器色，导致同一屏内
 * 出现三种圆角与两种底色；这里把间距与配色收口到一处。
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Spacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

/**
 * 卡片内的小节标题。
 *
 * 带 `heading()` 语义：读屏可把标题当作导航锚点逐段跳读。此前全项目 0 处
 * heading，视障用户只能从头线性听完一整屏 —— 而本应用的关键操作（开关关键词、
 * 打开无障碍）都在长列表之后。
 */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * 页面级标题（副标题可选）。
 *
 * 外壳只提供底部导航栏，页面标题由各页自己渲染——四个页面都自带 `Scaffold` + `TopAppBar`
 * 会在底部栏之上再叠一层顶栏，既重复又挤占首屏空间。
 *
 * 标题带 `heading()`，副标题不带：副标题是补充说明，列为标题会让读屏的
 * 标题导航被噪声填满。
 */
@Composable
fun PageHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.md),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 状态圆点：实心 = 就绪，空心环 = 未就绪。
 *
 * 用**颜色 + 形状双重编码**：只靠颜色区分对色觉障碍用户不可读，而这是判断
 * 「功能是否生效」的唯一视觉线索，误判成本很高。
 *
 * 圆点自身对无障碍服务隐藏（`clearAndSetSemantics`）：状态结论由相邻文字承担，
 * 否则读屏会念出一个没有信息的「圆点」。
 */
@Composable
fun StatusDot(ready: Boolean, modifier: Modifier = Modifier) {
    val color = if (ready) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = modifier
            .size(UiSizes.skeletonLineCompact)
            .clip(CircleShape)
            .then(if (ready) Modifier.background(color) else Modifier.border(Spacing.xs / 3, color, CircleShape))
            .clearAndSetSemantics {},
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

/**
 * 统计数字块：首页「累计跳过 / 最近应用」两栏共用。
 *
 * 首版用 `displaySmall` 且不设 `overflow`，结果应用名（`com.android.launcher3`）
 * 被硬生生截断成 `com.andr`，既不可读也没有省略号提示。
 * 现在统一降到 `headlineMedium` + `maxLines = 2` + 省略号：数字仍然够醒目，
 * 长文本则优雅降级。
 */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            // 数值与标签是一个语义单元：「累计跳过 42 次」。
            // 不合并时读屏会把它拆成两个孤立节点，用户听到「42」与「次」分离。
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 设置项：标题 + 可选副标题 + 开关。整行可点，命中区域远大于开关本身。
 *
 * 无障碍：整行是**一个** `Role.Switch` 节点（`toggleable` + `mergeDescendants`），
 * 标题与状态一次读完。此前标题与开关是两个独立节点，读屏需要先停在标题、
 * 再移到开关才能操作，副标题还会被跳过。`Switch` 自身因此置为不可交互
 * （`onCheckedChange = null`），避免出现第二个可点区域。
 */
@Composable
fun LabeledSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.lg)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * 包名 → 应用显示名。
 *
 * 旧版直接把 `com.android.launcher3` 这类包名怼到界面上，用户无法判断是哪个应用。
 *
 * 实现注意：Android 11+ 有包可见性限制，直接 `getApplicationInfo(pkg, 0)` 对
 * 非本应用的包可能抛 NameNotFoundException（实测在模拟器上就取不到启动器名）。
 * 因此这里改为**复用应用管理页已在用的查询方式**——按 MAIN/LAUNCHER 查询可见应用
 * 并建映射（AndroidManifest 的 `<queries>` 已声明该 intent），再回退到单包查询；
 * 两条路都失败时返回包名本身，保证不会崩溃或留空。
 */
@Composable
fun rememberAppLabel(pkg: String): String {
    val context = LocalContext.current
    return remember(pkg) { resolveAppLabel(context, pkg) }
}

private val labelCache = HashMap<String, String>()

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
