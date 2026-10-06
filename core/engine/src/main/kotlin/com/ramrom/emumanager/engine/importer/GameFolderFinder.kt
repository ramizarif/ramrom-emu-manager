package com.ramrom.emumanager.engine.importer

import com.ramrom.emumanager.engine.BiosFinder
import com.ramrom.emumanager.engine.StorageLister

/** A folder that looks like it holds games, for S23 to offer. */
data class GameFolder(val path: String, val games: Int, val archives: Int)

/**
 * Finds folders worth importing from, so the user confirms one instead of hunting. Names only, no
 * file is opened: game extensions count, archives count only beside them (a zip alone is just as
 * likely to be anything else). A folder of system folders (Games/ps2, Games/psp) is offered once.
 */
class GameFolderFinder(
    private val lister: StorageLister,
    /** Folders never offered or searched: the ROM folder (already imported), ES-DE's own. */
    private val exclude: List<String>,
    private val maxDepth: Int = 4,
    private val maxFolders: Int = 3000,
) {
    private val gameExts = ImportClassifier.SURE.keys + setOf("iso", "chd", "cue", "gdi", "rvz", "wia", "gcz", "ciso", "wbfs", "pbp")
    private val archiveExts = setOf("zip", "7z", "rar")

    fun find(root: String, limit: Int = 4): List<GameFolder> {
        val direct = mutableMapOf<String, GameFolder>()
        val queue = ArrayDeque(listOf(root to 0))
        var visited = 0
        while (queue.isNotEmpty() && visited < maxFolders) {
            val (dir, d) = queue.removeFirst()
            visited++
            var games = 0; var archives = 0
            for (e in lister.list(dir).orEmpty()) {
                if (e.name.startsWith('.')) continue
                val path = "$dir/${e.name}"
                if (e.isDir) {
                    if (d < maxDepth && !(d == 0 && e.name in BiosFinder.SKIP_TOP) && e.name !in BiosFinder.SKIP_ANY && !excluded(path)) queue += path to d + 1
                } else when (e.name.substringAfterLast('.', "").lowercase()) {
                    in gameExts -> games++
                    in archiveExts -> archives++
                }
            }
            if (games > 0) direct[dir] = GameFolder(dir, games, archives)
        }
        // Two or more game folders under one parent (Games/ps2, Games/psp): offer the parent, which
        // the import scans with its subfolders.
        val merged = direct.values.toMutableList()
        val parents = direct.keys.map { it.substringBeforeLast('/') }.filter { it.length > root.length }
            .groupingBy { it }.eachCount().filterValues { it >= 2 }.keys
        for (p in parents) {
            val kids = merged.filter { it.path.substringBeforeLast('/') == p }
            merged.removeAll(kids)
            val own = merged.firstOrNull { it.path == p }?.also { merged.remove(it) }
            merged += GameFolder(p, kids.sumOf { it.games } + (own?.games ?: 0), kids.sumOf { it.archives } + (own?.archives ?: 0))
        }
        return merged.sortedByDescending { it.games }.take(limit)
    }

    private fun excluded(path: String) = exclude.any { path.equals(it, ignoreCase = true) || path.startsWith("$it/", ignoreCase = true) }
}
