package com.ramrom.emumanager.knowledge

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BundleParsingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun bundleLoads() {
        val data = bundleAsString()
        val bundle = json.decodeFromString<KnowledgeBundle>(data)
        assertEquals(1, bundle.schemaVersion.toLong())
        assertTrue(bundle.devices.isNotEmpty())
        assertTrue(bundle.emulators.isNotEmpty())
        assertTrue(bundle.chipFamilies.isNotEmpty())
    }

    @Test
    fun allSystemDefReferencedEmulatorsExist() {
        val data = bundleAsString()
        val bundle = json.decodeFromString<KnowledgeBundle>(data)
        val emulatorIds = bundle.emulators.map { it.id }.toSet()

        for (system in bundle.systems) {
            for ((chipFamily, emuId) in system.recommended) {
                assertTrue(
                    "System ${system.id} references unknown emulator $emuId",
                    emuId in emulatorIds
                )
            }
            for (alt in system.alternatives) {
                assertTrue(
                    "System ${system.id} alternative $alt does not exist",
                    alt in emulatorIds
                )
            }
        }
    }

    @Test
    fun allPackagesAreUnique() {
        val data = bundleAsString()
        val bundle = json.decodeFromString<KnowledgeBundle>(data)
        val allPackages = mutableListOf<String>()

        for (emu in bundle.emulators) {
            allPackages.addAll(emu.packages)
        }

        for (fe in bundle.frontends) {
            allPackages.addAll(fe.packages)
        }

        val unique = allPackages.toSet()
        val dups = allPackages.groupingBy { it }.eachCount().filter { it.value > 1 }
        assertEquals("Duplicate packages found: $dups", unique.size.toLong(), allPackages.size.toLong())
    }

    @Test
    fun systemFoldersMapExists() {
        val data = bundleAsString()
        val bundle = json.decodeFromString<KnowledgeBundle>(data)
        val esde = bundle.frontends.find { it.id == "es-de" }
        assertNotNull(esde)
        assertTrue(esde!!.systemFolders.isNotEmpty())
        // Check mandatory folders
        assertTrue(esde.systemFolders.containsKey("switch"))
        assertTrue(esde.systemFolders.containsKey("nds"))
        assertTrue(esde.systemFolders.containsKey("ps1"))
    }

    @Test
    fun thorDeviceExists() {
        val data = bundleAsString()
        val bundle = json.decodeFromString<KnowledgeBundle>(data)
        val thor = bundle.devices.find { it.id == "ayn-thor" }
        assertNotNull(thor)
        assertEquals("verified", thor!!.verification)
        assertEquals("AYN Thor", thor.match.models.first())
        assertEquals("kalama", thor.match.devices.first())
        assertEquals("QCS8550", thor.match.socModels.first())
        assertEquals(2, thor.displays.size.toLong())
        assertTrue(thor.controls.gamepad)
    }

    @Test
    fun chipFamiliesComplete() {
        val data = bundleAsString()
        val bundle = json.decodeFromString<KnowledgeBundle>(data)
        val families = bundle.chipFamilies.map { it.id }.toSet()

        assertEquals(6, families.size.toLong())
        assertTrue("sd8gen2" in families)
        assertTrue("sd8gen3" in families)
        assertTrue("sd8elite" in families)
        assertTrue("generic" in families)

        val sd8gen2 = bundle.chipFamilies.find { it.id == "sd8gen2" }
        assertNotNull(sd8gen2)
        assertTrue("SM8550" in sd8gen2!!.socModels)
        assertTrue("QCS8550" in sd8gen2.socModels)
    }

    @Test
    fun edenProfileValid() {
        val data = bundleAsString()
        val bundle = json.decodeFromString<KnowledgeBundle>(data)
        val eden = bundle.emulators.find { it.id == "eden" }
        assertNotNull(eden)
        assertEquals("Eden", eden!!.name)
        assertTrue("dev.eden.eden_emulator" in eden.packages)
        assertTrue("dev.eden.eden_emulator.nightly" in eden.packages)
        assertTrue("switch" in eden.systems)
        assertNotNull(eden.folderPick)
        assertTrue(eden.folderPick!!.needed)
        assertEquals("qt_ini", eden.configFormat)
    }

    @Test
    fun retroarchSystemsCovered() {
        val data = bundleAsString()
        val bundle = json.decodeFromString<KnowledgeBundle>(data)
        val retroarch = bundle.emulators.find { it.id == "retroarch" }
        assertNotNull(retroarch)
        // Check mandatory systems
        assertTrue("ps1" in retroarch!!.systems)
        assertTrue("n64" in retroarch.systems)
        assertTrue("snes" in retroarch.systems)
    }

    @Test
    fun superStationThemeExists() {
        val data = bundleAsString()
        val bundle = json.decodeFromString<KnowledgeBundle>(data)
        val theme = bundle.themes.find { it.themeId == "superstation-one-menu-es-de" }
        assertNotNull(theme)
        assertTrue(theme!!.customization.supported)
        assertEquals("colors.xml", theme.customization.file)
        assertTrue(theme.customization.survivesUpdates)
    }

    private fun bundleAsString(): String {
        val path = "src/main/assets/knowledge/bundle-v1.json"
        val file = File(path)
        return if (file.exists()) {
            file.readText()
        } else {
            throw IllegalStateException("Bundle file not found at $path (run from repo root)")
        }
    }
}
