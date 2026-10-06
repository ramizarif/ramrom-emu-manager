package com.ramrom.emumanager.engine.importer

/** Tiny synthetic disc images and an in-memory probe; real dumps are gigabytes. */
object Fixtures {
    fun reader(b: ByteArray) = ByteReader { o, l -> if (o >= b.size) null else b.copyOfRange(o.toInt(), minOf(b.size, (o + l).toInt())) }

    /** An ISO9660 image with [files] in the root, in 2048-byte sectors or raw 2352 with [header] bytes before each. */
    fun iso(files: Map<String, ByteArray>, sector: Int = 2048, header: Int = 0): ByteArray {
        val rootLba = 18
        val firstFile = 20
        val sectors = firstFile + files.size + 1
        val out = ByteArray(sectors * sector)
        fun put(lba: Int, data: ByteArray) = data.copyInto(out, lba * sector + header)
        val pvd = ByteArray(2048)
        pvd[0] = 1; "CD001".toByteArray().copyInto(pvd, 1)
        le32(pvd, 156 + 2, rootLba); le32(pvd, 156 + 10, 2048)
        put(16, pvd)
        val dir = ByteArray(2048)
        var p = 0
        files.entries.forEachIndexed { i, (name, data) ->
            val n = "$name;1".toByteArray()
            val len = 33 + n.size + (if (n.size % 2 == 0) 1 else 0)
            dir[p] = len.toByte(); le32(dir, p + 2, firstFile + i); le32(dir, p + 10, data.size)
            dir[p + 32] = n.size.toByte(); n.copyInto(dir, p + 33)
            p += len
            put(firstFile + i, data)
        }
        put(rootLba, dir)
        return out
    }

    fun gamecube() = ByteArray(0x40).also { be32(it, 0x1C, 0xC2339F3DL) }
    fun wii() = ByteArray(0x40).also { be32(it, 0x18, 0x5D1C9EA3L) }
    fun rvz(discType: Long) = ByteArray(0x100).also { "RVZ".toByteArray().copyInto(it); it[3] = 1; be32(it, 0x48, discType) }
    fun saturn() = ByteArray(0x100).also { "SEGA SEGASATURN ".toByteArray().copyInto(it) }

    /** A CIA whose single content is [encrypted] or not, with an RSA-2048 TMD signature. */
    fun cia(encrypted: Boolean): ByteArray {
        val b = ByteArray(0x6000)
        le32(b, 0, 0x2020); le32(b, 0x08, 0xA00); le32(b, 0x0C, 0x350); le32(b, 0x10, 0xB34)
        val tmd = 0x2DC0
        be32(b, tmd, 0x10004)
        val header = tmd + 4 + 0x13C
        b[header + 0x9E] = 0; b[header + 0x9F] = 1
        b[header + 0xC4 + 0x900 + 7] = if (encrypted) 1 else 0
        return b
    }

    fun ncsd(encrypted: Boolean): ByteArray {
        val b = ByteArray(0x400)
        "NCSD".toByteArray().copyInto(b, 0x100); le32(b, 0x120, 1)
        "NCCH".toByteArray().copyInto(b, 0x300)
        b[0x200 + 0x18F] = if (encrypted) 0 else 0x04
        return b
    }

    fun le32(b: ByteArray, o: Int, v: Int) { for (i in 0..3) b[o + i] = (v ushr (8 * i)).toByte() }
    fun be32(b: ByteArray, o: Int, v: Long) { for (i in 0..3) b[o + i] = (v ushr (8 * (3 - i))).toByte() }
}

class FakeProbe(
    private val files: Map<String, ByteArray> = emptyMap(),
    private val archives: Map<String, Map<String, ByteArray>> = emptyMap(),
) : ImportProbe {
    override fun reader(path: String) = files[path]?.let { Fixtures.reader(it) } ?: ByteReader { _, _ -> null }
    override fun entries(path: String) = archives[path]?.map { (n, b) -> ArchiveEntry(n, b.size.toLong()) }
    override fun peek(path: String, entry: String, maxBytes: Int) = archives[path]?.get(entry)?.let { it.copyOf(minOf(it.size, maxBytes)) }
}
