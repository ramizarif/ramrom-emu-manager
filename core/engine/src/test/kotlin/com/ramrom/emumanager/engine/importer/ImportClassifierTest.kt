package com.ramrom.emumanager.engine.importer

import com.ramrom.emumanager.engine.importer.ImportCandidate.Flag
import com.ramrom.emumanager.engine.testBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportClassifierTest {
    private val dl = "/storage/emulated/0/Download"
    private fun f(name: String, size: Long = 100) = SourceFile("$dl/$name", size)
    private fun scan(probe: FakeProbe, vararg files: SourceFile) = ImportClassifier(testBundle, probe).scan(files.toList())
    private fun one(probe: FakeProbe, vararg files: SourceFile) = scan(probe, *files).single()

    private val ps2Cnf = "BOOT2 = cdrom0:\\SLUS_212.92;1\r\nVER = 1.00\r\n".toByteArray()
    private val ps1Cnf = "BOOT = cdrom:\\SLUS_007.70;1\r\nTCB = 4\r\n".toByteArray()

    @Test fun extensionsOnlyOneSystemUses() {
        val c = scan(FakeProbe(), f("Zelda.nsp"), f("Pokemon.nds"), f("Mario.z64"), f("Sonic.md"), f("readme.txt"), f("cover.jpg"))
        assertEquals(listOf("switch", "nds", "n64", "genesis"), listOf("Zelda", "Pokemon", "Mario", "Sonic").map { n -> c.single { it.primary.path.contains(n) }.systemId })
        assertEquals("text and images aren't games", 4, c.size)
        assertTrue(c.all { it.confidence == Confidence.Sure })
    }

    @Test fun ps2AndPspIsosByTheirHeaders() {
        val probe = FakeProbe(files = mapOf(
            "$dl/Okami.iso" to Fixtures.iso(mapOf("SYSTEM.CNF" to ps2Cnf)),
            "$dl/Patapon.iso" to Fixtures.iso(mapOf("UMD_DATA.BIN" to ByteArray(16), "PSP_GAME" to ByteArray(0))),
        ))
        val c = scan(probe, f("Okami.iso"), f("Patapon.iso"))
        assertEquals("ps2", c.single { "Okami" in it.primary.path }.systemId)
        assertEquals("psp", c.single { "Patapon" in it.primary.path }.systemId)
        assertEquals("PlayStation 2 disc header", c.single { "Okami" in it.primary.path }.reason)
    }

    @Test fun rawPs1BinJoinsItsCueAndIsReadThroughIt() {
        val cue = "FILE \"Crash (USA).bin\" BINARY\r\n  TRACK 01 MODE2/2352\r\n    INDEX 01 00:00:00\r\n".toByteArray()
        val probe = FakeProbe(files = mapOf(
            "$dl/Crash (USA).cue" to cue,
            "$dl/Crash (USA).bin" to Fixtures.iso(mapOf("SYSTEM.CNF" to ps1Cnf), sector = 2352, header = 24),
        ))
        val c = one(probe, f("Crash (USA).cue"), f("Crash (USA).bin", 500))
        assertEquals("ps1", c.systemId)
        assertEquals(listOf("$dl/Crash (USA).cue", "$dl/Crash (USA).bin"), c.files.map { it.path })
    }

    @Test fun nintendoDiscs() {
        val probe = FakeProbe(files = mapOf(
            "$dl/a.iso" to Fixtures.gamecube(), "$dl/b.iso" to Fixtures.wii(),
            "$dl/c.rvz" to Fixtures.rvz(1), "$dl/d.rvz" to Fixtures.rvz(2), "$dl/e.bin" to Fixtures.saturn(),
        ))
        val c = scan(probe, f("a.iso"), f("b.iso"), f("c.rvz"), f("d.rvz"), f("e.bin")).associate { it.primary.path.substringAfterLast('/') to it.systemId }
        assertEquals(mapOf("a.iso" to "gc", "b.iso" to "wii", "c.rvz" to "gc", "d.rvz" to "wii", "e.bin" to "saturn"), c)
    }

    @Test fun folderNameIsAHintNotAFact() {
        val c = ImportClassifier(testBundle, FakeProbe()).scan(listOf(SourceFile("/sdcard/stuff/psx/Game.chd", 10), SourceFile("/sdcard/stuff/Other.chd", 10)))
        assertEquals("ps1", c.single { "Game" in it.primary.path }.systemId)
        assertEquals(Confidence.Likely, c.single { "Game" in it.primary.path }.confidence)
        assertNull(c.single { "Other" in it.primary.path }.systemId)
    }

    @Test fun unrecognisedIsoIsTheUsersCall() {
        val c = one(FakeProbe(files = mapOf("$dl/x.iso" to ByteArray(0x10000))), f("x.iso"))
        assertNull(c.systemId); assertEquals(Confidence.Unknown, c.confidence)
    }

    @Test fun archivesAreJudgedByWhatsInside() {
        val probe = FakeProbe(archives = mapOf(
            "$dl/Game.zip" to mapOf("Game/Game.nsp" to ByteArray(10), "Game/info.nfo" to ByteArray(1)),
            "$dl/Okami.7z" to mapOf("Okami.iso" to Fixtures.iso(mapOf("SYSTEM.CNF" to ps2Cnf))),
        ))
        val c = scan(probe, f("Game.zip"), f("Okami.7z"))
        val sw = c.single { "Game.zip" in it.primary.path }
        assertEquals("switch", sw.systemId)
        assertEquals("only game files are unpacked", listOf("Game/Game.nsp"), sw.inner.map { it.name })
        assertEquals("ps2", c.single { "Okami" in it.primary.path }.systemId)
    }

    @Test fun zipOfUnknownFilesIsAnArcadeSet() {
        val c = one(FakeProbe(archives = mapOf("$dl/sf2.zip" to mapOf("sf2.01" to ByteArray(1), "sf2.02" to ByteArray(1), "sf2_e.11" to ByteArray(1)))), f("sf2.zip"))
        assertNull(c.systemId)
        assertTrue(Flag.ArcadeSet in c.flags)
    }

    @Test fun archivesOfOtherThingsArentListed() {
        val c = scan(FakeProbe(archives = mapOf("$dl/turnip.adpkg.zip" to mapOf("meta.json" to ByteArray(1), "libvulkan_freedreno.so" to ByteArray(1)))), f("turnip.adpkg.zip"))
        assertTrue(c.isEmpty())
    }

    @Test fun splitArchivesAreOneItemAndFlagged() {
        val c = scan(FakeProbe(), f("Big.part01.rar"), f("Big.part02.rar"), f("Big.part10.rar"), f("Other.7z.001"), f("Other.7z.002"))
        assertEquals(2, c.size)
        assertEquals(3, c.single { "Big" in it.id }.files.size)
        assertEquals(2, c.single { "Other" in it.id }.files.size)
        assertTrue(c.all { Flag.SplitArchive in it.flags })
    }

    @Test fun unreadableArchiveFallsBackToItsName() {
        val c = one(FakeProbe(), f("Zelda [NSP].rar"))
        assertTrue(Flag.Unreadable in c.flags)
        assertEquals("switch", c.systemId); assertEquals(Confidence.Likely, c.confidence)
    }

    /** [device] A real 1.5 GB .7z whose header points 3.1 GB in: a download cut short. */
    @Test fun truncatedArchivesAreCalledIncomplete() {
        val sevenZip = ByteArray(64).also { "7z".toByteArray().copyInto(it); it[2] = 0xBC.toByte(); it[3] = 0xAF.toByte(); it[4] = 0x27; it[5] = 0x1C
            Fixtures.le32(it, 12, 0x7000_0000) }
        val zip = ByteArray(64).also { it[0] = 'P'.code.toByte(); it[1] = 'K'.code.toByte(); it[2] = 3; it[3] = 4 }
        val c = scan(FakeProbe(files = mapOf("$dl/cut.7z" to sevenZip, "$dl/cut.zip" to zip)), SourceFile("$dl/cut.7z", 64), SourceFile("$dl/cut.zip", 64))
        assertTrue(c.all { Flag.Incomplete in it.flags })
    }

    @Test fun encrypted3dsIsFlaggedNeverConverted() {
        val probe = FakeProbe(files = mapOf(
            "$dl/enc.cia" to Fixtures.cia(true), "$dl/dec.cia" to Fixtures.cia(false),
            "$dl/enc.3ds" to Fixtures.ncsd(true), "$dl/dec.3ds" to Fixtures.ncsd(false),
        ))
        val c = scan(probe, f("enc.cia"), f("dec.cia"), f("enc.3ds"), f("dec.3ds")).associate { it.primary.path.substringAfterLast('/') to (Flag.Encrypted in it.flags) }
        assertEquals(mapOf("enc.cia" to true, "dec.cia" to false, "enc.3ds" to true, "dec.3ds" to false), c)
    }

    @Test fun biosFilesAreSpotted() {
        val c = one(FakeProbe(), f("scph1001.bin"))
        assertTrue(Flag.Bios in c.flags)
    }
}
