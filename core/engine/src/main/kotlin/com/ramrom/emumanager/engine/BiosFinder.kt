package com.ramrom.emumanager.engine

import com.ramrom.emumanager.knowledge.KnowledgeBundle

data class StorageEntry(val name: String, val isDir: Boolean, val size: Long)

/** Lists one folder; null when it can't be read. */
fun interface StorageLister {
    fun list(dir: String): List<StorageEntry>?
}

/** A folder that holds BIOS files for the plan, with what it covers. */
data class BiosFolder(val path: String, val results: List<BiosCheckResult>) {
    val found get() = results.count { it.status is BiosCheckStatus.Found }
    /** Right size and a BIOS-like name, but a fingerprint we don't know (another region, say). */
    val unrecognised get() = results.count { val s = it.status; s is BiosCheckStatus.WrongVersion && BIOS_NAME.containsMatchIn(s.file.path.substringAfterLast('/')) }
    private val score get() = found * 10 + unrecognised

    internal fun ranks() = score
}

/** Names BIOS files go by; only used to rank unrecognised files, never to accept one. */
private val BIOS_NAME = Regex("""(?i)bios|scph|ps[12x]|dc_boot|dc_flash|ipl|firmware|sega_|mpr-|syscard|kick""")

/**
 * Looks for the user's BIOS files so they confirm a folder instead of hunting for one. Walks shared
 * storage by size only (cheap), fingerprints files the size of a known BIOS, and ranks the folders
 * holding matches by how many of the plan's systems they cover. Nothing leaves the device.
 */
class BiosFinder(
    private val bundle: KnowledgeBundle,
    private val lister: StorageLister,
    md5: (String) -> String,
    private val maxDepth: Int = 5,
    private val maxFolders: Int = 4000,
) {
    private val sizes = bundle.bios.map { it.size }.filter { it in 1..MAX_SIZE }.toSet()
    private val hashes = mutableMapOf<String, String>()
    private val md5Cached: (String) -> String = { p -> hashes.getOrPut(p) { md5(p) } }

    /** Up to [limit] folders, best first: most systems covered, then the most specific folder. */
    fun find(root: String, chosen: List<Pair<String, String>>, limit: Int = 3): List<BiosFolder> {
        val matches = walk(root)
        if (matches.isEmpty()) return emptyList()
        // A folder counts if it holds a match directly, or up to two levels down (BIOS/dc/dc_boot.bin),
        // which is how a picked folder is read too. Never the storage root itself.
        val folders = matches.flatMap { f ->
            val dir = f.path.substringBeforeLast('/')
            listOf(dir, dir.substringBeforeLast('/'), dir.substringBeforeLast('/').substringBeforeLast('/'))
        }.filter { it.length > root.length && it.startsWith("$root/") }.toSet()
        val ranked = folders.map { BiosFolder(it, check(it, chosen, matches)) }
            .filter { it.ranks() > 0 }
            .sortedWith(compareByDescending<BiosFolder> { it.ranks() }.thenByDescending { it.path.count { c -> c == '/' } })
        // A folder inside or around one already listed adds nothing unless it covers more.
        val kept = mutableListOf<BiosFolder>()
        for (f in ranked) if (kept.none { k -> (k.path.startsWith(f.path + "/") || f.path.startsWith(k.path + "/")) && k.ranks() >= f.ranks() }) kept += f
        return kept.take(limit)
    }

    /** What one folder covers, e.g. after the user picks it or the plan changes. */
    fun check(folder: String, chosen: List<Pair<String, String>>): List<BiosCheckResult> = check(folder, chosen, walk(folder, depth = 2))

    private fun check(folder: String, chosen: List<Pair<String, String>>, all: List<CandidateFile>): List<BiosCheckResult> {
        val depth = folder.count { it == '/' }
        val inside = all.filter { it.path.startsWith("$folder/") && it.path.count { c -> c == '/' } - depth <= 3 }
        return BiosIdentifier(bundle).checkBios(inside, chosen)
    }

    private fun walk(root: String, depth: Int = maxDepth): List<CandidateFile> {
        val out = mutableListOf<CandidateFile>()
        val queue = ArrayDeque(listOf(root to 0))
        var visited = 0
        while (queue.isNotEmpty() && visited < maxFolders) {
            val (dir, d) = queue.removeFirst()
            visited++
            for (e in lister.list(dir).orEmpty()) {
                if (e.name.startsWith('.')) continue
                val path = "$dir/${e.name}"
                if (e.isDir) { if (d < depth && !(d == 0 && e.name in SKIP_TOP) && e.name !in SKIP_ANY) queue += path to d + 1 }
                else if (e.size in sizes) out += CandidateFile(path, e.size, "file://$path") { md5Cached(path) }
            }
        }
        return out
    }

    companion object {
        /** No BIOS in the table is bigger than this. */
        const val MAX_SIZE = 8L * 1024 * 1024
        /** Top-level folders that hold media or other apps' data, never BIOS files we can use. */
        val SKIP_TOP = setOf("Android", "DCIM", "Pictures", "Movies", "Music", "Podcasts", "Ringtones", "Alarms", "Notifications", "Audiobooks", "Recordings")
        val SKIP_ANY = setOf("downloaded_media", "screenshots", "themes", "cache", "shaders")
    }
}
