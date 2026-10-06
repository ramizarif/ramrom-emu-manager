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
    val esde = bundle?.frontends?.firstOrNull()?.packages?.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
    HealthScreen(
        report = report.takeUnless { checking && it == null },
        handheld = handheldOf(app, report?.plan?.systems?.map { it.emulatorId }.orEmpty()),
        canFixWithSetup = report?.issues.orEmpty().any { it.fix.setupCanDo() },
        esdeInstalled = esde != null,
        onOpenIssue = { nav.push(Route.Issue(it.id)) },
        onFixWithSetup = onFixWithSetup,
        onRecheck = { scope.launch { app.health.check() } },
        onOpenEsde = { esde?.let { context.tryStart(it) } },
        onDone = onDone,
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
    IssueScreen(issue, fixFor(issue, context, app, elevated, recheck, onFixWithSetup, nav), handheldOf(app, report?.plan?.systems?.map { it.emulatorId }.orEmpty()), resolved, onBack = { nav.pop() })
}

private fun fixFor(issue: HealthIssue, context: Context, app: EmuApplication, elevated: Boolean, recheck: () -> Unit, setup: () -> Unit, nav: Navigator): IssueFix? {
    val scope = app.scope
    val bundle = app.knowledge.getBundle()
    val frontend = bundle?.frontends?.firstOrNull()
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
            launch?.let { IssueFix("Open it", { context.tryStart(it) }, note = "Come back when it's done its first-run screens.") }
        }
        is FixAction.StageBios -> IssueFix("I've copied it", recheck, note = "Copy one of the files above into the emulator's BIOS folder. Setup copies it for you when you pick your BIOS folder on the BIOS check.")
        FixAction.ReconnectAccess -> IssueFix("Connect Shizuku", { nav.push(Route.Access(Route.Health)) })
        else -> null
    }
}

private fun Context.tryStart(intent: Intent): Boolean = try { startActivity(intent); true } catch (_: ActivityNotFoundException) { false }

private fun handheldOf(app: EmuApplication, emulatorIds: List<String>): HandheldState {
    val names = app.knowledge.getBundle()?.emulators?.associate { it.id to it.name }.orEmpty()
    return HandheldState(awake = true, tiles = emulatorIds.distinct().mapIndexed { i, id ->
        HandheldTile(id, (names[id] ?: id).filter { it.isLetterOrDigit() }.take(2).replaceFirstChar { it.uppercase() }, Detection.tileColor(i))
    })
}
