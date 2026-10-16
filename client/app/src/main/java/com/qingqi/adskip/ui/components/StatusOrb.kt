package com.qingqi.adskip.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.qingqi.adskip.ui.theme.StatusColors
import com.qingqi.adskip.ui.theme.UiSizes

/**
 * 服务状态的自绘指示环。
 *
 * **纯装饰，不参与语义树**（`clearAndSetSemantics {}`）：状态结论由紧邻的
 * `Text` 承担。早期这里另挂了一份 `contentDescription`，于是同一句
 * 「服务未开启 / 服务运行中」在语义树里出现两次，读屏会连念两遍。
 * 图形与文字讲同一件事时，语义只该由其中一方提供。
 *
 * 为什么自绘而不是用现成组件：
 * `CircularProgressIndicator` 的语义是「未知的、进行中的加载」，用它表示
 * 「无障碍服务是否生效」会误导用户。服务状态只有开 / 关两态，需要一眼可辨，
 * 因此用「轨道环 + 扫光弧 + 呼吸圆点」表达。
 *
 * 三态（本次重构新增 [unknown]）：
 *  - ON      实心扫光弧在转 + 中心点呼吸，明确表达「正在工作」；
 *  - OFF     整圈实色、中心点静止、整体降透明度，表达「停着」；
 *  - UNKNOWN 轨道变虚线感 ← 用更细的轨道 + 琥珀色 + 中心短横表达「读不出来」。
 *    **不能与 OFF 同色**：OFF 的用户动作是「点开关」，UNKNOWN 是「去系统设置核对」。
 *
 * 实现说明（为何是单一 Canvas，而不是 `Box { Canvas; Box }`）：
 * 轨道环、扫光弧与中心圆点本属同一套几何关系，拆成多个布局节点会带来两个成本：
 * 多一层 measure/layout，以及圆点与圆环的尺寸/对齐要靠父容器 `contentAlignment` 兜住。
 * 放进同一个 DrawScope 后只剩一个叶子节点，尺寸由 `Modifier.size` 直接钉死。
 *
 * 复核时的坑（别再踩）：本组件一度被误判为「高度塌缩、整颗不显示」。
 * 真实原因是验证方法有问题——`LazyColumn` 用 `rememberSaveable` 记住了滚动位置，
 * `am start` 复用任务时会恢复到上次的滚动位，此时截图与 uiautomator 边界
 * 看起来像「卡片顶部凭空少了一截」。判断 Compose 布局问题前，
 * 请先确认滚动位在顶部，否则量出来的 bounds 会把人带偏。
 */
@Composable
fun StatusOrb(
    running: Boolean,
    modifier: Modifier = Modifier,
    diameter: Dp = UiSizes.orbHalo,
    unknown: Boolean = false,
) {
    val accent by animateColorAsState(
        targetValue = when {
            unknown -> StatusColors.unknown
            running -> StatusColors.on
            else -> StatusColors.off
        },
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "orb-accent",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val coreColor = if (running) accent else accent.copy(alpha = 0.65f)

    val transition = rememberInfiniteTransition(label = "orb")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orb-sweep",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.70f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb-pulse",
    )

    Canvas(
        modifier = modifier
            .size(diameter)
            // 图形不提供语义：状态结论由相邻 Text 给出，两边都挂会让读屏重复播报。
            .clearAndSetSemantics {},
    ) {
        val strokeWidth = size.minDimension * 0.075f
        val half = strokeWidth / 2f
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(half, half)

        // 轨道底环
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        when {
            // 扫光弧：随动效旋转，明确表达「正在工作」
            running -> drawArc(
                color = accent,
                startAngle = sweep,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // 无法确认：只画四段短弧，视觉上「环是断的」——与「整圈实色」的 OFF 一眼可分
            unknown -> {
                repeat(4) { index ->
                    drawArc(
                        color = accent.copy(alpha = 0.75f),
                        startAngle = index * 90f + 12f,
                        sweepAngle = 40f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }

            // 关闭态：整圈实色，弱化但仍可辨
            else -> drawArc(
                color = accent.copy(alpha = 0.55f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        // 中心：运行时呼吸的实心点；停止时静止点；无法确认时短横（「读不出值」的通用符号）
        if (unknown) {
            drawLine(
                color = coreColor,
                start = Offset(center.x - size.minDimension * 0.11f, center.y),
                end = Offset(center.x + size.minDimension * 0.11f, center.y),
                strokeWidth = strokeWidth * 0.9f,
                cap = StrokeCap.Round,
            )
        } else {
            drawCircle(
                color = coreColor,
                radius = size.minDimension * 0.11f * (if (running) pulse else 1f),
                center = center,
            )
        }
    }
}

/**
 * 列表行内的小状态点（10dp）。
 *
 * 与 [StatusOrb] 的分工：那个是页面主视觉（96dp+，带动效），
 * 这个是行内的**静态标记**，出现在清单里逐行对照。
 *
 * 静态标记不带动效，也不做呼吸——一屏里十几个点同时闪动是噪声而不是信息。
 */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, filled: Boolean = true) {
    Box(
        modifier = modifier
            .size(UiSizes.statusDot)
            .clip(CircleShape)
            .then(
                if (filled) {
                    Modifier.background(color)
                } else {
                    Modifier.border(UiSizes.statusDotRing, color, CircleShape)
                },
            )
            .clearAndSetSemantics {},
    )
}

/**
 * 用色阶而不是透明度做「浅色底」。
 *
 * `color.copy(alpha)` 叠在不同容器色上会得到不同结果：同一颗点放在卡片上和
 * 放在页面上是两个颜色，同一枚徽章在浅色卡片和深色浮层上也不同。
 * 与给定的背景色做线性插值可以得到确定值，这才是「同一档色」应有的行为。
 */
internal fun blendOnto(color: Color, background: Color, fraction: Float): Color = Color(
    red = color.red * fraction + background.red * (1 - fraction),
    green = color.green * fraction + background.green * (1 - fraction),
    blue = color.blue * fraction + background.blue * (1 - fraction),
    alpha = 1f,
)
