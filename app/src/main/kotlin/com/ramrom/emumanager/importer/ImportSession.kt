package com.ramrom.emumanager.importer

import android.os.Environment
import android.os.StatFs
import com.ramrom.emumanager.EmuApplication
import com.ramrom.emumanager.engine.importer.ImportAction
import com.ramrom.emumanager.engine.importer.ImportCandidate
import com.ramrom.emumanager.engine.importer.ImportClassifier
import com.ramrom.emumanager.engine.importer.ImportOptions
import com.ramrom.emumanager.engine.importer.ImportPlan
import com.ramrom.emumanager.engine.importer.ImportPlanner
import com.ramrom.emumanager.engine.importer.SourceFile
import com.ramrom.emumanager.engine.inventory.FileLister
import com.ramrom.emumanager.run.DeviceStepExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Where S23 looks. */
data class ImportSourceDir(val path: String, val label: String, val isDownloads: Boolean)

/** A folder S23 found games in, to offer as a source. */
data class SuggestedSource(val dir: ImportSourceDir, val games: Int, val archives: Int)

/** S23's working state: what we found, the user's changes, and the plan they make. */
data class ImportDraft(
    val source: ImportSourceDir,
    val candidates: List<ImportCandidate>,
    val systemOverrides: Map<String, String> = emptyMap(),
    val includeOverrides: Map<String, Boolean> = emptyMap(),
    val options: ImportOptions,
    val plan: ImportPlan,
    val freeBytes: Long,
    /** Systems in the user's setup, for "not set up yet" warnings. */
    val setUp: Set<String>? = null,
    val setUpEmulators: Map<String, String> = emptyMap(),
) {
    val enoughSpace get() = plan.bytesNeeded <= freeBytes
}

/**
 * P12 on the device. Scanning and planning back S23; the import itself runs in the app's scope
 * with [ImportService] keeping the process alive, so leaving the screen doesn't stop it.
 */
class ImportSession(private val app: EmuApplication) {
    private val store = ImportJobStore(File(app.filesDir, "import"))
    private val _draft = MutableStateFlow<ImportDraft?>(null)
    val draft: StateFlow<ImportDraft?> = _draft.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    private val _job = MutableStateFlow<ImportJob?>(null)
    val job: StateFlow<ImportJob?> = _job.asStateFlow()
    private var worker: Job? = null
    private val _suggestions = MutableStateFlow<List<SuggestedSource>?>(null)
    /** Folders with games S23 found by itself; null until it has looked. */
    val suggestions: StateFlow<List<SuggestedSource>?> = _suggestions.asStateFlow()
    /** The notification asked to show S24. */
    val openRequested = MutableStateFlow(false)
    @Volatile private var cancelRequested = false

    val romRoot = DeviceStepExecutor.DEFAULT_ROM_ROOT
    val downloads = ImportSourceDir(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath, "Downloads", true)

    /** Without all-files access we can't see or move what's in Downloads. */
    fun canReadFiles() = Environment.isExternalStorageManager()

    suspend fun scan(source: ImportSourceDir) {
        val bundle = app.knowledge.getBundle() ?: return
        _scanning.value = true
        try {
            val candidates = withContext(Dispatchers.IO) {
                val files = listSources(File(source.path))
                ImportClassifier(bundle, DeviceImportProbe()).scan(files)
            }
            // F30: move out of Downloads (it's a holding area), copy from anywhere else.
            val options = ImportOptions(move = source.isDownloads, deleteArchives = source.isDownloads)
            val planned = app.runs.lastPlan()?.systems
            val setUp = planned?.map { it.systemId }?.toSet()
            _draft.value = replan(ImportDraft(source, candidates, options = options, plan = ImportPlan(emptyList(), emptyList(), options), freeBytes = 0,
                setUp = setUp, setUpEmulators = planned.orEmpty().associate { it.systemId to it.emulatorId }))
        } finally { _scanning.value = false }
    }

