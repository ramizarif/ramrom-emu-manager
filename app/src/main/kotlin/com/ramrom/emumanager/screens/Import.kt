package com.ramrom.emumanager.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ramrom.emumanager.engine.importer.Confidence
import com.ramrom.emumanager.engine.importer.ImportAction
import com.ramrom.emumanager.engine.importer.ImportItem
import com.ramrom.emumanager.engine.importer.ImportProblem
import com.ramrom.emumanager.importer.ImportDraft
import com.ramrom.emumanager.importer.ImportJob
import com.ramrom.emumanager.importer.JobItem
import com.ramrom.emumanager.importer.SuggestedSource
import com.ramrom.emumanager.ui.components.AlertKind
import com.ramrom.emumanager.ui.components.ButtonKind
import com.ramrom.emumanager.ui.components.EmuButton
import com.ramrom.emumanager.ui.components.InlineAlert
import com.ramrom.emumanager.ui.components.ListRow
import com.ramrom.emumanager.ui.components.ProgressBar
import com.ramrom.emumanager.ui.components.SectionLabel
import com.ramrom.emumanager.ui.components.Segmented
import com.ramrom.emumanager.ui.components.Tag
import com.ramrom.emumanager.ui.components.TagKind
import com.ramrom.emumanager.ui.components.Toggle
import com.ramrom.emumanager.ui.input.ScreenKeys
import com.ramrom.emumanager.ui.input.initialFocus
import com.ramrom.emumanager.ui.layout.PageScaffold
import com.ramrom.emumanager.ui.layout.Sheet
import com.ramrom.emumanager.ui.theme.EmuType

fun bytes(n: Long): String = when {
    n >= 1L shl 30 -> "%.1f GB".format(n / (1L shl 30).toDouble())
    n >= 1L shl 20 -> "%.0f MB".format(n / (1L shl 20).toDouble())
    n == 0L -> "nothing"
    else -> "${maxOf(1, n / 1024)} KB"
}

private fun ImportAction.word() = when (this) {
    ImportAction.Move -> "Move"; ImportAction.Copy -> "Copy"; ImportAction.Extract -> "Unpack"; ImportAction.KeepZipped -> "Keep zipped"
}

private fun ImportAction.doing() = when (this) {
    ImportAction.Move, ImportAction.KeepZipped -> "Moving"; ImportAction.Copy -> "Copying"; ImportAction.Extract -> "Unpacking"
}

