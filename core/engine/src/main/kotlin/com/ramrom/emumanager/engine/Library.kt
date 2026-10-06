package com.ramrom.emumanager.engine

import com.ramrom.emumanager.knowledge.KnowledgeBundle
import java.net.URLDecoder

/** One game on S18: a display title and the files that make it up. */
data class LibraryGame(
    val title: String,
    /** Basename ES-DE uses for media: `covers/<baseName>.png`. */
    val baseName: String,
    val files: List<String>,
    /** The emulator chosen for its system can open it. */
    val playable: Boolean,
)

data class LibrarySystem(val systemId: String, val name: String, val folderName: String, val emulatorId: String?, val games: List<LibraryGame>)

/**
 * S18 from the ROM folders P03 found. No hashing: titles come from file names, grouped so a
 * `.cue` and its `.bin`, or a `.m3u` and its discs, count as one game.
 */
object Library {
    private val escaped = Regex("%[0-9A-Fa-f]{2}")
    /** Disc and track files that belong to a playlist or cue sheet, never games on their own. */
    private val parts = setOf("bin", "img", "sub", "ccd")

    fun build(bundle: KnowledgeBundle, inventory: Inventory, plan: Plan?): List<LibrarySystem> {
        val chosen = plan?.systems?.associate { it.systemId to it.emulatorId }.orEmpty()
        return inventory.romFolders.mapNotNull { folder ->
            val system = bundle.systems.firstOrNull { it.id == folder.systemId } ?: return@mapNotNull null
            val emuId = chosen[folder.systemId]
            val opens = (emuId?.let { id -> bundle.emulators.firstOrNull { it.id == id }?.romExtensions }
                ?: bundle.emulators.filter { folder.systemId in it.systems }.flatMap { it.romExtensions }).map { it.lowercase() }.toSet()
            val strays = folder.strays.map { it.fileName }.toSet()
            val files = folder.files.filter { it !in strays }
            // A `Game.m3u/` folder (ES-DE's directory-as-file) is one game, whatever discs are inside.
            val byBase = files.groupBy { f ->
                f.split('/').dropLast(1).lastOrNull { it.endsWith(".m3u", true) }?.substringBeforeLast('.')
                    ?: f.substringAfterLast('/').substringBeforeLast('.')
            }
            val games = byBase.mapNotNull { (base, group) ->
                val exts = group.map { it.substringAfterLast('.', "").lowercase() }
                // A lone .bin with a matching .cue is one game; a .bin alone (PS2 dumps) is a game itself.
                if (exts.all { it in parts } && byBase.keys.any { other -> other != base && base.startsWith(other) && byBase.getValue(other).any { f -> f.endsWith(".cue", true) || f.endsWith(".m3u", true) } }) return@mapNotNull null
                LibraryGame(title(base), base, group, opens.isEmpty() || exts.any { it in opens })
            }.sortedBy { it.title.lowercase() }
            if (games.isEmpty()) null else LibrarySystem(system.id, system.name, folder.folderName, emuId, games)
        }.sortedBy { it.name }
    }

    /** "Tales%20of%20the%20Abyss%20%28USA%29" → "Tales of the Abyss (USA)". */
    fun title(base: String): String {
        val decoded = if (escaped.containsMatchIn(base)) runCatching { URLDecoder.decode(base.replace("+", "%2B"), "UTF-8") }.getOrDefault(base) else base
        return decoded.replace('_', ' ').trim()
    }
}
