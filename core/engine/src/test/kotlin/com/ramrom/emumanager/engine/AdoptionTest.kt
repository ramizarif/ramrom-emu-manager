package com.ramrom.emumanager.engine

import com.ramrom.emumanager.engine.inventory.DeviceFacts
import com.ramrom.emumanager.engine.inventory.InstalledApp
import com.ramrom.emumanager.engine.inventory.InventoryBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdoptionTest {
    private val r = "/storage/emulated/0/ROMs"
    private fun inv(installed: List<String>, vararg files: String) =
        InventoryBuilder(testBundle, FakeFiles(*files)).build(DeviceFacts(installed = installed.associateWith { InstalledApp(it) }))

    @Test fun dedicatedAppsAndFoldersWithGamesBecomeThePlan() {
        val plan = Adoption.infer(testBundle, inv(listOf("dev.eden.eden_emulator", "com.retroarch", "org.es_de.frontend"), "$r/snes/Mario.zip"), "sd8gen2")
        val bySystem = plan.systems.associate { it.systemId to it.emulatorId }
        assertEquals("eden", bySystem["switch"])
        // RetroArch alone doesn't pull in all 17 of its systems...
        assertFalse("n64" in bySystem)
        // ...but a folder with games does, using what's installed.
        assertTrue("snes" in bySystem)
        assertEquals("es-de", plan.frontendId)
    }

    @Test fun prefersTheRecommendedEmulatorWhenBothAreInstalled() {
        val plan = Adoption.infer(testBundle, inv(listOf("com.retroarch", "org.dolphinemu.dolphinemu"), "$r/gc/Game.iso"), "sd8gen2")
        assertEquals("dolphin", plan.systems.first { it.systemId == "gc" }.emulatorId)
    }

    @Test fun gamesWithoutAnEmulatorPlanTheRecommendedOne() {
        val plan = Adoption.infer(testBundle, inv(emptyList(), "$r/nds/Mario.nds"), "sd8gen2")
        assertEquals("melonds", plan.systems.single().emulatorId)
        assertNull(plan.frontendId)
    }

    @Test fun emptyDeviceIsAnEmptyPlan() = assertTrue(Adoption.infer(testBundle, inv(emptyList()), "sd8gen2").systems.isEmpty())
}
