package com.ldp.adskip.core

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator

/**
 * 加密偏好视图（SharedPreferences 兼容层）JVM 单测：
 * 用内存假容器验证「读写语义与 SharedPreferences 一致」且「底层落盘的是密文」。
 */
class SecureSharedPreferencesTest {

    private val fake = FakeSharedPreferences()
    private val cipher = SecureValueCipher(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())
    private val secure = SecureSharedPreferences(fake, cipher)

    @Test
    fun `round trips every type through the shared-preferences contract`() {
        secure.edit()
            .putString("s", "跳过")
            .putInt("i", 7)
            .putLong("l", 9L)
            .putFloat("f", 3.5f)
            .putBoolean("b", true)
            .putStringSet("ss", mutableSetOf("a", "b"))
            .commit()

        assertEquals("跳过", secure.getString("s", null))
        assertEquals(7, secure.getInt("i", 0))
        assertEquals(9L, secure.getLong("l", 0L))
        assertEquals(3.5f, secure.getFloat("f", 0f), 0f)
        assertTrue(secure.getBoolean("b", false))
        assertEquals(setOf("a", "b"), secure.getStringSet("ss", null))
        assertTrue(secure.contains("s"))
    }

    @Test
    fun `underlying container stores ciphertext not plaintext`() {
        secure.edit().putString("s", "机密关键词").commit()
        val raw = fake.raw("s") as String
        assertTrue(raw.startsWith("enc:v1:"))
        assertFalse(raw.contains("机密关键词"))
    }

    @Test
    fun `wrong-type and missing reads return defaults`() {
        secure.edit().putInt("i", 7).commit()
        assertNull(secure.getString("i", null))
        assertEquals("def", secure.getString("missing", "def"))
        assertEquals(0, secure.getInt("missing", 0))
        assertFalse(secure.contains("missing"))
    }

    @Test
    fun `remove and clear work as expected`() {
        secure.edit().putString("s", "x").putInt("i", 1).commit()
        secure.edit().remove("s").commit()
        assertFalse(secure.contains("s"))
        assertTrue(secure.contains("i"))
        secure.edit().clear().commit()
        assertFalse(secure.contains("i"))
    }

    @Test
    fun `corrupt value reads as default not crash`() {
        fake.putRaw("s", "not-an-envelope")
        assertNull(secure.getString("s", null))
        assertEquals("def", secure.getString("s", "def"))
    }

    /** 内存假容器：只关心键值存取，不做加密 */
    private class FakeSharedPreferences : SharedPreferences {
        private val store = mutableMapOf<String, Any?>()

        fun raw(key: String): Any? = store[key]

        fun putRaw(key: String, value: Any?) {
            store[key] = value
        }

        override fun getAll(): MutableMap<String, Any?> = store.toMutableMap()

        override fun getString(key: String?, defValue: String?): String? = store[key] as? String ?: defValue

        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST")
            (store[key] as? Set<String>)?.toMutableSet() ?: defValues

        override fun getInt(key: String?, defValue: Int): Int = store[key] as? Int ?: defValue

        override fun getLong(key: String?, defValue: Long): Long = store[key] as? Long ?: defValue

        override fun getFloat(key: String?, defValue: Float): Float = store[key] as? Float ?: defValue

        override fun getBoolean(key: String?, defValue: Boolean): Boolean = store[key] as? Boolean ?: defValue

        override fun contains(key: String?): Boolean = key != null && store.containsKey(key)

        override fun edit(): SharedPreferences.Editor = FakeEditor()

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        private inner class FakeEditor : SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearFlag = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) pending[key] = values
                return this
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }

            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = REMOVE
                return this
            }

            override fun clear(): SharedPreferences.Editor {
                clearFlag = true
                return this
            }

            override fun commit(): Boolean {
                if (clearFlag) store.clear()
                for ((k, v) in pending) {
                    if (v === REMOVE) store.remove(k) else store[k] = v
                }
                return true
            }

            override fun apply() {
                commit()
            }
        }

        private companion object {
            val REMOVE = Any()
        }
    }
}
