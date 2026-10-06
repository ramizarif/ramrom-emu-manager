package com.ramrom.emumanager

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ramrom.emumanager.engine.Inventory
import com.ramrom.emumanager.engine.Plan
import com.ramrom.emumanager.engine.install.Versions
import com.ramrom.emumanager.knowledge.EmulatorProfile
import com.ramrom.emumanager.knowledge.KnowledgeBundle
import com.ramrom.emumanager.knowledge.SourceType
import com.ramrom.emumanager.run.DeviceInventory
import com.ramrom.emumanager.run.InstallState
import com.ramrom.emumanager.run.Latest
import com.ramrom.emumanager.screens.AppDetailScreen
import com.ramrom.emumanager.screens.AppRow
import com.ramrom.emumanager.screens.AppsBody
import kotlinx.coroutines.launch

/** What S32/S33 read from the device; refreshed whenever they come to the front. */
private class AppsData(val inventory: Inventory, val plan: Plan?)

@Composable
private fun rememberAppsData(app: EmuApplication): AppsData? {
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<AppsData?>(null) }
    val installs by app.apps.installs.collectAsState()
    val refresh = {
        scope.launch {
            val bundle = app.knowledge.getBundle() ?: return@launch
            data = AppsData(DeviceInventory(app, bundle, app.access).scan(), app.runs.lastPlan())
        }
    }
    LifecycleResumeEffect(Unit) { refresh(); onPauseOrDispose { } }
    // A finished install changes what's on the device.
    androidx.compose.runtime.LaunchedEffect(installs.values.count { it == InstallState.Done }) { refresh() }
    return data
}

private fun sourceLabel(e: EmulatorProfile) = when (e.source.type) {
    SourceType.GITHUB_RELEASE, SourceType.FORGEJO_RELEASE, SourceType.DIRECT_URL -> "We install and update it"
    SourceType.OFFICIAL_PAGE -> "From its official site"
    SourceType.PLAY_STORE -> "From Google Play"
    SourceType.USER_OBTAINS -> "You get this one"
    SourceType.COMMUNITY_PORT -> "Community port"
}

private fun rows(app: EmuApplication, bundle: KnowledgeBundle, data: AppsData, latest: Map<String, Latest>, installs: Map<String, InstallState>): List<AppRow> {
    val planned = data.plan?.systems?.map { it.emulatorId }.orEmpty().toSet()
    val issues = app.health.report.value?.issues.orEmpty()
    return bundle.emulators.filter { e -> e.id in planned || e.packages.any { it in data.inventory.installedPackages } }.mapIndexed { i, e ->
        val installed = data.inventory.installedApps.firstOrNull { it.packageId in e.packages }
        val l = latest[e.id]
        val newer = (l as? Latest.Known)?.let { installed?.let { inst -> Versions.isNewer(inst.versionName, it.apk.version) } } == true
        val inst = installs[e.id]
        AppRow(
            e.id, e.name, e.name.filter { it.isLetterOrDigit() }.take(2).replaceFirstChar { it.uppercase() } to Detection.tileColor(i),
            e.systems.mapNotNull { s -> bundle.systems.firstOrNull { it.id == s }?.name }.let { if (it.size > 3) "${it.size} systems" else it.joinToString(", ") },
            e.id in planned, installed?.versionName?.ifBlank { "installed" },
            when (l) { is Latest.Known -> "latest ${l.apk.version}"; Latest.Checking -> "checking…"; else -> null },
            newer, sourceLabel(e),
            issues.count { iss -> iss.fix.toString().contains(e.id) || e.packages.any { iss.fix.toString().contains(it) } },
            (inst as? InstallState.Working)?.progress, (inst as? InstallState.Failed)?.reason,
        )
    }.sortedWith(compareByDescending<AppRow> { it.inPlan }.thenBy { it.name.lowercase() })
}

/** S32 inside the Apps section. */
@Composable
fun ColumnScope.AppsSection(app: EmuApplication, onOpen: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val bundle = app.knowledge.getBundle() ?: return
    val data = rememberAppsData(app) ?: return
    val latest by app.apps.latest.collectAsState()
    val installs by app.apps.installs.collectAsState()
    var checking by remember { mutableStateOf(false) }
    val list = rows(app, bundle, data, latest, installs)
    AppsBody(list, checking, onCheck = {
        if (!checking) scope.launch { checking = true; app.apps.checkUpdates(bundle.emulators.filter { e -> list.any { it.id == e.id } }); checking = false }
    }, onOpen = onOpen)
}

