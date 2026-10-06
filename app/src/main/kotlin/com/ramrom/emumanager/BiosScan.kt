package com.ramrom.emumanager

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.ramrom.emumanager.engine.CandidateFile
import com.ramrom.emumanager.engine.BiosFinder
import com.ramrom.emumanager.engine.StorageEntry
import com.ramrom.emumanager.engine.StorageLister
import com.ramrom.emumanager.knowledge.KnowledgeBundle
import java.io.File
import java.security.MessageDigest

/** Lists files under a picked folder (P11's input). Hashing is deferred until a size matches. */
object BiosScan {
    /** No BIOS in the table is bigger than this; anything larger is a game, not a BIOS. */
    private const val MAX_SIZE = 8L * 1024 * 1024

    /** Walks the tree two levels deep: BIOS folders often have a subfolder like `dc/`. */
    fun candidates(context: Context, tree: Uri, wantedSizes: Set<Long>): List<CandidateFile> {
        val out = mutableListOf<CandidateFile>()
        fun walk(docId: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            context.contentResolver.query(
                children,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0); val name = c.getString(1); val mime = c.getString(2); val size = c.getLong(3)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) { if (depth < 2) walk(id, depth + 1); continue }
                    if (size > MAX_SIZE || size !in wantedSizes) continue
                    val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    out += CandidateFile(name, size, uri.toString()) { md5(context, uri) }
                }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(tree), 0)
        return out
    }

    private fun md5(context: Context, uri: Uri): String {
        val digest = MessageDigest.getInstance("MD5")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; digest.update(buf, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Shared storage, which S09 searches when it may read all files. */
    const val STORAGE_ROOT = "/storage/emulated/0"

    /** [BiosFinder] over real files; needs all-files access. */
    fun finder(bundle: KnowledgeBundle) = BiosFinder(bundle, storage, ::md5File)

    /** Shared storage through plain files (all-files access); also used to find folders of games. */
    val storage = StorageLister { dir -> File(dir).listFiles()?.map { StorageEntry(it.name, it.isDirectory, if (it.isFile) it.length() else 0) } }

    private fun md5File(path: String): String {
        val digest = MessageDigest.getInstance("MD5")
        File(path).inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; digest.update(buf, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
