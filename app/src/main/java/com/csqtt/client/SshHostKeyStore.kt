package com.csqtt.client

import android.content.Context
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import java.util.Collections

object SshHostKeyStore {
    private const val PREFS_NAME = "ssh_known_hosts"
    private const val FINGERPRINT_ALGORITHM = "SHA-256"

    private fun entryKey(host: String, port: Int): String =
        "${host.trim().lowercase()}:$port"

    fun fingerprint(key: PublicKey): String {
        val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
        val digest = MessageDigest.getInstance(FINGERPRINT_ALGORITHM).digest(blob)
        val encoded = Base64.getEncoder().withoutPadding().encodeToString(digest)
        return "SHA256:$encoded"
    }

    /** A server that presented a key other than the one remembered for it: the old and the new fingerprint. */
    data class KeyChange(val host: String, val port: Int, val pinned: String, val presented: String)

    @Volatile
    private var lastChange: KeyChange? = null

    /** The key change the last refused connection to [host]:[port] met, once; null when that was not the reason. */
    fun takeKeyChange(host: String, port: Int): KeyChange? {
        val change = lastChange ?: return null
        if (entryKey(change.host, change.port) != entryKey(host, port)) return null
        lastChange = null
        return change
    }

    /**
     * The user accepted the new key: remember exactly the one they were shown. Clearing the entry instead would pin
     * whatever key the next connection meets, which need not be the one on the screen.
     */
    fun trust(context: Context, change: KeyChange) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(entryKey(change.host, change.port), change.presented)
            .apply()
        DeployManager.writeError("SSH host key for ${entryKey(change.host, change.port)} trusted by the user: ${change.presented}")
    }

    fun clear(context: Context, host: String, port: Int) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(entryKey(host, port))
            .apply()
    }

    class TofuVerifier(context: Context) : HostKeyVerifier {
        private val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
            val presented = fingerprint(key)
            val entry = entryKey(hostname, port)
            val pinned = prefs.getString(entry, null)
            if (pinned == null) {
                prefs.edit().putString(entry, presented).apply()
                DeployManager.writeError("SSH host key pinned for $entry: $presented")
                return true
            }
            if (pinned == presented) {
                return true
            }
            lastChange = KeyChange(hostname, port, pinned, presented)
            DeployManager.writeError(
                "SSH host key mismatch for $entry: expected $pinned, presented $presented"
            )
            return false
        }

        override fun findExistingAlgorithms(hostname: String, port: Int): List<String> =
            Collections.emptyList()
    }
}
