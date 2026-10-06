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
    /** Absolute folder [files] are relative to; a system can have two (`psx` and an alias `ps1`). */
    val folderPath: String = "",
    /** ES-DE's system name for that folder, which names its media and gamelist folders. */
    val folderName: String = "",
)

data class LibrarySystem(
    val systemId: String, val name: String, val folderName: String, val emulatorId: String?, val games: List<LibraryGame>,
    /** The system's main folder; a game's own folder is [LibraryGame.folderPath]. */
    val folderPath: String = "",
)

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
        // A system's games may sit in more than one folder (an alias kept for a custom ES-DE system).
        return inventory.romFolders.groupBy { it.systemId }.mapNotNull { (systemId, folders) ->
            val system = bundle.systems.firstOrNull { it.id == systemId } ?: return@mapNotNull null
            val emuId = chosen[systemId]
            val opens = (emuId?.let { id -> bundle.emulators.firstOrNull { it.id == id }?.romExtensions }
                ?: bundle.emulators.filter { systemId in it.systems }.flatMap { it.romExtensions }).map { it.lowercase() }.toSet()
            val games = folders.flatMap { folder -> gamesIn(folder, opens) }.sortedBy { it.title.lowercase() }
            val main = folders.first()
            if (games.isEmpty()) null else LibrarySystem(system.id, system.name, main.folderName, emuId, games, main.folderPath)
        }.sortedBy { it.name }
    }

    private fun gamesIn(folder: com.ramrom.emumanager.engine.inventory.RomFolder, opens: Set<String>): List<LibraryGame> {
        val strays = folder.strays.map { it.fileName }.toSet()
        val files = folder.files.filter { it !in strays }
        // A `Game.m3u/` folder (ES-DE's directory-as-file) is one game, whatever discs are inside.
        val byBase = files.groupBy { f ->
            f.split('/').dropLast(1).lastOrNull { it.endsWith(".m3u", true) }?.substringBeforeLast('.')
                ?: f.substringAfterLast('/').substringBeforeLast('.')
        }
        return byBase.mapNotNull { (base, group) ->
            val exts = group.map { it.substringAfterLast('.', "").lowercase() }
            // A lone .bin with a matching .cue is one game; a .bin alone (PS2 dumps) is a game itself.
            if (exts.all { it in parts } && byBase.keys.any { other -> other != base && base.startsWith(other) && byBase.getValue(other).any { f -> f.endsWith(".cue", true) || f.endsWith(".m3u", true) } }) return@mapNotNull null
            LibraryGame(displayTitle(base), base, group, opens.isEmpty() || exts.any { it in opens }, folder.folderPath, folder.folderName)
        }
    }

    /** "Tales%20of%20the%20Abyss%20%28USA%29" → "Tales of the Abyss (USA)". */
    fun title(base: String): String {
        val decoded = if (escaped.containsMatchIn(base)) runCatching { URLDecoder.decode(base.replace("+", "%2B"), "UTF-8") }.getOrDefault(base) else base
        return decoded.replace('_', ' ').trim()
    }

    /** S18 search: every word typed starts a word of the title, ignoring case and punctuation ("zel bre" finds Breath of the Wild). */
    fun matches(title: String, query: String): Boolean {
        fun words(s: String) = s.lowercase().split(nonWord).filter { it.isNotEmpty() }
        val have = words(title)
        return words(query).all { q -> have.any { it.startsWith(q) } }
    }
    private val nonWord = Regex("""[^\p{L}\p{N}]+""")

    /** [title] for showing on screen, without dump tags. File names keep them; this is display only. */
    fun displayTitle(base: String) = title(base).let { t -> t.replace(tags, "").trim().ifEmpty { t } }

    /**
     * Dump tags, not part of the name: any `[…]` (title IDs, `[!]`, `[v0]`) and `(…)` groups that are
     * regions, languages, revisions or the store (`(USA)`, `(En,Fr)`, `(Rev 1)`, `(eShop)`). Others,
     * like `(Disc 2)`, stay.
     */
    private val tags = Regex(
        """\s*\[[^\]]*]|\s*\((?:USA|US|Europe|EU|Japan|JP|World|Asia|Korea|Australia|Germany|France|Spain|Italy|""" +
            """(?:USA|Europe|Japan|World)(?:, (?:USA|Europe|Japan|World|Korea|Asia))+|[A-Z][a-z](?:,[A-Z][a-z])+|Rev [0-9A-Z]+|v[0-9.]+|eShop|Unl|Beta|Proto)\)""",
    )
}