/** S23 Import, source and preview. [draft] null with [scanning] false = not scanned yet. */
@Composable
fun ImportScreen(
    draft: ImportDraft?,
    scanning: Boolean,
    canReadFiles: Boolean,
    /** id → name, setup systems first. */
    systems: List<Pair<String, String>>,
    onAllowFiles: () -> Unit,
    onDownloads: () -> Unit,
    onPickFolder: () -> Unit,
    onInclude: (String, Boolean) -> Unit,
    onSystem: (String, String) -> Unit,
    onMove: (Boolean) -> Unit,
    onDeleteArchives: (Boolean) -> Unit,
    onImport: () -> Unit,
    /** Folders with games found by name; null while looking. */
    suggestions: List<SuggestedSource>? = null,
    onUseSource: (SuggestedSource) -> Unit = {},
) {
    var open by remember { mutableStateOf<String?>(null) }
    val plan = draft?.plan
    val ready = plan != null && plan.included.isNotEmpty() && draft.enoughSpace
    val focus = initialFocus(canReadFiles, draft?.source?.path, scanning)
    val name: (String?) -> String = { id -> systems.firstOrNull { it.first == id }?.second ?: id ?: "?" }
    ScreenKeys(start = if (ready) onImport else null, x = if (canReadFiles) onPickFolder else null)
    PageScaffold(
        "Import games",
        hints = listOfNotNull(if (ready) "START" to "Import" else null, "A" to "Change", "X" to "Pick folder", "B" to "Back"),
        overlay = {
            val item = open?.let { id -> plan?.items?.firstOrNull { it.id == id } }
            Sheet(item != null, onDismiss = { open = null }) {
                if (item != null) ItemSheet(item, systems, name, onInclude = { onInclude(item.id, it); open = null }, onSystem = { onSystem(item.id, it); open = null })
            }
        },
    ) {
        if (!canReadFiles) {
            InlineAlert("Let RamROM see your files", "To find games in Downloads and put them in your ROMs folder, RamROM needs access to all files.", AlertKind.YourTurn) {
                EmuButton("Allow access", onAllowFiles, kind = ButtonKind.Go, small = true, focusRequester = focus)
            }
            return@PageScaffold
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Segmented(listOf("Downloads", "Pick a folder"), if (draft?.source?.isDownloads == false) 1 else 0, { if (it == 0) onDownloads() else onPickFolder() })
            Spacer(Modifier.width(12.dp))
            if (draft?.source?.isDownloads == false) BasicText(draft.source.path.removePrefix("/storage/emulated/0/"), style = EmuType.Hint)
        }
        if (scanning || draft == null) { BasicText("Looking through ${draft?.source?.label ?: "Downloads"}…", style = EmuType.Lead); return@PageScaffold }
        val elsewhere = suggestions.orEmpty().filter { !it.dir.path.equals(draft.source.path, ignoreCase = true) }
        if (plan!!.items.isEmpty()) {
            BasicText("No games in ${draft.source.label}", style = EmuType.Headline2)
            val best = elsewhere.firstOrNull()
            if (best == null) {
                BasicText(if (suggestions == null) "Looking for other folders with games…" else "We didn't spot games anywhere else either. Pick the folder you keep them in.", style = EmuType.Body)
                EmuButton("Pick a folder", onPickFolder, focusRequester = focus)
                return@PageScaffold
            }
            InlineAlert("Found games in ${best.dir.label}", "${countOf(best)} there. Is this the folder?", AlertKind.YourTurn) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EmuButton("Yes, look there", { onUseSource(best) }, kind = ButtonKind.Go, small = true, focusRequester = focus)
                    EmuButton("Pick a folder", onPickFolder, small = true)
                }
            }
            OtherSources(elsewhere.drop(1), "Or", onUseSource)
            return@PageScaffold
        }
        val included = plan.included
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText("${included.size} game${if (included.size == 1) "" else "s"} to import", style = EmuType.Headline2)
                BasicText(if (included.isEmpty()) "Nothing to import yet. Pick a system for anything left out, or include it." else "Needs ${bytes(plan.bytesNeeded)}  ·  ${bytes(draft.freeBytes)} free", style = EmuType.Body)
            }
            if (ready) EmuButton("Import", onImport, kind = ButtonKind.Go, focusRequester = focus)
        }
        if (!draft.enoughSpace) InlineAlert("Not enough space", "This needs ${bytes(plan.bytesNeeded)} and the device has ${bytes(draft.freeBytes)} free. Leave some games out or free up space.", AlertKind.Blocked)
        ListRow(if (plan.options.move) "Move files out of ${draft.source.label}" else "Copy files, leave the originals", detail = "Archives are unpacked either way", onClick = { onMove(!plan.options.move) }) { Toggle(plan.options.move) }
        if (included.any { it.action == ImportAction.Extract }) {
            ListRow("Delete archives after unpacking", detail = if (plan.options.deleteArchives) "Frees the space they took" else "Keeps the archive as well", onClick = { onDeleteArchives(!plan.options.deleteArchives) }) { Toggle(plan.options.deleteArchives) }
        }
        val (inList, out) = plan.items.partition { it.included }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (inList.isNotEmpty()) SectionLabel("Importing", inList.size)
            inList.forEach { ItemRow(it, name) { open = it.id } }
            if (out.isNotEmpty()) SectionLabel("Left out", out.size)
            out.forEach { ItemRow(it, name) { open = it.id } }
        }
        if (plan.playlists.isNotEmpty()) BasicText("Multi-disc games go in one folder each, so ES-DE lists them once.", style = EmuType.Hint)
        OtherSources(elsewhere, "Other folders with games", onUseSource)
    }
}

private fun countOf(s: SuggestedSource) = "${s.games} game file${if (s.games == 1) "" else "s"}" + if (s.archives > 0) " and ${s.archives} archive${if (s.archives == 1) "" else "s"}" else ""

@Composable
private fun OtherSources(list: List<SuggestedSource>, label: String, onUse: (SuggestedSource) -> Unit) {
    if (list.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label, list.size)
        list.forEach { s -> ListRow(s.dir.label, detail = countOf(s), onClick = { onUse(s) }) { Tag("Look here", TagKind.Neutral) } }
    }
}

@Composable
private fun ItemRow(item: ImportItem, name: (String?) -> String, onClick: () -> Unit) {
    val confidence = when (item.candidate.confidence) { Confidence.Sure -> null; Confidence.Likely -> "probably"; Confidence.Unknown -> null }
    val where = item.destDir?.substringAfter("/ROMs/")?.let { "ROMs/$it" }
    val detail = item.problems.firstOrNull { it.kind != ImportProblem.Kind.NotSetUp || item.problems.size == 1 }?.text
        ?: listOfNotNull(item.systemId?.let { listOfNotNull(name(it), confidence).joinToString(", ") }, where, bytes(item.candidate.sizeBytes), item.action.word()).joinToString("  ·  ")
    ListRow(item.title, detail = detail, onClick = onClick) {
        when {
            !item.included && item.problems.any { it.kind == ImportProblem.Kind.Bios } -> Tag("BIOS", TagKind.Neutral)
            !item.included && item.problems.any { it.kind == ImportProblem.Kind.NoSystem } -> Tag("Pick system", TagKind.YourTurn)
            !item.included -> Tag("Left out", TagKind.Neutral)
            item.problems.any { it.kind == ImportProblem.Kind.Encrypted || it.kind == ImportProblem.Kind.NotSetUp } -> Tag(name(item.systemId), TagKind.Caveat)
            else -> Tag(name(item.systemId), TagKind.Done)
        }
    }
}

