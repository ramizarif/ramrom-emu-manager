package com.ramrom.emumanager.engine.importer

import com.ramrom.emumanager.knowledge.KnowledgeBundle

/**
 * P12 scan and classify: groups what's in a folder into games and says which system each is for.
 * Uses only what's in the files: extensions, archive contents, disc headers, and the name of the
 * folder they sit in. When none of that settles it, the user picks.
 */
class ImportClassifier(private val bundle: KnowledgeBundle, private val probe: ImportProbe) {

    private val sure = SURE

    /** Disc images whose header tells us the system. */
    private val sniffable = setOf("iso", "bin", "img", "rvz", "wia", "gcz", "ciso")
    /** Other disc formats several systems share. */
    private val ambiguous = sniffable + setOf("chd", "cue", "pbp")
    private val archives = setOf("zip", "7z", "rar")
    /** Tracks that belong to a cue sheet or gdi, never a game alone. */
    private val tracks = setOf("bin", "img", "raw", "sub")
    /** Files no game archive or arcade set holds: an archive with these is something else (drivers, apps, docs). */
    private val foreign = setOf("so", "json", "apk", "exe", "dll", "xml", "html", "htm", "pdf", "md", "ini", "cfg", "lua", "js", "py", "sh")

    /** Folder names people use, beyond ES-DE's own. */
    private val aliases = mapOf(
        "psx" to "ps1", "playstation" to "ps1", "playstation 2" to "ps2", "playstation2" to "ps2",
        "gamecube" to "gc", "ngc" to "gc", "megadrive" to "genesis", "mega drive" to "genesis",
        "sfc" to "snes", "super nintendo" to "snes", "famicom" to "nes", "fc" to "nes",
        "3ds" to "n3ds", "ns" to "switch", "nintendo switch" to "switch", "dc" to "dreamcast",
        "mame" to "arcade", "fbneo" to "arcade", "fba" to "arcade", "vita" to "psvita", "wii u" to "wiiu",
    )
    private val folderHints: Map<String, String> = buildMap {
        bundle.systems.forEach { put(it.id.lowercase(), it.id); put(it.name.lowercase(), it.id) }
        bundle.frontends.forEach { f -> f.systemFolders.forEach { (sys, folder) -> put(folder.lowercase(), sys) } }
        bundle.frontends.forEach { f -> f.folderAliases.forEach { (sys, names) -> names.forEach { put(it.lowercase(), sys) } } }
        putAll(aliases)
    }
    private val biosNames = bundle.bios.map { it.fileName.lowercase() }.filter { it.isNotEmpty() }.toSet()
    private val split = Regex("""(?i)\.(part(?!0*1\.)\d+\.rar|r\d{2}|7z\.(?!0*1$)\d+|zip\.(?!0*1$)\d+|z(?!64)\d{2})$""")
    private val firstPart = Regex("""(?i)\.(part0*1\.rar|7z\.0*1|zip\.0*1)$""")

    private fun ext(name: String) = name.substringAfterLast('.', "").lowercase()

    /** Everything in [files] as games; unrelated files (`.txt`, images, partial downloads) are skipped. */
    fun scan(files: List<SourceFile>): List<ImportCandidate> {
        val claimed = mutableSetOf<String>()
        val out = mutableListOf<ImportCandidate>()
        // Cue sheets and gdi first, so their tracks join them.
        for (f in files.filter { ext(it.path) == "cue" || ext(it.path) == "gdi" }) {
            val parts = sheetTracks(f, files).filter { it.path !in claimed }
            claimed += f.path; claimed += parts.map { it.path }
            out += classify(ImportCandidate(f.path, listOf(f) + parts))
        }
        for (f in files) {
            if (f.path in claimed) continue
            val name = f.path.substringAfterLast('/')
            val e = ext(name)
            when {
                split.containsMatchIn(name) -> continue // later volumes ride with the first
                firstPart.containsMatchIn(name) -> out += classify(ImportCandidate(f.path, listOf(f) + volumes(f, files), isArchive = true, flags = setOf(ImportCandidate.Flag.SplitArchive)))
                e in archives -> out += classify(ImportCandidate(f.path, listOf(f), isArchive = true))
                e in sure || e in ambiguous -> out += classify(ImportCandidate(f.path, listOf(f)))
            }
            claimed += f.path
        }
        return out.filter { ImportCandidate.Flag.NotGames !in it.flags }.sortedBy { it.primary.path.substringAfterLast('/').lowercase() }
    }

