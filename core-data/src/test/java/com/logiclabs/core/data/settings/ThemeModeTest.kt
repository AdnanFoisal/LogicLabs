package com.logiclabs.core.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `fromString parses all hardware theme modes correctly`() {
        assertEquals(ThemeMode.OBSIDIAN, ThemeMode.fromString("OBSIDIAN"))
        assertEquals(ThemeMode.AMBER_CRT, ThemeMode.fromString("AMBER_CRT"))
        assertEquals(ThemeMode.HP_SLATE, ThemeMode.fromString("HP_SLATE"))
        assertEquals(ThemeMode.CLEANROOM, ThemeMode.fromString("CLEANROOM"))
        assertEquals(ThemeMode.CYBERPUNK_NEON, ThemeMode.fromString("CYBERPUNK_NEON"))
        assertEquals(ThemeMode.TOKYO_NIGHT, ThemeMode.fromString("TOKYO_NIGHT"))
        assertEquals(ThemeMode.SOLARIZED_DARK, ThemeMode.fromString("SOLARIZED_DARK"))
        assertEquals(ThemeMode.VINTAGE_BRITISH_LAB, ThemeMode.fromString("VINTAGE_BRITISH_LAB"))
        assertEquals(ThemeMode.TITANIUM_FROST, ThemeMode.fromString("TITANIUM_FROST"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromString("SYSTEM"))
    }

    @Test
    fun `fromString handles legacy theme strings with backwards compatibility`() {
        assertEquals(ThemeMode.OBSIDIAN, ThemeMode.fromString("DARK"))
        assertEquals(ThemeMode.CLEANROOM, ThemeMode.fromString("LIGHT"))
    }

    @Test
    fun `fromString falls back to OBSIDIAN for unknown or null values`() {
        assertEquals(ThemeMode.OBSIDIAN, ThemeMode.fromString(null))
        assertEquals(ThemeMode.OBSIDIAN, ThemeMode.fromString("INVALID_THEME"))
        assertEquals(ThemeMode.OBSIDIAN, ThemeMode.fromString(""))
    }
}
