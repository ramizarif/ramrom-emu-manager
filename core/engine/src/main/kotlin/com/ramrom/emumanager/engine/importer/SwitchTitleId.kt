package com.ramrom.emumanager.engine.importer

/**
 * A Switch game's title ID, which keys its per-game settings (Eden's `custom/<ID>.ini`). Read
 * without decrypting anything: an NSP's PFS0 file table names its ticket `<rights ID>.tik`, and the
 * rights ID starts with the title ID. XCIs carry no ticket, so they rely on a `[0100…]` tag in the
 * file name, as do NSPs without one.
 */
object SwitchTitleId {
    private val TAG = Regex("""\[(01[0-9A-Fa-f]{14})]""")
    private val TICKET = Regex("""^(01[0-9a-fA-F]{14})[0-9a-fA-F]{16}\.tik$""")

    private val ADDON_WORDS = Regex("""(?i)(^|[^a-z])(update|upd|dlc)([^a-z]|$)""")
    private val VERSION = Regex("""(?i)\[v([0-9]+)]""")

    /**
     * An update or DLC rather than a game, judged from the name: the words, a `[v…]` tag above
     * zero, or a title ID that isn't a base game's (bases end in 000). Emulators install these
     * into their own storage; in the games folder a frontend lists them as games.
     */
    fun isAddOn(fileName: String): Boolean {
        val name = fileName.substringAfterLast('/')
        if (ADDON_WORDS.containsMatchIn(name.substringBeforeLast('.'))) return true
        if (VERSION.find(name)?.groupValues?.get(1)?.toLongOrNull()?.let { it > 0 } == true) return true
        return TAG.find(name)?.groupValues?.get(1)?.let { !it.endsWith("000") } == true
    }

    fun read(fileName: String, reader: ByteReader?): String? =
        reader?.let(::fromNsp) ?: TAG.find(fileName)?.groupValues?.get(1)?.let(::base)

    private fun fromNsp(r: ByteReader): String? {
        val head = r.read(0, 16) ?: return null
        if (String(head, 0, 4, Charsets.US_ASCII) != "PFS0") return null
        val count = u32(head, 4); val tableSize = u32(head, 8)
        if (count !in 1..4096 || tableSize !in 1..0x100000) return null
        val names = r.read(16L + count * 24L, tableSize.toInt()) ?: return null
        return String(names, Charsets.US_ASCII).split('\u0000').firstNotNullOfOrNull { TICKET.find(it)?.groupValues?.get(1) }?.let(::base)
    }

    /** An update's ID (…800) names the same game as its base (…000). */
    private fun base(id: String) = id.uppercase().let { if (it.endsWith("800")) it.dropLast(3) + "000" else it }

    private fun u32(b: ByteArray, at: Int) =
        (b[at].toLong() and 0xFF) or ((b[at + 1].toLong() and 0xFF) shl 8) or ((b[at + 2].toLong() and 0xFF) shl 16) or ((b[at + 3].toLong() and 0xFF) shl 24)
}
