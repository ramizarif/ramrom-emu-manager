package com.ramrom.emumanager.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramrom.emumanager.ui.components.AlertKind
import com.ramrom.emumanager.ui.components.ButtonKind
import com.ramrom.emumanager.ui.components.EmuButton
import com.ramrom.emumanager.ui.components.InlineAlert
import com.ramrom.emumanager.ui.components.KeyValues
import com.ramrom.emumanager.ui.components.ListRow
import com.ramrom.emumanager.ui.components.SectionLabel
import com.ramrom.emumanager.ui.components.Tag
import com.ramrom.emumanager.ui.components.TagKind
import com.ramrom.emumanager.ui.input.ScreenKeys
import com.ramrom.emumanager.ui.input.initialFocus
import com.ramrom.emumanager.ui.layout.PageScaffold
import com.ramrom.emumanager.ui.theme.EmuColors
import com.ramrom.emumanager.ui.theme.EmuShape
import com.ramrom.emumanager.ui.theme.EmuType

/** A settings profile on S19: what it sets and what it costs. */
data class ProfileCard(
    val name: String,
    val source: String,
    val verdict: String?,
    val tradeoff: String,
    val notes: String,
    val settings: List<Pair<String, String>>,
)

/** S19, the parts that exist so far: identity and the Settings section. */
@Composable
fun GameDetailScreen(
    title: String,
    facts: List<Pair<String, String>>,
    emulator: String?,
    profiles: List<ProfileCard>,
    lastChange: String?,
    onApply: (Int) -> Unit,
    onUndoLast: () -> Unit,
    onHistory: () -> Unit,
) {
    val focus = initialFocus(title, profiles.size)
    ScreenKeys(start = profiles.firstOrNull()?.let { { onApply(0) } }, y = onHistory)
    PageScaffold(title, hints = listOfNotNull(profiles.firstOrNull()?.let { "Start" to "Apply" }, "Y" to "Changes", "B" to "Back")) {
        KeyValues(facts)
        Spacer(Modifier.height(16.dp))
        SectionLabel("Settings")
        if (profiles.isEmpty()) {
            BasicText(
                if (emulator == null) "No emulator is set up for this system yet."
                else "No settings just for this game. It plays with $emulator's settings for this device.",
                style = EmuType.Body,
            )
        }
        profiles.forEachIndexed { i, p ->
            Column(Modifier.fillMaxWidth().background(EmuColors.Surface, RoundedCornerShape(EmuShape.Md)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BasicText(p.name, style = EmuType.Title)
                    Tag(p.source, TagKind.Done)
                    p.verdict?.let { Tag(it, TagKind.Neutral) }
                }
                if (p.tradeoff.isNotEmpty()) BasicText(p.tradeoff, style = EmuType.Body)
                p.settings.forEach { (k, v) ->
                    Row {
                        BasicText(k, Modifier.weight(1f), style = EmuType.Body.copy(fontSize = 14.sp, color = EmuColors.InkSoft))
                        BasicText(v, style = EmuType.Detail.copy(fontSize = 13.sp, color = EmuColors.Ink))
                    }
                }
                if (p.notes.isNotEmpty()) BasicText(p.notes, style = EmuType.Hint)
                Spacer(Modifier.height(4.dp))
                EmuButton("Apply these settings", { onApply(i) }, kind = ButtonKind.Go, small = true, focusRequester = if (i == 0) focus else null)
            }
            Spacer(Modifier.height(10.dp))
        }
        lastChange?.let {
            InlineAlert("Last change", it, AlertKind.Neutral) { EmuButton("Undo", onUndoLast, small = true) }
            Spacer(Modifier.height(10.dp))
        }
        ListRow("Change history", detail = "Everything RamROM changed, with undo", onClick = onHistory)
    }
}

/** One file on S21. */
data class PreviewFile(val app: String, val path: String, val rows: List<Triple<String, String, String>>, val alreadySet: Int, val newFile: Boolean)

