package com.qingqi.adskip.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qingqi.adskip.R
import com.qingqi.adskip.device.KeepAliveNavigator
import com.qingqi.adskip.device.LanguageMode
import com.qingqi.adskip.device.PermissionKeys
import com.qingqi.adskip.device.Vendor
import com.qingqi.adskip.ui.Messenger
import com.qingqi.adskip.ui.components.FullDivider
import com.qingqi.adskip.ui.components.InfoRow
import com.qingqi.adskip.ui.components.PageHeader
import com.qingqi.adskip.ui.components.SectionCard
import com.qingqi.adskip.ui.components.SettingRow
import com.qingqi.adskip.ui.components.TagBadge
import com.qingqi.adskip.ui.settings.SettingsContent
import com.qingqi.adskip.ui.settings.SettingsViewModel
import com.qingqi.adskip.ui.theme.BadgeGold
import com.qingqi.adskip.ui.theme.BadgeGoldContainer
import com.qingqi.adskip.ui.theme.Spacing
import com.qingqi.adskip.ui.theme.UiSizes
import com.qingqi.adskip.ui.theme.icons.AdSkipIcons

/** 可展开的设置分组。同一时刻只展开一个，避免页面被无限拉长。 */
private enum class SettingSection { SERVICE, CLOUD, PRIVACY, LANGUAGE, ABOUT }

/**
 * 「我的」页（v4 设计稿，页面标题为「设置」）。
 *
 * 版式：用户卡 → 五行设置项。与 v3 的差别是本页从「四个区块 + 内嵌全部设置」
 * 收敛为**一条索引**：v3 把权限清单、统计、全部设置项、关于信息全部铺在一屏，
 * 结果是首屏要滑很久才能看到「语言设置」这类高频项。
 *
 * **为什么是「行内展开」而不是「跳到二级页」**：设计稿的每一行都带 `>`，
 * 暗示进入下一层。本实现改为行内展开——原因是本应用的全部设置项
 * 共用同一个 [SettingsViewModel] 与同一份状态，为「云端同步」单独开一个
 * NavHost 目的地会引入第二份状态来源；行内展开让「设置」始终只有一屏状态。
 * 静止态与设计稿一致（用户卡 + 五行），展开态是设计稿未覆盖的补充。
 *
 * 无障碍设置入口仍经 `device/KeepAliveNavigator`（UI 不自行拼 Intent）。
 */
