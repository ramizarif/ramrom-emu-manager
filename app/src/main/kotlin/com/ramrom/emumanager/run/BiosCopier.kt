package com.ramrom.emumanager.run

import android.content.Context
import android.os.Environment
import com.ramrom.emumanager.access.AccessManager
import com.ramrom.emumanager.access.AccessMode
import com.ramrom.emumanager.access.AccessResult
import com.ramrom.emumanager.access.AppOpMode
import com.ramrom.emumanager.engine.run.StepResult
import com.ramrom.emumanager.knowledge.KnowledgeBundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Puts a BIOS file where its emulator looks; shared by setup runs (P05) and Health's fix (S15). */
class BiosCopier(private val context: Context, private val bundle: KnowledgeBundle, private val access: AccessManager) {
    private val pm = context.packageManager

    suspend fun copy(copy: BiosCopy): StepResult = withContext(Dispatchers.IO) {
        val bytes = runCatching { context.contentResolver.openInputStream(copy.source)?.use { it.readBytes() } }.getOrNull()
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
    fun destOf(copy: BiosCopy): File? {
        val dir = if ("{appData}" in copy.destDir) {
            val pkg = packageOf(copy.emulatorId) ?: return null
            copy.destDir.replace("{appData}", "${Environment.getExternalStorageDirectory()}/Android/data/$pkg")
        } else copy.destDir
        return File(dir, copy.fileName)
    }

    /** True when we can read shared storage ourselves; in elevated mode we grant it to ourselves. */
    suspend fun ensureOwnStorageAccess(): Boolean {
        if (Environment.isExternalStorageManager()) return true
        if (access.mode.value == AccessMode.Elevated) {
            val me = context.packageName
            access.access.setAppOp(me, pm.getApplicationInfo(me, 0).uid, "MANAGE_EXTERNAL_STORAGE", AppOpMode.ALLOW)
        }
        return Environment.isExternalStorageManager()
    }

    private fun packageOf(id: String): String? {
        val pkgs = bundle.emulators.firstOrNull { it.id == id }?.packages ?: return null
        return pkgs.firstOrNull { runCatching { pm.getPackageInfo(it, 0) }.isSuccess }
    }
}
