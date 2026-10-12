package com.ramrom.emumanager

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ramrom.emumanager.data.ConfigChange
import com.ramrom.emumanager.engine.LibraryGame
import com.ramrom.emumanager.engine.LibrarySystem
import com.ramrom.emumanager.engine.config.EsdeLaunch
import com.ramrom.emumanager.engine.config.SettingsProfiles
import com.ramrom.emumanager.engine.importer.SwitchTitleId
import com.ramrom.emumanager.importer.DeviceImportProbe
import com.ramrom.emumanager.knowledge.DeviceDetector
import com.ramrom.emumanager.knowledge.EmulatorProfile
import com.ramrom.emumanager.knowledge.GameSettingsProfile
import com.ramrom.emumanager.knowledge.KnowledgeBundle
import com.ramrom.emumanager.nav.Navigator
import com.ramrom.emumanager.nav.Route
import com.ramrom.emumanager.run.UndoOutcome
import com.ramrom.emumanager.screens.ApplyPreviewScreen
import com.ramrom.emumanager.screens.ChangeHistoryScreen
import com.ramrom.emumanager.screens.GameDetailScreen
import com.ramrom.emumanager.screens.HistoryRow
import com.ramrom.emumanager.screens.PreviewFile
import com.ramrom.emumanager.screens.ProfileCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Which device and chip family profiles are picked for. */
data class DeviceKey(val deviceId: String?, val chipFamily: String)

fun deviceKey(app: EmuApplication, bundle: KnowledgeBundle): DeviceKey {
    // Debug builds can act as another device, for testing its profiles on the emulator:
    // `adb shell run-as <package> sh -c 'echo ayn-thor > files/debug-device'`.
    if (app.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
        val id = runCatching { java.io.File(app.filesDir, "debug-device").readText().trim() }.getOrNull()
        bundle.devices.firstOrNull { it.id == id }?.let { return DeviceKey(it.id, it.chipFamily) }
    }
    val m = DeviceDetector.match(DeviceDetector.readDevice(app), bundle)
    if (m.profile == null) Detection.deviceProfile(app, bundle)?.let { return DeviceKey(it.id, it.chipFamily) }
    return DeviceKey(m.profile?.id, m.chipFamily?.id ?: "generic")
}

/** The emulator a library system plays in: the plan's choice, else the recommendation for this chip. */
fun emulatorFor(bundle: KnowledgeBundle, s: LibrarySystem, chipFamily: String): EmulatorProfile? {
    val id = s.emulatorId ?: bundle.systems.firstOrNull { it.id == s.systemId }?.recommended?.get(chipFamily)
    return bundle.emulators.firstOrNull { it.id == id } ?: bundle.emulators.firstOrNull { s.systemId in it.systems }
}

/** The ID per-game settings are keyed by; only Switch titles so far. Reads a few KB of the file. */
fun gameIdOf(s: LibrarySystem, g: LibraryGame): String? {
    if (s.systemId != "switch") return null
    val file = g.files.firstOrNull { it.endsWith(".nsp", true) || it.endsWith(".xci", true) } ?: return null
    val path = "${g.folderPath.ifEmpty { s.folderPath }}/$file"
    return SwitchTitleId.read(file.substringAfterLast('/'), if (file.endsWith(".nsp", true)) DeviceImportProbe().reader(path) else null)
}

/** The build ES-DE opens [g] in, read from its gamelist; the emulator's main build without ES-DE. */
fun launchOf(app: EmuApplication, bundle: KnowledgeBundle, s: LibrarySystem, g: LibraryGame, emu: EmulatorProfile): EsdeLaunch.Pick {
    val dir = bundle.esde?.paths?.gamelists?.ifEmpty { null }
    val xml = dir?.let { runCatching { java.io.File("$it/${g.folderName.ifEmpty { s.folderName }}/gamelist.xml").readText() }.getOrNull() }
    val label = xml?.let { x -> g.files.firstNotNullOfOrNull { EsdeLaunch.label(x, it) } }
    return EsdeLaunch.packageFor(emu, label, app.settings.installed(emu))
}

private fun GameSettingsProfile.card(bundle: KnowledgeBundle, device: String?) = ProfileCard(
    name = if (gameKey.titleId.isEmpty()) name else "Recommended",
    source = when {
        verifiedOn.isEmpty() -> "Best guess"
        device in verifiedOn -> "Verified on this device"
        else -> "Verified on ${verifiedOn.first().let { id -> bundle.devices.firstOrNull { it.id == id }?.names?.firstOrNull() ?: id }}"
    },
    verdict = verdict.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() },
    tradeoff = tradeoff,
    notes = notes,
    settings = settings.map { it.label.substringBefore(": ", it.label) to (if (": " in it.label) it.label.substringAfter(": ") else it.value) },
)

