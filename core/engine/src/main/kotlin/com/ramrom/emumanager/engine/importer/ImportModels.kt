package com.ramrom.emumanager.engine.importer

/** A file found where the user asked us to look. */
data class SourceFile(val path: String, val size: Long)

data class ArchiveEntry(val name: String, val size: Long)

/** Device I/O the classifier needs; the app implements it with files and 7-Zip. */
interface ImportProbe {
    fun reader(path: String): ByteReader
    /** An archive's files, or null when it can't be opened (password, damaged, split). */
    fun entries(path: String): List<ArchiveEntry>?
    /** The first [maxBytes] of one entry, unpacked: enough to read a disc header. */
    fun peek(path: String, entry: String, maxBytes: Int): ByteArray?
}

enum class Confidence { Sure, Likely, Unknown }

/** One game as found: a loose file, a cue sheet and its tracks, or an archive. */
data class ImportCandidate(
    val id: String,
    /** Source files, the primary first (the `.cue`, the archive, the image). */
    val files: List<SourceFile>,
    /** For an archive: the game files inside it that will be unpacked. */
    val inner: List<ArchiveEntry> = emptyList(),
    val isArchive: Boolean = false,
    val systemId: String? = null,
    val confidence: Confidence = Confidence.Unknown,
    /** Why we think so, in the user's words: "PS2 disc header", "In a folder called psx". */
    val reason: String = "",
    val flags: Set<Flag> = emptySet(),
) {
    val primary get() = files.first()
    val sizeBytes get() = files.sumOf { it.size }
    val unpackedBytes get() = if (isArchive) inner.sumOf { it.size } else sizeBytes

    enum class Flag {
        /** 3DS content still encrypted: Azahar may not open it. We never decrypt. */
        Encrypted,
        /** `.part1.rar`, `.7z.001`: split archives aren't unpacked yet. */
        SplitArchive,
        /** The archive wouldn't open. */
        Unreadable,
        /** Shorter than its own headers say: the download stopped early. */
        Incomplete,
        /** A zip of many unknown files: an arcade romset, which stays zipped. */
        ArcadeSet,
        /** The name matches a BIOS file the bundle knows. */
        Bios,
        /** An archive of something other than games; left out of the scan. */
        NotGames,
    }
}

enum class ImportAction { Move, Copy, Extract, KeepZipped }

data class ImportProblem(val kind: Kind, val text: String) {
    /** AddOn: a Switch update or DLC, installed inside the emulator rather than kept with the games. */
    /** Guessed: the system was picked because only one in the setup opens the file; a note, not a stop. */
    enum class Kind { NoSystem, AlreadyThere, NameClash, Encrypted, SplitArchive, Unreadable, Incomplete, Bios, NotSetUp, Guessed, AddOn }
    /** Problems that leave the item out unless the user includes it. */
    val excludesByDefault get() = kind in setOf(Kind.NoSystem, Kind.AlreadyThere, Kind.NameClash, Kind.SplitArchive, Kind.Unreadable, Kind.Incomplete, Kind.Bios, Kind.AddOn)
}

/** One row on S23. */
data class ImportItem(
    val candidate: ImportCandidate,
    val title: String,
    val systemId: String?,
    val action: ImportAction,
    /** Absolute folder the game lands in, or null with no system. */
    val destDir: String?,
    /** Names it will have there (the unpacked files for an archive). */
    val outputs: List<String>,
    val problems: List<ImportProblem>,
    val included: Boolean,
) {
    val id get() = candidate.id
}

/** A `.m3u` the import writes so a multi-disc game shows once. */
data class Playlist(val dir: String, val fileName: String, val discs: List<String>)

data class ImportOptions(
    /** Move from the source (Downloads) rather than copy. */
    val move: Boolean,
    val deleteArchives: Boolean,
)

data class ImportPlan(
    val items: List<ImportItem>,
    val playlists: List<Playlist>,
    val options: ImportOptions,
) {
    val included get() = items.filter { it.included }
    /** Space the import needs on the device, worst case: unpacked size plus anything copied. */
    val bytesNeeded get() = included.sumOf { i ->
        when (i.action) {
            ImportAction.Extract -> i.candidate.unpackedBytes
            ImportAction.Copy -> i.candidate.sizeBytes
            ImportAction.Move, ImportAction.KeepZipped -> if (options.move) 0L else i.candidate.sizeBytes
        }
    }
}