    /** Look for folders with games, by name only, so the user confirms one instead of hunting. */
    suspend fun findSources() {
        val root = com.ramrom.emumanager.BiosScan.STORAGE_ROOT
        val esde = "$root/ES-DE"
        _suggestions.value = withContext(Dispatchers.IO) {
            runCatching {
                com.ramrom.emumanager.engine.importer.GameFolderFinder(com.ramrom.emumanager.BiosScan.storage, listOf(romRoot, esde)).find(root)
            }.getOrDefault(emptyList()).map { g ->
                val rel = g.path.removePrefix("$root/")
                SuggestedSource(ImportSourceDir(g.path, rel, g.path.equals(downloads.path, ignoreCase = true)), g.games, g.archives)
            }
        }
    }

    fun setSystem(itemId: String, systemId: String) = update { it.copy(systemOverrides = it.systemOverrides + (itemId to systemId), includeOverrides = it.includeOverrides - itemId) }
    fun setIncluded(itemId: String, included: Boolean) = update { it.copy(includeOverrides = it.includeOverrides + (itemId to included)) }
    fun setOptions(options: ImportOptions) = update { it.copy(options = options) }

    private fun update(change: (ImportDraft) -> ImportDraft) { _draft.value?.let { _draft.value = replan(change(it)) } }

    private fun replan(d: ImportDraft): ImportDraft {
        val bundle = app.knowledge.getBundle() ?: return d
        val plan = ImportPlanner(bundle, DirectFiles, romRoot).plan(d.candidates, d.options, d.setUp, d.systemOverrides, d.includeOverrides, d.setUpEmulators)
        return d.copy(plan = plan, freeBytes = free())
    }

    private fun free() = runCatching { File(romRoot).let { if (it.exists()) it else it.parentFile!! }.let { StatFs(it.path).availableBytes } }.getOrDefault(0L)

    /** Start importing what S23 shows. False when there's nothing to do or no room. */
    fun start(): Boolean {
        val d = _draft.value ?: return false
        if (d.plan.included.isEmpty() || !d.enoughSpace || worker?.isActive == true) return false
        val job = ImportJob.of(d.plan, System.currentTimeMillis())
        store.save(job)
        _job.value = job
        launch(job)
        return true
    }

    /** Stop after the file in hand; nothing half-written is left behind. */
    fun cancel() { cancelRequested = true }

    /** An import the process lost carries on. */
    fun resumeIfAny() {
        val job = store.load() ?: return
        if (job.finished || job.cancelled) { _job.value = job; return }
        // Whatever was mid-way gets another go; the executor skips what's already in place.
        val reset = job.copy(items = job.items.map { if (it.state == JobItem.State.Working) it.copy(state = JobItem.State.Waiting) else it })
        _job.value = reset
        launch(reset)
    }

    /** S24 closed on a finished import. */
    fun dismiss() { if (_job.value?.finished == true) { store.clear(); _job.value = null; _draft.value = null; _suggestions.value = null } }

    private fun launch(job: ImportJob) {
        cancelRequested = false
        ImportService.start(app)
        worker = app.scope.launch(Dispatchers.IO) {
            val done = ImportExecutor(job, { cancelRequested }) { j -> store.save(j); _job.value = j }.run()
            store.save(done); _job.value = done
            if (done.touchedSystems.isNotEmpty()) runCatching { app.health.check() }
        }
    }

    private fun listSources(root: File, depth: Int = 3): List<SourceFile> {
        val out = mutableListOf<SourceFile>()
        fun walk(dir: File, d: Int) {
            val kids = dir.listFiles() ?: return
            for (f in kids) {
                if (f.name.startsWith('.')) continue
                if (f.isDirectory) { if (d > 0 && !f.absolutePath.startsWith(romRoot)) walk(f, d - 1) }
                else if (!f.name.endsWith(ArchiveTools.TEMP)) out += SourceFile(f.absolutePath, f.length())
            }
        }
        walk(root, depth)
        return out
    }

    private object DirectFiles : FileLister {
        override fun listFiles(folderPath: String) = emptyList<String>()
        override fun exists(path: String) = File(path).exists()
        override fun isDirectory(path: String) = File(path).isDirectory
    }
}

