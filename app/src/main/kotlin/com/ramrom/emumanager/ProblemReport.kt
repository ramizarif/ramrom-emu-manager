package com.ramrom.emumanager

import android.os.Build
import com.ramrom.emumanager.data.DiagnosticsExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * P18's "Report a problem": a plain-text file the user saves and sends themselves. Nothing leaves
 * the device on its own. Game file names are left out (paths under ROMs/ become a count), and so is
 * each issue's evidence, which can name files.
 */
suspend fun problemReport(app: EmuApplication): String = withContext(Dispatchers.IO) {
    val bundle = app.knowledge.getBundle()
    val health = app.health.report.value
    val plan = app.runs.lastPlan()
    val pm = app.packageManager
    val apps = bundle?.let { b -> (b.emulators.flatMap { it.packages } + b.frontends.flatMap { it.packages } + "moe.shizuku.privileged.api").distinct() }.orEmpty()
        .mapNotNull { p -> runCatching { pm.getPackageInfo(p, 0) }.getOrNull()?.let { "$p ${it.versionName}" } }
    DiagnosticsExporter(File(app.filesDir, "logs")).buildRedactedBundle(linkedMapOf(
        "App" to "RamROM Emu Manager ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})${if (BuildConfig.DEBUG) " debug" else ""}",
        "Device" to "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}) · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.SOC_MODEL}",
        "Access" to app.access.mode.value.toString(),
        "Plan" to (plan?.let { p -> "frontends: ${p.frontendIds.joinToString()}\n" + p.systems.joinToString("\n") { "${it.systemId}: ${it.emulatorId}" } } ?: "none"),
        "Health" to (health?.let { h -> "${h.score.percent}% · ${h.readyCount} of ${h.systems.size} systems ready\n" +
            h.issues.joinToString("\n") { "${it.severity} ${it.id}: ${it.title}" } } ?: "not checked yet"),
        "Installed apps" to apps.joinToString("\n"),
    ))
}
