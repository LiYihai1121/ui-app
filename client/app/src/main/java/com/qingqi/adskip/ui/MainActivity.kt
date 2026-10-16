package com.qingqi.adskip.ui

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.qingqi.adskip.AdSkipApp
import com.qingqi.adskip.R
import com.qingqi.adskip.device.LocaleApplier
import com.qingqi.adskip.ui.apps.AppsScreen
import com.qingqi.adskip.ui.home.HomeScreen
import com.qingqi.adskip.ui.logs.LogsScreen
import com.qingqi.adskip.ui.onboarding.OnboardingScreen
import com.qingqi.adskip.ui.profile.ProfileScreen
import com.qingqi.adskip.ui.rules.RulesScreen
import com.qingqi.adskip.ui.theme.AdSkipTheme
import com.qingqi.adskip.ui.theme.icons.AdSkipIcons
import com.qingqi.adskip.ui.theme.screenContentWidth

/**
 * 唯一 Activity：承载 Navigation Compose 导航的四个页面。
 *
 * 旧架构的四个 View 页面（MainActivity / AppListActivity / LogsActivity /
 * SettingsActivity）合并为本单 Activity + Compose 架构。
 *
 * 边到边（edge-to-edge）约定：
 * `targetSdk = 35` 在 Android 15 上强制边到边，内容必然绘制到状态栏/导航栏之下。
 * 因此 insets 的唯一收口点是 [AdSkipShell] 的 `Scaffold`：页面自身不再各自处理
 * `statusBarsPadding`（旧版只有 Home 漏了，导致标题压在时钟上）。
 * 页面内容统一只消费 `innerPadding`。
 */
class MainActivity : ComponentActivity() {

    /**
     * 低版本（API < 33）的界面语言在这里生效。
     *
     * 平台级的 `LocaleManager` 从 API 33 才有；在此之前，应用级语言只能靠在
     * `attachBaseContext` 里包一层带 locale 的 Context，让**资源解析**用新语言。
     * 必须在这个时机做：晚于它（例如 onCreate 里）资源已经按系统语言解析完了，
     * 界面会先闪一次旧语言。
     *
     * 直接读 SharedPreferences 而不经 AppContainer：`attachBaseContext` 早于
     * `Application.onCreate`，那时容器可能尚未构建完成。
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleApplier.wrapContextWithSavedLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 自动同步 Job 的兜底重注册在组合根 AdSkipApp.onCreate 中完成

        // 显式开启边到边，不依赖 targetSdk 的隐式行为（隐式行为随系统版本变化，过于脆弱）
        enableEdgeToEdge()

        // Android 10+ 默认会给三键导航栏加一层不透明遮罩，在边到边下表现为
        // 底部一条突兀的白带，与应用背景割裂。关闭对比度强制，让导航栏与应用背景连成一体。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContent {
            AdSkipTheme {
                AdSkipShell()
            }
        }
    }
}

/**
 * 一级目的地：底部导航栏的唯一数据源。
 *
 * 图标成对携带（描边 + 实心）：未选中用描边、选中用实心，再叠加
 * `NavigationBarItemDefaults` 的指示器底色——形状与颜色双重编码，
 * 色觉障碍用户也能分辨当前页。
 */
private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val iconSelected: ImageVector,
)

