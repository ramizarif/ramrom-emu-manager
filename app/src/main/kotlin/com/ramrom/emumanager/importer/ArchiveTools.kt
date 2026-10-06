package com.ramrom.emumanager.importer

import com.ramrom.emumanager.engine.importer.ArchiveEntry
import com.ramrom.emumanager.engine.importer.ByteReader
import com.ramrom.emumanager.engine.importer.ImportProbe
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile

/**
 * RAR5, 7z and zip through 7-Zip's native decoder, which also tells the formats apart. Single-volume
 * archives only; split sets are flagged by the classifier and never reach here.
 */
object ArchiveTools {
    private fun <T> open(file: File, block: (IInArchive) -> T): T =
        RandomAccessFile(file, "r").use { raf ->
            val archive = SevenZip.openInArchive(null, RandomAccessFileInStream(raf))
            try { block(archive) } finally { archive.close() }
        }

    private fun IInArchive.path(i: Int) = (getProperty(i, PropID.PATH) as? String).orEmpty().replace('\\', '/')
    private fun IInArchive.isFolder(i: Int) = getProperty(i, PropID.IS_FOLDER) as? Boolean ?: false
    private fun IInArchive.encrypted(i: Int) = getProperty(i, PropID.ENCRYPTED) as? Boolean ?: false

    /** Files inside, or null when it won't open or wants a password. */
    fun entries(file: File): List<ArchiveEntry>? = logged("list ${file.name}") {
        open(file) { a ->
            (0 until a.numberOfItems).filter { !a.isFolder(it) }.map { i ->
                if (a.encrypted(i)) return@open null
                ArchiveEntry(a.path(i), (a.getProperty(i, PropID.SIZE) as? Long) ?: 0L)
            }
        }
    }

    private fun <T> logged(what: String, block: () -> T?): T? = runCatching(block)
        .onFailure { android.util.Log.w("RamROM/archive", "$what failed", it) }.getOrNull()

    /** The first [maxBytes] of [entry], unpacked; stops decoding once it has them. */
    fun peek(file: File, entry: String, maxBytes: Int): ByteArray? = runCatching {
        open(file) { a ->
            val index = (0 until a.numberOfItems).firstOrNull { a.path(it) == entry } ?: return@open null
            val buf = ByteArrayOutputStream()
            try {
                a.extractSlow(index, ISequentialOutStream { data ->
                    buf.write(data, 0, minOf(data.size, maxBytes - buf.size()))
                    if (buf.size() >= maxBytes) throw SevenZipException("enough")
                    data.size
                })
            } catch (_: SevenZipException) { /* stopped on purpose, or damaged: use what we have */ }
            buf.toByteArray().takeIf { it.isNotEmpty() }
        }
    }.getOrNull()

    class Cancelled : Exception("Cancelled")

    /**
     * Unpack [wanted] entries (by path) into [destDir], flattened to their file names. Each lands
     * under a temporary name and is renamed only when complete, so a crash leaves no half-file
     * that looks like a game. Returns the files written.
     */
    fun extract(file: File, wanted: Set<String>, destDir: File, onBytes: (Long) -> Unit, cancelled: () -> Boolean): List<File> {
        val written = mutableListOf<File>()
        open(file) { a ->
            val indices = (0 until a.numberOfItems).filter { !a.isFolder(it) && a.path(it) in wanted }
            if (indices.isEmpty()) return@open
            var failure: String? = null
            a.extract(indices.toIntArray(), false, object : IArchiveExtractCallback {
                private var out: OutputStream? = null
                private var temp: File? = null
                private var final: File? = null

                override fun getStream(index: Int, mode: ExtractAskMode): ISequentialOutStream? {
                    if (mode != ExtractAskMode.EXTRACT) return null
                    val name = Names.safe(a.path(index).substringAfterLast('/'))
                    final = File(destDir, name)
                    val t = File(destDir, "$name$TEMP").also { temp = it }
                    val os = t.outputStream().buffered(1 shl 20).also { out = it }
                    return ISequentialOutStream { data ->
                        if (cancelled()) throw SevenZipException("cancelled")
                        os.write(data); onBytes(data.size.toLong()); data.size
                    }
                }

                override fun prepareOperation(mode: ExtractAskMode) {}

                override fun setOperationResult(result: ExtractOperationResult) {
                    out?.close(); out = null
                    val t = temp; val f = final
                    temp = null; final = null
                    if (t == null || f == null) return
                    if (result == ExtractOperationResult.OK && t.renameTo(f)) written += f
                    else { t.delete(); if (failure == null) failure = "Couldn't unpack ${f.name} ($result)" }
                }

                override fun setTotal(total: Long) {}
                override fun setCompleted(complete: Long) {}
            })
            if (cancelled()) throw Cancelled()
            failure?.let { throw IllegalStateException(it) }
        }
        return written
    }

    /** Leftovers from an unpack or copy the process didn't finish. */
    const val TEMP = ".ramrom-part"
}

/** [ImportProbe] on real files. */
class DeviceImportProbe : ImportProbe {
    override fun reader(path: String) = ByteReader { offset, length ->
        runCatching {
            RandomAccessFile(path, "r").use { f ->
                if (offset >= f.length()) return@use null
                val n = minOf(length.toLong(), f.length() - offset).toInt()
                ByteArray(n).also { f.seek(offset); f.readFully(it) }
            }
        }.getOrNull()
    }
    override fun entries(path: String) = ArchiveTools.entries(File(path))
    override fun peek(path: String, entry: String, maxBytes: Int) = ArchiveTools.peek(File(path), entry, maxBytes)
}

object Names {
    fun safe(name: String) = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
}
