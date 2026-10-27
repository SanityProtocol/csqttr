package com.csqtt.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MaskOwnerHashesTest {
    private val owner = "AbCdEfGh1234567890xyz"
    private val own = "ZzYyXxWw0987654321abc"

    @Test
    fun `the owner's hash, whole or as the prefixes the app and the client print, is masked`() {
        val masked = maskOwnerHashes(
            "[VK] Хеш \"${displayVkHash(owner)}\" устарел · [КРЕД #1] Запрос (хеш: ${owner.take(8)}...) · $owner",
            listOf(owner),
        )
        assertFalse(masked.contains(owner.take(8)))
        assertEquals("[VK] Хеш \"от владельца\" устарел · [КРЕД #1] Запрос (хеш: от владельца) · от владельца", masked)
    }

    @Test
    fun `the participant's own hashes and other text stay`() {
        val line = "Запрос (хеш: ${own.take(8)}...) порт 46000"
        assertEquals(line, maskOwnerHashes(line, listOf(owner)))
        assertEquals(line, maskOwnerHashes(line, emptyList()))
    }
}
