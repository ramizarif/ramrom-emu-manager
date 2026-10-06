package com.ramrom.emumanager.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
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

/** S18 Library: systems with game counts. [rows] null = scanning. */
@Composable
fun ColumnScope.LibraryBody(rows: List<LibrarySystemRow>?, onOpen: (String) -> Unit, onImport: () -> Unit) {
    when {
        rows == null -> EmptyState(Face.LookDown, "Looking for your games", "One moment.")
        rows.isEmpty() -> EmptyState(Face.Look, "No games yet", "Import the games you own from Downloads or any folder, or put them in ROMs yourself, one folder per system (ROMs/snes, ROMs/ps2...).", "Import games", onImport)
        else -> {
            val focus = initialFocus(rows.size)
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                BasicText("${rows.sumOf { it.detail.substringBefore(' ').toIntOrNull() ?: 0 }} games", Modifier.weight(1f), style = EmuType.Headline2)
                com.ramrom.emumanager.ui.components.EmuButton("Import games", onImport, small = true)
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
