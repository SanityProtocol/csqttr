package com.csqtt.client

import org.junit.Assert.assertEquals
import org.junit.Test

class ParticipantHashesTest {
    private val a = "a".repeat(20)
    private val b = "b".repeat(20)
    private val c = "c".repeat(20)

    @Test
    fun `the owner's hashes come first, then the participant's own`() {
        assertEquals("$a,$b,$c", participantHashes(listOf(a, b), emptySet(), c))
    }

    @Test
    fun `a removed owner hash is left out, and a replacement takes the participant's place`() {
        assertEquals("$b,$c", participantHashes(listOf(a, b), setOf(a), c))
    }

    @Test
    fun `everything removed leaves only the participant's own, duplicates once`() {
        assertEquals(c, participantHashes(listOf(a), setOf(a), "$c, $c"))
        assertEquals(a, participantHashes(listOf(a), emptySet(), a))
    }
}
