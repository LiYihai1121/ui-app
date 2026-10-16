package com.qingqi.adskip.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.qingqi.adskip.R
import com.qingqi.adskip.ui.components.IconPlate
import com.qingqi.adskip.ui.theme.Brand05
import com.qingqi.adskip.ui.theme.Brand10
import com.qingqi.adskip.ui.theme.Brand20
import com.qingqi.adskip.ui.theme.Brand50
import com.qingqi.adskip.ui.theme.Spacing
import com.qingqi.adskip.ui.theme.UiSizes
import com.qingqi.adskip.ui.theme.icons.AdSkipIcons

/**
 * 首次启动引导页（v4 设计稿）。
 *
 * 内容自上而下：品牌标识 → 应用名 → 一句价值主张 → 插图 → 四条卖点 → 页码点 → 开始使用。
 *
 * 为什么需要它（v3 之前没有引导页）：应用的核心能力是「开启无障碍服务」，
 * 而这个开关**藏在系统设置的深层页面里**——没有前置解释时，用户第一次打开只会看到
 * 「服务未开启」和一个跳转按钮，不知道自己为什么要授权一个无障碍服务。
 * 引导页把「它做什么、为什么安全、数据在哪」在授权之前讲清楚。
 *
 * 实现说明：
 * - 插图是**几何拼贴**（圆角方块 + 徽标）而不是位图，与图标库同属一套几何语言，
 *   也不引入图片资源与压缩/密度适配问题；
 * - 背景渐变用 `Brand05 → 白` 的竖向渐变：设计稿是浅蓝到白的过渡，
 *   取值全部来自色板，不写色值字面量。
 */
@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Brand10, Brand05, MaterialTheme.colorScheme.surface)))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Spacing.huge))

        // 品牌标识：与首页头部同构（蓝底圆角方块 + 盾形），保证「同一款应用」的观感连续。
        Box(
            modifier = Modifier
                .size(UiSizes.statusShield)
                .clip(MaterialTheme.shapes.large)
                .background(Brand50),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AdSkipIcons.Shield,
                contentDescription = null,
                modifier = Modifier.size(UiSizes.statusShieldGlyph),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }

        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.xxl))
        OnboardingIllustration()
        Spacer(Modifier.height(Spacing.xxl))

        OnboardingPoint(stringResource(R.string.onboarding_point_1))
        OnboardingPoint(stringResource(R.string.onboarding_point_2))
        OnboardingPoint(stringResource(R.string.onboarding_point_3))
        OnboardingPoint(stringResource(R.string.onboarding_point_4))

        Spacer(Modifier.height(Spacing.xxl))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_start))
        }

        Spacer(Modifier.height(Spacing.xxl))
    }
}

/**
 * 插图：两台叠放的圆角设备 + 一枚「AD」徽标。
 *
 * 用纯几何图形表达「手机上的广告被拦下」，不需要位图资源——
 * 位图会因为密度、深色模式、不同宽高比各自需要一套，而几何图形自动适配全部。
 */
@Composable
private fun OnboardingIllustration() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(UiSizes.illustrationHeight),
        contentAlignment = Alignment.Center,
    ) {
        // 后面那台「设备」
        Box(
            modifier = Modifier
                .size(width = UiSizes.illustrationBackWidth, height = UiSizes.illustrationBackHeight)
                .clip(MaterialTheme.shapes.medium)
                .background(Brand20)
                .padding(Spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface),
            )
        }
        // 前面那枚徽标：AD 被拦下
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconPlate(
                icon = AdSkipIcons.Shield,
                size = UiSizes.tileIcon,
                glyphSize = UiSizes.tileGlyph,
                containerColor = Brand50,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = stringResource(R.string.onboarding_badge),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 单条卖点：对勾 + 文案。 */
@Composable
private fun OnboardingPoint(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            imageVector = AdSkipIcons.Check,
            contentDescription = null,
            modifier = Modifier.size(UiSizes.inlineIcon),
            tint = Brand50,
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
