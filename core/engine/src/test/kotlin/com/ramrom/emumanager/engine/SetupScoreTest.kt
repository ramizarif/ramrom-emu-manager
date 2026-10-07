package com.ramrom.emumanager.engine

import com.ramrom.emumanager.engine.health.HealthChecker
import com.ramrom.emumanager.engine.health.SetupScore
import com.ramrom.emumanager.engine.inventory.DeviceFacts
import com.ramrom.emumanager.engine.inventory.InstalledApp
import com.ramrom.emumanager.engine.inventory.InventoryBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupScoreTest {
    private val r = "/storage/emulated/0/ROMs"
    private val esde = "/storage/emulated/0/ES-DE/settings/es_settings.xml"
    private val plan = Plan("ayn-thor", "sd8gen2", "es-de", listOf(Plan.PlanSystem("switch", "eden"), Plan.PlanSystem("gc", "dolphin")), null)
    private val apps = listOf("org.es_de.frontend", "dev.eden.eden_emulator", "org.dolphinemu.dolphinemu")

    private fun score(installed: List<String>, vararg files: String, facts: DeviceFacts.() -> DeviceFacts = { this }): SetupScore {
        val inv = InventoryBuilder(testBundle, FakeFiles(*files)).build(DeviceFacts(
            installed = installed.associateWith { InstalledApp(it) }, allFilesAccess = installed.associateWith { true },
        ).facts())
        return SetupScore.of(plan, HealthChecker(testBundle).check(plan, inv), testBundle)
    }

    @Test fun aFinishedSetupIs100EvenWithEmptyFoldersAndNotes() {
        val s = score(apps, esde, "$r/switch/.keep", "$r/gc/.keep") { copy(esdeChoices = mapOf("switch" to null, "gc" to "Dolphin (Standalone)")) }
        assertEquals(s.missing.toString(), 100, s.percent)
    }

    @Test fun eachGapCostsItsShareAndIsNamed() {
        // GameCube still opens in RetroArch's core: the Thor today.
        val s = score(apps, esde, "$r/switch/.keep", "$r/gc/.keep") { copy(esdeChoices = mapOf("switch" to null, "gc" to null)) }
        assertEquals(s.total - 1, s.done)
        assertTrue(s.percent in 80..99)
        assertEquals("gc", s.missing.single().systemId)
    }

    @Test fun aBareDeviceStartsLow() {
        val s = score(emptyList())
        assertTrue(s.percent.toString(), s.percent < 20)
    }
}
