package com.ramrom.emumanager.run

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import com.ramrom.emumanager.access.AccessManager
import com.ramrom.emumanager.access.AccessMode
import com.ramrom.emumanager.access.AccessResult
import com.ramrom.emumanager.access.AppOpMode
import com.ramrom.emumanager.engine.Step
import com.ramrom.emumanager.engine.StepType
import com.ramrom.emumanager.engine.run.StepExecutor
import com.ramrom.emumanager.engine.run.StepResult
import com.ramrom.emumanager.install.AppInstaller
import com.ramrom.emumanager.install.InstallOutcome
import com.ramrom.emumanager.knowledge.KnowledgeBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** A BIOS file the user picked on S09, and where it goes. */
data class BiosCopy(val source: Uri, val emulatorId: String, val destDir: String, val fileName: String)

/**
 * Carries out plan steps on this device (P05's executor). Each step's verify() reads the device's
 * real state, so the runner never takes our word for it.
 */
class DeviceStepExecutor(
    private val context: Context,
    private val bundle: KnowledgeBundle,
    private val access: AccessManager,
    private val installer: AppInstaller,
    /** S09's findings, by system id. */
    private val bios: Map<String, BiosCopy>,
    private val romRoot: String = DEFAULT_ROM_ROOT,
    /** P09's writer and the device the plan is for, for L2 defaults. */
    private val settings: ConfigWriter? = null,
    private val deviceId: String? = null,
    private val chipFamily: String = "generic",
    /** The plan's systems, for ES-DE's per-system emulator choice. */
    private val systems: List<com.ramrom.emumanager.engine.Plan.PlanSystem> = emptyList(),
) : StepExecutor {
    private val pm = context.packageManager

    override suspend fun execute(step: Step, progress: (Float) -> Unit): StepResult = when (step.type) {
        StepType.InstallApp -> install(step.subject, progress)
        StepType.GrantRuntimePermission -> grantPermissions(step.subject)
        StepType.GrantAllFilesAccess -> packageOf(step.subject)?.let { grantAllFiles(it) } ?: notInstalled(step.subject)
        StepType.CreateFolder -> createFolder(step.subject)
        StepType.StageBios -> stageBios(step.subject)
        StepType.WriteFrontendConfig -> frontendChoices(step.subject)
        StepType.WriteEmulatorConfig -> deviceDefaults(step.subject)
        StepType.HealthCheck -> StepResult.Ok
        // Guided types only reach here if the plan marked them automatic; they still need the user.
        else -> StepResult.NeedsUser(step.userFacingText.ifEmpty { "This one needs you." })
    }

    override suspend fun verify(step: Step): Boolean = withContext(Dispatchers.IO) {
        when (step.type) {
            StepType.InstallApp, StepType.GuidedObtainApp, StepType.FirstLaunch -> packageOf(step.subject) != null
            StepType.GrantRuntimePermission -> {
                val pkg = packageOf(step.subject) ?: return@withContext false
                permissionsFor(step.subject).all { pm.checkPermission(it, pkg) == PackageManager.PERMISSION_GRANTED }
            }
            StepType.GrantAllFilesAccess -> packageOf(step.subject)?.let(::hasAllFiles) ?: false
            StepType.CreateFolder -> File(romRoot, step.subject).isDirectory
            StepType.StageBios -> bios[step.subject]?.let { exists(destOf(it)) } ?: false
            // Folder picks and in-app setup can't be read back without elevated dumpsys parsing (P08,
            // next); until then the user's "I did it" stands.
            else -> true
        }
    }

    /**
     * L2 defaults through P09: snapshot, history and undo like any other change. The plan said so
     * before the run started; the app is closed first only if it's open.
     */
    private suspend fun deviceDefaults(appId: String): StepResult {
        val writer = settings ?: return StepResult.Ok
        val emu = bundle.emulators.firstOrNull { it.id == appId } ?: return StepResult.Ok
        for (profile in com.ramrom.emumanager.engine.config.SettingsProfiles.defaults(bundle, appId, deviceId, chipFamily)) {
            val plan = writer.plan(emu, profile, profile.name, null)
            plan.blocked?.let { reason ->
                val values = profile.settings.joinToString(", ") { it.label }
                return StepResult.NeedsUser("$reason Set these in ${emu.name}: $values.")
            }
            // A config the emulator hasn't created yet would be ours alone; let it make its own first.
            if (plan.files.any { !it.existed && it.rows.isNotEmpty() }) return StepResult.NeedsUser("Open ${emu.name} once so it creates its settings, then come back.")
            writer.apply(plan)?.let { return StepResult.Failed(it) }
        }
        return StepResult.Ok
    }

    /**
     * ES-DE's per-system emulator choice, where its default isn't the plan's emulator. A choice that
     * already opens the right emulator (Eden Nightly for Eden) is kept.
     */
    private suspend fun frontendChoices(frontendId: String): StepResult {
        val writer = settings ?: return StepResult.Ok
        if (bundle.frontends.none { it.id == frontendId }) return StepResult.Ok
        for (s in systems) {
            val emu = bundle.emulators.firstOrNull { it.id == s.emulatorId } ?: continue
            writer.setEsdeChoice(s.systemId, emu)?.let { return if (it.startsWith("Install")) StepResult.NeedsUser(it) else StepResult.Failed(it) }
        }
        return StepResult.Ok
    }

    private suspend fun install(appId: String, progress: (Float) -> Unit): StepResult {
        if (packageOf(appId) != null) return StepResult.Ok
        val app = bundle.emulators.firstOrNull { it.id == appId } ?: return StepResult.Failed("Unknown app $appId", retryable = false)
        val apk = try { installer.resolve(app) } catch (e: Exception) { return StepResult.Failed("Couldn't reach ${app.name}'s releases: ${e.message}") }
            ?: return StepResult.Failed("No Android build in ${app.name}'s latest releases.", retryable = false)
        val file = try { installer.download(apk) { progress(it * 0.9f) } } catch (e: Exception) { return StepResult.Failed("Download stopped: ${e.message}") }
        return when (val r = installer.install(file)) {
            InstallOutcome.Installed -> StepResult.Ok
            InstallOutcome.Cancelled -> StepResult.NeedsUser("Android's install prompt was closed. Try again and tap Install.")
            is InstallOutcome.Failed -> StepResult.Failed(r.reason, r.retryable)
        }
    }

    private suspend fun grantPermissions(appId: String): StepResult {
        val pkg = packageOf(appId) ?: return notInstalled(appId)
        for (perm in permissionsFor(appId)) {
            val r = access.access.grantPermission(pkg, perm)
            if (r !is AccessResult.Ok) return StepResult.NeedsUser("Allow ${perm.substringAfterLast('.').lowercase().replace('_', ' ')} for this app in Settings.")
        }
        return StepResult.Ok
    }

    private suspend fun grantAllFiles(pkg: String): StepResult {
        val uid = pm.getApplicationInfo(pkg, 0).uid
        return when (val r = access.access.setAppOp(pkg, uid, "MANAGE_EXTERNAL_STORAGE", AppOpMode.ALLOW)) {
            is AccessResult.Ok -> StepResult.Ok
            else -> StepResult.NeedsUser("Turn on \"Allow access to manage all files\" for this app in Settings.")
        }
    }

    private suspend fun createFolder(folder: String): StepResult = withContext(Dispatchers.IO) {
        ensureOwnStorageAccess()
        val dir = File(romRoot, folder)
        if (dir.isDirectory || dir.mkdirs()) StepResult.Ok else StepResult.Failed("Couldn't create ROMs/$folder")
    }

    private val copier = BiosCopier(context, bundle, access)

    private suspend fun stageBios(systemId: String): StepResult {
        val copy = bios[systemId] ?: return StepResult.NeedsUser("Pick your BIOS folder on the BIOS check.")
        return copier.copy(copy)
    }

    private fun destOf(copy: BiosCopy): File? = copier.destOf(copy)

    /** Android/data can't be listed by us; Shizuku can. Elsewhere a plain check works. */
    private suspend fun exists(file: File?): Boolean {
        file ?: return false
        if ("/Android/data/" !in file.path) return file.exists()
        return (access.access.exists(file.path) as? AccessResult.Ok)?.value == true
    }

    /** Elevated mode may not have asked for all-files access; grant it to ourselves so folders can be made. */
    private suspend fun ensureOwnStorageAccess() { copier.ensureOwnStorageAccess() }

    private fun hasAllFiles(pkg: String): Boolean {
        val uid = runCatching { pm.getApplicationInfo(pkg, 0).uid }.getOrNull() ?: return false
        val ops = context.getSystemService(AppOpsManager::class.java)
        return ops.unsafeCheckOpNoThrow("android:manage_external_storage", uid, pkg) == AppOpsManager.MODE_ALLOWED
    }

    private fun permissionsFor(appId: String) = bundle.emulators.firstOrNull { it.id == appId }?.permissions.orEmpty()

    /** The installed package for an app or frontend id, if any. */
    fun packageOf(id: String): String? {
        val pkgs = bundle.emulators.firstOrNull { it.id == id }?.packages ?: bundle.frontends.firstOrNull { it.id == id }?.packages ?: return null
        return pkgs.firstOrNull { runCatching { pm.getPackageInfo(it, 0) }.isSuccess }
    }

    private fun notInstalled(id: String) = StepResult.NeedsUser("Install ${bundle.emulators.firstOrNull { it.id == id }?.name ?: id} first.")

    companion object {
        /** ES-DE's default ROM directory. P10 reads the real one from es_settings.xml once verified. */
        const val DEFAULT_ROM_ROOT = "/storage/emulated/0/ROMs"
    }
}
