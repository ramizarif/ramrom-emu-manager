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
) : StepExecutor {
    private val pm = context.packageManager

    override suspend fun execute(step: Step, progress: (Float) -> Unit): StepResult = when (step.type) {
        StepType.InstallApp -> install(step.subject, progress)
        StepType.GrantRuntimePermission -> grantPermissions(step.subject)
        StepType.GrantAllFilesAccess -> packageOf(step.subject)?.let { grantAllFiles(it) } ?: notInstalled(step.subject)
        StepType.CreateFolder -> createFolder(step.subject)
        StepType.StageBios -> stageBios(step.subject)
        // ES-DE already knows every emulator in the bundle; nothing to write until a per-system
        // alternative emulator is chosen (verified on the Thor first).
        StepType.WriteFrontendConfig -> StepResult.Ok
        // L2 config profiles are M3's next step; until then the app's defaults stand.
        StepType.WriteEmulatorConfig -> StepResult.Ok
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

    private suspend fun stageBios(systemId: String): StepResult = withContext(Dispatchers.IO) {
        val copy = bios[systemId] ?: return@withContext StepResult.NeedsUser("Pick your BIOS folder on the BIOS check.")
        val bytes = context.contentResolver.openInputStream(copy.source)?.use { it.readBytes() }
            ?: return@withContext StepResult.Failed("Couldn't read the BIOS file again. Pick the folder once more.")
        val dest = destOf(copy) ?: return@withContext StepResult.NeedsUser("Install the emulator first.")
        // Android/data belongs to the emulator: only Shizuku can write there. Shared storage we can.
        if ("/Android/data/" in dest.path) {
            when (val r = access.access.writeFile(dest.path, bytes)) {
                is AccessResult.Ok -> StepResult.Ok
                is AccessResult.Denied -> StepResult.NeedsUser("Copy ${copy.fileName} into the emulator's BIOS folder (connect Shizuku to do this for you).")
                is AccessResult.Failed -> StepResult.Failed(r.message)
            }
        } else {
            ensureOwnStorageAccess(); dest.parentFile?.mkdirs(); dest.writeBytes(bytes); StepResult.Ok
        }
    }

    /** Where a BIOS copy lands; `{appData}` is the emulator's Android/data folder. Null if it isn't installed. */
    private fun destOf(copy: BiosCopy): File? {
        val dir = if ("{appData}" in copy.destDir) {
            val pkg = packageOf(copy.emulatorId) ?: return null
            copy.destDir.replace("{appData}", "${Environment.getExternalStorageDirectory()}/Android/data/$pkg")
        } else copy.destDir
        return File(dir, copy.fileName)
    }

    /** Android/data can't be listed by us; Shizuku can. Elsewhere a plain check works. */
    private suspend fun exists(file: File?): Boolean {
        file ?: return false
        if ("/Android/data/" !in file.path) return file.exists()
        return (access.access.exists(file.path) as? AccessResult.Ok)?.value == true
    }

    /** Elevated mode may not have asked for all-files access; grant it to ourselves so folders can be made. */
    private suspend fun ensureOwnStorageAccess() {
        if (Environment.isExternalStorageManager()) return
        if (access.mode.value == AccessMode.Elevated) {
            val me = context.packageName
            access.access.setAppOp(me, pm.getApplicationInfo(me, 0).uid, "MANAGE_EXTERNAL_STORAGE", AppOpMode.ALLOW)
        }
    }

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
