package com.csqtt.client

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyInt
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import java.security.KeyPairGenerator
import java.security.PublicKey

class SshHostKeyStoreTest {
    private val prefs = MemoryPrefs()
    private val context: Context = mock(Context::class.java).also {
        `when`(it.applicationContext).thenReturn(it)
        `when`(it.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
    }
    private val verifier = SshHostKeyStore.TofuVerifier(context)

    private fun newKey(): PublicKey = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.genKeyPair().public

    @Test
    fun `a changed key is refused and reported once, with both fingerprints`() {
        val old = newKey()
        val new = newKey()
        assertTrue(verifier.verify("1.2.3.4", 22, old))
        assertTrue(verifier.verify("1.2.3.4", 22, old))
        assertFalse(verifier.verify("1.2.3.4", 22, new))

        assertNull(SshHostKeyStore.takeKeyChange("5.6.7.8", 22))
        assertNull(SshHostKeyStore.takeKeyChange("1.2.3.4", 2222))
        val change = SshHostKeyStore.takeKeyChange("1.2.3.4", 22)!!
        assertEquals(SshHostKeyStore.fingerprint(old), change.pinned)
        assertEquals(SshHostKeyStore.fingerprint(new), change.presented)
        assertNull(SshHostKeyStore.takeKeyChange("1.2.3.4", 22))
    }

    @Test
    fun `trusting keeps exactly the key that was shown`() {
        val old = newKey()
        val shown = newKey()
        assertTrue(verifier.verify("vps.example", 22, old))
        assertFalse(verifier.verify("vps.example", 22, shown))
        SshHostKeyStore.trust(context, SshHostKeyStore.takeKeyChange("VPS.example", 22)!!)

        assertTrue(verifier.verify("vps.example", 22, shown))
        assertFalse(verifier.verify("vps.example", 22, old))
        // a third key after the trust is a new change, not accepted on the strength of the earlier one
        assertFalse(verifier.verify("vps.example", 22, newKey()))
    }

    private class MemoryPrefs : SharedPreferences {
        val values = mutableMapOf<String, String>()
        override fun getString(key: String, defValue: String?): String? = values[key] ?: defValue
        override fun contains(key: String): Boolean = key in values
        override fun getAll(): MutableMap<String, *> = values
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String, defValue: Int): Int = defValue
        override fun getLong(key: String, defValue: Long): Long = defValue
        override fun getFloat(key: String, defValue: Float): Float = defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            override fun putString(key: String, value: String?) = apply { if (value == null) values.remove(key) else values[key] = value }
            override fun remove(key: String) = apply { values.remove(key) }
            override fun putStringSet(key: String, values: MutableSet<String>?) = this
            override fun putInt(key: String, value: Int) = this
            override fun putLong(key: String, value: Long) = this
            override fun putFloat(key: String, value: Float) = this
            override fun putBoolean(key: String, value: Boolean) = this
            override fun clear() = apply { values.clear() }
            override fun commit() = true
            override fun apply() = Unit
        }
    }
}
