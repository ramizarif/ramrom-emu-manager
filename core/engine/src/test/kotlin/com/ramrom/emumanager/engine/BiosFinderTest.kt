package com.ramrom.emumanager.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BiosFinderTest {
    private val root = "/storage/emulated/0"
    private val ps1 = "924e392ed05558ffdb115408c263dccf" // scph1001.bin, 512 KiB
    private val ps2 = "8221b32c7c1aaebb04c98e1f33fffb45" // 4 MiB
    private val dc = "e10c53c2f8b90bab96ead2d368858623" // dc_boot.bin, 2 MiB

    /** Files: path → (size, md5). Folders are implied. */
    private class Tree(val files: Map<String, Pair<Long, String>>) {
        val hashed = mutableListOf<String>()
        val lister = StorageLister { dir ->
            val kids = files.keys.filter { it.startsWith("$dir/") }.map { it.removePrefix("$dir/") }
            kids.map { it.substringBefore('/') }.distinct().map { n ->
                val f = files["$dir/$n"]
                StorageEntry(n, f == null, f?.first ?: 0)
            }
        }
        val md5: (String) -> String = { p -> hashed += p; files.getValue(p).second }
    }

    private val plan = listOf("ps1" to "duckstation", "ps2" to "nethersx2", "dreamcast" to "retroarch")

    @Test fun findsTheFolderAndPrefersTheOneCoveringMost() {
        val t = Tree(mapOf(
            "$root/BIOS/scph1001.bin" to (524288L to ps1),
            "$root/BIOS/ps2.bin" to (4194304L to ps2),
            "$root/BIOS/dc/dc_boot.bin" to (2097152L to dc),
            "$root/Download/old/scph1001.bin" to (524288L to ps1),
        ))
        val found = BiosFinder(testBundle, t.lister, t.md5).find(root, plan)
        assertEquals("$root/BIOS", found.first().path)
        assertEquals(3, found.first().found)
        assertTrue("the dc subfolder adds nothing BIOS/ doesn't", found.none { it.path == "$root/BIOS/dc" })
        assertTrue(found.any { it.path == "$root/Download/old" })
    }

    @Test fun onlyFilesOfABiosSizeAreFingerprinted() {
        val t = Tree(mapOf(
            "$root/BIOS/scph1001.bin" to (524288L to ps1),
            "$root/BIOS/notes.txt" to (12L to "x"),
            "$root/ROMs/ps2/game.iso" to (4_000_000_000L to "y"),
        ))
        BiosFinder(testBundle, t.lister, t.md5).find(root, plan)
        assertEquals(listOf("$root/BIOS/scph1001.bin"), t.hashed)
    }

    @Test fun mediaAndOtherAppsAreNotSearched() {
        val t = Tree(mapOf("$root/Android/data/x/scph1001.bin" to (524288L to ps1), "$root/DCIM/scph1001.bin" to (524288L to ps1)))
        assertTrue(BiosFinder(testBundle, t.lister, t.md5).find(root, plan).isEmpty())
    }

    /** [device] A PS2 BIOS from a region the table doesn't list: offered, but never as a match. */
    @Test fun unrecognisedBiosLikeFilesRankAfterMatches() {
        val t = Tree(mapOf(
            "$root/BIOS/ps2.bin" to (4194304L to "7c6a054322b32a83e9a40afdb1e1369c"),
            "$root/Keep/scph1001.bin" to (524288L to ps1),
        ))
        val found = BiosFinder(testBundle, t.lister, t.md5).find(root, plan)
        assertEquals(listOf("$root/Keep", "$root/BIOS"), found.map { it.path })
        assertEquals(0, found[1].found); assertEquals(1, found[1].unrecognised)
    }

    @Test fun aRightSizedFileThatIsntABiosIsNotASuggestion() {
        val t = Tree(mapOf("$root/Download/song.bin" to (524288L to "00000000000000000000000000000000")))
        assertTrue(BiosFinder(testBundle, t.lister, t.md5).find(root, plan).isEmpty())
    }

    @Test fun checkReadsOneFolder() {
        val t = Tree(mapOf("$root/BIOS/scph1001.bin" to (524288L to ps1)))
        val r = BiosFinder(testBundle, t.lister, t.md5).check("$root/BIOS", plan)
        assertTrue(r.single { it.system.id == "ps1" }.status is BiosCheckStatus.Found)
        assertEquals("file://$root/BIOS/scph1001.bin", (r.single { it.system.id == "ps1" }.status as BiosCheckStatus.Found).file.uri)
    }
}
