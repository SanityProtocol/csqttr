package com.csqtt.client.ui.utils

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class CsqttLink(
    val host: String,
    val port: Int,
    val password: String = "",
    val hashes: List<String> = emptyList(),
    val vkAccessToken: String = "",
)

private const val CSQTT_CIPHER_KEY_SEED = "csqtt-chiper-key"

private val cipherKey: SecretKeySpec by lazy {
    val digest = MessageDigest.getInstance("SHA-256")
    val keyBytes = digest.digest(CSQTT_CIPHER_KEY_SEED.toByteArray(Charsets.UTF_8))
    SecretKeySpec(keyBytes, "AES")
}

fun decodeBase64Safe(input: String): ByteArray? {
    val clean = input.replace("\n", "").replace("\r", "").replace(" ", "").trim()
    return runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            runCatching { java.util.Base64.getUrlDecoder().decode(clean) }
                .getOrElse { java.util.Base64.getDecoder().decode(clean) }
        } else {
            android.util.Base64.decode(clean, android.util.Base64.DEFAULT or android.util.Base64.URL_SAFE)
        }
    }.recoverCatching {
        runCatching { java.util.Base64.getUrlDecoder().decode(clean) }
            .getOrElse { java.util.Base64.getDecoder().decode(clean) }
    }.getOrNull()
}

fun decryptCsqttPayload(rawPayload: String): String? {
    val bytes = decodeBase64Safe(rawPayload) ?: return null
    if (bytes.size >= 28) {
        val decrypted = runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, bytes, 0, 12)
            cipher.init(Cipher.DECRYPT_MODE, cipherKey, spec)
            val plaintext = cipher.doFinal(bytes, 12, bytes.size - 12)
            String(plaintext, Charsets.UTF_8)
        }.getOrNull()
        if (decrypted != null) {
            return decrypted
        }
    }
    return null
}

fun encryptCsqttPayload(plaintext: String): String {
    val iv = runCatching {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(cipherKey)
        val hmacBytes = mac.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        ByteArray(12).apply { System.arraycopy(hmacBytes, 0, this, 0, 12) }
    }.getOrElse {
        ByteArray(12).apply { java.security.SecureRandom().nextBytes(this) }
    }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    val spec = GCMParameterSpec(128, iv)
    cipher.init(Cipher.ENCRYPT_MODE, cipherKey, spec)
    val ciphertextAndTag = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
    val combined = ByteArray(12 + ciphertextAndTag.size)
    System.arraycopy(iv, 0, combined, 0, 12)
    System.arraycopy(ciphertextAndTag, 0, combined, 12, ciphertextAndTag.size)
    return runCatching {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(combined)
        } else {
            android.util.Base64.encodeToString(
                combined,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP
            ).trim()
        }
    }.recoverCatching {
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(combined)
    }.getOrElse { "" }
}

fun parseCsqttLink(raw: String): CsqttLink? {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("csqtt://", ignoreCase = true)) return null
    val payload = trimmed.substring(8).trim()
    val decoded = decryptCsqttPayload(payload) ?: return null
    if (decoded.contains("host=") || decoded.contains("connect?")) {
        val candidate = when {
            decoded.startsWith("csqtt://connect?", ignoreCase = true) -> decoded
            decoded.startsWith("connect?", ignoreCase = true) -> "csqtt://$decoded"
            decoded.startsWith("csqtt://", ignoreCase = true) -> "csqtt://connect?" + decoded.substring(8)
            else -> "csqtt://connect?$decoded"
        }
        val decodedUri = runCatching { java.net.URI(candidate) }.getOrNull()
        if (decodedUri != null) {
            val parsed = parseCsqttV2(decodedUri)
            if (parsed != null) return parsed
        }
    }
    return null
}

fun CsqttLink.peerAddress(): String {
    val formattedHost = if (host.contains(':') && !host.startsWith('[')) "[$host]" else host
    return "$formattedHost:$port"
}

private fun parseCsqttV2(uri: java.net.URI): CsqttLink? {
    if (uri.rawUserInfo != null || uri.port != -1 || uri.rawPath.orEmpty().isNotEmpty()) return null
    val parameters = parseRawQuery(uri.rawQuery ?: return null) ?: return null
    if (parameters["v"]?.let(::decodeQueryComponent) != "2") return null
    val host = parameters["host"]?.let(::decodeQueryComponent)?.takeIf { it.isNotBlank() } ?: return null
    if (host == "0.0.0.0" || host == "127.0.0.1" || host.equals("localhost", ignoreCase = true) || host == "::1") return null
    val port = parameters["peer"]?.let(::decodeQueryComponent)?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    val password = parameters["password"]?.let(::decodeQueryComponent)?.trim().orEmpty()
    if (host.any(Char::isWhitespace) || (password.isNotEmpty() && password.any(Char::isWhitespace))) return null
    val hashes = parameters["hashes"]?.let(::parseLinkHashes) ?: emptyList()
    if (parameters.containsKey("hashes") && hashes.isEmpty()) return null
    val vkAccessToken = parameters["token"]?.let(::parseLinkVkAccessToken).orEmpty()
    if (parameters.containsKey("token") && vkAccessToken.isEmpty()) return null
    if (hashes.isNotEmpty() && vkAccessToken.isNotEmpty()) return null
    return CsqttLink(host, port, password, hashes, vkAccessToken)
}

