package com.ldp.adskip.data

import android.content.Context
import org.json.JSONObject

/**
 * 自定义取点规则（ROADMAP「截屏取点自定义规则」）：
 * 对无法识别的广告，用户手动标注「跳过」按钮的点击位置，之后自动点击该点。
 *
 * 设计取舍：
 * - 取点用**透明悬浮层在真实界面上直接点**，而不是截屏后在图片上标注——
 *   目标一致（手动标注跳过位置），但无需录屏/截屏权限与授权弹窗，且所见即所得；
 * - 坐标以**屏幕比例**（fx/fy ∈ 0..1）保存而非像素：换分辨率、折叠屏展开态
 *   等情况下规则仍然落在相对位置上；
 * - 仅存本机加密偏好（[Prefs]），不参与云端同步——个人标注不是共享规则，
 *   且让恶意标注走云端下发会放大点击面。
 */
object PointRules {

    /** 一条取点规则：包名 + 相对坐标 */
    data class Entry(val pkg: String, val fx: Float, val fy: Float)

    private const val KEY_POINT_RULES = "point_rules"

    /** 取当前应用的取点规则 */
    fun get(context: Context, pkg: String): Entry? = all(context).firstOrNull { it.pkg == pkg }

    /** 写入/覆盖某应用的取点规则 */
    fun set(context: Context, pkg: String, fx: Float, fy: Float) {
        val next = all(context).filterNot { it.pkg == pkg } + Entry(pkg, fx.coerceIn(0f, 1f), fy.coerceIn(0f, 1f))
        save(context, next)
    }

    fun remove(context: Context, pkg: String) {
        save(context, all(context).filterNot { it.pkg == pkg })
    }

    fun clear(context: Context) = save(context, emptyList())

    /** 全部规则（按包名排序，便于列表稳定展示） */
    fun all(context: Context): List<Entry> {
        val raw = Prefs.sp(context).getString(KEY_POINT_RULES, null) ?: return emptyList()
        return try {
            val json = JSONObject(raw)
            val out = mutableListOf<Entry>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val o = json.optJSONObject(pkg) ?: continue
                val fx = o.optDouble("fx", -1.0)
                val fy = o.optDouble("fy", -1.0)
                if (fx < 0 || fy < 0) continue
                out.add(Entry(pkg, fx.toFloat().coerceIn(0f, 1f), fy.toFloat().coerceIn(0f, 1f)))
            }
            out.sortedBy { it.pkg }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 相对坐标 → 实际像素。
     *
     * 纯函数、独立可测：悬浮层取点与无障碍点击两条链路共用同一换算，
     * 避免「存一套算一套」导致点偏。
     */
    fun scale(fx: Float, fy: Float, width: Int, height: Int): Pair<Float, Float> =
        fx.coerceIn(0f, 1f) * width to fy.coerceIn(0f, 1f) * height

    private fun save(context: Context, entries: List<Entry>) {
        val json = JSONObject()
        for (e in entries) {
            json.put(e.pkg, JSONObject().put("fx", e.fx.toDouble()).put("fy", e.fy.toDouble()))
        }
        Prefs.sp(context).edit().putString(KEY_POINT_RULES, json.toString()).apply()
    }
}
