package com.qingqi.adskip.ui.theme.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/*
 * 应用自绘图标库（唯一图标事实源）。
 *
 * 为什么自绘而不用 Material 图标：本应用的品牌图形（启动器/磁贴）是
 * 「HUD 取景框 + 对勾」的科技母题，Material 全家桶的箭头系、纸飞机系
 * 与它不属于同一套语言；图标是出现频率最高、最小可复制的品牌资产，
 * 与启动器割裂的图标会让「同一款应用」的观感碎掉。
 *
 * 设计规范：
 * - 24dp 视窗、2dp 描边、圆头圆角——与 Material line 图标同构，可无缝替换；
 * - 描边/填充统一用黑色占位，实际颜色由 `Icon(tint = …)` 注入
 *   （与 Material 图标相同的约定，保证随主题着色）；
 * - **箭头语素自 v4 起解禁（仅限 chevron）**：原规则为「禁止箭头 / 纸飞机 / chevron，
 *   指向性一律由布局与文案表达」。v4 设计稿的每一处可点行尾都带 `>`，
 *   这是移动端「整行可点」最通用的可发现性信号；去掉它会让设计稿里
 *   「关键词行 / 设置行 / 应用行」的可点性只能靠用户试出来。
 *   故**刻意**新增 [ChevronRight]，并把这条决定写在这里而非悄悄加一个图标。
 *   其余箭头语素（返回箭头、纸飞机）仍在禁止之列——返回由系统返回键与
 *   页面标题承担，分享由 [Share] 的三节点图形承担。
 * - 新增图标前先确认现库没有可复用语义；需要新图标时保持同一套几何语言
 *   （圆角矩形 + 圆弧 + 直线段）。
 */

private val ink = SolidColor(Color.Black)

/** 构建一个 24dp 视窗的自绘图标。 */
private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply(block).build()

/** 2dp 圆头描边路径——线稿图标的默认画法。 */
private fun ImageVector.Builder.pathStroke(block: PathBuilder.() -> Unit) = path(
    stroke = ink,
    strokeLineWidth = 2f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    pathBuilder = block,
)

/** 实心填充路径；[fillType] 传 EvenOdd 时后续子路径成为镂空。 */
private fun ImageVector.Builder.pathFill(
    fillType: PathFillType = PathFillType.NonZero,
    block: PathBuilder.() -> Unit,
) = path(
    fill = ink,
    pathFillType = fillType,
    pathBuilder = block,
)

/** 圆角矩形（quadTo 近似圆角，24dp 网格下与真实圆弧不可区分）。 */
private fun PathBuilder.roundedRect(x: Float, y: Float, w: Float, h: Float, r: Float) {
    moveTo(x + r, y)
    lineTo(x + w - r, y)
    quadTo(x + w, y, x + w, y + r)
    lineTo(x + w, y + h - r)
    quadTo(x + w, y + h, x + w - r, y + h)
    lineTo(x + r, y + h)
    quadTo(x, y + h, x, y + h - r)
    lineTo(x, y + r)
    quadTo(x, y, x + r, y)
    close()
}

object AdSkipIcons {

    // ==================== 导航（描边 + 实心成对） ====================

    /** 首页：屋顶 + 圆角墙体 + 圆角门。 */
    val Home: ImageVector by lazy {
        icon("AdSkip.Home") {
            pathStroke {
                moveTo(4.5f, 10.8f)
                lineTo(12f, 4.4f)
                lineTo(19.5f, 10.8f)
                lineTo(19.5f, 19f)
                quadTo(19.5f, 20.5f, 18f, 20.5f)
                lineTo(6f, 20.5f)
                quadTo(4.5f, 20.5f, 4.5f, 19f)
                close()
            }
            pathStroke {
                moveTo(9.4f, 20.5f)
                lineTo(9.4f, 15.6f)
                quadTo(9.4f, 14.6f, 10.4f, 14.6f)
                lineTo(13.6f, 14.6f)
                quadTo(14.6f, 14.6f, 14.6f, 15.6f)
                lineTo(14.6f, 20.5f)
            }
        }
    }

