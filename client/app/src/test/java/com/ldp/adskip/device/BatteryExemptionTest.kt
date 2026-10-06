package com.ldp.adskip.device

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [BatteryExemption.decide] 的真值表。
 *
 * 重点是那条边界：**查询不可用不得报成「未豁免」**。
 * 报错方向反过来（把未知当已豁免）会让用户以为已放行而后台仍被杀；
 * 报成未豁免则让已放行的用户反复去设置页。两者都错，故引入 UNKNOWN。
 */
class BatteryExemptionTest {

    @Test
    fun `a failed query is UNKNOWN rather than NOT_EXEMPT`() {
        assertEquals(BatteryExemption.UNKNOWN, BatteryExemption.decide(isExempt = null))
    }

    @Test
    fun `known answers map to their own states`() {
        assertEquals(BatteryExemption.EXEMPT, BatteryExemption.decide(isExempt = true))
        assertEquals(BatteryExemption.NOT_EXEMPT, BatteryExemption.decide(isExempt = false))
    }

    @Test
    fun `missing system service yields UNKNOWN`() {
        assertEquals(
            BatteryExemption.UNKNOWN,
            BatteryExemption.detect(powerManager = null, packageName = "com.ldp.adskip"),
        )
    }
}
