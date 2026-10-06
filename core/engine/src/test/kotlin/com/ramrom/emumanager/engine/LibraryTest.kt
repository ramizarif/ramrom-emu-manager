package com.ramrom.emumanager.engine

import com.ramrom.emumanager.engine.inventory.DeviceFacts
import com.ramrom.emumanager.engine.inventory.InventoryBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private val r = "/storage/emulated/0/ROMs"
    private fun lib(plan: Plan?, vararg files: String) =
        Library.build(testBundle, InventoryBuilder(testBundle, FakeFiles(*files)).build(DeviceFacts()), plan)

    @Test fun cueAndBinAreOneGame() {
        val ps1 = lib(null, "$r/ps1/Crash (USA).cue", "$r/ps1/Crash (USA).bin", "$r/ps1/Spyro (USA).chd").single()
        assertEquals(listOf("Crash (USA)", "Spyro (USA)"), ps1.games.map { it.title })
    }

    @Test fun multiTrackBinsFoldIntoTheirCue() {
        val ps1 = lib(null, "$r/ps1/Tomb Raider.cue", "$r/ps1/Tomb Raider (Track 1).bin", "$r/ps1/Tomb Raider (Track 2).bin").single()
        assertEquals(listOf("Tomb Raider"), ps1.games.map { it.title })
    }

    /** [device] RamROM downloads arrive URL-escaped. */
    @Test fun escapedNamesAreDecoded() {
        val ps2 = lib(null, "$r/ps2/Tales%20of%20the%20Abyss%20%28USA%29.iso").single()
        assertEquals("Tales of the Abyss (USA)", ps2.games.single().title)
        assertEquals("Tales%20of%20the%20Abyss%20%28USA%29", ps2.games.single().baseName)
    }

    @Test fun playableFollowsTheChosenEmulator() {
        val plan = Plan(null, "sd8gen2", null, listOf(Plan.PlanSystem("ps2", "nethersx2")), null)
        val ps2 = lib(plan, "$r/ps2/Game.7z", "$r/ps2/Other.iso").single()
        assertFalse(ps2.games.first { it.title == "Game" }.playable)
        assertTrue(ps2.games.first { it.title == "Other" }.playable)
    }

    /** What import writes for a multi-disc game: ES-DE shows the folder as one entry. */
    @Test fun m3uFolderIsOneGame() {
        val d = "$r/ps1/FF7 (USA).m3u"
        val ps1 = lib(null, "$d/FF7 (USA).m3u", "$d/FF7 (USA) (Disc 1).cue", "$d/FF7 (USA) (Disc 1).bin", "$d/FF7 (USA) (Disc 2).cue", "$d/FF7 (USA) (Disc 2).bin").single()
        assertEquals(listOf("FF7 (USA)"), ps1.games.map { it.title })
        assertEquals(4, ps1.games.single().files.size)
    }

    @Test fun emptyFoldersAreLeftOut() = assertTrue(lib(null, "$r/nds/.keep").isEmpty())
}