    /** 首页（实心，导航选中态）。 */
    val HomeFilled: ImageVector by lazy {
        icon("AdSkip.HomeFilled") {
            pathFill(PathFillType.EvenOdd) {
                moveTo(4.5f, 10.8f)
                lineTo(12f, 4.4f)
                lineTo(19.5f, 10.8f)
                lineTo(19.5f, 19f)
                quadTo(19.5f, 20.5f, 18f, 20.5f)
                lineTo(6f, 20.5f)
                quadTo(4.5f, 20.5f, 4.5f, 19f)
                close()
                moveTo(9.4f, 20.5f)
                lineTo(9.4f, 15.6f)
                quadTo(9.4f, 14.6f, 10.4f, 14.6f)
                lineTo(13.6f, 14.6f)
                quadTo(14.6f, 14.6f, 14.6f, 15.6f)
                lineTo(14.6f, 20.5f)
                close()
            }
        }
    }

    /** 应用：2×2 圆角方块网格。 */
    val Apps: ImageVector by lazy {
        icon("AdSkip.Apps") {
            pathStroke {
                roundedRect(4.5f, 4.5f, 7f, 7f, 1.6f)
                roundedRect(12.5f, 4.5f, 7f, 7f, 1.6f)
                roundedRect(4.5f, 12.5f, 7f, 7f, 1.6f)
                roundedRect(12.5f, 12.5f, 7f, 7f, 1.6f)
            }
        }
    }

    /** 应用（实心，导航选中态）。 */
    val AppsFilled: ImageVector by lazy {
        icon("AdSkip.AppsFilled") {
            pathFill {
                roundedRect(4.5f, 4.5f, 7f, 7f, 1.6f)
                roundedRect(12.5f, 4.5f, 7f, 7f, 1.6f)
                roundedRect(4.5f, 12.5f, 7f, 7f, 1.6f)
                roundedRect(12.5f, 12.5f, 7f, 7f, 1.6f)
            }
        }
    }

    /** 记录：文档 + 摘要行（日志/记录的通用符号）。 */
    val Logs: ImageVector by lazy {
        icon("AdSkip.Logs") {
            pathStroke { roundedRect(5f, 3.5f, 14f, 17f, 1.6f) }
            pathStroke {
                moveTo(8.6f, 9f)
                lineTo(15.4f, 9f)
                moveTo(8.6f, 12.4f)
                lineTo(15.4f, 12.4f)
                moveTo(8.6f, 15.8f)
                lineTo(12.6f, 15.8f)
            }
        }
    }

    /** 记录（实心，导航选中态；摘要行镂空）。 */
    val LogsFilled: ImageVector by lazy {
        icon("AdSkip.LogsFilled") {
            pathFill(PathFillType.EvenOdd) {
                roundedRect(5f, 3.5f, 14f, 17f, 1.6f)
                moveTo(8.6f, 8.2f)
                lineTo(15.4f, 8.2f)
                lineTo(15.4f, 9.8f)
                lineTo(8.6f, 9.8f)
                close()
                moveTo(8.6f, 11.6f)
                lineTo(15.4f, 11.6f)
                lineTo(15.4f, 13.2f)
                lineTo(8.6f, 13.2f)
                close()
                moveTo(8.6f, 15f)
                lineTo(12.6f, 15f)
                lineTo(12.6f, 16.6f)
                lineTo(8.6f, 16.6f)
                close()
            }
        }
    }

    /** 我的：头 + 肩线。 */
    val Profile: ImageVector by lazy {
        icon("AdSkip.Profile") {
            pathStroke {
                moveTo(8.9f, 7.6f)
                arcTo(3.1f, 3.1f, 0f, false, false, 15.1f, 7.6f)
                arcTo(3.1f, 3.1f, 0f, false, false, 8.9f, 7.6f)
                close()
            }
            pathStroke {
                moveTo(4.6f, 20.5f)
                curveTo(4.6f, 16.9f, 7.8f, 14.5f, 12f, 14.5f)
                curveTo(16.2f, 14.5f, 19.4f, 16.9f, 19.4f, 20.5f)
            }
        }
    }

