package com.ldp.adskip.device

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.view.accessibility.AccessibilityManager
import com.ldp.adskip.core.LogRing

/**
 * 无障碍服务的**有效状态**。
 *
 * 存在理由：此前「服务开没开」在代码里有三份表示，且 UI 用的那份会过期——
 * 实测可复现的不一致是：用户在系统设置里关掉无障碍后，首页与「我的」页仍显示
 * 「服务运行中」，而快捷磁贴已经显示「已停止」。同一台设备两个说法。
 *
 * 三份表示与各自的角色：
 * - **系统真值**：`AccessibilityManager.getEnabledAccessibilityServiceList`。
 *   权威，任何时刻查询都正确；代价是需要 `Context`，且理论上可能抛异常。
 * - **进程信号**：Service 的 `onServiceConnected` / `onDestroy` 驱动的布尔值。
 *   实时（能捕捉用户刚按下开关的那一刻），但进程被杀、或系统关了开关而回调未到时过期。
 * - **查询失败**：既拿不到真值也拿不到信号。
 *
 * 本文件把它们收敛成一个三态枚举。**查询失败不得谎报为「未开启」**：那会让已经开启
 * 服务的用户被反复引导去设置页，而状态永远不变——这是 [UNKNOWN] 存在的唯一理由。
 *
 * 依赖边界：本文件属 `device/` 层，故不 import `service/`（磁贴同样受此约束），
 * 因此以「与自身同包名的无障碍 Service」作为查询目标，而非引用 `SkipAdService`。
 */
enum class AccessibilityStatus {
    /** 已确认开启。 */
    ON,

    /** 已确认关闭。 */
    OFF,

    /** 无法确认（查询异常且无进程信号可依据）。UI 应引导用户手动核对，而不是断言关闭。 */
    UNKNOWN,
    ;

    companion object {

        /**
         * 本应用无障碍服务的类名。
         *
         * 为什么是字符串常量而不是 `SkipAdService::class.java.name`：
         * - `ui/` 层**禁止** import `service/`（依赖倒置，见 ArchitectureBoundaryTest），
         *   但 ViewModel 需要它来告诉本文件「查哪个服务」；
         * - 若把静态方法放在 `SkipAdService` 上，它会反向 import `device/`，形成循环。
         *
         * 因此取值为字面量，并由 `AccessibilityStatusContractTest` 校验它与
         * AndroidManifest 中声明的服务类一致——改名而漏改这里会让契约变红。
         */
        const val SKIP_AD_SERVICE_CLASS_NAME = "com.ldp.adskip.service.SkipAdService"

        /**
         * 纯决策函数：把三种输入收敛为有效状态。
         *
         * 优先级刻意如此——**系统真值 > 进程信号 > 未知**：
         * 进程信号只是「曾经连上/断开」的痕迹，真值才是此刻的事实。
         * 若反过来让信号覆盖真值，缺陷会原样复现。
         *
         * 这个函数无 Android 依赖，因此可被 JVM 单测穷举覆盖（见 `AccessibilityStatusTest`）。
         *
         * @param systemEnabled 系统真值；`null` 表示查询失败
         * @param processSignal 进程内信号；`null` 表示不可用
         */
        fun decide(systemEnabled: Boolean?, processSignal: Boolean?): AccessibilityStatus = when {
            systemEnabled == true -> ON
            systemEnabled == false -> OFF
            processSignal == true -> ON
            processSignal == false -> OFF
            else -> UNKNOWN
        }

        /**
         * 查询系统真值。
         *
         * @param serviceClassName 无障碍 Service 的类名（如 `SkipAdService`），
         *        与 [context] 的包名共同构成查询目标。
         * @param processSignal 查询失败时的兜底依据。
         */
        fun detect(context: Context, serviceClassName: String, processSignal: Boolean?): AccessibilityStatus {
            val manager = context.getSystemService(AccessibilityManager::class.java)
            val systemEnabled = if (manager == null) {
                null
            } else {
                try {
                    enabledContains(manager, context.packageName, serviceClassName)
                } catch (e: Exception) {
                    LogRing.w("AccessibilityStatus", "查询已启用无障碍服务失败: ${e.message}")
                    null
                }
            }
            return decide(systemEnabled, processSignal)
        }

        /**
         * 系统「已启用」列表里是否包含目标服务。
         *
         * 独立成函数以便推断：它只依赖 [AccessibilityManager] 的返回结构，
         * 不参与决策优先级。
         */
        private fun enabledContains(
            manager: AccessibilityManager,
            packageName: String,
            serviceClassName: String,
        ): Boolean {
            val self = ComponentName(packageName, serviceClassName)
            return manager
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { info ->
                    val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
                    ComponentName(serviceInfo.packageName, serviceInfo.name) == self
                }
        }
    }
}
