package com.ramrom.emumanager.engine.importer

import com.ramrom.emumanager.engine.StorageEntry
import com.ramrom.emumanager.engine.StorageLister
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameFolderFinderTest {
    private val root = "/storage/emulated/0"

    private fun lister(vararg files: String) = StorageLister { dir ->
        files.filter { it.startsWith("$dir/") }.map { it.removePrefix("$dir/") }.map { it.substringBefore('/') }.distinct()
            .map { n -> StorageEntry(n, files.none { it == "$dir/$n" }, 1) }
    }

    private fun find(vararg files: String) =
        GameFolderFinder(lister(*files), listOf("$root/ROMs", "$root/ES-DE")).find(root)

    @Test fun foldersWithGameFilesAreOfferedMostFirst() {
        val f = find("$root/Documents/ds/a.nds", "$root/Stuff/x.iso", "$root/Stuff/y.iso", "$root/Stuff/z.zip")
        assertEquals(listOf("$root/Stuff", "$root/Documents/ds"), f.map { it.path })
        assertEquals(GameFolder("$root/Stuff", 2, 1), f.first())
    }

    @Test fun archivesAloneDontMakeAGameFolder() {
        assertTrue(find("$root/Download/photos.zip", "$root/Download/app.7z").isEmpty())
    }

    @Test fun theRomFolderAndMediaAreLeftOut() {
        // Shared storage is case-insensitive: Roms is ROMs.
        assertTrue(find("$root/Roms/ps2/a.iso", "$root/ROMs/nds/b.nds", "$root/ES-DE/x/c.nds", "$root/DCIM/d.iso", "$root/Android/data/e.nsp").isEmpty())
    }

    @Test fun aFolderOfSystemFoldersIsOfferedOnce() {
        val f = find("$root/Games/ps2/a.iso", "$root/Games/psp/b.cso", "$root/Games/psp/c.cso")
        assertEquals(listOf(GameFolder("$root/Games", 3, 0)), f)
    }
}
