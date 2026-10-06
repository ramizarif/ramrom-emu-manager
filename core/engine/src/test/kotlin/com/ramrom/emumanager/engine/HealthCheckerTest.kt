package com.ramrom.emumanager.engine

import com.ramrom.emumanager.engine.health.FixAction
import com.ramrom.emumanager.engine.health.HealthChecker
import com.ramrom.emumanager.engine.health.IssueSeverity
import com.ramrom.emumanager.engine.inventory.DeviceFacts
import com.ramrom.emumanager.engine.inventory.InstalledApp
import com.ramrom.emumanager.engine.inventory.InventoryBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCheckerTest {
    private val r = "/storage/emulated/0/ROMs"
    private val esdeSettings = "/storage/emulated/0/ES-DE/settings/es_settings.xml"
    private val plan = Plan("ayn-thor", "sd8gen2", "es-de", listOf(
        Plan.PlanSystem("switch", "eden"), Plan.PlanSystem("gc", "dolphin"), Plan.PlanSystem("wii", "dolphin"), Plan.PlanSystem("ps1", "duckstation"),
    ), null)
    private val all = listOf("org.es_de.frontend", "dev.eden.eden_emulator", "org.dolphinemu.dolphinemu", "com.github.stenzek.duckstation")

    private fun inv(vararg files: String, installed: List<String> = all, facts: DeviceFacts.() -> DeviceFacts = { this }) = inv(installed, files = arrayOf(*files), facts = facts)

    private fun inv(installed: List<String>, files: Array<String>, facts: DeviceFacts.() -> DeviceFacts = { this }) =
        InventoryBuilder(testBundle, FakeFiles(*files)).build(DeviceFacts(
            installed = installed.associateWith { InstalledApp(it) },
            allFilesAccess = installed.associateWith { true },
            grantedPermissions = installed.associateWith { setOf("android.permission.POST_NOTIFICATIONS") },
        ).facts())

    private fun check(inv: com.ramrom.emumanager.engine.Inventory) = HealthChecker(testBundle).check(plan, inv)

    @Test fun missingEmulatorIsOneIssueForAllItsSystems() {
        val issues = check(inv(esdeSettings, installed = all - "org.dolphinemu.dolphinemu"))
        val dolphin = issues.filter { it.fix == FixAction.InstallApp("dolphin") }
        assertEquals(1, dolphin.size)
        assertTrue(dolphin[0].detail.contains("GameCube") && dolphin[0].detail.contains("Wii"))
        assertNull(dolphin[0].systemId)
    }

    @Test fun esdeMissingThenNotOpened() {
        assertTrue(check(inv(installed = all - "org.es_de.frontend")).any { it.id == "frontend_missing" && it.severity == IssueSeverity.BLOCKER })
        assertTrue(check(inv()).any { it.id == "frontend_first_run" })
        assertFalse(check(inv(esdeSettings)).any { it.id.startsWith("frontend_") })
    }

    @Test fun foldersUseTheFrontendsNamesAndEmptyIsOnlyATip() {
        val issues = check(inv(esdeSettings, "$r/switch/Zelda.nsp", "$r/gc/.keep"))
        val wii = issues.first { it.id == "folder_missing_wii" }
        assertEquals(FixAction.CreateFolder("$r/wii"), wii.fix)
        assertEquals(IssueSeverity.INFO, issues.first { it.id == "folder_empty_gc" }.severity)
        assertFalse(issues.any { it.id == "folder_missing_switch" || it.id == "folder_empty_switch" })
    }

    @Test fun strayFilesAreFlagged() {
        val issues = check(inv(esdeSettings, "$r/switch/Zelda.nsp", "$r/switch/Pokemon.cia"))
        val stray = issues.first { it.id == "stray_switch_n3ds" }
        assertEquals(IssueSeverity.WARNING, stray.severity)
        assertTrue(stray.detail.contains("ROMs/n3ds"))
    }

    @Test fun biosRequiredMissingUncheckedOrPresent() {
        // Limited: DuckStation's BIOS folder is private, so we say we can't check, not that it's missing.
        assertTrue(check(inv(esdeSettings)).any { it.id == "bios_unchecked_ps1" && it.severity == IssueSeverity.INFO })
        val elevated = check(inv(esdeSettings) { copy(canReadAppData = true) })
        assertTrue(elevated.any { it.id == "bios_missing_ps1" && it.severity == IssueSeverity.BLOCKER })
        // GameCube's IPL is optional: never an issue.
        assertFalse(elevated.any { it.id.startsWith("bios_") && it.systemId == "gc" })
    }

    @Test fun grantRulesStayQuietUntilGrantsAreKnown() {
        assertFalse(check(inv(esdeSettings)).any { it.id.startsWith("folder_pick_") })
        val known = check(inv(esdeSettings) { copy(folderGrants = mapOf("dev.eden.eden_emulator" to listOf("$r/switch"), "org.dolphinemu.dolphinemu" to listOf(r))) })
        assertFalse(known.any { it.id == "folder_pick_eden" || it.id == "folder_pick_dolphin" })
        assertTrue(known.any { it.id == "folder_pick_duckstation" && it.severity == IssueSeverity.BLOCKER })
    }

    @Test fun aGrantOnAnyInstalledBuildCounts() {
        // The Thor: Stable and Nightly installed, only Nightly (which ES-DE launches) holds the grant.
        val both = all + "dev.eden.eden_emulator.nightly"
        val issues = check(inv(esdeSettings, installed = both) { copy(folderGrants = mapOf("dev.eden.eden_emulator.nightly" to listOf("$r/switch"))) })
        assertFalse(issues.any { it.id == "folder_pick_eden" })
    }

    @Test fun allFilesAndPermissions() {
        val issues = check(inv(esdeSettings) { copy(allFilesAccess = emptyMap(), grantedPermissions = emptyMap()) })
        assertTrue(issues.any { it.id == "all_files_eden" && it.severity == IssueSeverity.BLOCKER })
        assertTrue(issues.any { it.fix == FixAction.GrantPermission("dev.eden.eden_emulator", "android.permission.POST_NOTIFICATIONS") })
        // Dolphin doesn't use all-files access, so it's never asked for.
        assertFalse(issues.any { it.id == "all_files_dolphin" })
    }

    @Test fun blockersComeFirst() {
        val s = check(inv(installed = all - "dev.eden.eden_emulator")).map { it.severity.ordinal }
        assertEquals(s.sorted(), s)
    }

    /** [device] a `.7z` PS2 game sat in `ROMs/ps2`: NetherSX2 can't open it, and it isn't "no games". */
    @Test fun archivesAreCalledOutNotCountedAsEmpty() {
        val issues = check(inv(esdeSettings, "$r/gc/Game.7z"))
        val u = issues.first { it.id == "folder_unplayable_gc" }
        assertEquals(IssueSeverity.WARNING, u.severity)
        assertTrue(u.detail.contains("unpacking"))
        assertFalse(issues.any { it.id == "folder_empty_gc" })
    }

    /** Adopted setups often have no frontend; their folders still matter. */
    @Test fun foldersAreCheckedWithoutAFrontend() {
        val noFrontend = plan.copy(frontendId = null)
        val issues = HealthChecker(testBundle).check(noFrontend, inv(installed = all - "org.es_de.frontend", files = arrayOf("$r/gc/Game.7z")))
        assertTrue(issues.any { it.id == "folder_unplayable_gc" })
        assertTrue(issues.first { it.id == "folder_missing_wii" }.detail.startsWith("Keep your"))
        assertFalse(issues.any { it.id.startsWith("frontend_") })
    }
}
