package com.qingqi.adskip.ui

import androidx.annotation.StringRes
import com.qingqi.adskip.R
import com.qingqi.adskip.device.Vendor

/**
 * ROM 枚举 → 本地化显示名。
 *
 * 放在 `ui` 层而非 `device` 层：`device` 只负责识别与跳转，文案呈现是 UI 关注点，
 * 使 `device` 保持「零 `R` 依赖、可纯 JVM 单测」的纯数据 + 系统调用结构。
 */
@StringRes
fun vendorLabelRes(vendor: Vendor): Int = when (vendor) {
    Vendor.XIAOMI -> R.string.vendor_xiaomi
    Vendor.HUAWEI -> R.string.vendor_huawei
    Vendor.HONOR -> R.string.vendor_honor
    Vendor.OPPO -> R.string.vendor_oppo
    Vendor.VIVO -> R.string.vendor_vivo
    Vendor.MEIZU -> R.string.vendor_meizu
    Vendor.ONEPLUS -> R.string.vendor_oneplus
    Vendor.SAMSUNG -> R.string.vendor_samsung
    Vendor.GENERIC -> R.string.vendor_generic
}