/** S21: exactly what will change, before anything is written. */
@Composable
fun ApplyPreviewScreen(
    title: String,
    files: List<PreviewFile>,
    closes: List<String>,
    blocked: String?,
    guided: List<Pair<String, String>>,
    error: String?,
    working: Boolean,
    onApply: () -> Unit,
    onOpenApp: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    val canApply = blocked == null && files.any { it.rows.isNotEmpty() }
    val focus = initialFocus(canApply, blocked)
    ScreenKeys(start = if (canApply && !working) onApply else null)
    PageScaffold("What will change", hints = listOfNotNull(if (canApply) "Start" to "Apply" else null, "B" to "Cancel")) {
        BasicText(title, style = EmuType.Headline)
        Spacer(Modifier.height(10.dp))
        if (blocked != null) {
            InlineAlert("Set these yourself", blocked, AlertKind.YourTurn)
            Spacer(Modifier.height(10.dp))
            guided.forEach { (k, v) ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    BasicText(k, Modifier.weight(1f), style = EmuType.Body)
                    BasicText(v, style = EmuType.Detail.copy(color = EmuColors.Ink))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                onOpenApp?.let { EmuButton("Open the app", it, kind = ButtonKind.Go, focusRequester = focus) }
                EmuButton("Back", onCancel, kind = if (onOpenApp == null) ButtonKind.Go else ButtonKind.Ghost, focusRequester = if (onOpenApp == null) focus else null)
            }
            return@PageScaffold
        }
        if (!canApply) {
            InlineAlert("Already set", "These settings are already in place.", AlertKind.Done)
            Spacer(Modifier.height(12.dp))
            EmuButton("Back", onCancel, kind = ButtonKind.Go, focusRequester = focus)
            return@PageScaffold
        }
        files.filter { it.rows.isNotEmpty() }.forEach { f ->
            SectionLabel(f.app, count = f.rows.size)
            BasicText(f.path.removePrefix("/storage/emulated/0/") + if (f.newFile) "  (new file)" else "", style = EmuType.Detail.copy(color = EmuColors.InkFaint), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            f.rows.forEach { (name, before, after) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText(name, Modifier.weight(1f), style = EmuType.Body)
                    BasicText(before, style = EmuType.Detail.copy(color = EmuColors.InkFaint))
                    BasicText("  →  ", style = EmuType.Detail.copy(color = EmuColors.InkFaint))
                    BasicText(after, style = EmuType.Detail.copy(color = EmuColors.Ink))
                }
            }
            if (f.alreadySet > 0) BasicText("${f.alreadySet} more already set this way.", style = EmuType.Hint)
            Spacer(Modifier.height(12.dp))
        }
        if (closes.isNotEmpty()) {
            InlineAlert("${closes.joinToString(" and ")} will be closed", "It saves its settings on exit, which would undo these. Anything unsaved in a game there is lost.", AlertKind.YourTurn)
            Spacer(Modifier.height(10.dp))
        }
        BasicText("RamROM keeps a copy of each file first, so you can undo this from Change history.", style = EmuType.Hint)
        error?.let { Spacer(Modifier.height(10.dp)); InlineAlert("That didn't work", it) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EmuButton(if (working) "Applying…" else "Apply", { if (!working) onApply() }, kind = ButtonKind.Go, focusRequester = focus)
            EmuButton("Cancel", onCancel, kind = ButtonKind.Ghost)
        }
    }
}

/** One row on S22. */
data class HistoryRow(val id: String, val what: String, val whenText: String, val detail: String, val reverted: Boolean)

/** S22: everything RamROM changed in other apps' files, newest first, each with undo. */
@Composable
fun ChangeHistoryScreen(
    rows: List<HistoryRow>,
    conflict: String?,
    message: String?,
    onUndo: (String) -> Unit,
    onUndoAnyway: () -> Unit,
    onKeep: () -> Unit,
) {
    val focus = initialFocus(rows.size, conflict)
    PageScaffold("Change history", hints = listOf("A" to "Undo", "B" to "Back")) {
        conflict?.let { id ->
            val row = rows.firstOrNull { it.id == id }
            InlineAlert(
                "Changed since",
                "${row?.what ?: "This file"} was changed after RamROM wrote it, probably in the app itself. Undoing now puts back the version from before RamROM's change and drops those later edits.",
                AlertKind.YourTurn,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EmuButton("Keep it as it is", onKeep, kind = ButtonKind.Go, focusRequester = focus)
                EmuButton("Undo anyway", onUndoAnyway, kind = ButtonKind.Ghost)
            }
            Spacer(Modifier.height(12.dp))
        }
        message?.let { InlineAlert(it, null, AlertKind.Done); Spacer(Modifier.height(10.dp)) }
        if (rows.isEmpty()) BasicText("Nothing yet. When RamROM changes an app's settings, it shows up here with a way back.", style = EmuType.Body)
        rows.forEach { r ->
            ListRow(r.what, detail = "${r.whenText}  ·  ${r.detail}", onClick = if (r.reverted) null else ({ onUndo(r.id) })) {
                if (r.reverted) Tag("Undone", TagKind.Neutral) else Tag("Undo", TagKind.YourTurn)
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
