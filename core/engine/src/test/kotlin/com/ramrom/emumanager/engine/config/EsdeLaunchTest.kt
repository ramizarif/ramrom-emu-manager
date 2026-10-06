package com.ramrom.emumanager.engine.config

import com.ramrom.emumanager.engine.testBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