private fun parseRawQuery(rawQuery: String): Map<String, String>? {
    if (rawQuery.isEmpty()) return null
    val sanitized = rawQuery.replace("&amp;", "&")
    val result = linkedMapOf<String, String>()
    if (sanitized.contains('&') || sanitized.contains(';')) {
        for (part in sanitized.split(Regex("[&;]"))) {
            if (part.isBlank()) continue
            val separator = part.indexOf('=')
            if (separator <= 0) continue
            val key = decodeQueryComponent(part.substring(0, separator)) ?: continue
            val value = part.substring(separator + 1)
            if (key.isNotBlank()) {
                if (result.containsKey(key)) return null
                result[key] = value
            }
        }
        if (result.isNotEmpty() && result.containsKey("v") && result.containsKey("host") && result.containsKey("peer")) {
            return result
        }
    }

    val pattern = Regex("(?:^|[&;]|(?<=[a-zA-Z0-9_]))(v|host|peer|password|client|hashes|token|workers)=([^&?]*?)(?=(?:v|host|peer|password|client|hashes|token|workers)=|$)", RegexOption.IGNORE_CASE)
    val matches = pattern.findAll(sanitized).toList()
    if (matches.isNotEmpty()) {
        val extracted = linkedMapOf<String, String>()
        for (match in matches) {
            val key = decodeQueryComponent(match.groupValues[1].lowercase()) ?: continue
            val value = match.groupValues[2]
            if (extracted.containsKey(key)) return null
            extracted[key] = value
        }
        if (extracted.isNotEmpty() && extracted.containsKey("v") && extracted.containsKey("host") && extracted.containsKey("peer")) {
            return extracted
        }
    }

    return if (result.isNotEmpty()) result else null
}

private fun parseLinkHashes(rawHashes: String): List<String> {
    val parts = rawHashes.split('+')
    if (parts.size !in 1..6 || parts.any { it.isEmpty() }) return emptyList()
    val hashes = parts.map { encoded ->
        decodeQueryComponent(encoded)?.let(::stripVkUrlStatic).orEmpty()
    }
    if (hashes.any { it.length < 16 || it.any(Char::isWhitespace) }) return emptyList()
    return hashes.distinct().takeIf { it.size == hashes.size }.orEmpty()
}

private fun parseLinkVkAccessToken(rawToken: String): String? {
    val token = decodeQueryComponent(rawToken)?.trim().orEmpty()
    return token.takeIf { it.isNotEmpty() && it.length <= 4096 && it.none(Char::isWhitespace) }
}

private fun decodeQueryComponent(value: String): String? {
    return runCatching {
        java.net.URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())
    }.getOrNull()
}

fun stripVkUrlStatic(input: String): String {
    var s = input.trim()
    val lower = s.lowercase()
    val prefixes = listOf(
        "https://vk.com/call/join/",
        "http://vk.com/call/join/",
        "https://m.vk.com/call/join/",
        "http://m.vk.com/call/join/",
        "m.vk.com/call/join/",
        "vk.com/call/join/",
        "https://vk.ru/call/join/",
        "http://vk.ru/call/join/",
        "https://m.vk.ru/call/join/",
        "http://m.vk.ru/call/join/",
        "m.vk.ru/call/join/",
        "vk.ru/call/join/"
    )
    for (prefix in prefixes) {
        if (lower.startsWith(prefix)) {
            s = s.substring(prefix.length)
            break
        }
    }
    val qIdx = s.indexOf('?')
    if (qIdx != -1) s = s.substring(0, qIdx)
    val hIdx = s.indexOf('#')
    if (hIdx != -1) s = s.substring(0, hIdx)
    return s.trimEnd('/')
}

// The server takes the password out of "GETCONF:port|password|…" (split on '|', each field trimmed) and accepts 1..128
// bytes; the rest of the way it is only bytes (HKDF, the TURN digest), so any other character is fine, Cyrillic too.
const val TUNNEL_PASSWORD_MAX_BYTES = 128

fun isValidTunnelPassword(password: String): Boolean =
    password.isNotEmpty() &&
        password.toByteArray(Charsets.UTF_8).size <= TUNNEL_PASSWORD_MAX_BYTES &&
        password.none { it == '|' || it.isWhitespace() || it.isISOControl() }

const val TUNNEL_PASSWORD_RULE = "Без пробелов и «|», не длиннее 128 байт (64 буквы кириллицей)"

fun generate16CharPassword(): String {
    val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    val random = java.security.SecureRandom()
    val sb = StringBuilder(16)
    for (i in 0 until 16) {
        sb.append(chars[random.nextInt(chars.length)])
    }
    return sb.toString()
}

fun formatCsqttLink(
    host: String,
    peer: Int,
    password: String,
    hashes: List<String> = emptyList(),
    token: String = "",
): String {
    val queryParts = mutableListOf("v=2", "host=$host", "peer=$peer", "password=" + java.net.URLEncoder.encode(password, Charsets.UTF_8.name()))
    if (hashes.isNotEmpty()) {
        queryParts.add("hashes=" + hashes.joinToString("+"))
    } else if (token.isNotEmpty()) {
        queryParts.add("token=$token")
    }
    val plain = "connect?" + queryParts.joinToString("&")
    return "csqtt://" + encryptCsqttPayload(plain)
}
