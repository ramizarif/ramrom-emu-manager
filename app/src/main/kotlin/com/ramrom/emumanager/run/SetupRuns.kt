package com.ramrom.emumanager.run

import android.content.Context
import android.net.Uri
import com.ramrom.emumanager.EmuApplication
import com.ramrom.emumanager.engine.BiosSummary
import com.ramrom.emumanager.engine.CompiledPlan
import com.ramrom.emumanager.engine.Inventory
import com.ramrom.emumanager.engine.Plan
import com.ramrom.emumanager.engine.PlanCompiler
import com.ramrom.emumanager.engine.run.JsonRunStore
import com.ramrom.emumanager.engine.run.WizardRunner
import com.ramrom.emumanager.install.AppInstaller
import com.ramrom.emumanager.knowledge.KnowledgeBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** A run in progress: the compiled plan, its runner, and the executor S13 asks about apps. */
class ActiveRun(
    val plan: CompiledPlan,
    val runner: WizardRunner,
    val executor: DeviceStepExecutor,
    val bundle: KnowledgeBundle,
    internal val scope: CoroutineScope,
)

/**
 * Owns the one setup run (P05). It lives in the application, not a screen, so leaving S12 doesn't
 * stop it; P19's service keeps the process alive. The plan input and BIOS picks are saved next to
 * the runner's own file, so a run killed mid-way comes back when the app next starts.
 */
class SetupRuns(private val app: EmuApplication) {
    private val _active = MutableStateFlow<ActiveRun?>(null)
    val active: StateFlow<ActiveRun?> = _active.asStateFlow()
    private val dir get() = File(app.filesDir, "runs")
    private val recordFile get() = File(dir, "current.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /** Start a run of [plan]. Replaces any run already going. */
    suspend fun start(plan: Plan, bios: Map<String, BiosCopy>): ActiveRun? {
        val bundle = app.knowledge.getBundle() ?: return null
        stop(forget = true)
        val runId = "run-${System.currentTimeMillis()}"
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            recordFile.writeText(json.encodeToString(RunRecord.serializer(), RunRecord.of(runId, plan, bios)))
        }
        return launch(runId, plan, bios, bundle)
    }

    /** Pick up a run the process lost. No-op when there isn't one or it's already loaded. */
    suspend fun resumeIfAny(): ActiveRun? {
        _active.value?.let { return it }
        val bundle = app.knowledge.getBundle() ?: return null
        val record = withContext(Dispatchers.IO) {
            recordFile.takeIf { it.exists() }?.let { f -> runCatching { json.decodeFromString(RunRecord.serializer(), f.readText()) }.getOrNull() }
        } ?: return null
        return launch(record.runId, record.plan(), record.biosCopies(), bundle)
    }

    /** Stop scheduling. [forget] also drops the saved run, e.g. when it finished or the user started over. */
    suspend fun stop(forget: Boolean) {
        val run = _active.value
        _active.value = null
        run?.scope?.cancel()
        SetupRunService.stop(app)
        if (forget) withContext(Dispatchers.IO) {
            run?.let { File(dir, "${it.runner.runId}.json").delete() }
            recordFile.delete()
        }
    }

    private suspend fun launch(runId: String, plan: Plan, bios: Map<String, BiosCopy>, bundle: KnowledgeBundle): ActiveRun {
        val compiled = PlanCompiler().compile(plan, bundle, inventory(app, bundle), app.access.capabilities.value)
        val executor = DeviceStepExecutor(app, bundle, app.access, AppInstaller(app, app.access), bios)
        val runner = WizardRunner(compiled, executor, JsonRunStore(dir, compiled), runId)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        runner.start(scope)
        return ActiveRun(compiled, runner, executor, bundle, scope).also {
            _active.value = it
            SetupRunService.start(app)
        }
    }

    companion object {
        /** What's already here, so the compiler doesn't plan work that's done. */
        fun inventory(context: Context, bundle: KnowledgeBundle): Inventory {
            val pm = context.packageManager
            val installed = (bundle.emulators.flatMap { it.packages } + bundle.frontends.flatMap { it.packages })
                .filter { runCatching { pm.getPackageInfo(it, 0) }.isSuccess }.toSet()
            val folders = bundle.frontends.flatMap { it.systemFolders.values }.distinct()
                .filter { File(DeviceStepExecutor.DEFAULT_ROM_ROOT, it).isDirectory }.toSet()
            return Inventory(installedPackages = installed, existingFolders = folders)
        }
    }
}

/** The run's inputs as saved on disk. */
@Serializable
private data class RunRecord(
    val runId: String,
    val targetDeviceId: String? = null,
    val chipFamily: String,
    val frontendId: String? = null,
    val systems: List<Pair<String, String>>,
    val biosFound: Set<String> = emptySet(),
    val biosMissing: Set<String> = emptySet(),
    val biosWrong: Set<String> = emptySet(),
    val biosCopies: List<SavedBios> = emptyList(),
    val hasBios: Boolean = false,
) {
    @Serializable data class SavedBios(val system: String, val uri: String, val emulatorId: String, val destDir: String, val fileName: String)

    fun plan() = Plan(targetDeviceId, chipFamily, frontendId, systems.map { Plan.PlanSystem(it.first, it.second) },
        if (hasBios) BiosSummary(biosFound, biosMissing, biosWrong) else null)

    fun biosCopies() = biosCopies.associate { it.system to BiosCopy(Uri.parse(it.uri), it.emulatorId, it.destDir, it.fileName) }

    companion object {
        fun of(runId: String, p: Plan, bios: Map<String, BiosCopy>) = RunRecord(
            runId, p.targetDeviceId, p.chipFamily, p.frontendId, p.systems.map { it.systemId to it.emulatorId },
            p.bios?.found.orEmpty(), p.bios?.missing.orEmpty(), p.bios?.wrongVersion.orEmpty(),
            bios.map { (s, c) -> SavedBios(s, c.source.toString(), c.emulatorId, c.destDir, c.fileName) },
            hasBios = p.bios != null,
        )
    }
}