@Composable
fun ProfileScreen(
    messenger: Messenger,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val tileLabel = stringResource(R.string.tile_label)
    var expanded by remember { mutableStateOf<SettingSection?>(null) }

    // 每次回到前台都重读：统计可能已变化，且用户可能刚跳去系统无障碍设置改了开关。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshAccessibilityStatus()
        viewModel.refreshStats()
    }

    val onOpenPermission: (String) -> Unit = { key ->
        val vendorName = viewModel.vendorName(state.vendor)
        when (key) {
            PermissionKeys.ACCESSIBILITY -> {
                if (!KeepAliveNavigator.openAccessibilitySettings(context)) {
                    messenger.show(context.getString(R.string.settings_open_failed))
                }
            }

            PermissionKeys.BATTERY -> {
                if (!KeepAliveNavigator.openBatteryOptimizationSettings(context)) {
                    messenger.show(context.getString(R.string.settings_open_failed))
                }
            }

            PermissionKeys.VENDOR_KEEPALIVE -> {
                val opened = KeepAliveNavigator.openKeepAliveSettings(context) != null
                val res = if (opened) R.string.settings_keepalive_opened else R.string.settings_keepalive_failed
                messenger.show(context.getString(res, vendorName))
            }

            PermissionKeys.QUICK_TILE -> {
                messenger.show(context.getString(R.string.settings_tile_manual, tileLabel))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PageHeader(title = stringResource(R.string.profile_title))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item { UserCard(totalSkips = state.totalSkips, activeApps = state.activeAppCount) }

            item {
                SectionCard(contentPadding = PaddingValues(vertical = Spacing.xs)) {
                    SettingRow(
                        icon = AdSkipIcons.Settings,
                        title = stringResource(R.string.profile_row_service),
                        subtitle = stringResource(R.string.profile_row_service_desc),
                        onClick = { expanded = expanded.toggle(SettingSection.SERVICE) },
                    )
                    RowDividerThin()
                    SettingRow(
                        icon = AdSkipIcons.Server,
                        title = stringResource(R.string.profile_row_cloud),
                        subtitle = stringResource(R.string.profile_row_cloud_desc),
                        onClick = { expanded = expanded.toggle(SettingSection.CLOUD) },
                    )
                    RowDividerThin()
                    SettingRow(
                        icon = AdSkipIcons.Shield,
                        title = stringResource(R.string.profile_row_privacy),
                        subtitle = stringResource(R.string.profile_row_privacy_desc),
                        onClick = { expanded = expanded.toggle(SettingSection.PRIVACY) },
                    )
                    RowDividerThin()
                    SettingRow(
                        icon = AdSkipIcons.Globe,
                        title = stringResource(R.string.profile_row_language),
                        subtitle = stringResource(settings.language.labelRes()),
                        onClick = { expanded = expanded.toggle(SettingSection.LANGUAGE) },
                    )
                    RowDividerThin()
                    SettingRow(
                        icon = AdSkipIcons.Info,
                        title = stringResource(R.string.profile_row_about),
                        subtitle = state.versionDisplay.ifBlank { stringResource(R.string.profile_value_unknown) },
                        onClick = { expanded = expanded.toggle(SettingSection.ABOUT) },
                    )
                }
            }

            item {
                AnimatedVisibility(visible = expanded == SettingSection.SERVICE) {
                    PermissionCard(
                        items = state.permissionItems,
                        readyCount = state.permissionReadyCount,
                        onOpen = onOpenPermission,
                    )
                }
            }

            item {
                AnimatedVisibility(visible = expanded == SettingSection.CLOUD) {
                    SettingsContent(messenger)
                }
            }

            item {
                AnimatedVisibility(visible = expanded == SettingSection.PRIVACY) {
                    SectionCard {
                        Text(
                            text = stringResource(R.string.profile_privacy_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                AnimatedVisibility(visible = expanded == SettingSection.LANGUAGE) {
                    SectionCard(contentPadding = PaddingValues(vertical = Spacing.xs)) {
                        LanguageMode.entries.forEach { mode ->
                            LanguageOptionRow(
                                mode = mode,
                                selected = settings.language == mode,
                                onSelect = { settingsViewModel.setLanguage(mode) },
                            )
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(visible = expanded == SettingSection.ABOUT) {
                    AboutCard(state = state, vendorName = viewModel::vendorName)
                }
            }
        }
    }
}

/** 展开/收起切换：点已展开的那一行则收起。 */
private fun SettingSection?.toggle(target: SettingSection): SettingSection? = if (this == target) null else target

/** 语言模式 → 显示名。与设置页各自持有的私有映射同源（都指向同一组文案）。 */
private fun LanguageMode.labelRes(): Int = when (this) {
    LanguageMode.SYSTEM -> R.string.language_system
    LanguageMode.CHINESE -> R.string.language_zh
    LanguageMode.ENGLISH -> R.string.language_en
}

/**
 * 用户卡：头像 + 用户名 + 会员徽章 + 使用概览。
 *
 * 头像用「底衬 + 人像图形」而不是位图：设计稿是一张真实照片，本应用没有账号体系、
 * 也就没有头像来源，引入网络图片会与「本地优先、隐私可控」直接冲突。
 * 见交付说明的图标替换差异。
 */
@Composable
private fun UserCard(totalSkips: Int, activeApps: Int) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(UiSizes.avatar)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AdSkipIcons.Profile,
                    contentDescription = null,
                    modifier = Modifier.size(UiSizes.rowIcon),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.profile_user_placeholder),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Spacing.xs))
                TagBadge(
                    text = stringResource(R.string.profile_premium_badge),
                    containerColor = BadgeGoldContainer,
                    contentColor = BadgeGold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = totalSkips.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.profile_active_apps_label, activeApps),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 语言选项行：单选。整行可点，读屏念「简体中文，已选中，单选按钮」。 */
@Composable
private fun LanguageOptionRow(mode: LanguageMode, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = stringResource(mode.labelRes()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 关于卡：版本与设备信息 + 外链。 */
@Composable
private fun AboutCard(state: ProfileViewModel.UiState, vendorName: (Vendor) -> String) {
    SectionCard(contentPadding = PaddingValues(vertical = Spacing.xs)) {
        InfoRow(
            label = stringResource(R.string.profile_version_label),
            value = state.versionDisplay.ifBlank { stringResource(R.string.profile_value_unknown) },
        )
        FullDivider()
        InfoRow(
            label = stringResource(R.string.profile_android_label),
            value = state.androidVersion.ifBlank { stringResource(R.string.profile_value_unknown) },
        )
        FullDivider()
        InfoRow(
            label = stringResource(R.string.profile_device_label),
            value = state.deviceModel.ifBlank { stringResource(R.string.profile_value_unknown) },
        )
        FullDivider()
        InfoRow(
            label = stringResource(R.string.profile_vendor_label),
            value = vendorName(state.vendor),
        )
        FullDivider()
        LinkRow(
            icon = AdSkipIcons.Globe,
            label = stringResource(R.string.profile_link_project),
            url = PROJECT_URL,
        )
        FullDivider()
        LinkRow(
            icon = AdSkipIcons.Feedback,
            label = stringResource(R.string.profile_link_feedback),
            url = FEEDBACK_URL,
        )
    }
}

/** 开源项目主页与问题反馈入口。 */
private const val PROJECT_URL = "https://github.com/LiYihai1121/ui-app"
private const val FEEDBACK_URL = "https://github.com/LiYihai1121/ui-app/issues"

/**
 * 链接行：图标 + 标题，整行可点，跳系统浏览器。
 *
 * 为什么直接用 `ACTION_VIEW` 而不经 KeepAliveNavigator：那收口的是「系统设置页」
 * 跳转（厂商差异大、需要按 ROM 分发），网页链接没有厂商差异。`runCatching`
 * 兜住无浏览器应用极端情况下的 ActivityNotFoundException，避免崩溃。
 */
@Composable
private fun LinkRow(icon: ImageVector, label: String, url: String) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(UiSizes.rowIcon),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 卡片内的细分隔线（两侧各内缩一个卡片内边距）。 */
@Composable
private fun RowDividerThin() {
    FullDivider(modifier = Modifier.padding(horizontal = Spacing.lg))
}
