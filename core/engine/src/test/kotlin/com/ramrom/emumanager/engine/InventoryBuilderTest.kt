package com.ramrom.emumanager.engine

import com.ramrom.emumanager.engine.inventory.DeviceFacts
import com.ramrom.emumanager.engine.inventory.InstalledApp
import com.ramrom.emumanager.engine.inventory.InventoryBuilder
import com.ramrom.emumanager.engine.inventory.StrayFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryBuilderTest {
    private val r = "/storage/emulated/0/ROMs"
    private val installed = listOf("dev.eden.eden_emulator", "me.magnum.melonds", "com.github.stenzek.duckstation").associateWith { InstalledApp(it) }

    @Test fun countsGamesAndSkipsCompanions() {
        val inv = InventoryBuilder(testBundle, FakeFiles("$r/nds/Mario.nds", "$r/nds/Mario.sav", "$r/nds/Mario.ml1", "$r/nds/.hidden.nds", "$r/switch/Zelda.nsp"))
            .build(DeviceFacts())
        assertEquals(1, inv.romFolders.first { it.systemId == "nds" }.gameCount)
        assertEquals(1, inv.romFolders.first { it.systemId == "switch" }.gameCount)
        assertEquals(setOf("nds", "switch"), inv.existingFolders)
    }

    /** [device] a `.cia` sat in `nds/` on Ramiz's Thor. */
    @Test fun findsFilesInTheWrongFolder() {
        val inv = InventoryBuilder(testBundle, FakeFiles("$r/nds/Style Savvy.cia", "$r/nds/Mario.nds")).build(DeviceFacts())
        assertEquals(listOf(StrayFile("Style Savvy.cia", "n3ds")), inv.romFolders.first { it.systemId == "nds" }.strays)
    }

    @Test fun grantsMapToSystemsAndRootCoversAll() {
        val inv = InventoryBuilder(testBundle, FakeFiles()).build(DeviceFacts(folderGrants = mapOf(
            "dev.eden.eden_emulator" to listOf("$r/switch"),
            "me.magnum.melonds" to listOf("$r"),
        )))
        assertTrue(inv.grantsKnown)
        assertTrue(inv.folderGrants.any { it.packageId == "dev.eden.eden_emulator" && it.systemId == "switch" })
        assertTrue(inv.folderGrants.any { it.packageId == "me.magnum.melonds" && it.systemId == "" })
        assertTrue("n3ds" in inv.grantedFolderTrees)
    }

    @Test fun unknownGrantsSaySo() = assertFalse(InventoryBuilder(testBundle, FakeFiles()).build(DeviceFacts()).grantsKnown)

    @Test fun biosInAppDataIsUncheckedWithoutElevatedAccess() {
        val ps1 = testBundle.bios.first { it.system == "ps1" && it.required }.targets.first { it.emulatorId == "duckstation" }
        val path = ps1.path.replace("{appData}", "/storage/emulated/0/Android/data/com.github.stenzek.duckstation").trimEnd('/') + "/scph1001.bin"
        val limited = InventoryBuilder(testBundle, FakeFiles(path)).build(DeviceFacts(installed = installed))
        assertTrue("ps1" in limited.biosUnchecked)
        val elevated = InventoryBuilder(testBundle, FakeFiles(path)).build(DeviceFacts(installed = installed, canReadAppData = true))
        assertTrue(elevated.biosFiles.any { it.systemId == "ps1" && it.emulatorId == "duckstation" })
        assertFalse("ps1" in elevated.biosUnchecked)
    }

    @Test fun reportsMissingPermissionsAndAllFiles() {
        val inv = InventoryBuilder(testBundle, FakeFiles()).build(DeviceFacts(installed = installed, allFilesAccess = mapOf("dev.eden.eden_emulator" to true)))
        val eden = inv.appPermissions.first { it.packageId == "dev.eden.eden_emulator" }
        assertTrue(eden.grantedManageExternalStorage)
        assertEquals(listOf("android.permission.POST_NOTIFICATIONS"), eden.missingPermissions)
    }

    @Test fun esdeSettingsAndOrphans() {
        val e = "/storage/emulated/0/ES-DE"
        val inv = InventoryBuilder(testBundle, FakeFiles(
            "$e/settings/es_settings.xml", "$r/nds/Mario.nds",
            "$e/downloaded_media/nds/covers/Mario.png", "$e/downloaded_media/nds/covers/Gone.png",
        )).build(DeviceFacts())
        assertTrue(inv.esdeConfig.installed)
        assertEquals(listOf("$e/downloaded_media/nds/covers/Gone.png"), inv.orphanedMedia.map { it.filePath })
    }

    @Test fun findsBiosTheEmulatorRenamed() {
        val data = "/storage/emulated/0/Android/data"
        val elevated = DeviceFacts(installed = installed + listOf("xyz.aethersx2.tturnip", "org.dolphinemu.dolphinemu").associateWith { InstalledApp(it) }, canReadAppData = true)
        // DuckStation names an import after its MD5; NetherSX2 after a short code, and any PS2 dump will do.
        val duck = "$data/com.github.stenzek.duckstation/files/bios/924e392ed05558ffdb115408c263dccf.bin"
        val ps2 = "$data/xyz.aethersx2.tturnip/files/bios/7EBD68DE.bin"
        val found = InventoryBuilder(testBundle, FakeFiles(duck, ps2, "$data/xyz.aethersx2.tturnip/files/bios/7EBD68DE.nvm", sizes = mapOf(ps2 to 4194304L))).build(elevated).biosFiles
        assertTrue(found.any { it.systemId == "ps1" && it.filePath == duck })
        assertTrue(found.any { it.systemId == "ps2" && it.filePath == ps2 })
        // Matched by content when the name says nothing.
        val ipl = "$data/org.dolphinemu.dolphinemu/files/GC/USA/gc-ntsc.bin"
        val gc = testBundle.bios.first { it.system == "gc" && "/USA/" in it.targets.first().path }
        assertTrue(InventoryBuilder(testBundle, FakeFiles(ipl, hashes = mapOf(ipl to gc.md5))).build(elevated).biosFiles.any { it.systemId == "gc" })
        // A wrong-size file is not a PS2 BIOS.
        assertFalse(InventoryBuilder(testBundle, FakeFiles(ps2, sizes = mapOf(ps2 to 512L))).build(elevated).biosFiles.any { it.systemId == "ps2" })
    }
}
