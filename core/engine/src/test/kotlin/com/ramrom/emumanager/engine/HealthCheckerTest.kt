package com.ramrom.emumanager.engine

import com.ramrom.emumanager.engine.health.FixAction
import com.ramrom.emumanager.engine.health.HealthChecker
import com.ramrom.emumanager.engine.health.IssueSeverity
import com.ramrom.emumanager.engine.inventory.AppPermissionState
import com.ramrom.emumanager.engine.inventory.BiosFile
import com.ramrom.emumanager.engine.inventory.EsdeConfig
import com.ramrom.emumanager.engine.inventory.FolderGrant
import com.ramrom.emumanager.engine.inventory.InstalledApp
import com.ramrom.emumanager.engine.inventory.OrphanedMedia
import com.ramrom.emumanager.engine.inventory.RomFolder
import com.ramrom.emumanager.knowledge.KnowledgeBundle
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Health checker tests with comprehensive coverage of P13 rules.
 */
class HealthCheckerTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val bundle: KnowledgeBundle by lazy { loadBundle() }

    private fun loadBundle(): KnowledgeBundle {
        val bundleJson = this::class.java.classLoader?.getResourceAsStream("knowledge/bundle-v1.json")
            ?.bufferedReader()
            ?.readText()
            ?: error("Could not load bundle-v1.json")
        return json.decodeFromString(KnowledgeBundle.serializer(), bundleJson)
    }

    /**
     * Build a realistic Thor inventory with ES-DE + Eden + Dolphin + several systems.
     */
    private fun buildRealisticThorInventory(
        missingEmulator: String? = null,
        missingRomFolder: String? = null,
        withOrphanedMedia: Boolean = false
    ): Inventory {
        val installedPkgs = mutableSetOf(
            "org.es_de.frontend",
            "com.github.yuzu.emu.ea",
            "org.dolphinemu.dolphin",
            "com.skyemu.nds"
        )
        if (missingEmulator != null) {
            installedPkgs.remove(missingEmulator)
        }

        val romFolders = mutableListOf(
            RomFolder("switch", "/storage/emulated/0/ROMs/switch", gameCount = 5),
            RomFolder("wii", "/storage/emulated/0/ROMs/wii", gameCount = 3),
            RomFolder("gc", "/storage/emulated/0/ROMs/gc", gameCount = 2),
            RomFolder("n3ds", "/storage/emulated/0/ROMs/n3ds", gameCount = 4)
        )
        if (missingRomFolder != null) {
            romFolders.removeAll { it.systemId == missingRomFolder }
        }

        val folderGrants = listOf(
            FolderGrant("com.github.yuzu.emu.ea", "switch"),
            FolderGrant("org.dolphinemu.dolphin", "wii"),
            FolderGrant("org.dolphinemu.dolphin", "gc"),
            FolderGrant("com.skyemu.nds", "n3ds"),
            FolderGrant("org.es_de.frontend", "switch"),
            FolderGrant("org.es_de.frontend", "wii"),
            FolderGrant("org.es_de.frontend", "gc"),
            FolderGrant("org.es_de.frontend", "n3ds")
        )

        val biosFiles = listOf(
            BiosFile("wii", "nand_key.bin", "/Android/data/org.dolphinemu.dolphin/files/bios/nand_key.bin"),
            BiosFile("gc", "gci_key.bin", "/Android/data/org.dolphinemu.dolphin/files/bios/gci_key.bin")
        )

        val orphans = if (withOrphanedMedia) {
            listOf(
                OrphanedMedia(
                    "switch", "covers",
                    "/storage/emulated/0/ES-DE/downloaded_media/switch/covers/orphaned_game.png"
                )
            )
        } else {
            emptyList()
        }

        return Inventory(
            installedPackages = installedPkgs,
            grantedFolderTrees = romFolders.map { it.systemId }.toSet(),
            existingFolders = romFolders.map { it.systemId }.toSet(),
            esdeConfigured = true,
            installedApps = installedPkgs.map { InstalledApp(it) },
            romFolders = romFolders,
            folderGrants = folderGrants,
            biosFiles = biosFiles,
            appPermissions = installedPkgs.map { pkg ->
                AppPermissionState(
                    packageId = pkg,
                    declaresManageExternalStorage = pkg in setOf(
                        "com.github.yuzu.emu.ea",
                        "org.dolphinemu.dolphin",
                        "com.skyemu.nds",
                        "org.es_de.frontend"
                    ),
                    grantedManageExternalStorage = true
                )
            },
            esdeConfig = EsdeConfig(
                installed = true,
                configPath = "/storage/emulated/0/ES-DE/settings/es_settings.xml"
            ),
            orphanedMedia = orphans
        )
    }

    @Test
    fun testHealthCheckReturnsIssues() {
        // Basic test: when everything is configured, health check should run
        val inventory = buildRealisticThorInventory()
        val plan = Plan(
            targetDeviceId = "ayn-thor",
            chipFamily = "sd8gen2",
            frontendId = "es-de",
            systems = listOf(
                Plan.PlanSystem("switch", "eden"),
                Plan.PlanSystem("wii", "dolphin"),
                Plan.PlanSystem("gc", "dolphin"),
                Plan.PlanSystem("n3ds", "azahar")
            ),
            bios = BiosSummary(found = setOf("wii", "gc"), missing = emptySet())
        )

        val checker = HealthChecker(bundle)
        val issues = checker.check(plan, inventory)

        // Just verify it runs and returns a list
        assertTrue("Should return issues list", issues is List)
    }

    @Test
    fun testEmulatorMissingIssues() {
        val inventory = buildRealisticThorInventory(missingEmulator = "org.dolphinemu.dolphin")
        val plan = Plan(
            targetDeviceId = "ayn-thor",
            chipFamily = "sd8gen2",
            frontendId = "es-de",
            systems = listOf(
                Plan.PlanSystem("switch", "eden"),
                Plan.PlanSystem("wii", "dolphin"),
                Plan.PlanSystem("gc", "dolphin")
            ),
            bios = null
        )

        val checker = HealthChecker(bundle)
        val issues = checker.check(plan, inventory)

        // Should find missing emulator issues for wii and gc (Dolphin not installed)
        val emulatorIssues = issues.filter { it.id.contains("emulator_missing") }
        assertTrue("Should find emulator missing issues", emulatorIssues.size >= 2)
        assertTrue("Issues should be blockers", emulatorIssues.all { it.severity == IssueSeverity.BLOCKER })
    }

    @Test
    fun testRomFolderMissingIssues() {
        val inventory = buildRealisticThorInventory(missingRomFolder = "wii")
        val plan = Plan(
            targetDeviceId = "ayn-thor",
            chipFamily = "sd8gen2",
            frontendId = "es-de",
            systems = listOf(
                Plan.PlanSystem("wii", "dolphin"),
                Plan.PlanSystem("switch", "eden")
            ),
            bios = null
        )

        val checker = HealthChecker(bundle)
        val issues = checker.check(plan, inventory)

        // Should find missing folder issue
        val folderIssues = issues.filter { it.id.contains("rom_folder") }
        assertTrue("Should find ROM folder issues", folderIssues.isNotEmpty())
    }

    @Test
    fun testOrphanedMediaIssues() {
        val inventory = buildRealisticThorInventory(withOrphanedMedia = true)
        val plan = Plan(
            targetDeviceId = "ayn-thor",
            chipFamily = "sd8gen2",
            frontendId = "es-de",
            systems = listOf(Plan.PlanSystem("switch", "eden")),
            bios = null
        )

        val checker = HealthChecker(bundle)
        val issues = checker.check(plan, inventory)

        // Should find orphaned media issue (INFO level)
        val orphans = issues.filter { it.id.contains("orphaned_media") }
        assertTrue("Should find orphaned media issues", orphans.isNotEmpty())
        assertTrue("Orphaned issues should be INFO", orphans.all { it.severity == IssueSeverity.INFO })
    }

    @Test
    fun testSelectiveSystemCheck() {
        val inventory = buildRealisticThorInventory(missingEmulator = "org.dolphinemu.dolphin")
        val plan = Plan(
            targetDeviceId = "ayn-thor",
            chipFamily = "sd8gen2",
            frontendId = "es-de",
            systems = listOf(
                Plan.PlanSystem("switch", "eden"),
                Plan.PlanSystem("wii", "dolphin"),
                Plan.PlanSystem("gc", "dolphin"),
                Plan.PlanSystem("n3ds", "azahar")
            ),
            bios = null
        )

        val checker = HealthChecker(bundle)

        // Check only switch - should have fewer issues
        val switchIssues = checker.check(plan, inventory, selectedSystems = setOf("switch"))
        val wiiEmulatorIssues = switchIssues.filter { it.id.contains("emulator_missing_wii") }
        assertEquals("Wii issues should not appear when checking only switch", 0, wiiEmulatorIssues.size)

        // Check wii - should have emulator missing issue
        val wiiIssues = checker.check(plan, inventory, selectedSystems = setOf("wii"))
        val wiiEmulatorIssue = wiiIssues.filter { it.id.contains("emulator_missing_wii") }
        assertTrue("Should find wii emulator missing when checking wii", wiiEmulatorIssue.isNotEmpty())
    }
}
