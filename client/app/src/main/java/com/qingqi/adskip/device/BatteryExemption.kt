package com.qingqi.adskip.device

import android.content.Context
import android.os.PowerManager
import com.qingqi.adskip.core.LogRing

/**
 * 电池优化豁免状态。
 *
 * 存在理由：此前它是 `Boolean`，且实现写作
 * `pm?.isIgnoringBatteryOptimizations(pkg) ?: false` —— 取不到 `PowerManager`
 * 或查询抛异常时**返回 false**，与「确实没豁免」不可区分。于是已经豁免的用户
 * 会一直看到「允许后台运行」按钮，点进去系统却显示已允许。
 *
 * 这与无障碍状态那条缺陷是同一类错误：**拿不确定冒充确定**。
 * 故同样收敛为三态，[UNKNOWN] 让 UI 能说「请手动核对」而不是断言未开启。
 */
enum class BatteryExemption {
    /** 已确认豁免（不受电池优化限制）。 */
    EXEMPT,

    /** 已确认未豁免。 */
    NOT_EXEMPT,

    /** 无法确认（系统服务缺失或查询异常）。UI 应引导手动核对。 */
    UNKNOWN,
    ;

    companion object {

        /**
         * 纯决策函数：`null` 查询结果表示查询不可用，落到 [UNKNOWN]。
         *
         * 无 Android 依赖，可被 JVM 单测穷举（见 `BatteryExemptionTest`）。
         */
        fun decide(isExempt: Boolean?): BatteryExemption = when (isExempt) {
            true -> EXEMPT
            false -> NOT_EXEMPT
            null -> UNKNOWN
        }

        /**
         * 查询系统真值。
         *
         * @param powerManager 由调用方取得；`null` 表示系统服务不可用。
         */
        fun detect(powerManager: PowerManager?, packageName: String): BatteryExemption {
            val isExempt = if (powerManager == null) {
                null
            } else {
                try {
                    powerManager.isIgnoringBatteryOptimizations(packageName)
                } catch (e: Exception) {
                    LogRing.w("BatteryExemption", "查询电池优化豁免失败: ${e.message}")
                    null
                }
            }
            return decide(isExempt)
        }

        /** 便捷入口：自行取系统服务后查询。 */
        fun detect(context: Context): BatteryExemption =
            detect(context.getSystemService(PowerManager::class.java), context.packageName)
    }
}