/** S33 for one app. */
@Composable
fun AppDetailRoute(app: EmuApplication, nav: com.ramrom.emumanager.nav.Navigator, appId: String) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = LocalContext.current
    val bundle = app.knowledge.getBundle() ?: return
    val e = bundle.emulators.firstOrNull { it.id == appId } ?: return
    val data = rememberAppsData(app) ?: return
    val latest by app.apps.latest.collectAsState()
    val installs by app.apps.installs.collectAsState()
    val row = rows(app, bundle, data, latest, installs).firstOrNull { it.id == appId } ?: return
    val pkg = e.packages.firstOrNull { it in data.inventory.installedPackages }
    val frontend = bundle.frontends.firstOrNull()
    val perms = data.inventory.appPermissions.firstOrNull { it.packageId == pkg }
    val auto = com.ramrom.emumanager.engine.install.Releases.releasesUrl(e.source) != null
    val page = e.source.url.takeIf { e.source.type == SourceType.OFFICIAL_PAGE && it.isNotEmpty() }
    val primary: Pair<String, () -> Unit>? = when {
        row.installing != null -> null
        auto && (pkg == null || row.updateAvailable) -> (if (pkg == null) "Install" else "Update") to { app.apps.install(e) }
        auto && pkg != null && latest[appId] is Latest.Known && !row.updateAvailable -> "Install latest" to { app.apps.install(e) }
        page != null -> (if (pkg == null) "Open download page" else "Check its site") to { context.tryStart(Intent(Intent.ACTION_VIEW, Uri.parse(page))) }
        else -> null
    }
    val appData = pkg?.let { "Android/data/$it" }
    val key = remember { deviceKey(app, bundle) }
    val defaults = remember(appId, pkg) { if (pkg == null) emptyList() else com.ramrom.emumanager.engine.config.SettingsProfiles.defaults(bundle, appId, key.deviceId, key.chipFamily) }
    fun local(path: String) = path.replace("{appData}", appData ?: "Android/data/…").removePrefix("/sdcard/").removePrefix("/storage/emulated/0/")
    AppDetailScreen(
        row,
        facts = listOfNotNull(
            "Runs" to row.systems,
            "Source" to row.source,
            if (e.declaresAllFilesAccess && pkg != null) "Files" to if (perms?.grantedManageExternalStorage == true) "Can read all files ✓" else "Can't read your files yet" else null,
            if (e.permissions.isNotEmpty() && pkg != null) "Allowed" to if (perms?.missingPermissions.isNullOrEmpty()) "Everything it asks for ✓" else "Missing: " + perms!!.missingPermissions.joinToString { com.ramrom.emumanager.engine.PermissionNames.of(it) } else null,
        ),
        paths = listOfNotNull(
            e.systems.mapNotNull { frontend?.systemFolders?.get(it) }.take(3).takeIf { it.isNotEmpty() }?.let { "Games" to it.joinToString(", ") { f -> "ROMs/$f" } },
            e.paths.biosDir.takeIf { it.isNotEmpty() }?.let { "BIOS" to local(it) },
            e.paths.configFile.takeIf { it.isNotEmpty() }?.let { "Settings" to local(it) },
        ),
        note = e.caveats.firstOrNull() ?: e.source.note.takeIf { it.isNotBlank() && e.source.type != SourceType.USER_OBTAINS },
        primary = primary,
        onOpenApp = pkg?.let { p -> context.packageManager.getLaunchIntentForPackage(p)?.let { i -> { context.tryStart(i); Unit } } },
        onUninstall = pkg?.let { p -> { context.tryStart(Intent(Intent.ACTION_DELETE, Uri.parse("package:$p"))); Unit } },
        deviceSettings = defaults.map { d -> d.name to d.settings.joinToString(", ") { it.label.substringBefore(": ") } },
        onDeviceSettings = { i ->
            scope.launch {
                app.settings.pending.value = app.settings.plan(e, defaults[i], defaults[i].name, null)
                nav.push(com.ramrom.emumanager.nav.Route.ApplyPreview)
            }
        },
    )
}

private fun Context.tryStart(intent: Intent): Boolean = try { startActivity(intent); true } catch (_: ActivityNotFoundException) { false }
