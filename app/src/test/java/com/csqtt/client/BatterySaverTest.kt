package com.csqtt.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatterySaverTest {
    private fun save(mode: String, level: Int = 50, charging: Boolean = false, systemSaver: Boolean = false) =
        BatterySaver.shouldSave(mode, percent = 30, level = level, charging = charging, systemSaver = systemSaver)

    @Test
    fun `auto follows the phone's battery saver, whatever the charge`() {
        assertTrue(save(BatterySaver.MODE_AUTO, level = 80, systemSaver = true))
        assertFalse(save(BatterySaver.MODE_AUTO, level = 5, systemSaver = false))
    }

    @Test
    fun `own level saves at or below the level and not charging, and ignores the phone's saver`() {
        assertTrue(save(BatterySaver.MODE_LEVEL, level = 30))
        assertTrue(save(BatterySaver.MODE_LEVEL, level = 0))
        assertFalse(save(BatterySaver.MODE_LEVEL, level = 31, systemSaver = true))
        assertFalse(save(BatterySaver.MODE_LEVEL, level = 10, charging = true))
        assertFalse(save(BatterySaver.MODE_LEVEL, level = -1))
    }

    @Test
    fun `off and an unknown mode never save`() {
        assertFalse(save(BatterySaver.MODE_OFF, level = 5, systemSaver = true))
        assertFalse(save("", level = 5, systemSaver = true))
    }

    @Test
    fun `auto is the default and first, the default level is offered`() {
        assertEquals(BatterySaver.MODE_AUTO, BatterySaver.DEFAULT_MODE)
        assertEquals(BatterySaver.MODE_AUTO, BatterySaver.MODES.first().first)
        assertTrue(BatterySaver.DEFAULT_PERCENT in BatterySaver.PERCENT_CHOICES)
    }
}