    /** 我的（实心，导航选中态）。 */
    val ProfileFilled: ImageVector by lazy {
        icon("AdSkip.ProfileFilled") {
            pathFill {
                moveTo(8.9f, 7.6f)
                arcTo(3.1f, 3.1f, 0f, false, false, 15.1f, 7.6f)
                arcTo(3.1f, 3.1f, 0f, false, false, 8.9f, 7.6f)
                close()
            }
            pathFill {
                moveTo(4.5f, 20.5f)
                curveTo(4.5f, 16.6f, 7.9f, 14.2f, 12f, 14.2f)
                curveTo(16.1f, 14.2f, 19.5f, 16.6f, 19.5f, 20.5f)
                close()
            }
        }
    }

    // ==================== 操作 ====================

    /** 添加（加号）。 */
    val Plus: ImageVector by lazy {
        icon("AdSkip.Plus") {
            pathStroke {
                moveTo(12f, 5.2f)
                lineTo(12f, 18.8f)
                moveTo(5.2f, 12f)
                lineTo(18.8f, 12f)
            }
        }
    }

    /** 关闭/移除（叉号）。 */
    val Close: ImageVector by lazy {
        icon("AdSkip.Close") {
            pathStroke {
                moveTo(6.8f, 6.8f)
                lineTo(17.2f, 17.2f)
                moveTo(17.2f, 6.8f)
                lineTo(6.8f, 17.2f)
            }
        }
    }

    /** 清空（垃圾桶）。 */
    val Delete: ImageVector by lazy {
        icon("AdSkip.Delete") {
            pathStroke {
                moveTo(4.6f, 7f)
                lineTo(19.4f, 7f)
                moveTo(9.6f, 7f)
                lineTo(9.6f, 5.4f)
                quadTo(9.6f, 4.6f, 10.4f, 4.6f)
                lineTo(13.6f, 4.6f)
                quadTo(14.4f, 4.6f, 14.4f, 5.4f)
                lineTo(14.4f, 7f)
                moveTo(6.4f, 7f)
                lineTo(7.1f, 18.9f)
                quadTo(7.2f, 19.6f, 7.9f, 19.6f)
                lineTo(16.1f, 19.6f)
                quadTo(16.8f, 19.6f, 16.9f, 18.9f)
                lineTo(17.6f, 7f)
                moveTo(10.1f, 10.8f)
                lineTo(10.1f, 15.8f)
                moveTo(13.9f, 10.8f)
                lineTo(13.9f, 15.8f)
            }
        }
    }

    /** 分享：三节点互联（无箭头，替代 Material 的 Send 纸飞机）。 */
    val Share: ImageVector by lazy {
        icon("AdSkip.Share") {
            pathStroke {
                moveTo(4.3f, 12f)
                arcTo(2.2f, 2.2f, 0f, false, false, 8.7f, 12f)
                arcTo(2.2f, 2.2f, 0f, false, false, 4.3f, 12f)
                close()
                moveTo(15.3f, 5.8f)
                arcTo(2.2f, 2.2f, 0f, false, false, 19.7f, 5.8f)
                arcTo(2.2f, 2.2f, 0f, false, false, 15.3f, 5.8f)
                close()
                moveTo(15.3f, 18.2f)
                arcTo(2.2f, 2.2f, 0f, false, false, 19.7f, 18.2f)
                arcTo(2.2f, 2.2f, 0f, false, false, 15.3f, 18.2f)
                close()
                moveTo(9.3f, 10.4f)
                lineTo(14.7f, 7.4f)
                moveTo(9.3f, 13.6f)
                lineTo(14.7f, 16.6f)
            }
        }
    }

    /** 警告（三角 + 感叹号）。 */
    val Warning: ImageVector by lazy {
        icon("AdSkip.Warning") {
            pathStroke {
                moveTo(12f, 3.8f)
                lineTo(20.8f, 19.4f)
                lineTo(3.2f, 19.4f)
                close()
                moveTo(12f, 9.6f)
                lineTo(12f, 13.6f)
            }
            pathFill {
                moveTo(12.9f, 16.3f)
                arcTo(0.9f, 0.9f, 0f, false, false, 11.1f, 16.3f)
                arcTo(0.9f, 0.9f, 0f, false, false, 12.9f, 16.3f)
                close()
            }
        }
    }

