package com.ldp.adskip.ui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ldp.adskip.R
import com.ldp.adskip.ui.apps.AppsScreen
import com.ldp.adskip.ui.home.HomeScreen
import com.ldp.adskip.ui.logs.LogsScreen
import com.ldp.adskip.ui.profile.ProfileScreen
import com.ldp.adskip.ui.theme.AdskipTheme
import com.ldp.adskip.ui.theme.UiSizes

/**
 * 唯一 Activity：承载 Navigation Compose 导航的四个页面。
 *
 * 旧架构的四个 View 页面（MainActivity / AppListActivity / LogsActivity /
 * SettingsActivity）合并为本单 Activity + Compose 架构。
 *
 * 边到边（edge-to-edge）约定：
 * `targetSdk = 35` 在 Android 15 上强制边到边，内容必然绘制到状态栏/导航栏之下。
 * 因此 insets 的唯一收口点是 [AdskipApp] 的 `Scaffold`：页面自身不再各自处理
 * `statusBarsPadding`（旧版只有 Home 漏了，导致标题压在时钟上）。
 * 页面内容统一只消费 `innerPadding`。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 自动同步 Job 的兜底重注册在组合根 AdskipApp.onCreate 中完成

        // 显式开启边到边，不依赖 targetSdk 的隐式行为（隐式行为随系统版本变化，过于脆弱）
        enableEdgeToEdge()

        // Android 10+ 默认会给三键导航栏加一层不透明遮罩，在边到边下表现为
        // 底部一条突兀的白带，与应用背景割裂。关闭对比度强制，让导航栏与应用背景连成一体。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            AdskipTheme {
                AdskipShell()
            }
        }
    }
}

/** 一级目的地：底部导航栏的唯一数据源。 */
private data class TopLevelDestination(val route: String, val labelRes: Int, val icon: ImageVector)

private val TopLevelDestinations = listOf(
    TopLevelDestination(Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    TopLevelDestination(Routes.APPS, R.string.nav_apps, Icons.AutoMirrored.Filled.List),
    TopLevelDestination(Routes.LOGS, R.string.nav_logs, Icons.Filled.DateRange),
    TopLevelDestination(Routes.PROFILE, R.string.nav_profile, Icons.Filled.Person),
)

/**
 * 应用外壳：底部导航栏 + 导航容器。
 *
 * 把 `Scaffold` 提到这里之后，四个页面都不再自带 `Scaffold`，
 * inset（状态栏 / 导航栏 / 输入法）由本层一次性分发，页面只管内容。
 *
 * 转场只保留淡入淡出：四个目的地是**同级 tab**，不是层级导航。
 * 水平滑动暗示「进入下一层」，在 tab 切换时会给出错误的深度感；
 * 只有将来真的出现「列表 → 详情」这类二级页面时，才应恢复方向性转场。
 */
@Composable
private fun AdskipShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val snackbarHostState = remember { SnackbarHostState() }
    val messenger = rememberMessenger(snackbarHostState)

    // 系统返回键：在非首页时回到首页，而不是逐级退出应用。
    // 用 popBackStack 而非 popUpTo(inclusive) + 重新 navigate：后者会销毁并重建首页，
    // 用户按返回后滚动位置、输入的关键词草稿全部丢失——「回首页」不该等价于「重开首页」。
    BackHandler(enabled = currentRoute != null && currentRoute != Routes.HOME) {
        if (!navController.popBackStack(Routes.HOME, inclusive = false)) {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.HOME) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Snackbar 宿主放在外壳：四个页面共用一个 host，消息按顺序排队，
    // 不再像 Toast 那样「后一条顶掉前一条」。Messenger 显式传参给需要它的页面，
    // 不用 CompositionLocal——依赖写在签名上，比隐式查找更容易发现遗漏。
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                TopLevelDestinations.forEach { destination ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                navController.navigate(destination.route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                // null 而非 labelRes：下方 label 已渲染同一文案，
                                // 给图标再挂一次会让读屏把每个 tab 名念两遍。
                                contentDescription = null,
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(destination.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        // 内容宽度封顶 + 居中：三家的规范在这里是同一个结论。
        // - Material：WindowSizeClass 提示 expanded 宽度下应改用 NavigationRail / ListDetail；
        // - Ant Design：Row/Col 栅格 + Container 居中 + max-width 封顶；
        // - Tailwind：max-w-* 居中 + 断点。
        // 本应用是单列信息流，不需要栅格分栏，但**需要封顶**：否则平板、折叠屏展开态
        // 与横屏下卡片会被拉成整屏宽的条带，一行文字横跨上千 dp。
        // 先只做封顶（改动一处、四个页面全部受益）；等真的引入列表-详情式布局时，
        // 再在 expanded 宽度把底部 NavigationBar 换成 NavigationRail。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = CONTENT_MAX_WIDTH),
                enterTransition = { fadeIn(tween(160)) },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(160)) },
                popExitTransition = { fadeOut(tween(120)) },
            ) {
                composable(Routes.HOME) { HomeScreen(messenger) }
                composable(Routes.APPS) { AppsScreen() }
                composable(Routes.LOGS) { LogsScreen(messenger) }
                composable(Routes.PROFILE) { ProfileScreen(messenger) }
            }
        }
    }
}

/**
 * 页面内容最大宽度。
 *
 * 640dp 约等于「平板竖屏一半」与「手机横屏」的舒适阅读宽度；再宽则单行中文超过
 * 30 个字，阅读时视线回到行首的行程过长。底部导航栏**不**受此约束——它是系统级
 * 导航，全宽是 Material 与 Ant Design 的共同做法，内容封顶而栏不封顶。
 */
private val CONTENT_MAX_WIDTH = UiSizes.contentMaxWidth
