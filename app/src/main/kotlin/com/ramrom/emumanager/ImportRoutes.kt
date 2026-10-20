package com.ramrom.emumanager

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ramrom.emumanager.importer.ImportSourceDir
import com.ramrom.emumanager.nav.Navigator
import com.ramrom.emumanager.nav.Route
import com.ramrom.emumanager.screens.ImportProgressScreen
import com.ramrom.emumanager.screens.ImportScreen
import com.ramrom.emumanager.ui.layout.Section
import kotlinx.coroutines.launch

/** "primary:Download/Games" → "/storage/emulated/0/Download/Games"; null for providers that aren't storage. */
private fun treePath(uri: Uri): String? {
    if (uri.authority != "com.android.externalstorage.documents") return null
    val id = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
    val volume = id.substringBefore(':')
    val rest = id.substringAfter(':', "")
    val root = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
    return if (rest.isEmpty()) root else "$root/$rest"
}

/** S23 bound to [ImportSession]. */
@Composable
fun ImportRoute(app: EmuApplication, nav: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val session = app.imports
    val draft by session.draft.collectAsState()
    val scanning by session.scanning.collectAsState()
    val suggestions by session.suggestions.collectAsState()
    var canRead by remember { mutableStateOf(session.canReadFiles()) }
    // Every return (from the folder picker, a file manager) looks again: folders come and go.
    var visits by remember { mutableStateOf(0) }
    LifecycleResumeEffect(Unit) { canRead = session.canReadFiles(); visits++; onPauseOrDispose { } }
    // A fresh visit looks in Downloads; coming back looks at the same folder again and keeps the
    // user's choices, so a download that finished meanwhile shows up.
    LaunchedEffect(canRead) { if (canRead && draft == null && !scanning) session.scan(session.downloads) }
    LaunchedEffect(visits) { if (visits > 0 && canRead) session.refresh() }
    // Meanwhile, look for other folders with games so an empty Downloads isn't a dead end.
    LaunchedEffect(canRead, visits) { if (canRead) session.findSources() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val path = uri?.let(::treePath) ?: return@rememberLauncherForActivityResult
        val name = path.substringAfterLast('/')
        scope.launch { session.scan(ImportSourceDir(path, name, path == session.downloads.path)) }
    }
    val bundle = app.knowledge.getBundle()
    val systems = remember(draft?.setUp) {
        val setUp = draft?.setUp.orEmpty()
        bundle?.systems.orEmpty().filter { it.id != "steam" && it.id != "pc" }
            .sortedWith(compareBy({ it.id !in setUp }, { it.name })).map { it.id to it.name }
    }
    ImportScreen(
        draft, scanning, canRead, systems,
        onAllowFiles = { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))) },
        onDownloads = { scope.launch { session.scan(session.downloads) } },
        onPickFolder = { runCatching { pick.launch(null) } },
        onInclude = session::setIncluded,
        onSystem = session::setSystem,
        onMove = { m -> draft?.let { session.setOptions(it.plan.options.copy(move = m)) } },
        onDeleteArchives = { d -> draft?.let { session.setOptions(it.plan.options.copy(deleteArchives = d)) } },
        onImport = { if (session.start()) nav.replace(Route.ImportProgress) },
        suggestions = suggestions,
        onUseSource = { s -> scope.launch { session.scan(s.dir) } },
    )
}

/** S24, from S23 or the notification. */
@Composable
fun ImportProgressRoute(app: EmuApplication, nav: Navigator) {
    val context = LocalContext.current
    val job by app.imports.job.collectAsState()
    val j = job ?: run { LaunchedEffect(Unit) { nav.pop() }; return }
    val health = app.health.report.collectAsState().value
    val esde = remember(health) { frontendLaunch(context, app.knowledge.getBundle(), health?.plan, health) }
    val frontends = remember(health) { app.knowledge.getBundle()?.let { b -> health?.plan?.frontends(b)?.map { it.name } }.orEmpty() }
    ImportProgressScreen(
        j, esde?.name, frontends,
        onCancel = app.imports::cancel,
        onOpenEsde = { esde?.let { context.startActivity(it.intent) } },
        onLibrary = { app.imports.dismiss(); nav.reset(Route.Main(Section.Library)) },
        onClose = { app.imports.dismiss(); nav.pop() },
    )
}