    /** Files a cue or gdi lists, else those whose name starts with its own. */
    private fun sheetTracks(sheet: SourceFile, all: List<SourceFile>): List<SourceFile> {
        val dir = sheet.path.substringBeforeLast('/')
        val listed = runCatching {
            val text = probe.reader(sheet.path).read(0, 64 * 1024)?.let { String(it, Charsets.ISO_8859_1) }.orEmpty()
            Regex("""(?im)^\s*FILE\s+"([^"]+)"""").findAll(text).map { it.groupValues[1] }.toList() +
                Regex("""(?m)^\s*\d+\s+\d+\s+\d+\s+\d+\s+("?)([^"\r\n]+?)\1\s+\d+\s*$""").findAll(text).map { it.groupValues[2] }.toList()
        }.getOrDefault(emptyList())
        if (listed.isNotEmpty()) return listed.mapNotNull { n -> all.firstOrNull { it.path == "$dir/$n" } }
        val base = sheet.path.substringBeforeLast('.')
        return all.filter { it.path != sheet.path && it.path.startsWith(base) && ext(it.path) in tracks }
    }

    private fun volumes(first: SourceFile, all: List<SourceFile>): List<SourceFile> {
        val stem = first.path.replace(firstPart, "")
        return all.filter { it.path != first.path && it.path.startsWith(stem) && split.containsMatchIn(it.path) }
    }

    /** Settle the system for one candidate. */
    fun classify(c: ImportCandidate): ImportCandidate {
        val name = c.primary.path.substringAfterLast('/')
        val flags = c.flags.toMutableSet()
        if (name.lowercase() in biosNames) flags += ImportCandidate.Flag.Bios
        var inner = c.inner
        val found = if (c.isArchive) archive(c, flags) { inner = it } else loose(c)
        val (system, confidence, reason) = found ?: folderHint(c.primary.path) ?: Verdict(null, Confidence.Unknown, "")
        if (system == "n3ds" && !c.isArchive && runCatching { DiscSniffer.n3dsEncrypted(probe.reader(c.primary.path), ext(name)) }.getOrNull() == true) flags += ImportCandidate.Flag.Encrypted
        return c.copy(systemId = system, confidence = confidence, reason = reason, flags = flags, inner = inner)
    }

    private data class Verdict(val system: String?, val confidence: Confidence, val reason: String)

    private fun loose(c: ImportCandidate): Verdict? {
        val e = ext(c.primary.path)
        sure[e]?.let { return Verdict(it, Confidence.Sure, ".$e file") }
        // A cue says nothing itself; its first data track does.
        val image = if (e == "cue") c.files.drop(1).firstOrNull { ext(it.path) in sniffable } ?: return null else c.primary
        if (ext(image.path) !in sniffable) return null
        val sys = runCatching { DiscSniffer.sniff(probe.reader(image.path)) }.getOrNull() ?: return null
        return Verdict(sys, Confidence.Sure, headerReason(sys))
    }

    /** [found] receives the game files inside, which are what gets unpacked. */
    private fun archive(c: ImportCandidate, flags: MutableSet<ImportCandidate.Flag>, found: (List<ArchiveEntry>) -> Unit): Verdict? {
        if (ImportCandidate.Flag.SplitArchive in flags) return switchName(c)
        val entries = runCatching { probe.entries(c.primary.path) }.getOrNull()
        if (entries == null) {
            val partial = runCatching { DiscSniffer.archiveIncomplete(probe.reader(c.primary.path), c.primary.size) }.getOrDefault(false)
            flags += if (partial) ImportCandidate.Flag.Incomplete else ImportCandidate.Flag.Unreadable
            return switchName(c)
        }
        val games = entries.filter { val x = ext(it.name); x in sure || x in ambiguous || x in tracks || x == "m3u" }
        val known = games.filter { ext(it.name) in sure || ext(it.name) in ambiguous }
        if (known.isEmpty()) {
            // Many files we don't know, zipped together: what an arcade set looks like. Drivers,
            // apps and documents aren't games at all, and aren't listed.
            if (entries.any { ext(it.name) in foreign } || entries.isEmpty()) flags += ImportCandidate.Flag.NotGames
            else if (entries.size > 1) flags += ImportCandidate.Flag.ArcadeSet
            return null
        }
        found(games)
        sure[ext(known.first().name)]?.let { sys ->
            val exts = known.map { ext(it.name) }.toSet()
            return Verdict(sys, Confidence.Sure, "Archive holds .${exts.first()}")
        }
        val disc = known.filter { ext(it.name) in sniffable }.maxByOrNull { it.size } ?: return null
        val head = runCatching { probe.peek(c.primary.path, disc.name, PEEK_BYTES) }.getOrNull() ?: return null
        val sys = DiscSniffer.sniff { o, l -> if (o >= head.size) null else head.copyOfRange(o.toInt(), minOf(head.size, (o + l).toInt())) } ?: return null
        return Verdict(sys, Confidence.Sure, headerReason(sys))
    }

    /** Unreadable archives: a Switch archive often says so in its name. */
    private fun switchName(c: ImportCandidate): Verdict? =
        if (Regex("""(?i)\b(nsp|xci|nsz)\b""").containsMatchIn(c.primary.path.substringAfterLast('/'))) Verdict("switch", Confidence.Likely, "Name says it's a Switch game") else null

    private fun folderHint(path: String): Verdict? {
        val parent = path.substringBeforeLast('/').substringAfterLast('/')
        val sys = folderHints[parent.lowercase()] ?: return null
        return Verdict(sys, Confidence.Likely, "In a folder called $parent")
    }

    private fun headerReason(sys: String) = "${bundle.systems.firstOrNull { it.id == sys }?.name ?: sys} disc header"

    companion object {
        /** Extensions only one system uses, whatever emulator opens them. (`.pbp` isn't: PSP and PS1 both use it.) */
        val SURE = mapOf(
            "nsp" to "switch", "xci" to "switch", "nsz" to "switch", "xcz" to "switch",
            "nds" to "nds", "srl" to "nds",
            "cia" to "n3ds", "3ds" to "n3ds", "cci" to "n3ds", "cxi" to "n3ds",
            "gba" to "gba", "gbc" to "gbc", "gb" to "gb",
            "nes" to "nes", "fds" to "nes", "sfc" to "snes", "smc" to "snes",
            "z64" to "n64", "n64" to "n64", "v64" to "n64",
            "md" to "genesis", "gen" to "genesis", "smd" to "genesis",
            "gg" to "gamegear", "sms" to "mastersystem",
            "cso" to "psp",
            "gcm" to "gc", "wbfs" to "wii",
            "gdi" to "dreamcast", "cdi" to "dreamcast",
            "vpk" to "psvita", "wud" to "wiiu", "wux" to "wiiu", "wua" to "wiiu",
            "ecm" to "ps1",
        )

        /** Enough of a disc image to reach the ISO9660 root and SYSTEM.CNF on nearly every dump. */
        const val PEEK_BYTES = 4 * 1024 * 1024
    }
}
