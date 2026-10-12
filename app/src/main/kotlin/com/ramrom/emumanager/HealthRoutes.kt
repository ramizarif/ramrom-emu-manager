package com.ramrom.emumanager

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ramrom.emumanager.access.AccessMode
import com.ramrom.emumanager.access.AccessResult
import com.ramrom.emumanager.access.AppOpMode
import com.ramrom.emumanager.engine.health.FixAction
import com.ramrom.emumanager.engine.health.HealthIssue
import com.ramrom.emumanager.nav.Navigator
import com.ramrom.emumanager.nav.Route
import com.ramrom.emumanager.run.StepText
import com.ramrom.emumanager.screens.HealthScreen
import com.ramrom.emumanager.screens.IssueFix
import com.ramrom.emumanager.screens.IssueScreen
import com.ramrom.emumanager.ui.handheld.HandheldState
import com.ramrom.emumanager.ui.handheld.HandheldTile
import kotlinx.coroutines.launch
import java.io.File

/** Fixes that a setup run can do on its own (installs, grants, folders). */
private fun FixAction.setupCanDo() = this is FixAction.InstallApp || this is FixAction.GrantPermission || this is FixAction.GrantAllFilesAccess || this is FixAction.CreateFolder

/** S14 bound to the device. Re-checks whenever it comes back to the front. */
@Composable
fun HealthRoute(app: EmuApplication, nav: Navigator, onFixWithSetup: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val report by app.health.report.collectAsState()
    val checking by app.health.checking.collectAsState()
    LifecycleResumeEffect(Unit) { scope.launch { runCatching { app.health.check() }.onFailure { app.log.e("health", "check failed", it) } }; onPauseOrDispose { } }
    val bundle = app.knowledge.getBundle()
    val esde = remember(report) { frontendLaunch(context, bundle, report?.plan, report) }
    HealthScreen(
        report = report.takeUnless { checking && it == null },
        handheld = handheldOf(app, report?.plan?.systems?.map { it.emulatorId }.orEmpty()),
        canFixWithSetup = report?.issues.orEmpty().any { it.fix.setupCanDo() },
        frontendName = esde?.name,
        onOpenIssue = { nav.push(Route.Issue(it.id)) },
        onFixWithSetup = onFixWithSetup,
        onRecheck = { scope.launch { app.health.check() } },
        onOpenEsde = { esde?.let { context.tryStart(it.intent) } },
        onDone = onDone,
        onImport = { nav.push(Route.ImportGames) },
    )
}

/** S15 for one issue: the fix right here where we can, otherwise the place to do it. */
@Composable
fun IssueRoute(app: EmuApplication, issueId: String, nav: Navigator, onFixWithSetup: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val report by app.health.report.collectAsState()
    // Keep showing the issue after it's fixed, so "Fixed!" has something to sit on.
    val first = remember(issueId) { report?.issues?.firstOrNull { it.id == issueId } }
    val issue = report?.issues?.firstOrNull { it.id == issueId } ?: first ?: run { LaunchedEffect(Unit) { nav.pop() }; return }
    val resolved = report != null && report!!.issues.none { it.id == issueId }
    LifecycleResumeEffect(issueId) { scope.launch { app.health.check() }; onPauseOrDispose { } }
    val elevated = app.access.mode.collectAsState().value == AccessMode.Elevated
    val recheck = { scope.launch { app.health.check() }; Unit }
    // A missing BIOS: look for it first, so the fix is "copy this one in", not "go and find it".
    val biosFix = (issue.fix as? FixAction.StageBios)?.let { f ->
        var canRead by remember { mutableStateOf(Environment.isExternalStorageManager() || elevated) }
        LifecycleResumeEffect(issueId) { canRead = Environment.isExternalStorageManager() || elevated; onPauseOrDispose { } }
        val hit by produceState<BiosHit?>(null, issueId, canRead) { if (canRead) value = findBios(app, context, f.systemId) }
        when {
            hit?.copy != null -> hit!!.let { h -> IssueFix("Copy it in", { scope.launch {
                val r = com.ramrom.emumanager.run.BiosCopier(context, app.knowledge.getBundle()!!, app.access).copy(h.copy!!)
                if (r !is com.ramrom.emumanager.engine.run.StepResult.Ok) app.log.w("bios", "copy failed: $r")
                recheck()
            } }, value = "Found" to h.label, note = "Is this the one? We copy it; your file stays where it is.") }
            !canRead -> IssueFix("Let RamROM look for it", { context.tryStart(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))) },
                note = "Allow access to all files and we'll find it by fingerprint and copy it in. Nothing leaves your device.")
            hit != null -> IssueFix("I've copied it", recheck, note = "We didn't spot one on this device. Copy one of the files above into the emulator's BIOS folder.")
            else -> IssueFix("Looking…", {}, note = "Checking your storage for it.")
        }
    }
    IssueScreen(issue, biosFix ?: fixFor(issue, context, app, elevated, recheck, onFixWithSetup, nav), handheldOf(app, report?.plan?.systems?.map { it.emulatorId }.orEmpty()), resolved, onBack = { nav.pop() })
}