private val TopLevelDestinations = listOf(
    TopLevelDestination(Routes.HOME, R.string.nav_home, AdSkipIcons.Home, AdSkipIcons.HomeFilled),
    TopLevelDestination(Routes.LOGS, R.string.nav_logs, AdSkipIcons.Logs, AdSkipIcons.LogsFilled),
    TopLevelDestination(Routes.RULES, R.string.nav_rules, AdSkipIcons.Tag, AdSkipIcons.TagFilled),
    TopLevelDestination(Routes.PROFILE, R.string.nav_profile, AdSkipIcons.Profile, AdSkipIcons.ProfileFilled),
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
 *
 * **本次重构的关键决定：不引入 `TopAppBar`。**
 *
 * 一开始考虑过给四个页面加统一的顶栏，但核对 M3 与 iOS 的规范后发现那是错的：
 *  - 顶栏的存在意义是承载**导航返回**与**页面级动作**。本应用四个页面是同级的
 *    底部导航目的地，没有任何一页需要返回箭头，也没有页面级动作（清空/分享在日志页
 *    做成了内容区操作行）；
 *  - 顶栏会与底部导航栏一起把内容区压掉约 128dp，而首屏最重要的信息（服务状态）
 *    就会被挤出可视区；
 *  - M3 对「只有一个标题、没有返回、没有动作」的场景给的做法就是**不要顶栏**，
 *    标题直接作为内容流的第一项。
 *
 * 所以标题仍留在内容流里，但改由各页面画成「大标题」（见 PageHeader）——
 * 这与 iOS Large Title、M3 的 large top app bar 展开态是同一套视觉，
 * 只是省掉了滚动折叠的复杂度。滚动后标题移出视野是可接受的：四个页面都是
 * 底部导航直达，用户不会「找不到自己在哪一页」——底部导航本身就是常驻的位置指示。
 */
@Composable
private fun AdSkipShell() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val snackbarHostState = remember { SnackbarHostState() }
    val messenger = rememberMessenger(snackbarHostState)

    // 引导页只在首次启动时作为起始目的地；看过一次后直接进首页。
    val container = (LocalContext.current.applicationContext as AdSkipApp).container
    val startDestination = remember {
        if (container.settingsRepo.hasSeenOnboarding()) Routes.HOME else Routes.ONBOARDING
    }

    // 系统返回键：在非首页时回到首页，而不是逐级退出应用。
    // 用 popBackStack 而非 popUpTo(inclusive) + 重新 navigate：后者会销毁并重建首页，
    // 用户按返回后滚动位置、输入的关键词草稿全部丢失——「回首页」不该等价于「重开首页」。
    // 引导页不接管返回：那时首页尚未入栈，「回到首页」会绕过引导写标记的逻辑，
    // 于是下次冷启动又弹一次。交给系统返回键直接退出更符合预期。
    BackHandler(
        enabled = currentRoute != null &&
            currentRoute != Routes.HOME &&
            currentRoute != Routes.ONBOARDING,
    ) {
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
            // 引导页不挂底部导航：它还没有「去哪儿」的语义，多一条底栏只会分散注意力（设计稿亦然）。
            if (currentRoute != Routes.ONBOARDING) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
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
                                    imageVector = if (selected) destination.iconSelected else destination.icon,
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
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        // 内容宽度封顶 + 居中，规则与理由见 theme/Spacing.kt 的 screenContentWidth()。
        // 封顶只作用于内容：底部导航栏**不**受约束——它是系统级导航，
        // 全宽是 Material 与 Ant Design 的共同做法。
        //
        // 约束挂在 Box 上而不是 NavHost：NavHost 需要 fillMaxSize 才能撑满高度
        // 承载各页滚动；宽度封顶由外层盒子完成后再水平居中。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(modifier = Modifier.screenContentWidth()) {
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = { fadeIn(tween(160)) },
                    exitTransition = { fadeOut(tween(120)) },
                    popEnterTransition = { fadeIn(tween(160)) },
                    popExitTransition = { fadeOut(tween(120)) },
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            messenger = messenger,
                            onOpenLogs = {
                                navController.navigate(Routes.LOGS) { launchSingleTop = true }
                            },
                            onOpenRules = {
                                navController.navigate(Routes.RULES) { launchSingleTop = true }
                            },
                            onOpenApps = {
                                navController.navigate(Routes.APPS) { launchSingleTop = true }
                            },
                            onOpenSettings = {
                                navController.navigate(Routes.PROFILE) { launchSingleTop = true }
                            },
                        )
                    }
                    composable(Routes.LOGS) { LogsScreen(messenger) }
                    composable(Routes.RULES) { RulesScreen(messenger) }
                    composable(Routes.PROFILE) { ProfileScreen(messenger) }
                    composable(Routes.APPS) {
                        AppsScreen(onBack = { navController.popBackStack() })
                    }
                    composable(Routes.ONBOARDING) {
                        OnboardingScreen(
                            onStart = {
                                // 落盘「已看过」再进首页：只导航不置位的话，
                                // 下次冷启动会再弹一次引导——而引导只该出现一次。
                                container.settingsRepo.setSeenOnboarding(true)
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
