package com.ramrom.emumanager.engine.config

import com.ramrom.emumanager.engine.testBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsProfilesTest {
    private val eden = testBundle.emulators.first { it.id == "eden" }

    @Test fun thorGetsItsDualScreenLayoutOtherGen2DevicesDont() {
        val thor = SettingsProfiles.defaults(testBundle, "azahar", "ayn-thor", "sd8gen2")
        assertEquals(listOf("Thor dual screens", "Thor defaults"), thor.map { it.name })
        val odin = SettingsProfiles.defaults(testBundle, "azahar", "ayn-odin-2", "sd8gen2")
        assertEquals(listOf("Thor defaults"), odin.map { it.name })
    }

    @Test fun edenPerGameWritesTheThreeLineOverride() {
        val botw = SettingsProfiles.forGame(testBundle, "eden", "01007ef00011e000", "ayn-thor", "sd8gen2").single()
        val w = SettingsProfiles.write(eden, botw)!!
        assertEquals("{appData}/files/config/custom/01007EF00011E000.ini", w.filePath)
        val c = w.changes.changes
        assertEquals("false", c["Renderer/resolution_setup\\use_global"])
        assertEquals("false", c["Renderer/resolution_setup\\default"])
        assertEquals("3", c["Renderer/resolution_setup"])
        // Written into an empty per-game file, Eden's layout comes out.
        val text = QtIniCodec().render(QtIniCodec().parse("").also { p -> c.forEach { (k, v) -> p.values[k] = v!! } })
        assertTrue(text, text.contains("[Renderer]\nresolution_setup\\use_global=false\nresolution_setup\\default=false\nresolution_setup=3\n"))
    }

    @Test fun edenDefaultsGoToTheMainConfigWithoutUseGlobal() {
        val w = SettingsProfiles.write(eden, SettingsProfiles.defaults(testBundle, "eden", "ayn-thor", "sd8gen2").single())!!
        assertEquals("{appData}/files/config/config.ini", w.filePath)
        assertTrue(w.changes.keys.none { it.endsWith("\\use_global") })
        assertEquals("false", w.changes.changes["Overlay/show_input_overlay\\default"])
    }

    @Test fun plainIniGetsOnlyTheValues() {
        val azahar = testBundle.emulators.first { it.id == "azahar" }
        val w = SettingsProfiles.write(azahar, SettingsProfiles.defaults(testBundle, "azahar", "ayn-thor", "sd8gen2").first())!!
        assertEquals(mapOf("Layout/layout_option" to "1", "Layout/secondary_display_layout" to "2"), w.changes.changes)
    }

    @Test fun everyProfileHasLabelsAndAWritableTarget() {
        for (p in testBundle.settingsProfiles) {
            val emu = testBundle.emulators.first { it.id == p.emulatorId }
            assertTrue(p.name, SettingsProfiles.write(emu, p) != null)
            assertTrue(p.name, p.settings.all { it.label.isNotBlank() && !it.label.contains('—') })
        }
    }
}