/** S19 for one library game. */
@Composable
fun GameDetailRoute(app: EmuApplication, nav: Navigator, systemId: String, baseName: String, onToast: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val bundle = app.knowledge.getBundle() ?: return
    val lib = rememberLibrary(app) ?: return
    val s = lib.firstOrNull { it.systemId == systemId } ?: return
    val g = s.games.firstOrNull { it.baseName == baseName } ?: return
    val key = remember { deviceKey(app, bundle) }
    val emu = remember(s) { emulatorFor(bundle, s, key.chipFamily) }
    val gameId by produceState<String?>(null, s, g) { value = withContext(Dispatchers.IO) { gameIdOf(s, g) } }
    val launch by produceState<EsdeLaunch.Pick?>(null, s, g, emu) { value = emu?.let { e -> withContext(Dispatchers.IO) { launchOf(app, bundle, s, g, e) } } }
    val playsIn = when (val l = launch) {
        is EsdeLaunch.Pick.Package -> if (l.pkg.endsWith(".nightly")) "${emu?.name} Nightly" else emu?.name
        is EsdeLaunch.Pick.OtherEmulator -> "${l.name}, chosen in ES-DE"
        else -> emu?.name
    }
    val profiles = remember(gameId, emu) {
        val id = gameId ?: return@remember emptyList()
        emu?.let { SettingsProfiles.forGame(bundle, it.id, id, key.deviceId, key.chipFamily) }.orEmpty()
    }
    var refresh by remember { mutableStateOf(0) }
    val last by produceState<ConfigChange?>(null, gameId, refresh) { value = gameId?.let { app.settings.lastFor(it) } }
    GameDetailScreen(
        title = g.title,
        facts = listOfNotNull(
            "System" to s.name,
            "File" to g.files.joinToString(", ") { it.substringAfterLast('/') },
            if (s.systemId == "switch") "Title ID" to (gameId ?: "Not in the file name or file") else null,
            playsIn?.let { "Plays in" to it },
        ),
        emulator = emu?.name,
        profiles = profiles.map { it.card(bundle, key.deviceId) },
        lastChange = last?.let { "${it.title}, ${DateUtils.getRelativeTimeSpanString(it.at)}" },
        onApply = { i ->
            val e = emu ?: return@GameDetailScreen
            scope.launch {
                val launch = withContext(Dispatchers.IO) { launchOf(app, bundle, s, g, e) }
                app.settings.pending.value = app.settings.plan(e, profiles[i], g.title, gameId, launch)
                nav.push(Route.ApplyPreview)
            }
        },
        onUndoLast = {
            val c = last ?: return@GameDetailScreen
            scope.launch {
                when (val r = app.settings.undo(c)) {
                    UndoOutcome.Restored -> { onToast("Undone"); refresh++ }
                    UndoOutcome.ChangedSince -> nav.push(Route.ChangeHistory(gameId))
                    is UndoOutcome.Failed -> onToast(r.reason)
                }
            }
        },
        onHistory = { nav.push(Route.ChangeHistory(gameId)) },
    )
}

/** S21 for whatever [com.ramrom.emumanager.run.ConfigWriter.pending] holds. */
@Composable
fun ApplyPreviewRoute(app: EmuApplication, nav: Navigator, onToast: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val plan by app.settings.pending.collectAsState()
    val p = plan ?: run { LaunchedEffect(Unit) { nav.pop() }; return }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val launch = remember(p) { app.settings.installed(p.emulator).firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) } }
    ApplyPreviewScreen(
        title = p.title,
        files = p.files.map { f -> PreviewFile(f.appName, f.path, f.rows.map { Triple(it.name, it.before, it.after) }, f.alreadySet, !f.existed) },
        closes = p.closes,
        blocked = p.blocked,
        guided = p.profile.settings.map { it.label.substringBefore(": ", it.label) to (if (": " in it.label) it.label.substringAfter(": ") else it.value) },
        error = error,
        working = working,
        onApply = {
            working = true; error = null
            scope.launch {
                val failed = app.settings.apply(p)
                working = false
                if (failed == null) { app.settings.pending.value = null; onToast("Applied. Undo it from Change history."); nav.pop() } else error = failed
            }
        },
        onOpenApp = launch?.let { i -> { runCatching { context.startActivity(i) } } },
        onCancel = { app.settings.pending.value = null; nav.pop() },
    )
}

/** S22, all changes or one game's. Undo checks the file first; a conflict asks before dropping later edits. */
@Composable
fun ChangeHistoryRoute(app: EmuApplication, gameId: String?) {
    val scope = rememberCoroutineScope()
    val all by app.settings.history().collectAsState(emptyList())
    val changes = all.filter { gameId == null || it.gameId == gameId }
    var conflict by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    fun undo(c: ConfigChange, force: Boolean) = scope.launch {
        message = when (val r = app.settings.undo(c, force)) {
            UndoOutcome.Restored -> { conflict = null; "Undone: ${c.title}" }
            UndoOutcome.ChangedSince -> { conflict = c.id; null }
            is UndoOutcome.Failed -> { conflict = null; r.reason }
        }
    }
    ChangeHistoryScreen(
        rows = changes.map { c ->
            val n = runCatching { JSONObject(c.keysAfter).length() }.getOrDefault(0)
            HistoryRow(c.id, c.title, DateUtils.getRelativeTimeSpanString(c.at).toString(),
                "$n setting${if (n == 1) "" else "s"} · ${c.filePath.substringAfterLast('/')}", c.revertedAt != null)
        },
        conflict = conflict,
        message = message,
        onUndo = { id -> changes.firstOrNull { it.id == id }?.let { undo(it, false) } },
        onUndoAnyway = { changes.firstOrNull { it.id == conflict }?.let { undo(it, true) } },
        onKeep = { conflict = null },
    )
}
