package com.ldp.adskip.core

import android.content.SharedPreferences

/** 监听器类型别名：全限定名过长，收口后签名与映射表均可一行放下 */
private typealias PrefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener

/**
 * 加密偏好视图：对外是普通 [SharedPreferences] 接口，对内每个值经
 * [SecureValueCipher] 封成密文信封后落进底层明文容器（键名保留明文，
 * 便于调试与迁移；值全部加密）。
 *
 * 为什么实现接口而不是改调用方：`data/Prefs`、`StatsRepository`、
 * `LanguagePreferences` 等全部读写点零改动，存储机制对上层透明——
 * 也避免为换加密后端在业务代码里掀起无关重排。
 *
 * 损坏值处理：单个值解不出来（换钥/损坏）按「不存在」处理，读取返回默认值，
 * 不让一条坏数据打断整个读取链路。
 */
internal class SecureSharedPreferences(
    private val delegate: SharedPreferences,
    private val cipher: SecureValueCipher,
) : SharedPreferences {

    override fun getAll(): MutableMap<String, Any?> {
        val out = mutableMapOf<String, Any?>()
        for ((key, raw) in delegate.all) {
            val value = (raw as? String)?.let(cipher::unseal) ?: continue
            out[key] = value.toAny()
        }
        return out
    }

    override fun getString(key: String?, defValue: String?): String? =
        read(key)?.let { (it as? StoredValue.Str)?.value } ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        read(key)?.let { (it as? StoredValue.StrSet)?.value?.toMutableSet() } ?: defValues

    override fun getInt(key: String?, defValue: Int): Int =
        read(key)?.let { (it as? StoredValue.Int)?.value } ?: defValue

    override fun getLong(key: String?, defValue: Long): Long =
        read(key)?.let { (it as? StoredValue.Long)?.value } ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float =
        read(key)?.let { (it as? StoredValue.Float)?.value } ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        read(key)?.let { (it as? StoredValue.Bool)?.value } ?: defValue

    override fun contains(key: String?): Boolean = read(key) != null

    override fun edit(): SharedPreferences.Editor = SecureEditor(delegate.edit(), cipher)

    /** 原监听器 → 包装监听器（回调传出本视图；反注册时按原实例找回） */
    private val listenerWrappers = mutableMapOf<PrefChangeListener, PrefChangeListener>()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        if (listener == null) return
        val wrapper = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            listener.onSharedPreferenceChanged(this, key)
        }
        listenerWrappers[listener] = wrapper
        delegate.registerOnSharedPreferenceChangeListener(wrapper)
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        if (listener == null) return
        listenerWrappers.remove(listener)?.let { delegate.unregisterOnSharedPreferenceChangeListener(it) }
    }

    private fun read(key: String?): StoredValue? {
        if (key == null) return null
        val raw = delegate.all[key] as? String ?: return null
        return cipher.unseal(raw)
    }

    private class SecureEditor(private val editor: SharedPreferences.Editor, private val cipher: SecureValueCipher) :
        SharedPreferences.Editor {

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key == null) return this
            if (value == null) {
                editor.remove(key)
            } else {
                editor.putString(key, cipher.seal(StoredValue.Str(value)))
            }
            return this
        }

        @Suppress("UNCHECKED_CAST")
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key == null) return this
            if (values == null) {
                editor.remove(key)
            } else {
                editor.putString(key, cipher.seal(StoredValue.StrSet(values.toSet())))
            }
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) editor.putString(key, cipher.seal(StoredValue.Int(value)))
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) editor.putString(key, cipher.seal(StoredValue.Long(value)))
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) editor.putString(key, cipher.seal(StoredValue.Float(value)))
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) editor.putString(key, cipher.seal(StoredValue.Bool(value)))
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) editor.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            editor.clear()
            return this
        }

        override fun commit(): Boolean = editor.commit()

        override fun apply() = editor.apply()
    }
}

/** 解封后的值 → SharedPreferences 语义里的原生类型 */
private fun StoredValue.toAny(): Any = when (this) {
    is StoredValue.Str -> value
    is StoredValue.Int -> value
    is StoredValue.Long -> value
    is StoredValue.Float -> value
    is StoredValue.Bool -> value
    is StoredValue.StrSet -> value
}