/** Runs a job item by item; [save] gets every state change. */
class ImportExecutor(private var job: ImportJob, private val cancelled: () -> Boolean, private val save: (ImportJob) -> Unit) {
    fun run(): ImportJob {
        for (item in job.items) {
            if (item.state != JobItem.State.Waiting) continue
            if (cancelled()) { set(item.id, JobItem.State.Skipped, "Cancelled"); continue }
            set(item.id, JobItem.State.Working)
            val result = runCatching { importOne(item) }
            when {
                result.isSuccess -> set(item.id, result.getOrThrow().first, result.getOrThrow().second)
                result.exceptionOrNull() is ArchiveTools.Cancelled -> set(item.id, JobItem.State.Skipped, "Cancelled")
                else -> set(item.id, JobItem.State.Failed, result.exceptionOrNull()?.message ?: "Something went wrong")
            }
        }
        writePlaylists()
        tidySources()
        job = job.copy(cancelled = cancelled())
        return job
    }

    /**
     * A folder the import emptied (Download/MyGames) goes too, and its emptied parents, but never a
     * top-level folder like Download itself, and never one with anything left in it.
     */
    private fun tidySources() {
        val root = File("/storage/emulated/0")
        job.items.filter { it.state == JobItem.State.Done }.flatMap { it.sources }.mapNotNull { File(it).parentFile }.distinct().forEach { start ->
            var dir: File? = start
            while (dir != null && dir.parentFile != null && dir.parentFile != root && dir != root) {
                if (dir.list()?.isEmpty() != true || !dir.delete()) break
                dir = dir.parentFile
            }
        }
    }

    private fun importOne(item: JobItem): Pair<JobItem.State, String?> {
        val dest = File(item.destDir).apply { mkdirs() }
        if (!dest.isDirectory) error("Couldn't make the folder ${dest.name}")
        val sources = item.sources.map(::File)
        return when (item.action) {
            ImportAction.Extract -> {
                val outputs = item.outputs.map { File(dest, it) }
                if (outputs.all { it.exists() }) return JobItem.State.Done to "Already unpacked"
                val archive = sources.first()
                if (!archive.exists()) error("The archive is gone")
                val written = ArchiveTools.extract(archive, item.inner.toSet(), dest, ::progress, cancelled)
                if (written.isEmpty()) error("Nothing to unpack")
                if (job.deleteArchives) sources.forEach { it.delete() }
                JobItem.State.Done to null
            }
            ImportAction.Move, ImportAction.Copy, ImportAction.KeepZipped -> {
                val move = item.action == ImportAction.Move || (item.action == ImportAction.KeepZipped && job.move)
                sources.zip(item.outputs).forEach { (src, name) ->
                    val target = File(dest, name)
                    when {
                        target.exists() && !src.exists() -> progress(target.length()) // moved before a restart
                        target.exists() -> if (target.length() == src.length()) { if (move) src.delete(); progress(src.length()) } else error("${target.name} is already there")
                        move && src.renameTo(target) -> progress(src.length().coerceAtLeast(target.length()))
                        else -> { copy(src, target); if (move) src.delete() }
                    }
                }
                JobItem.State.Done to null
            }
        }
    }

    private fun copy(src: File, target: File) {
        val temp = File(target.parentFile, target.name + ArchiveTools.TEMP)
        try {
            src.inputStream().use { input -> temp.outputStream().use { out ->
                val buf = ByteArray(1 shl 20)
                while (true) {
                    if (cancelled()) throw ArchiveTools.Cancelled()
                    val n = input.read(buf); if (n < 0) break
                    out.write(buf, 0, n); progress(n.toLong())
                }
            } }
            if (!temp.renameTo(target)) error("Couldn't finish ${target.name}")
        } finally { temp.delete() }
    }

    private fun writePlaylists() {
        for (p in job.playlists) {
            val discs = p.discs.filter { File(p.dir, it).exists() }
            if (discs.size < 2) continue
            runCatching { File(p.dir, p.fileName).writeText(discs.joinToString("\n", postfix = "\n")) }
        }
    }

    private var lastSaved = 0L
    private fun progress(bytes: Long) {
        job = job.copy(bytesDone = job.bytesDone + bytes)
        // Progress is for the eye; save it at most a few times a second.
        val now = System.currentTimeMillis()
        if (now - lastSaved > 250) { lastSaved = now; save(job) }
    }

    private fun set(id: String, state: JobItem.State, note: String? = null) {
        job = job.copy(items = job.items.map { if (it.id == id) it.copy(state = state, note = note) else it })
        save(job)
    }
}
