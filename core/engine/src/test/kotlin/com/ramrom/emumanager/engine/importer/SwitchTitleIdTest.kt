package com.ramrom.emumanager.engine.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SwitchTitleIdTest {
    /** A PFS0 header and string table naming [names]; no file data, which the reader never needs. */
    private fun nsp(vararg names: String): ByteReader {
        val table = names.joinToString("\u0000", postfix = "\u0000").toByteArray()
        val b = ByteBuffer.allocate(16 + names.size * 24 + table.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("PFS0".toByteArray()).putInt(names.size).putInt(table.size).putInt(0)
        repeat(names.size) { b.putLong(0).putLong(0).putInt(0).putInt(0) }
        b.put(table)
        val bytes = b.array()
        return ByteReader { off, len -> if (off + len > bytes.size) null else bytes.copyOfRange(off.toInt(), off.toInt() + len) }
    }

    @Test fun nspTicketNamesTheGame() {
        // The Thor's own BOTW dump: ticket 01007ef00011e0000000000000000000.tik.
        val r = nsp("a1b2c3d4e5f60718293a4b5c6d7e8f90.nca", "01007ef00011e0000000000000000000.tik", "01007ef00011e0000000000000000000.cert")
        assertEquals("01007EF00011E000", SwitchTitleId.read("The Legend of Zelda - Breath of the Wild.nsp", r))
    }

    @Test fun anUpdateMapsToItsGame() {
        assertEquals("0100A00019DE0000", SwitchTitleId.read("x.nsp", nsp("0100a00019de08000000000000000012.tik")))
    }

    @Test fun noTicketFallsBackToTheFileNameTag() {
        assertEquals("010040601A9B8000", SwitchTitleId.read("Tamagotchi Plaza [010040601A9B8000].xci", null))
        assertEquals("0100535012974000", SwitchTitleId.read("Hades[0100535012974000][US][v0].nsp", nsp("abc.nca")))
    }

    @Test fun nothingToGoOn() {
        assertNull(SwitchTitleId.read("Dusk Diver 2.xci", null))
        assertNull(SwitchTitleId.read("Metroid Dread.nsp", ByteReader { _, n -> ByteArray(n) }))
    }

    @Test fun updatesAndDlcAreNotGames() {
        // Names from the Thor: the first one was about to be filed as a game.
        listOf("BOTW-Update-v786432-1.6.0.nsp", "Hades [0100535012974800][v131072].nsp", "Game DLC Pack.nsp", "Game [0100535012975001].nsp")
            .forEach { assertTrue(it, SwitchTitleId.isAddOn(it)) }
        listOf("Danganronpa - Trigger Happy Havoc Anniversary Edition [0100A3301618E000][USA][v0](eShop).nsp",
            "Hades[0100535012974000][US][v0].nsp", "Super Mario Bros. Wonder.nsp", "Updated Edition Deluxe.xci")
            .forEach { assertFalse(it, SwitchTitleId.isAddOn(it)) }
    }
}
