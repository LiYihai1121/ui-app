package com.qingqi.adskip.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 厂商识别与保活候选入口表的 JVM 单测。
 *
 * 入口表会随 ROM 升级失效，故这里只固化「表本身的结构契约」（可解析、不可越权、包名/action 可声明），
 * 具体在某台真机上是否可跳转由 [KeepAliveNavigator] 运行时探测，不在单测中假设。
 */
class VendorKeepAliveTest {

    // ---------- 厂商识别 ----------

    @Test
    fun `detect major vendor manufacturers`() {
        assertEquals(Vendor.XIAOMI, VendorKeepAlive.detect("Xiaomi"))
        assertEquals(Vendor.HUAWEI, VendorKeepAlive.detect("HUAWEI"))
        assertEquals(Vendor.HONOR, VendorKeepAlive.detect("HONOR"))
        assertEquals(Vendor.OPPO, VendorKeepAlive.detect("OPPO"))
        assertEquals(Vendor.VIVO, VendorKeepAlive.detect("vivo"))
        assertEquals(Vendor.MEIZU, VendorKeepAlive.detect("Meizu"))
        assertEquals(Vendor.ONEPLUS, VendorKeepAlive.detect("OnePlus"))
        assertEquals(Vendor.SAMSUNG, VendorKeepAlive.detect("samsung"))
    }

    @Test
    fun `detect sub brands fall back to their rom family`() {
        assertEquals(Vendor.XIAOMI, VendorKeepAlive.detect("Xiaomi", "Redmi"))
        assertEquals(Vendor.XIAOMI, VendorKeepAlive.detect(brand = "POCO"))
        assertEquals(Vendor.OPPO, VendorKeepAlive.detect("realme"))
        assertEquals(Vendor.VIVO, VendorKeepAlive.detect(brand = "iQOO"))
    }

    @Test
    fun `unknown or blank input falls back to generic`() {
        assertEquals(Vendor.GENERIC, VendorKeepAlive.detect(null))
        assertEquals(Vendor.GENERIC, VendorKeepAlive.detect(""))
        assertEquals(Vendor.GENERIC, VendorKeepAlive.detect("   "))
        assertEquals(Vendor.GENERIC, VendorKeepAlive.detect("Google", "Pixel"))
    }

    @Test
    fun `detect is case insensitive`() {
        assertEquals(Vendor.XIAOMI, VendorKeepAlive.detect("XIAOMI"))
        assertEquals(Vendor.VIVO, VendorKeepAlive.detect("VIVO"))
    }

    // ---------- 候选入口表结构 ----------

    @Test
    fun `every vendor except generic has at least one package scoped entry`() {
        for (vendor in Vendor.entries - Vendor.GENERIC) {
            val entries = VendorKeepAlive.entries(vendor)
            assertTrue("$vendor 缺少候选入口", entries.isNotEmpty())
            assertTrue(
                "$vendor 的候选入口必须包含包名限定跳转（否则无法做包可见性声明）",
                entries.any { it.pkg != null },
            )
        }
    }

    @Test
    fun `generic vendor has no vendor specific entry but keeps fallback chain`() {
        assertTrue(VendorKeepAlive.entries(Vendor.GENERIC).isEmpty())
        assertEquals(
            VendorKeepAlive.genericEntries(),
            VendorKeepAlive.candidates(Vendor.GENERIC),
        )
    }

    @Test
    fun `candidates keep vendor entries before generic fallbacks`() {
        val candidates = VendorKeepAlive.candidates(Vendor.XIAOMI)
        val genericSize = VendorKeepAlive.genericEntries().size
        assertEquals(
            VendorKeepAlive.entries(Vendor.XIAOMI).size + genericSize,
            candidates.size,
        )
        assertTrue(candidates.take(candidates.size - genericSize).all { it.pkg != null })
    }

    @Test
    fun `component entries use fully qualified class names`() {
        for (vendor in Vendor.entries) {
            for (entry in VendorKeepAlive.candidates(vendor)) {
                val cls = entry.cls ?: continue
                assertFalse(
                    "${entry.key} 的类名必须全限定（不得使用相对 `.Foo` 写法）",
                    cls.startsWith("."),
                )
                assertTrue(
                    "${entry.key} 的类名至少两段（包名 + 类名）",
                    cls.count { it == '.' } >= 2,
                )
            }
        }
    }

    @Test
    fun `class name may live outside the component package`() {
        // 三星 One UI 的电池管理 Activity 类在 com.samsung.android.sm.*，
        // 但由 com.samsung.android.lool 这个包提供——这是框架事实，不是笔误。
        val samsungBattery = VendorKeepAlive.entries(Vendor.SAMSUNG)
            .first { it.pkg == "com.samsung.android.lool" }
        assertEquals("com.samsung.android.sm.battery.ui.BatteryActivity", samsungBattery.cls)
    }

    @Test
    fun `vendor entries never use the system data uri payload`() {
        // DATA_URI 是系统「应用详情」页的约定，只属于通用兜底；厂商入口一律是「本应用配置页」
        for (vendor in Vendor.entries - Vendor.GENERIC) {
            for (entry in VendorKeepAlive.entries(vendor)) {
                assertTrue(
                    "${entry.key} 不应使用 DATA_URI 载荷",
                    entry.payload != PackagePayload.DATA_URI,
                )
            }
        }
    }

    @Test
    fun `package name extra is only used with an action`() {
        // 少数厂商（如魅族）用 action + packageName extra 定位目标页，不接受组件名
        for (vendor in Vendor.entries) {
            for (entry in VendorKeepAlive.candidates(vendor)) {
                if (entry.payload == PackagePayload.EXTRA_NAME) {
                    assertTrue(
                        "${entry.key} 使用 extra 传包名时应配 action",
                        entry.action != null,
                    )
                }
            }
        }
    }

    @Test
    fun `generic app details entry carries the package uri`() {
        val appDetails = VendorKeepAlive.genericEntries().first()
        assertEquals("android.settings.APPLICATION_DETAILS_SETTINGS", appDetails.action)
        assertEquals(PackagePayload.DATA_URI, appDetails.payload)
    }

    @Test
    fun `generic fallback always offers app details and accessibility settings`() {
        val actions = VendorKeepAlive.genericEntries().mapNotNull { it.action }
        assertTrue(actions.contains("android.settings.APPLICATION_DETAILS_SETTINGS"))
        assertTrue(actions.contains("android.settings.ACCESSIBILITY_SETTINGS"))
    }

    @Test
    fun `allPackages and allActions cover every candidate entry`() {
        val entries = Vendor.entries.flatMap { VendorKeepAlive.candidates(it) }
        assertTrue(entries.size > Vendor.entries.size)
        for (entry in entries) {
            entry.pkg?.let { assertTrue(it in VendorKeepAlive.allPackages()) }
            entry.action?.let { assertTrue(it in VendorKeepAlive.allActions()) }
        }
        assertTrue(VendorKeepAlive.allPackages().all { it.contains(".") })
        assertTrue(VendorKeepAlive.allActions().all { it.contains(".") })
    }

    @Test
    fun `guide entry rejects malformed definitions`() {
        // 至少要有 action 或 pkg，否则跳转无从发起
        var thrown = false
        try {
            GuideEntry()
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("空 GuideEntry 应被拒绝", thrown)

        // 指定 cls 必须同时指定 pkg
        thrown = false
        try {
            GuideEntry(action = "some.action", cls = "com.example.Foo")
        } catch (e: IllegalArgumentException) {
            thrown = true
        }
        assertTrue("缺少 pkg 的组件入口应被拒绝", thrown)
    }
}