private fun fixFor(issue: HealthIssue, context: Context, app: EmuApplication, elevated: Boolean, recheck: () -> Unit, setup: () -> Unit, nav: Navigator): IssueFix? {
    val scope = app.scope
    val bundle = app.knowledge.getBundle()
    val frontend = bundle?.esde
    fun settings(action: String, pkg: String) = IssueFix("Open settings", { context.tryStart(Intent(action, Uri.parse("package:$pkg"))) })
    return when (val f = issue.fix) {
        is FixAction.InstallApp -> {
            val emu = bundle?.emulators?.firstOrNull { it.id == f.appId }
            val page = emu?.source?.takeIf { it.type == com.ramrom.emumanager.knowledge.SourceType.OFFICIAL_PAGE }?.url
            when {
                page != null -> IssueFix("Open download page", { context.tryStart(Intent(Intent.ACTION_VIEW, Uri.parse(page))) }, note = "Install it, then come back. We'll check again.")
                f.appId == frontend?.id || emu?.source?.type == com.ramrom.emumanager.knowledge.SourceType.USER_OBTAINS ->
                    IssueFix("I've installed it", recheck, note = "We can't install this one for you. Install it the way you normally would.")
                else -> IssueFix("Install with setup", setup)
            }
        }
        is FixAction.GrantPermission ->
            if (elevated) IssueFix("Allow it", { scope.launch { app.access.access.grantPermission(f.packageId, f.permission); recheck() } })
            else settings(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, f.packageId).copy(value = "Allow" to StepText.permissionName(f.permission))
        is FixAction.GrantAllFilesAccess ->
            if (elevated) IssueFix("Allow it", { scope.launch {
                val uid = context.packageManager.getApplicationInfo(f.packageId, 0).uid
                app.access.access.setAppOp(f.packageId, uid, "MANAGE_EXTERNAL_STORAGE", AppOpMode.ALLOW); recheck()
            } })
            else settings(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, f.packageId).copy(value = "Turn on" to "Allow access to manage all files")
        is FixAction.CreateFolder -> {
            val rel = f.folderPath.substringAfter("/storage/emulated/0/")
            if (Environment.isExternalStorageManager()) IssueFix("Make the folder", { scope.launch(kotlinx.coroutines.Dispatchers.IO) { File(f.folderPath).mkdirs(); recheck() } }, value = "Folder" to rel)
            else IssueFix("Allow file access", { context.tryStart(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))) },
                value = "Folder" to rel, note = "Then come back and we'll make it.")
        }
        is FixAction.GrantFolder -> {
            val folder = frontend?.systemFolders?.get(f.systemId) ?: f.systemId
            IssueFix("Open the app", { context.packageManager.getLaunchIntentForPackage(f.packageId)?.let { context.tryStart(it) } },
                value = "Pick this folder" to "ROMs/$folder", note = "Find its game folder setting and choose the folder above.")
        }
        is FixAction.OpenAppSetup -> {
            val pkg = (bundle?.frontends?.firstOrNull { it.id == f.appId }?.packages ?: bundle?.emulators?.firstOrNull { it.id == f.appId }?.packages).orEmpty()
            val launch = pkg.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
            // A frontend's games folder is its own setting; anything else is a first run to finish.
            if (issue.id.startsWith("frontend_folder_")) launch?.let { IssueFix("Open it", { context.tryStart(it) }, value = "Pick this folder" to "ROMs",
                note = "Find where it asks for your games folder, choose ROMs and allow access. Then come back.") }
            else if (issue.id.startsWith("keys_missing_")) launch?.let { IssueFix("Open it", { context.tryStart(it) },
                note = "Install keys picks a prod.keys file you dumped from your own console. Come back once it's in and we'll check again.") }
            else launch?.let { IssueFix("Open it", { context.tryStart(it) }, note = "Come back when it's done its first-run screens.") }
        }
        is FixAction.StageBios -> IssueFix("I've copied it", recheck, note = "Copy one of the files above into the emulator's BIOS folder. Setup copies it for you when you pick your BIOS folder on the BIOS check.")
        is FixAction.ConfigureInEsde -> {
            val emuId = app.health.report.value?.plan?.systems?.firstOrNull { it.systemId == f.systemId }?.emulatorId
            val emu = bundle?.emulators?.firstOrNull { it.id == emuId }
            emu?.let { e ->
                IssueFix("Set it in ES-DE", { scope.launch { app.settings.setEsdeChoice(f.systemId, e)?.let { app.log.w("health", it) }; recheck() } },
                    note = "ES-DE is closed while we write. Undo from Change history.")
            }
        }
        FixAction.ReconnectAccess -> IssueFix("Connect Shizuku", { nav.push(Route.Access(Route.Health)) })
        else -> null
    }
}