    /** 搜索（放大镜）。 */
    val Search: ImageVector by lazy {
        icon("AdSkip.Search") {
            pathStroke {
                moveTo(4.9f, 10.7f)
                arcTo(5.8f, 5.8f, 0f, false, false, 16.5f, 10.7f)
                arcTo(5.8f, 5.8f, 0f, false, false, 4.9f, 10.7f)
                close()
                moveTo(15.1f, 15.1f)
                lineTo(19.4f, 19.4f)
            }
        }
    }

    // ==================== 设置行与空态 ====================

    /** 语言/网络（地球）。 */
    val Globe: ImageVector by lazy {
        icon("AdSkip.Globe") {
            pathStroke {
                moveTo(3.6f, 12f)
                arcTo(8.4f, 8.4f, 0f, false, false, 20.4f, 12f)
                arcTo(8.4f, 8.4f, 0f, false, false, 3.6f, 12f)
                close()
                moveTo(12f, 3.6f)
                curveTo(15.9f, 7.4f, 15.9f, 16.6f, 12f, 20.4f)
                curveTo(8.1f, 16.6f, 8.1f, 7.4f, 12f, 3.6f)
                moveTo(3.6f, 12f)
                lineTo(20.4f, 12f)
            }
        }
    }

    /** 云端服务（双层服务架）。 */
    val Server: ImageVector by lazy {
        icon("AdSkip.Server") {
            pathStroke {
                roundedRect(4.4f, 4.2f, 15.2f, 6.6f, 1.5f)
                roundedRect(4.4f, 13.2f, 15.2f, 6.6f, 1.5f)
                moveTo(11.6f, 7.5f)
                lineTo(16.2f, 7.5f)
                moveTo(11.6f, 16.5f)
                lineTo(16.2f, 16.5f)
            }
            pathFill {
                moveTo(8.3f, 7.5f)
                arcTo(0.9f, 0.9f, 0f, false, false, 6.5f, 7.5f)
                arcTo(0.9f, 0.9f, 0f, false, false, 8.3f, 7.5f)
                close()
                moveTo(8.3f, 16.5f)
                arcTo(0.9f, 0.9f, 0f, false, false, 6.5f, 16.5f)
                arcTo(0.9f, 0.9f, 0f, false, false, 8.3f, 16.5f)
                close()
            }
        }
    }

    /** 安全（盾牌）。 */
    val Shield: ImageVector by lazy {
        icon("AdSkip.Shield") {
            pathStroke {
                moveTo(12f, 3.6f)
                lineTo(19.6f, 6.4f)
                lineTo(19.6f, 12.1f)
                curveTo(19.6f, 16.4f, 16.5f, 19.7f, 12f, 21f)
                curveTo(7.5f, 19.7f, 4.4f, 16.4f, 4.4f, 12.1f)
                lineTo(4.4f, 6.4f)
                close()
            }
        }
    }

    /** 计划（时钟）。 */
    val Clock: ImageVector by lazy {
        icon("AdSkip.Clock") {
            pathStroke {
                moveTo(3.6f, 12f)
                arcTo(8.4f, 8.4f, 0f, false, false, 20.4f, 12f)
                arcTo(8.4f, 8.4f, 0f, false, false, 3.6f, 12f)
                close()
                moveTo(12f, 7.4f)
                lineTo(12f, 12.4f)
                lineTo(15.4f, 14.2f)
            }
        }
    }

    /** 保活（心电脉冲）。 */
    val Activity: ImageVector by lazy {
        icon("AdSkip.Activity") {
            pathStroke {
                moveTo(3.4f, 12.6f)
                lineTo(7.6f, 12.6f)
                lineTo(9.6f, 6.8f)
                lineTo(12.8f, 17.2f)
                lineTo(14.8f, 12.6f)
                lineTo(20.6f, 12.6f)
            }
        }
    }

