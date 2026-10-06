package com.ramrom.emumanager.knowledge

/** One game in search results; regional releases of the same name collapse into one hit. */
data class SearchHit(val system: String, val name: String, val year: Int, val regions: List<String>)

/**
 * Offline search over the title index (P20 free tier). Every query word must start a word in the
 * title, so "zel bre" finds "The Legend of Zelda: Breath of the Wild". Pure, so it's unit-tested.
 */
class TitleSearch(entries: List<TitleIndexEntry>) {
    private class Doc(val entry: TitleIndexEntry, val norm: String, val words: List<String>)

    private val docs = entries.map { e -> val n = normalize(e.name); Doc(e, n, n.split(' ')) }

    fun search(query: String, systems: Set<String>? = null, limit: Int = 40): List<SearchHit> {
        val q = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (q.isEmpty()) return emptyList()
        val qJoined = q.joinToString(" ")
        return docs.asSequence()
            .filter { systems == null || it.entry.system in systems }
            .filter { d -> q.all { t -> d.words.any { it.startsWith(t) } } }
            .groupBy { it.entry.system to it.norm }
            .map { (_, ds) ->
                val first = ds.minBy { if (it.entry.year > 0) it.entry.year else Int.MAX_VALUE }
                val score = when {
                    first.norm == qJoined -> 0
                    first.norm.startsWith(qJoined) -> 1
                    first.words.firstOrNull()?.startsWith(q.first()) == true -> 2
                    else -> 3
                }
                score to SearchHit(first.entry.system, first.entry.name, first.entry.year,
                    ds.map { it.entry.region }.filter { it.isNotEmpty() && it != "UNKNOWN" }.distinct())
            }
            .sortedWith(compareBy({ it.first }, { it.second.name.length }, { it.second.name }))
            .take(limit)
            .map { it.second }
    }

    companion object {
        /** Lowercase, accents and punctuation off, "&" as "and", so typing on a controller is forgiving. */
        fun normalize(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace("&", " and ")
            .replace(Regex("['’‘`]"), "") // "Let’s" and "Lets" match
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }
}

/** How a system is likely to run on a chip. A general estimate, not a per-game test (that's M4). */
enum class Verdict(val label: String) {
    Great("Runs well"),
    Good("Most games run well"),
    Varies("Varies by game"),
    Tough("Demanding on this chip"),
}

object Verdicts {
    private val light = setOf("nes", "snes", "gb", "gbc", "gba", "genesis", "mastersystem", "gamegear", "n64", "ps1", "psp", "nds", "neogeo", "arcade")
    private val medium = setOf("ps2", "gc", "wii", "n3ds", "dreamcast", "saturn", "psvita")
    private val heavy = setOf("switch", "wiiu")
    private val strong = setOf("sd8gen2", "sd8gen3", "sd8elite", "g3xgen2")

    /** Conservative on purpose: better to under-promise and let M4's per-game data say "Runs well". */
    fun forSystem(systemId: String, chipFamily: String): Verdict = when (systemId) {
        in light -> Verdict.Great
        in medium -> if (chipFamily in strong) Verdict.Good else Verdict.Varies
        in heavy -> when (chipFamily) { "sd8elite" -> Verdict.Good; in strong -> Verdict.Varies; "sd865" -> Verdict.Tough; else -> Verdict.Varies }
        else -> Verdict.Varies
    }
}