/** What S15 found for a missing BIOS: the copy to make, or none. */
private data class BiosHit(val copy: com.ramrom.emumanager.run.BiosCopy?, val label: String)

private suspend fun findBios(app: EmuApplication, context: Context, systemId: String): BiosHit = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val bundle = app.knowledge.getBundle() ?: return@withContext BiosHit(null, "")
    val emu = app.runs.lastPlan()?.systems?.firstOrNull { it.systemId == systemId }?.emulatorId ?: return@withContext BiosHit(null, "")
    com.ramrom.emumanager.run.BiosCopier(context, bundle, app.access).ensureOwnStorageAccess()
    val folder = runCatching { BiosScan.finder(bundle).find(BiosScan.STORAGE_ROOT, listOf(systemId to emu), limit = 1).firstOrNull() }.getOrNull()
    val found = folder?.results?.firstOrNull()?.status as? com.ramrom.emumanager.engine.BiosCheckStatus.Found ?: return@withContext BiosHit(null, "")
    val target = found.entry.targets.firstOrNull { it.emulatorId == emu } ?: return@withContext BiosHit(null, "")
    BiosHit(com.ramrom.emumanager.run.BiosCopy(Uri.parse(found.file.uri), emu, target.path, target.renameTo.ifEmpty { found.entry.fileName }),
        found.file.path.removePrefix(BiosScan.STORAGE_ROOT + "/"))
}

private fun Context.tryStart(intent: Intent): Boolean = try { startActivity(intent); true } catch (_: ActivityNotFoundException) { false }

private fun handheldOf(app: EmuApplication, emulatorIds: List<String>): HandheldState {
    val names = app.knowledge.getBundle()?.emulators?.associate { it.id to it.name }.orEmpty()
    return HandheldState(awake = true, tiles = emulatorIds.distinct().mapIndexed { i, id ->
        HandheldTile(id, (names[id] ?: id).filter { it.isLetterOrDigit() }.take(2).replaceFirstChar { it.uppercase() }, Detection.tileColor(i))
    })
}
