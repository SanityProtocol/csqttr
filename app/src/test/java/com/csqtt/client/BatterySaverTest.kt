package com.csqtt.client

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatterySaverTest {
    @Test
    fun `saves only when on, at or below the chosen charge and not charging`() {
        assertTrue(BatterySaver.shouldSave(enabled = true, percent = 30, level = 30, charging = false))
        assertTrue(BatterySaver.shouldSave(enabled = true, percent = 30, level = 0, charging = false))
        assertFalse(BatterySaver.shouldSave(enabled = true, percent = 30, level = 31, charging = false))
        assertFalse(BatterySaver.shouldSave(enabled = false, percent = 30, level = 10, charging = false))
        assertFalse(BatterySaver.shouldSave(enabled = true, percent = 30, level = 10, charging = true))
    }

    @Test
    fun `an unknown charge never saves`() {
        assertFalse(BatterySaver.shouldSave(enabled = true, percent = 30, level = -1, charging = false))
    }

    @Test
    fun `the default is among the choices`() {
        assertTrue(BatterySaver.DEFAULT_PERCENT in BatterySaver.PERCENT_CHOICES)
    }
}
