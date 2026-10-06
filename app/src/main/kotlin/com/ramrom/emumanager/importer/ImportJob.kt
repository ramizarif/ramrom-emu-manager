package com.ramrom.emumanager.importer

import com.ramrom.emumanager.engine.importer.ImportAction
import com.ramrom.emumanager.engine.importer.ImportPlan
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * What S24 shows and what survives the process: the plan flattened to plain data, with each item's
 * state. Saved after every item, so a killed import picks up where it stopped.
 */
@Serializable
data class ImportJob(
    val id: String,
    val items: List<JobItem>,
    val playlists: List<JobPlaylist>,
    val move: Boolean,
    val deleteArchives: Boolean,
    val startedAtMs: Long,
    val cancelled: Boolean = false,
    /** Bytes processed so far, for the progress bar. Not exact across a resume. */
    val bytesDone: Long = 0,
) {
    val bytesTotal get() = items.sumOf { it.bytes }
    val finished get() = items.none { it.state == JobItem.State.Waiting || it.state == JobItem.State.Working }
    val progress get() = if (bytesTotal == 0L) (if (finished) 1f else 0f) else (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f)
    fun count(s: JobItem.State) = items.count { it.state == s }
    /** Systems that got new games, for "Restart ES-DE" and the settings follow-up. */
    val touchedSystems get() = items.filter { it.state == JobItem.State.Done }.map { it.systemId }.toSet()

    companion object {
        fun of(plan: ImportPlan, now: Long) = ImportJob(
            id = "import-$now",
            items = plan.included.map { i ->
                JobItem(
                    id = i.id, title = i.title, systemId = i.systemId!!, action = i.action,
                    sources = i.candidate.files.map { it.path }, destDir = i.destDir!!, outputs = i.outputs,
                    inner = i.candidate.inner.map { it.name },
                    bytes = if (i.action == ImportAction.Extract) i.candidate.unpackedBytes else i.candidate.sizeBytes,
                )
            },
            playlists = plan.playlists.map { JobPlaylist(it.dir, it.fileName, it.discs) },
            move = plan.options.move, deleteArchives = plan.options.deleteArchives, startedAtMs = now,
        )
    }
}

@Serializable
data class JobItem(
    val id: String,
    val title: String,
    val systemId: String,
    val action: ImportAction,
    val sources: List<String>,
    val destDir: String,
    val outputs: List<String>,
    val inner: List<String> = emptyList(),
    val bytes: Long,
    val state: State = State.Waiting,
    /** Why it was skipped or failed, in the user's words. */
    val note: String? = null,
) {
    enum class State { Waiting, Working, Done, Skipped, Failed }
}

@Serializable
data class JobPlaylist(val dir: String, val fileName: String, val discs: List<String>)

/** `filesDir/import/current.json`, written atomically. */
class ImportJobStore(private val dir: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file get() = File(dir, "current.json")

    fun load(): ImportJob? = runCatching { json.decodeFromString(ImportJob.serializer(), file.readText()) }.getOrNull()

    fun save(job: ImportJob) {
        dir.mkdirs()
        val tmp = File(dir, "current.json.tmp")
        tmp.writeText(json.encodeToString(ImportJob.serializer(), job))
        tmp.renameTo(file)
    }

    fun clear() { file.delete() }
}
