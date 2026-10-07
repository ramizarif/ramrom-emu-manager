package com.ramrom.emumanager.engine.importer

import com.ramrom.emumanager.engine.Library
import com.ramrom.emumanager.engine.inventory.FileLister
import com.ramrom.emumanager.knowledge.KnowledgeBundle

/**
 * P12 plan: where each classified game goes, under what name, and how (move, copy, unpack).
 * Pure and cheap, so S23 re-plans on every change the user makes.
 */
class ImportPlanner(
    private val bundle: KnowledgeBundle,
    private val files: FileLister,
    private val romRoot: String,
) {
    private val frontend = bundle.esde
    private fun folderOf(systemId: String) = frontend?.systemFolders?.get(systemId) ?: systemId
    private fun systemName(id: String) = bundle.systems.firstOrNull { it.id == id }?.name ?: id

    /** Systems where a `.m3u` folder makes a multi-disc game one entry in ES-DE. */
    private val playlistSystems = setOf("ps1", "saturn", "dreamcast")
    private val keepZipped = setOf("arcade", "neogeo")
    private val trackExts = setOf("bin", "img", "raw", "sub")
    private val discTag = Regex("""\s*\((?:Disc|Disk|CD)\s*(\d+)(?:\s+of\s+\d+)?\)""", RegexOption.IGNORE_CASE)

    fun plan(
        candidates: List<ImportCandidate>,
        options: ImportOptions,
        /** Systems in the user's setup; others still import but are flagged. Null skips the check. */
        setUp: Set<String>? = null,
        systemOverrides: Map<String, String> = emptyMap(),
        includeOverrides: Map<String, Boolean> = emptyMap(),
    ): ImportPlan {
        val drafts = candidates.map { c -> draft(c, systemOverrides[c.id] ?: c.systemId, options, setUp) }
        val withDiscs = playlists(drafts)
        val taken = mutableSetOf<String>()
        val items = withDiscs.first.map { d ->
            val problems = d.problems.toMutableList()
            val dir = d.destDir
            if (dir != null) {
                val there = d.outputs.filter { files.exists("$dir/$it") }
                if (there.isNotEmpty() && there.size == d.outputs.size) problems += ImportProblem(ImportProblem.Kind.AlreadyThere, "Already in your ${systemName(d.systemId!!)} folder.")
                else if (d.outputs.any { "$dir/$it".lowercase() in taken }) problems += ImportProblem(ImportProblem.Kind.NameClash, "Another file here has the same name.")
            }
            val included = includeOverrides[d.id] ?: problems.none { it.excludesByDefault }
            if (included && dir != null) d.outputs.forEach { taken += "$dir/$it".lowercase() }
            d.copy(problems = problems, included = included)
        }
        val included = items.filter { it.included }.map { it.id }.toSet()
        return ImportPlan(items, withDiscs.second.filter { p -> p.second.all { it in included } }.map { it.first }, options)
    }

    private fun draft(c: ImportCandidate, systemId: String?, options: ImportOptions, setUp: Set<String>?): ImportItem {
        val problems = mutableListOf<ImportProblem>()
        val flags = c.flags
        if (ImportCandidate.Flag.Bios in flags) problems += ImportProblem(ImportProblem.Kind.Bios, "This looks like a BIOS file, not a game. Health puts BIOS files in place.")
        if (ImportCandidate.Flag.SplitArchive in flags) problems += ImportProblem(ImportProblem.Kind.SplitArchive, "Split archives can't be unpacked here yet. Join the parts first.")
        if (ImportCandidate.Flag.Unreadable in flags) problems += ImportProblem(ImportProblem.Kind.Unreadable, "This archive wouldn't open. It may need a password or be damaged.")
        if (ImportCandidate.Flag.Incomplete in flags) problems += ImportProblem(ImportProblem.Kind.Incomplete, "This archive is incomplete, as if a copy or download stopped part way. A complete copy will import.")
        if (ImportCandidate.Flag.Encrypted in flags) problems += ImportProblem(ImportProblem.Kind.Encrypted, "This 3DS game is encrypted, so Azahar may not open it.")
        if (systemId == null) problems += ImportProblem(ImportProblem.Kind.NoSystem,
            if (ImportCandidate.Flag.ArcadeSet in flags) "Looks like an arcade set. Pick Arcade or Neo Geo and it stays zipped." else "Pick a system for this one.")
        if (systemId != null && setUp != null && systemId !in setUp) problems += ImportProblem(ImportProblem.Kind.NotSetUp, "${systemName(systemId)} isn't in your setup yet, so nothing will open it.")

        val action = when {
            !c.isArchive -> if (options.move) ImportAction.Move else ImportAction.Copy
            systemId in keepZipped || ImportCandidate.Flag.ArcadeSet in flags -> ImportAction.KeepZipped
            else -> ImportAction.Extract
        }
        val outputs = when (action) {
            ImportAction.Extract -> c.inner.map { safe(it.name.substringAfterLast('/')) }.distinct()
            // Cue sheets and gdi name their tracks: renaming one breaks the sheet, so the group keeps its names.
            else -> if (c.files.size > 1) c.files.map { it.path.substringAfterLast('/') } else listOf(cleanName(c.primary.path.substringAfterLast('/')))
        }
        val base = c.primary.path.substringAfterLast('/').let { if (c.isArchive) it.replace(Regex("""(?i)(\.part0*1)?\.(zip|7z|rar)(\.0*1)?$"""), "") else it.substringBeforeLast('.') }
        return ImportItem(
            candidate = c,
            title = Library.title(base),
            systemId = systemId,
            action = action,
            destDir = systemId?.let { "$romRoot/${folderOf(it)}" },
            outputs = outputs,
            problems = problems,
            included = false,
        )
    }

    /**
     * Groups "(Disc 1)", "(Disc 2)" of one game into `<Game>.m3u/` with a playlist inside, which
     * ES-DE shows as a single game. Returns the items with their new folders and each playlist with
     * the items it needs.
     */
    private fun playlists(items: List<ImportItem>): Pair<List<ImportItem>, List<Pair<Playlist, List<String>>>> {
        val groups = items.filter { it.systemId in playlistSystems && discTag.containsMatchIn(it.title) && it.destDir != null }
            .groupBy { it.systemId to discTag.replace(it.title, "").trim() }
            .filterValues { it.size > 1 }
        if (groups.isEmpty()) return items to emptyList()
        val moved = mutableMapOf<String, String>()
        val lists = groups.map { (key, discs) ->
            val name = safe(key.second)
            val dir = "${discs.first().destDir}/$name.m3u"
            discs.forEach { moved[it.id] = dir }
            val ordered = discs.sortedBy { discTag.find(it.title)!!.groupValues[1].toInt() }
            Playlist(dir, "$name.m3u", ordered.map { d -> d.outputs.firstOrNull { it.substringAfterLast('.').lowercase() !in trackExts } ?: d.outputs.first() }) to discs.map { it.id }
        }
        return items.map { i -> moved[i.id]?.let { i.copy(destDir = it) } ?: i } to lists
    }

    /** Downloads arrive `%20`-escaped; decode them, keep the extension. */
    private fun cleanName(name: String): String {
        val ext = name.substringAfterLast('.', "")
        val stem = name.substringBeforeLast('.')
        val decoded = Library.title(stem).let { if ('_' in stem && '%' !in stem) stem else it }
        return safe(if (ext.isEmpty()) decoded else "$decoded.$ext")
    }

    /** Characters shared storage won't take. */
    private fun safe(name: String) = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
}
