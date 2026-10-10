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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qingqi.adskip.ui.theme.StatusColors
import com.qingqi.adskip.ui.theme.UiSizes

/**
 * 服务运行状态的自绘指示环。
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
fun StatusOrb(running: Boolean, modifier: Modifier = Modifier, diameter: Dp = UiSizes.orbHalo) {
    val accent by animateColorAsState(
        targetValue = if (running) StatusColors.on else StatusColors.off,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "orb-accent",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val coreColor = remember(running, accent) {
        if (running) accent else accent.copy(alpha = 0.65f)
    }

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
        val strokeWidth = size.minDimension * 0.085f
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

        if (running) {
            // 扫光弧：随动效旋转，明确表达「正在工作」
            drawArc(
                color = accent,
                startAngle = sweep,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        } else {
            // 关闭态：整圈实色，弱化但仍可辨
            drawArc(
                color = accent.copy(alpha = 0.55f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        // 中心点：运行时呼吸，停止时静止
        drawCircle(
            color = coreColor,
            radius = size.minDimension * 0.11f * (if (running) pulse else 1f),
            center = center,
        )
    }
}
