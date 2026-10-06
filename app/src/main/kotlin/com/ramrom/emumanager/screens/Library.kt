package com.ramrom.emumanager.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.ramrom.emumanager.ui.components.SearchField
import com.ramrom.emumanager.ui.components.Segmented
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.ramrom.emumanager.ui.components.ListRow
import com.ramrom.emumanager.ui.components.SectionLabel
import com.ramrom.emumanager.ui.components.Tag
import com.ramrom.emumanager.ui.components.TagKind
import com.ramrom.emumanager.ui.components.TileIcon
import com.ramrom.emumanager.ui.handheld.Face
import com.ramrom.emumanager.ui.input.initialFocus
import com.ramrom.emumanager.ui.layout.PageScaffold
import com.ramrom.emumanager.ui.theme.EmuColors
import com.ramrom.emumanager.ui.theme.EmuType
import androidx.compose.ui.graphics.Color

data class LibrarySystemRow(val systemId: String, val name: String, val detail: String, val tile: Pair<String, Color>, val verdict: Pair<String, Boolean>?)

/** A game in the library-wide search and filter. */
data class LibraryFindRow(val systemId: String, val systemName: String, val baseName: String, val title: String, val playable: Boolean)

/**
 * S18 Library: systems with game counts, a search across every game and a "can't open" filter.
 * [rows] null = scanning.
 */
@Composable
fun ColumnScope.LibraryBody(
    rows: List<LibrarySystemRow>?,
    onOpen: (String) -> Unit,
    onImport: () -> Unit,
    games: List<LibraryFindRow> = emptyList(),
    onOpenGame: (String, String) -> Unit = { _, _ -> },
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(0) }
    when {
        rows == null -> EmptyState(Face.LookDown, "Looking for your games", "One moment.")
        rows.isEmpty() -> EmptyState(Face.Look, "No games yet", "Import the games you own from Downloads or any folder, or put them in ROMs yourself, one folder per system (ROMs/snes, ROMs/ps2...).", "Import games", onImport)
        else -> {
            val focus = initialFocus(rows.size)
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                BasicText("${games.size.takeIf { it > 0 } ?: rows.sumOf { it.detail.substringBefore(' ').toIntOrNull() ?: 0 }} games", Modifier.weight(1f), style = EmuType.Headline2)
                com.ramrom.emumanager.ui.components.EmuButton("Import games", onImport, small = true)
            }
            if (games.isNotEmpty()) SearchField(query, { query = it }, "Search your games", compact = true, style = EmuType.Body)
            val broken = games.count { !it.playable }
            if (broken > 0 && query.isBlank()) Segmented(listOf("By system", "Can't open ($broken)"), filter, { filter = it })
            val found = when {
                query.isNotBlank() -> games.filter { com.ramrom.emumanager.engine.Library.matches(it.title, query) }
                filter == 1 && broken > 0 -> games.filter { !it.playable }
                else -> null
            }
            if (found != null) {
                SectionLabel(if (query.isNotBlank()) "Matches" else "Can't open", found.size)
                if (found.isEmpty()) BasicText("Nothing called that in your games.", style = EmuType.Body)
                found.forEachIndexed { i, g ->
                    ListRow(g.title, if (i == 0) Modifier.focusRequester(focus) else Modifier, detail = g.systemName,
                        onClick = { onOpenGame(g.systemId, g.baseName) }) {
                        if (!g.playable) Tag("Can't open", TagKind.Caveat)
                    }
                }
                if (filter == 1 && query.isBlank()) BasicText("The emulator chosen for these systems doesn't open these files. Their system page says which formats it takes.", style = EmuType.Hint)
                return
            }
            SectionLabel("Systems", rows.size)
            rows.forEachIndexed { i, r ->
                ListRow(r.name, if (i == 0) Modifier.focusRequester(focus) else Modifier, detail = r.detail,
                    leading = { TileIcon(r.tile.first, r.tile.second, 34.dp) }, onClick = { onOpen(r.systemId) }) {
                    r.verdict?.let { (label, great) -> Tag(label, if (great) TagKind.Done else TagKind.Neutral) }
                }
            }
        }
    }
}


data class LibraryGameRow(val title: String, val files: String, val playable: Boolean, val cover: ImageBitmap?, val baseName: String = "", val tuned: Boolean = false)

/** One system's games. */
@Composable
fun LibrarySystemScreen(name: String, emulator: String?, games: List<LibraryGameRow>, onOpen: (String) -> Unit = {}) {
    PageScaffold(name, hints = listOf("B" to "Back")) {
        BasicText(listOfNotNull("${games.size} game${if (games.size == 1) "" else "s"}", emulator?.let { "plays in $it" }).joinToString("  ·  "), style = EmuType.Body)
        games.forEach { g ->
            ListRow(g.title, detail = g.files, monoDetail = true, onClick = { onOpen(g.baseName) }, leading = {
                Box(Modifier.size(36.dp, 48.dp).clip(RoundedCornerShape(6.dp)).background(EmuColors.Hairline), contentAlignment = Alignment.Center) {
                    g.cover?.let { Image(it, null, Modifier.size(36.dp, 48.dp), contentScale = ContentScale.Crop) }
                }
            }) {
                if (g.tuned) Tag("Tuned settings", TagKind.Done)
                if (!g.playable) Tag("Can't open", TagKind.Caveat)
            }
        }
    }
}
