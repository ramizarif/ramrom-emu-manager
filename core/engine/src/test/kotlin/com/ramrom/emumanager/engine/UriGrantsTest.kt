package com.ramrom.emumanager.engine

import com.ramrom.emumanager.engine.inventory.UriGrants
import org.junit.Assert.assertEquals
import org.junit.Test

class UriGrantsTest {
    @Test fun parsesTreesPerUid() {
        val dump = """
            ACTIVITY MANAGER URI PERMISSIONS (dumpsys activity permissions)
              Granted Uri Permissions:
              * UID 10123 holds:
                UriPermission{1a2b u0 content://com.android.externalstorage.documents/tree/primary%3AROMs%2Fswitch}
                UriPermission{3c4d u0 content://com.android.externalstorage.documents/tree/primary%3AROMs%2Fswitch}
              * UID 10200 holds:
                UriPermission{5e6f content://com.android.externalstorage.documents/tree/primary%3AROMs}
                UriPermission{7a8b content://com.android.providers.media.documents/document/image%3A12}
                UriPermission{9c0d u0 content://com.android.externalstorage.documents/tree/1234-ABCD%3AGames%2Fps2}
        """.trimIndent()
        assertEquals(mapOf(
            10123 to listOf("/storage/emulated/0/ROMs/switch"),
            10200 to listOf("/storage/emulated/0/ROMs", "/storage/1234-ABCD/Games/ps2"),
        ), UriGrants.parse(dump))
    }

    @Test fun nothingGranted() = assertEquals(emptyMap<Int, List<String>>(), UriGrants.parse("ACTIVITY MANAGER URI PERMISSIONS\n  (nothing)"))

    @Test fun withinIgnoresCase() {
        assertEquals(true, UriGrants.within("/storage/emulated/0/ROMs", "/storage/emulated/0/Roms/"))
        assertEquals(true, UriGrants.within("/storage/emulated/0/roms/switch", "/storage/emulated/0/ROMs"))
        assertEquals(false, UriGrants.within("/storage/emulated/0/ROMsOld", "/storage/emulated/0/ROMs"))
    }
}
