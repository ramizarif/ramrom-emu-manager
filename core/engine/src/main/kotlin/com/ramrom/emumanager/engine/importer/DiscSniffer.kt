package com.ramrom.emumanager.engine.importer

/** Reads [length] bytes at [offset]; shorter or null past the end or when unreadable. */
fun interface ByteReader {
    fun read(offset: Long, length: Int): ByteArray?
}

/**
 * Tells disc images apart by their headers, for extensions several systems share (`.iso`, `.bin`,
 * `.rvz`). Reads a few kilobytes, never the whole image.
 */
object DiscSniffer {
    private const val GC_MAGIC = 0xC2339F3DL
    private const val WII_MAGIC = 0x5D1C9EA3L

    /** The system an image belongs to, or null when the header says nothing we recognise. */
    fun sniff(r: ByteReader): String? =
        nintendo(r, 0) ?: rvz(r) ?: ciso(r) ?: saturn(r) ?: iso9660(r)

    private fun nintendo(r: ByteReader, base: Long): String? {
        val h = r.read(base, 0x20) ?: return null
        if (h.size < 0x20) return null
        return when {
            u32be(h, 0x18) == WII_MAGIC -> "wii"
            u32be(h, 0x1C) == GC_MAGIC -> "gc"
            else -> null
        }
    }

    /** RVZ/WIA: a 0x48-byte file head, then the disc struct whose first word is 1 (GameCube) or 2 (Wii). */
    private fun rvz(r: ByteReader): String? {
        val h = r.read(0, 0x58 + 0x20) ?: return null
        if (h.size < 0x4C) return null
        val magic = String(h, 0, 3, Charsets.US_ASCII)
        if (magic != "RVZ" && magic != "WIA") return null
        return when (u32be(h, 0x48)) {
            1L -> "gc"
            2L -> "wii"
            else -> if (h.size >= 0x78) nintendo({ o, l -> h.copyOfRange((0x58 + o).toInt(), minOf(h.size, (0x58 + o + l).toInt())) }, 0) else null
        }
    }

    /** Dolphin's CISO: a 0x8000 block map, then the disc. */
    private fun ciso(r: ByteReader): String? {
        val h = r.read(0, 4) ?: return null
        if (h.size < 4 || String(h, Charsets.US_ASCII) != "CISO") return null
        return nintendo(r, 0x8000)
    }

    private fun saturn(r: ByteReader): String? {
        val h = r.read(0, 0x20) ?: return null
        val text = String(h, Charsets.ISO_8859_1)
        return if ("SEGA SEGASATURN" in text) "saturn" else null
    }

    /** Sector layouts an ISO9660 image may use: plain 2048, raw mode 1 and raw mode 2 (most PS1 `.bin`). */
    private data class Layout(val sector: Int, val header: Int)
    private val layouts = listOf(Layout(2048, 0), Layout(2352, 16), Layout(2352, 24))

    private fun iso9660(r: ByteReader): String? {
        for (l in layouts) {
            val pvd = r.read(16L * l.sector + l.header, 2048) ?: continue
            if (pvd.size < 190 || pvd[0].toInt() != 1 || String(pvd, 1, 5, Charsets.US_ASCII) != "CD001") continue
            val rootLba = u32le(pvd, 156 + 2)
            val rootLen = u32le(pvd, 156 + 10).toInt().coerceIn(0, 8 * 2048)
            val entries = directory(r, l, rootLba, rootLen)
            val names = entries.keys
            if ("PSP_GAME" in names || "UMD_DATA.BIN" in names) return "psp"
            val cnf = entries["SYSTEM.CNF"] ?: continue
            val text = readExtent(r, l, cnf.first, cnf.second.coerceAtMost(2048))?.let { String(it, Charsets.ISO_8859_1) } ?: continue
            return when {
                Regex("""(?m)^\s*BOOT2\s*=""").containsMatchIn(text) -> "ps2"
                Regex("""(?m)^\s*BOOT\s*=""").containsMatchIn(text) -> "ps1"
                else -> null
            }
        }
        return null
    }