    /** 快照（相机）。 */
    val Snapshot: ImageVector by lazy {
        icon("AdSkip.Snapshot") {
            pathStroke {
                moveTo(8.9f, 7.2f)
                lineTo(9.7f, 5.3f)
                quadTo(9.9f, 4.8f, 10.5f, 4.8f)
                lineTo(13.5f, 4.8f)
                quadTo(14.1f, 4.8f, 14.3f, 5.3f)
                lineTo(15.1f, 7.2f)
                roundedRect(3.9f, 7.2f, 16.2f, 12f, 2f)
                moveTo(8.6f, 13.2f)
                arcTo(3.4f, 3.4f, 0f, false, false, 15.4f, 13.2f)
                arcTo(3.4f, 3.4f, 0f, false, false, 8.6f, 13.2f)
                close()
            }
        }
    }

    /** 反馈（对话气泡）。 */
    val Feedback: ImageVector by lazy {
        icon("AdSkip.Feedback") {
            pathStroke {
                roundedRect(4f, 4.8f, 16f, 11.6f, 1.8f)
                moveTo(9.6f, 16.4f)
                lineTo(7.8f, 19.6f)
                lineTo(7.8f, 16.4f)
            }
        }
    }

    /** 免打扰（月牙，开口朝右）。 */
    val Moon: ImageVector by lazy {
        icon("AdSkip.Moon") {
            pathStroke {
                moveTo(12f, 4.4f)
                // 外弧：圆心 (12,12) 的左半圆；内凹弧让月牙开口朝右。
                arcTo(7.7f, 7.7f, 0f, false, false, 12f, 19.6f)
                arcTo(11.1f, 11.1f, 0f, false, false, 12f, 4.4f)
                close()
            }
        }
    }

    /**
     * 暂无记录（HUD 取景框 + 摘要行）。
     *
     * 呼应启动器图标的取景框母题：空态不再是孤零零的一行字，
     * 而是品牌语言的一部分。
     */
    val EmptyLogs: ImageVector by lazy {
        icon("AdSkip.EmptyLogs") {
            pathStroke {
                moveTo(3.9f, 8.2f)
                lineTo(3.9f, 4.9f)
                lineTo(7.2f, 4.9f)
                moveTo(16.8f, 4.9f)
                lineTo(20.1f, 4.9f)
                lineTo(20.1f, 8.2f)
                moveTo(20.1f, 15.8f)
                lineTo(20.1f, 19.1f)
                lineTo(16.8f, 19.1f)
                moveTo(7.2f, 19.1f)
                lineTo(3.9f, 19.1f)
                lineTo(3.9f, 15.8f)
                moveTo(8.3f, 9.4f)
                lineTo(15.7f, 9.4f)
                moveTo(8.3f, 12.2f)
                lineTo(15.7f, 12.2f)
                moveTo(8.3f, 15f)
                lineTo(12.9f, 15f)
            }
        }
    }

    // ==================== v4 设计稿新增 ====================

    /**
     * 右向指示符（chevron）。
     *
     * 见文件头「箭头语素自 v4 起解禁」——这是本次**刻意**引入的箭头类语素，
     * 用于标记「整行可点」。仅在行尾作为可点性提示使用，不得用于导航返回。
     */
    val ChevronRight: ImageVector by lazy {
        icon("AdSkip.ChevronRight") {
            pathStroke {
                moveTo(9.6f, 5.4f)
                lineTo(16.2f, 12f)
                lineTo(9.6f, 18.6f)
            }
        }
    }

    /**
     * 左向指示符（返回）。
     *
     * 与 [ChevronRight] 同为本次刻意引入的箭头语素（见文件头说明）。
     * 仅用于二级页的返回入口：系统返回键仍然工作，但「关键词管理」「应用管理」
     * 这类从首页进入的页面必须给出可见的返回路径。
     */
    val ChevronLeft: ImageVector by lazy {
        icon("AdSkip.ChevronLeft") {
            pathStroke {
                moveTo(14.4f, 5.4f)
                lineTo(7.8f, 12f)
                lineTo(14.4f, 18.6f)
            }
        }
    }

