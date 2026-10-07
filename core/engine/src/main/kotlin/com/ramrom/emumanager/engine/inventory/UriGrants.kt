package com.ramrom.emumanager.engine.inventory

import java.net.URLDecoder

/**
 * Reads `dumpsys activity permissions` (elevated only): which app holds which folder tree.
 * Lines look like `  * UID 10123 holds:` followed by
 * `    UriPermission{1a2b u0 content://com.android.externalstorage.documents/tree/primary%3AROMs%2Fswitch}`.
 */
object UriGrants {
    private val uidLine = Regex("""\*\s*UID\s+(\d+)\s+holds:""")
    private val treeUri = Regex("""content://com\.android\.externalstorage\.documents/tree/([^\s}/]+)""")

    /**
     * [inner] is [outer] or inside it. Shared storage ignores case (Cocoon's grant on "Roms" on the
     * Thor is the ROMs folder, same inode), so paths compare the same way.
     */
    fun within(inner: String, outer: String): Boolean {
        val i = inner.trimEnd('/'); val o = outer.trimEnd('/')
        return i.equals(o, ignoreCase = true) || i.startsWith("$o/", ignoreCase = true)
    }

    /** uid → absolute folder paths it holds a tree grant for. */
    fun parse(dump: String, storageRoot: String = "/storage/emulated/0"): Map<Int, List<String>> {
        val out = mutableMapOf<Int, MutableList<String>>()
        var uid: Int? = null
        for (line in dump.lineSequence()) {
            uidLine.find(line)?.let { uid = it.groupValues[1].toInt(); return@let }
            val u = uid ?: continue
            val doc = treeUri.find(line)?.groupValues?.get(1) ?: continue
            pathOf(URLDecoder.decode(doc, "UTF-8"), storageRoot)?.let { p -> out.getOrPut(u) { mutableListOf() }.let { if (p !in it) it += p } }
        }
        return out
    }

    /** "primary:ROMs/switch" → "/storage/emulated/0/ROMs/switch"; SD cards are "XXXX-XXXX:path". */
    private fun pathOf(docId: String, storageRoot: String): String? {
        val volume = docId.substringBefore(':', "")
        val rel = docId.substringAfter(':', "").trim('/')
        return when {
            volume == "primary" -> if (rel.isEmpty()) storageRoot else "$storageRoot/$rel"
            volume.isNotEmpty() -> if (rel.isEmpty()) "/storage/$volume" else "/storage/$volume/$rel"
            else -> null
        }
    }
}
