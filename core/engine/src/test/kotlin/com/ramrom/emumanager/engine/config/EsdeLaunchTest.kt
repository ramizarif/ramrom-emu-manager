package com.ramrom.emumanager.engine.config

import com.ramrom.emumanager.engine.testBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EsdeLaunchTest {
    // The Thor's switch gamelist, trimmed: the system default is the top-level block; the copy
    // inside <gameList> is a stray ES-DE never reads.
    private val thor = """
        <?xml version="1.0"?>
        <alternativeEmulator>
        	<label>Eden Nightly (Standalone)</label>
        </alternativeEmulator>
        <gameList>
        	<alternativeEmulator>
        		<label>Eden (Standalone)</label>
        	</alternativeEmulator>
        	<game>
        		<path>./Danganronpa - Trigger Happy Havoc Anniversary Edition [0100A3301618E000][USA][v0](eShop).nsp</path>
        		<altemulator>Citron (Standalone)</altemulator>
        	</game>
        	<game>
        		<path>./The Legend of Zelda - Breath of the Wild.nsp</path>
        		<name>The Legend of Zelda: Breath of the Wild</name>
        	</game>
        </gameList>
    """.trimIndent()
    private val eden = testBundle.emulators.first { it.id == "eden" }
    private val both = listOf("dev.eden.eden_emulator", "dev.eden.eden_emulator.nightly")

    @Test fun systemDefaultIsTheTopLevelBlock() {
        val label = EsdeLaunch.label(thor, "The Legend of Zelda - Breath of the Wild.nsp")
        assertEquals("Eden Nightly (Standalone)", label)
        assertEquals(EsdeLaunch.Pick.Package("dev.eden.eden_emulator.nightly"), EsdeLaunch.packageFor(eden, label, both))
    }

    @Test fun aGamesOwnChoiceWins() {
        val label = EsdeLaunch.label(thor, "Danganronpa - Trigger Happy Havoc Anniversary Edition [0100A3301618E000][USA][v0](eShop).nsp")
        assertEquals(EsdeLaunch.Pick.OtherEmulator("Citron"), EsdeLaunch.packageFor(eden, label, both))
    }

    @Test fun noChoiceMeansTheMainBuild() {
        assertNull(EsdeLaunch.label("<gameList></gameList>", "x.nsp"))
        assertEquals(EsdeLaunch.Pick.Package("dev.eden.eden_emulator"), EsdeLaunch.packageFor(eden, null, both))
        assertEquals(EsdeLaunch.Pick.Package("dev.eden.eden_emulator"), EsdeLaunch.packageFor(eden, "Eden (Standalone)", both))
    }

    private val esde = testBundle.frontends.first()
    private fun emu(id: String) = testBundle.emulators.first { it.id == id }

    @Test fun standaloneEmulatorsNeedAChoiceWhereEsdeDefaultsToACore() {
        assertEquals("DuckStation (Standalone)", EsdeLaunch.wanted(esde, "ps1", emu("duckstation"), null))
        assertEquals("Dolphin (Standalone)", EsdeLaunch.wanted(esde, "gc", emu("dolphin"), null))
        assertEquals("PPSSPP (Standalone)", EsdeLaunch.wanted(esde, "psp", emu("ppsspp"), null))
        // ES-DE's defaults already: Eden for Switch, AetherSX2 for PS2, any RetroArch core.
        assertNull(EsdeLaunch.wanted(esde, "switch", eden, "dev.eden.eden_emulator"))
        assertNull(EsdeLaunch.wanted(esde, "ps2", emu("nethersx2"), null))
        assertNull(EsdeLaunch.wanted(esde, "snes", emu("retroarch"), null))
        // Only the nightly installed: its own label.
        assertEquals("Eden Nightly (Standalone)", EsdeLaunch.wanted(esde, "switch", eden, "dev.eden.eden_emulator.nightly"))
    }

    @Test fun anEdenNightlyChoiceStillOpensEden() {
        assertTrue(EsdeLaunch.opens(EsdeLaunch.systemLabel(thor), eden, esde))
        assertFalse(EsdeLaunch.opens("Citron (Standalone)", eden, esde))
    }

    @Test fun writingReplacesOnlyTheTopLevelChoice() {
        val out = EsdeLaunch.withSystemLabel(thor, "Eden (Standalone)")
        assertEquals("Eden (Standalone)", EsdeLaunch.systemLabel(out))
        assertTrue(out.contains("<altemulator>Citron (Standalone)</altemulator>"))
        assertFalse(out.substringBefore("<gameList").contains("Nightly"))
        assertEquals(thor.substringAfter("<gameList>"), out.substringAfter("<gameList>"))
    }

    @Test fun aChoiceIsAddedOrAFileCreated() {
        val plain = "<?xml version=\"1.0\"?>\n<gameList>\n\t<game><path>./a.chd</path></game>\n</gameList>\n"
        val out = EsdeLaunch.withSystemLabel(plain, "DuckStation (Standalone)")
        assertEquals("DuckStation (Standalone)", EsdeLaunch.systemLabel(out))
        assertTrue(out.endsWith(plain.substringAfter("?>\n")))
        assertEquals("Azahar (Standalone)", EsdeLaunch.systemLabel(EsdeLaunch.withSystemLabel("", "Azahar (Standalone)")))
    }
}