    /** 设置（齿轮）。圆心 + 八向齿。 */
    val Settings: ImageVector by lazy {
        icon("AdSkip.Settings") {
            pathStroke {
                moveTo(8.6f, 12f)
                arcTo(3.4f, 3.4f, 0f, false, false, 15.4f, 12f)
                arcTo(3.4f, 3.4f, 0f, false, false, 8.6f, 12f)
                close()
            }
            pathStroke {
                moveTo(12f, 4.4f)
                lineTo(12f, 6.4f)
                moveTo(12f, 17.6f)
                lineTo(12f, 19.6f)
                moveTo(4.4f, 12f)
                lineTo(6.4f, 12f)
                moveTo(17.6f, 12f)
                lineTo(19.6f, 12f)
                moveTo(6.6f, 6.6f)
                lineTo(8f, 8f)
                moveTo(16f, 16f)
                lineTo(17.4f, 17.4f)
                moveTo(17.4f, 6.6f)
                lineTo(16f, 8f)
                moveTo(8f, 16f)
                lineTo(6.6f, 17.4f)
            }
        }
    }

    /** 对勾（完成 / 卖点列表）。 */
    val Check: ImageVector by lazy {
        icon("AdSkip.Check") {
            pathStroke {
                moveTo(5f, 12.6f)
                lineTo(9.8f, 17.4f)
                lineTo(19f, 7f)
            }
        }
    }

    /** 信息（圆形 + i，用于提示条）。 */
    val Info: ImageVector by lazy {
        icon("AdSkip.Info") {
            pathStroke {
                moveTo(3.6f, 12f)
                arcTo(8.4f, 8.4f, 0f, false, false, 20.4f, 12f)
                arcTo(8.4f, 8.4f, 0f, false, false, 3.6f, 12f)
                close()
                moveTo(12f, 11.2f)
                lineTo(12f, 16.4f)
            }
            pathFill {
                moveTo(12.95f, 8.2f)
                arcTo(0.95f, 0.95f, 0f, false, false, 11.05f, 8.2f)
                arcTo(0.95f, 0.95f, 0f, false, false, 12.95f, 8.2f)
                close()
            }
        }
    }

    /**
     * 规则（标签）。
     *
     * 为什么不复用 [Logs]：规则页与记录页在底栏相邻，两个「文档 + 摘要行」
     * 图形并排会让用户无法靠形状区分（形状编码失效，只剩文字）。
     * 标签图形与「关键词」这一语义也更贴近。
     */
    val Tag: ImageVector by lazy {
        icon("AdSkip.Tag") {
            pathStroke {
                moveTo(11.4f, 4.4f)
                lineTo(19.6f, 4.4f)
                lineTo(19.6f, 12.6f)
                lineTo(12.2f, 19.8f)
                lineTo(4.2f, 11.8f)
                close()
            }
            pathFill {
                moveTo(16f, 8f)
                arcTo(1.2f, 1.2f, 0f, false, false, 16f, 5.6f)
                arcTo(1.2f, 1.2f, 0f, false, false, 16f, 8f)
                close()
            }
        }
    }

    /** 火箭（模拟测试入口）。 */
    val Rocket: ImageVector by lazy {
        icon("AdSkip.Rocket") {
            pathStroke {
                moveTo(12f, 3.8f)
                curveTo(15.6f, 6.6f, 16.8f, 11f, 15f, 15f)
                lineTo(9f, 15f)
                curveTo(7.2f, 11f, 8.4f, 6.6f, 12f, 3.8f)
                close()
                moveTo(9f, 15f)
                lineTo(6.6f, 17.6f)
                lineTo(6.6f, 19.4f)
                lineTo(9.8f, 19.4f)
                moveTo(15f, 15f)
                lineTo(17.4f, 17.6f)
                lineTo(17.4f, 19.4f)
                lineTo(14.2f, 19.4f)
            }
            pathStroke {
                moveTo(10.2f, 8.6f)
                arcTo(1.8f, 1.8f, 0f, false, false, 13.8f, 8.6f)
                arcTo(1.8f, 1.8f, 0f, false, false, 10.2f, 8.6f)
                close()
            }
        }
    }
}