    /** Name (without `;1`) → (extent LBA, data length). */
    private fun directory(r: ByteReader, l: Layout, lba: Long, length: Int): Map<String, Pair<Long, Int>> {
        val out = mutableMapOf<String, Pair<Long, Int>>()
        var sectorStart = 0
        while (sectorStart < length) {
            val sec = readExtent(r, l, lba + sectorStart / 2048, 2048) ?: break
            var p = 0
            while (p < sec.size) {
                val len = sec[p].toInt() and 0xFF
                if (len == 0 || p + len > sec.size || len < 34) break
                val nameLen = sec[p + 32].toInt() and 0xFF
                if (p + 33 + nameLen <= sec.size && nameLen > 0) {
                    val name = String(sec, p + 33, nameLen, Charsets.ISO_8859_1).substringBefore(';').uppercase()
                    out[name] = u32le(sec, p + 2) to u32le(sec, p + 10).toInt()
                }
                p += len
            }
            sectorStart += 2048
        }
        return out
    }

    private fun readExtent(r: ByteReader, l: Layout, lba: Long, length: Int): ByteArray? =
        r.read(lba * l.sector + l.header, minOf(length, 2048))

    /**
     * Whether a 3DS game is still encrypted, which Azahar may not open. Null when the file isn't
     * a format we can read the flag from.
     */
    fun n3dsEncrypted(r: ByteReader, ext: String): Boolean? = when (ext.lowercase()) {
        "cia" -> ciaEncrypted(r)
        "3ds", "cci" -> ncsdEncrypted(r)
        "cxi" -> ncchEncrypted(r, 0)
        else -> null
    }

    /** CIA: the TMD's content chunk records carry an "encrypted" bit per content. */
    private fun ciaEncrypted(r: ByteReader): Boolean? {
        val h = r.read(0, 0x20) ?: return null
        if (h.size < 0x20) return null
        val headerSize = u32le(h, 0)
        val cert = u32le(h, 0x08); val ticket = u32le(h, 0x0C)
        val tmd = align(align(align(headerSize) + cert) + ticket)
        val sigType = r.read(tmd, 4)?.takeIf { it.size == 4 }?.let { u32be(it, 0) } ?: return null
        val sigBlock = when (sigType) {
            0x10000L, 0x10003L -> 0x200 + 0x3C // RSA-4096
            0x10001L, 0x10004L -> 0x100 + 0x3C // RSA-2048
            0x10002L, 0x10005L -> 0x3C + 0x40 // ECDSA
            else -> return null
        }
        val header = tmd + 4 + sigBlock
        val count = r.read(header + 0x9E, 2)?.takeIf { it.size == 2 }?.let { ((it[0].toInt() and 0xFF) shl 8) or (it[1].toInt() and 0xFF) } ?: return null
        if (count == 0) return null
        val chunks = r.read(header + 0xC4 + 64 * 0x24, count * 0x30) ?: return null
        if (chunks.size < count * 0x30) return null
        return (0 until count).any { i -> (chunks[i * 0x30 + 7].toInt() and 0x01) != 0 }
    }

    /** NCSD (.3ds/.cci): the first partition is an NCCH whose flags say whether it uses crypto. */
    private fun ncsdEncrypted(r: ByteReader): Boolean? {
        val h = r.read(0x100, 0x28) ?: return null
        if (h.size < 0x28 || String(h, 0, 4, Charsets.US_ASCII) != "NCSD") return null
        return ncchEncrypted(r, u32le(h, 0x20) * 0x200)
    }

    private fun ncchEncrypted(r: ByteReader, base: Long): Boolean? {
        val h = r.read(base + 0x100, 0x90) ?: return null
        if (h.size < 0x90 || String(h, 0, 4, Charsets.US_ASCII) != "NCCH") return null
        // flags[7] at NCCH+0x18F; bit 0x04 = NoCrypto.
        return (h[0x8F].toInt() and 0x04) == 0
    }

    private fun align(v: Long) = (v + 63) / 64 * 64

    internal fun u32be(b: ByteArray, o: Int): Long =
        ((b[o].toLong() and 0xFF) shl 24) or ((b[o + 1].toLong() and 0xFF) shl 16) or ((b[o + 2].toLong() and 0xFF) shl 8) or (b[o + 3].toLong() and 0xFF)

    internal fun u32le(b: ByteArray, o: Int): Long =
        (b[o].toLong() and 0xFF) or ((b[o + 1].toLong() and 0xFF) shl 8) or ((b[o + 2].toLong() and 0xFF) shl 16) or ((b[o + 3].toLong() and 0xFF) shl 24)
}
