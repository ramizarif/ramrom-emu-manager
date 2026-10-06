package com.ramrom.emumanager

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ramrom.emumanager.engine.Library
import com.ramrom.emumanager.engine.LibrarySystem
import com.ramrom.emumanager.engine.config.SettingsProfiles
import com.ramrom.emumanager.knowledge.DeviceDetector
import com.ramrom.emumanager.knowledge.Verdict
import com.ramrom.emumanager.knowledge.Verdicts
import com.ramrom.emumanager.run.DeviceInventory
import com.ramrom.emumanager.screens.LibraryBody
import com.ramrom.emumanager.screens.LibraryGameRow
import com.ramrom.emumanager.screens.LibrarySystemRow
import com.ramrom.emumanager.screens.LibrarySystemScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** The library as last scanned; shared by the section and its system pages. */
@Composable
internal fun rememberLibrary(app: EmuApplication): List<LibrarySystem>? {
    val scope = rememberCoroutineScope()
    var lib by remember { mutableStateOf<List<LibrarySystem>?>(null) }
    LifecycleResumeEffect(Unit) {
        scope.launch {
            val bundle = app.knowledge.getBundle() ?: return@launch
            lib = Library.build(bundle, DeviceInventory(app, bundle, app.access).scan(), app.runs.lastPlan())
        }
        onPauseOrDispose { }
    }
    return lib
}

@Composable
fun ColumnScope.LibrarySection(app: EmuApplication, onOpen: (String) -> Unit, onImport: () -> Unit, onOpenGame: (String, String) -> Unit = { _, _ -> }) {
    val lib = rememberLibrary(app)
    val bundle = app.knowledge.getBundle()
    val family = remember { bundle?.let { DeviceDetector.match(DeviceDetector.readDevice(app), it).chipFamily?.id } ?: "generic" }
    LibraryBody(lib?.mapIndexed { i, s ->
        val emu = s.emulatorId?.let { id -> bundle?.emulators?.firstOrNull { it.id == id }?.name }
        val unplayable = s.games.count { !it.playable }
        LibrarySystemRow(s.systemId, s.name,
            listOfNotNull("${s.games.size} game${if (s.games.size == 1) "" else "s"}", emu, if (unplayable > 0) "$unplayable can't open" else null).joinToString("  ·  "),
            com.ramrom.emumanager.screens.cartLabel(s.systemId) to Detection.tileColor(i),
            Verdicts.forSystem(s.systemId, family).let { it.label to (it == Verdict.Great) })
    }, onOpen, onImport,
        games = lib?.flatMap { s -> s.games.map { g -> com.ramrom.emumanager.screens.LibraryFindRow(s.systemId, s.name, g.baseName, g.title, g.playable) } }.orEmpty(),
        onOpenGame = onOpenGame)
}

@Composable
fun LibrarySystemRoute(app: EmuApplication, systemId: String, onOpenGame: (String) -> Unit) {
    val lib = rememberLibrary(app) ?: return
    val s = lib.firstOrNull { it.systemId == systemId } ?: return
    val bundle = app.knowledge.getBundle()
    // ES-DE's scraped covers, when the user has them. Small decodes; a library page shows dozens.
    var covers by remember(s) { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    androidx.compose.runtime.LaunchedEffect(s) {
        covers = withContext(Dispatchers.IO) {
            s.games.mapNotNull { g ->
                val dir = "/storage/emulated/0/ES-DE/downloaded_media/${g.folderName.ifEmpty { s.folderName }}/covers"
                val f = listOf("png", "jpg", "jpeg").map { File(dir, "${g.baseName}.$it") }.firstOrNull { it.exists() } ?: return@mapNotNull null
                runCatching {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeFile(f.path, opts)?.asImageBitmap()
                }.getOrNull()?.let { g.baseName to it }
            }.toMap()
        }
    }
    // Games with settings tuned for this device get a tag; finding out reads a few KB of each file.
    var tuned by remember(s) { mutableStateOf<Set<String>>(emptySet()) }
    androidx.compose.runtime.LaunchedEffect(s) {
        val b = bundle ?: return@LaunchedEffect
        val key = deviceKey(app, b)
        val emu = emulatorFor(b, s, key.chipFamily) ?: return@LaunchedEffect
        tuned = withContext(Dispatchers.IO) {
            s.games.filter { g -> gameIdOf(s, g)?.let { id -> SettingsProfiles.forGame(b, emu.id, id, key.deviceId, key.chipFamily).isNotEmpty() } == true }.map { it.baseName }.toSet()
        }
    }
    LibrarySystemScreen(s.name, s.emulatorId?.let { id -> bundle?.emulators?.firstOrNull { it.id == id }?.name },
        s.games.map { g -> LibraryGameRow(g.title, g.files.joinToString(", ") { it.substringAfterLast('.').lowercase() }.let { if (g.files.size > 1) "${g.files.size} files · $it" else it }, g.playable, covers[g.baseName], g.baseName, g.baseName in tuned) },
        onOpenGame)
}
