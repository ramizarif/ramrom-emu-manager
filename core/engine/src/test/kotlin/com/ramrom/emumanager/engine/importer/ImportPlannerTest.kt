package com.ramrom.emumanager.engine.importer

import com.ramrom.emumanager.engine.FakeFiles
import com.ramrom.emumanager.engine.importer.ImportCandidate.Flag
import com.ramrom.emumanager.engine.importer.ImportProblem.Kind
import com.ramrom.emumanager.engine.testBundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPlannerTest {
    private val roms = "/storage/emulated/0/ROMs"
    private val dl = "/storage/emulated/0/Download"
    private val move = ImportOptions(move = true, deleteArchives = true)
    private fun planner(vararg existing: String) = ImportPlanner(testBundle, FakeFiles(*existing), roms)
    private fun loose(name: String, system: String?, size: Long = 100, flags: Set<Flag> = emptySet()) =
        ImportCandidate("$dl/$name", listOf(SourceFile("$dl/$name", size)), systemId = system, confidence = if (system == null) Confidence.Unknown else Confidence.Sure, flags = flags)

    @Test fun landsInTheFrontendFolderWithADecodedName() {
        val item = planner().plan(listOf(loose("Tales%20of%20the%20Abyss%20%28USA%29.iso", "ps2")), move).items.single()
        assertEquals("$roms/ps2", item.destDir)
        assertEquals(listOf("Tales of the Abyss (USA).iso"), item.outputs)
        assertEquals(ImportAction.Move, item.action)
        assertTrue(item.included)
    }

    @Test fun copyWhenNotMoving() {
        val plan = planner().plan(listOf(loose("a.nds", "nds", size = 50)), ImportOptions(move = false, deleteArchives = false))
        assertEquals(ImportAction.Copy, plan.items.single().action)
        assertEquals(50, plan.bytesNeeded)
    }

    @Test fun archivesUnpackAndCountUnpackedSize() {
        val c = ImportCandidate("$dl/G.7z", listOf(SourceFile("$dl/G.7z", 30)), inner = listOf(ArchiveEntry("G/G.nsp", 80)), isArchive = true, systemId = "switch")
        val plan = planner().plan(listOf(c), move)
        val item = plan.items.single()
        assertEquals(ImportAction.Extract, item.action)
        assertEquals(listOf("G.nsp"), item.outputs)
        assertEquals("G", item.title)
        assertEquals(80, plan.bytesNeeded)
    }

    @Test fun arcadeSetsStayZippedOnceTheUserPicks() {
        val c = ImportCandidate("$dl/sf2.zip", listOf(SourceFile("$dl/sf2.zip", 30)), isArchive = true, flags = setOf(Flag.ArcadeSet))
        val before = planner().plan(listOf(c), move).items.single()
        assertFalse(before.included)
        assertEquals(Kind.NoSystem, before.problems.single().kind)
        val after = planner().plan(listOf(c), move, systemOverrides = mapOf(c.id to "arcade")).items.single()
        assertEquals(ImportAction.KeepZipped, after.action)
        assertEquals(listOf("sf2.zip"), after.outputs)
        assertTrue(after.included)
    }

    @Test fun alreadyThereIsLeftOutAndClashesToo() {
        val plan = planner("$roms/nds/a.nds").plan(listOf(loose("a.nds", "nds"), loose("b.nds", "nds"), ImportCandidate("/sdcard/x/b.nds", listOf(SourceFile("/sdcard/x/b.nds", 1)), systemId = "nds")), move)
        val (a, b, b2) = plan.items.sortedBy { it.id }.let { l -> Triple(l.single { it.id.endsWith("Download/a.nds") }, l.single { it.id.endsWith("Download/b.nds") }, l.single { it.id.startsWith("/sdcard") }) }
        assertEquals(Kind.AlreadyThere, a.problems.single().kind); assertFalse(a.included)
        assertTrue(b.included != b2.included)
        assertTrue((if (b.included) b2 else b).problems.any { it.kind == Kind.NameClash })
    }

    @Test fun userCanIncludeAndExcludeAnything() {
        val plan = planner().plan(listOf(loose("a.nds", "nds"), loose("b.bin", null)), move, includeOverrides = mapOf("$dl/a.nds" to false))
        assertTrue(plan.included.isEmpty())
    }

    @Test fun notSetUpWarnsButStillImports() {
        val item = planner().plan(listOf(loose("a.nds", "nds")), move, setUp = setOf("ps2")).items.single()
        assertEquals(Kind.NotSetUp, item.problems.single().kind)
        assertTrue(item.included)
    }

    @Test fun biosAndEncryptedAreCalledOut() {
        val items = planner().plan(listOf(loose("scph1001.bin", "ps1", flags = setOf(Flag.Bios)), loose("g.cia", "n3ds", flags = setOf(Flag.Encrypted))), move).items
        assertFalse(items.single { it.systemId == "ps1" }.included)
        val enc = items.single { it.systemId == "n3ds" }
        assertTrue(enc.included); assertEquals(Kind.Encrypted, enc.problems.single().kind)
    }

    @Test fun multiDiscPs1GetsAnM3uFolder() {
        fun disc(n: Int) = ImportCandidate("$dl/FF7 (USA) (Disc $n).cue",
            listOf(SourceFile("$dl/FF7 (USA) (Disc $n).cue", 1), SourceFile("$dl/FF7 (USA) (Disc $n).bin", 700)), systemId = "ps1")
        val plan = planner().plan(listOf(disc(2), disc(1), disc(3)), move)
        val playlist = plan.playlists.single()
        assertEquals("$roms/ps1/FF7 (USA).m3u", playlist.dir)
        assertEquals("FF7 (USA).m3u", playlist.fileName)
        assertEquals((1..3).map { "FF7 (USA) (Disc $it).cue" }, playlist.discs)
        assertTrue(plan.items.all { it.destDir == playlist.dir })
        assertEquals("cue sheets keep their track names", listOf("FF7 (USA) (Disc 1).cue", "FF7 (USA) (Disc 1).bin"), plan.items.single { "Disc 1" in it.id }.outputs)
    }

    @Test fun noPlaylistWhenADiscIsLeftOut() {
        fun disc(n: Int) = loose("Game (Disc $n).chd", "ps1")
        val plan = planner().plan(listOf(disc(1), disc(2)), move, includeOverrides = mapOf("$dl/Game (Disc 2).chd" to false))
        assertTrue(plan.playlists.isEmpty())
    }
}