@Composable
private fun ItemSheet(item: ImportItem, systems: List<Pair<String, String>>, name: (String?) -> String, onInclude: (Boolean) -> Unit, onSystem: (String) -> Unit) {
    val focus = initialFocus(item.id)
    BasicText(item.title, style = EmuType.Headline2)
    BasicText(listOfNotNull(item.candidate.reason.ifEmpty { null }, item.candidate.files.size.takeIf { it > 1 }?.let { "$it files" }, bytes(item.candidate.sizeBytes)).joinToString("  ·  "), style = EmuType.Body)
    item.problems.forEach { BasicText(it.text, style = EmuType.Body) }
    val dest = item.destDir
    if (item.outputs.isNotEmpty() && dest != null) BasicText("→ ${dest.substringAfter("/storage/emulated/0/")}/${item.outputs.first()}${if (item.outputs.size > 1) " +${item.outputs.size - 1}" else ""}", style = EmuType.Hint)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (item.included) EmuButton("Leave it out", { onInclude(false) }, focusRequester = focus)
        else if (item.systemId != null) EmuButton("Import it", { onInclude(true) }, kind = ButtonKind.Go, focusRequester = focus)
    }
    SectionLabel(if (item.systemId == null) "Which system is it for?" else "Not ${name(item.systemId)}? Pick another")
    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        systems.chunked(3).forEachIndexed { row, chunk ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chunk.forEachIndexed { i, (id, n) ->
                    EmuButton(n, { onSystem(id) }, Modifier.weight(1f), kind = if (id == item.systemId) ButtonKind.Go else ButtonKind.Secondary, small = true,
                        focusRequester = if (item.systemId == null && row == 0 && i == 0) focus else null)
                }
                repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** S24 Import progress and results. */
@Composable
fun ImportProgressScreen(job: ImportJob, esdeInstalled: Boolean, onCancel: () -> Unit, onOpenEsde: () -> Unit, onLibrary: () -> Unit, onClose: () -> Unit) {
    val focus = initialFocus(job.finished)
    ScreenKeys(start = if (job.finished) onLibrary else null)
    PageScaffold(
        if (job.finished) (if (job.cancelled) "Import stopped" else "Import done") else "Importing",
        hints = if (job.finished) listOf("START" to "Library", "B" to "Close") else listOf("A" to "Cancel", "B" to "Back"),
    ) {
        val done = job.count(JobItem.State.Done); val failed = job.count(JobItem.State.Failed); val skipped = job.count(JobItem.State.Skipped)
        if (!job.finished) {
            val current = job.items.firstOrNull { it.state == JobItem.State.Working }
            BasicText(current?.let { "${it.action.doing()} ${it.title}" } ?: "Getting ready", style = EmuType.Headline2)
            BasicText("${job.items.count { it.state != JobItem.State.Waiting && it.state != JobItem.State.Working } + 1} of ${job.items.size}  ·  ${(job.progress * 100).toInt()}%", style = EmuType.Body)
            ProgressBar(job.progress)
            BasicText("You can leave this screen. The import keeps going and the notification brings you back.", style = EmuType.Hint)
            EmuButton("Cancel", onCancel, kind = ButtonKind.Ghost, focusRequester = focus)
        } else {
            BasicText(listOfNotNull("$done game${if (done == 1) "" else "s"} added", if (failed > 0) "$failed didn't work" else null, if (skipped > 0) "$skipped skipped" else null).joinToString("  ·  "), style = EmuType.Headline2)
            if (done > 0) InlineAlert("Restart ES-DE to see them", "ES-DE reads your game folders when it starts.", AlertKind.Done) {
                if (esdeInstalled) EmuButton("Open ES-DE", onOpenEsde, small = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EmuButton("See your library", onLibrary, kind = ButtonKind.Go, focusRequester = focus)
                EmuButton("Close", onClose, kind = ButtonKind.Ghost)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Games", job.items.size)
            job.items.forEach { i ->
                ListRow(i.title, detail = i.note ?: "ROMs/${i.destDir.substringAfter("/ROMs/")}") {
                    when (i.state) {
                        JobItem.State.Waiting -> Tag("Waiting", TagKind.Neutral)
                        JobItem.State.Working -> Tag("Working", TagKind.YourTurn)
                        JobItem.State.Done -> Tag("Added", TagKind.Done)
                        JobItem.State.Skipped -> Tag("Skipped", TagKind.Neutral)
                        JobItem.State.Failed -> Tag("Didn't work", TagKind.Blocked)
                    }
                }
            }
        }
    }
}
