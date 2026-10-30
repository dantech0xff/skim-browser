package com.dantech.skimbrowser.domain.shortcut

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortcutPresetTest {

    @Test
    fun onlyANamedWebAddressIsKept() {
        val kept = listOf(
            ShortcutPreset("  Wikipedia  ", "  https://www.wikipedia.org  "),
            ShortcutPreset(" ", "https://blank.example"),
            ShortcutPreset("Script", "javascript:alert(1)"),
            ShortcutPreset("File", "file:///tmp/a"),
            ShortcutPreset("No host", "https://"),
            ShortcutPreset("Search", "heat pumps"),
        ).webPresets()

        assertEquals(listOf(ShortcutPreset("Wikipedia", "https://www.wikipedia.org")), kept)
    }
}
