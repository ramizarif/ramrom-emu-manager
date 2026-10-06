package com.ramrom.emumanager.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ramrom.emumanager.ui.components.ButtonKind
import com.ramrom.emumanager.ui.components.EmuButton
import com.ramrom.emumanager.ui.components.KeyValues
import com.ramrom.emumanager.ui.components.ListRow
import com.ramrom.emumanager.ui.components.ProgressBar
import com.ramrom.emumanager.ui.components.SectionLabel
import com.ramrom.emumanager.ui.components.Tag
import com.ramrom.emumanager.ui.components.TagKind
import com.ramrom.emumanager.ui.components.TileIcon
import com.ramrom.emumanager.ui.input.ScreenKeys
import com.ramrom.emumanager.ui.input.initialFocus
import com.ramrom.emumanager.ui.layout.PageScaffold
import com.ramrom.emumanager.ui.theme.EmuType
import androidx.compose.ui.graphics.Color

/** One app on S32. */
data class AppRow(
    val id: String,
    val name: String,
    val tile: Pair<String, Color>,
    val systems: String,
    val inPlan: Boolean,
    val installed: String?,
    /** e.g. "Latest v0.2.1", "Checking…", null when we don't track it. */
    val latest: String?,
    /** True: we know a newer build exists. */
    val updateAvailable: Boolean,
    val source: String,
    val issues: Int,
    /** Install progress 0..1, or null. */
    val installing: Float?,
    val installError: String?,
)

/** S32 Apps, inside the Apps section. */
@Composable
fun ColumnScope.AppsBody(rows: List<AppRow>, checking: Boolean, onCheck: () -> Unit, onOpen: (String) -> Unit) {
    val focus = initialFocus(rows.size)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            BasicText("Your apps", style = EmuType.Headline2)
            BasicText("${rows.count { it.installed != null }} installed" + rows.count { it.updateAvailable }.let { if (it > 0) "  ·  $it update${if (it == 1) "" else "s"}" else "" }, style = EmuType.Body)
        }
        EmuButton(if (checking) "Checking…" else "Check for updates", onCheck, small = true, focusRequester = focus)
    }
    Spacer(Modifier.height(4.dp))
    rows.groupBy { it.inPlan }.toSortedMap(compareByDescending { it }).forEach { (inPlan, list) ->
        SectionLabel(if (inPlan) "In your setup" else "Also on this device", list.size)
        list.forEach { r ->
            ListRow(
                r.name,
                detail = r.installError ?: listOfNotNull(r.systems.ifEmpty { null }, r.installed ?: "not installed", r.latest).joinToString("  ·  "),
                leading = { TileIcon(r.tile.first, r.tile.second, 34.dp) },
                onClick = { onOpen(r.id) },
            ) {
                when {
                    r.installing != null -> Column(Modifier.width(90.dp), horizontalAlignment = Alignment.End) {
                        BasicText("${(r.installing * 100).toInt()}%", style = EmuType.Hint); ProgressBar(r.installing)
                    }
                    r.installed == null -> Tag("Not installed", TagKind.Caveat)
                    r.updateAvailable -> Tag("Update", TagKind.YourTurn)
                    r.issues > 0 -> Tag("${r.issues} to fix", TagKind.Caveat)
                    else -> Tag("OK", TagKind.Done)
                }
            }
        }
    }
}

/** S33 App detail. */
@Composable
fun AppDetailScreen(
    row: AppRow,
    facts: List<Pair<String, String>>,
    paths: List<Pair<String, String>>,
    note: String?,
    primary: Pair<String, () -> Unit>?,
    onOpenApp: (() -> Unit)?,
    onUninstall: (() -> Unit)?,
) {
    val focus = initialFocus(row.id, row.installed)
    ScreenKeys(start = primary?.second, x = onOpenApp)
    PageScaffold(row.name, hints = listOfNotNull(primary?.let { "START" to it.first }, onOpenApp?.let { "X" to "Open" }, "B" to "Back")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TileIcon(row.tile.first, row.tile.second, 56.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                BasicText(row.name, style = EmuType.Headline2)
                BasicText(listOfNotNull(row.installed?.let { "Installed $it" } ?: "Not installed", row.latest).joinToString("  ·  "), style = EmuType.Body)
            }
        }
        row.installing?.let { Column { BasicText("Installing… ${(it * 100).toInt()}%", style = EmuType.Hint); ProgressBar(it) } }
        row.installError?.let { BasicText(it, style = EmuType.Body) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (primary != null) EmuButton(primary.first, primary.second, kind = ButtonKind.Go, focusRequester = focus)
            if (onOpenApp != null) EmuButton("Open ${row.name}", onOpenApp, focusRequester = if (primary == null) focus else null)
            if (onUninstall != null) EmuButton("Uninstall", onUninstall, kind = ButtonKind.Ghost)
        }
        SectionLabel("About")
        KeyValues(facts, mono = false)
        if (paths.isNotEmpty()) { SectionLabel("Where things live"); KeyValues(paths) }
        note?.let { BasicText(it, style = EmuType.Hint) }
    }
}
