package com.ramrom.emumanager.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.ramrom.emumanager.ui.components.AlertKind
import com.ramrom.emumanager.ui.components.ButtonKind
import com.ramrom.emumanager.ui.components.EmuButton
import com.ramrom.emumanager.ui.components.InlineAlert
import com.ramrom.emumanager.ui.components.JourneyTrack
import com.ramrom.emumanager.ui.components.ListRow
import com.ramrom.emumanager.ui.components.OptionCard
import com.ramrom.emumanager.ui.components.SectionLabel
import com.ramrom.emumanager.ui.components.Tag
import com.ramrom.emumanager.ui.components.TagKind
import com.ramrom.emumanager.ui.components.TileIcon
import com.ramrom.emumanager.ui.components.Toggle
import com.ramrom.emumanager.ui.handheld.Face
import com.ramrom.emumanager.ui.handheld.HandheldState
import com.ramrom.emumanager.ui.handheld.HandheldTile
import com.ramrom.emumanager.ui.input.ScreenKeys
import com.ramrom.emumanager.ui.layout.GuidedScaffold
import com.ramrom.emumanager.ui.layout.Sheet
import com.ramrom.emumanager.ui.theme.EmuColors
import com.ramrom.emumanager.ui.theme.EmuType

/** The plan has its own four-step track (brief G2–G11 §2). */
@Composable
private fun PlanTrack(n: Int, name: String) = JourneyTrack(n, 4, "Plan · $name")

/** One frontend on S06. [note] says where it comes from when it isn't installed. */
data class FrontendChoice(val id: String, val name: String, val blurb: String, val tile: String, val installed: Boolean, val chosen: Boolean, val note: String)

/**
 * S06 Frontend choice. Any number, all set up alike; none means just emulators. ES-DE is paid and
 * RetroHrai! comes from the Play Store, so those two the user installs; Cocoon and iiSU we can.
 */
@Composable
fun FrontendScreen(choices: List<FrontendChoice>, onToggle: (String) -> Unit, onNone: () -> Unit, onNext: () -> Unit) {
    val focus = com.ramrom.emumanager.ui.input.initialFocus()
    ScreenKeys(start = onNext)
    val chosen = choices.filter { it.chosen }
    val missing = chosen.filter { !it.installed }
    GuidedScaffold(
        handheld = HandheldState(
            awake = true,
            face = if (missing.isNotEmpty()) Face.Look else Face.Happy,
            tiles = chosen.filter { it.installed }.map { HandheldTile(it.id, it.tile, EmuColors.Ink) },
            ghosts = missing.size,
        ),
        bubble = when {
            chosen.isEmpty() -> "Just emulators? Sure!"
            chosen.size > 1 -> "${chosen.size} frontends! I'll set up each."
            missing.isEmpty() -> "${chosen[0].name}! Great pick."
            else -> "I'll keep a spot for it."
        },
        hints = listOf("A" to "Choose", "START" to "Next", "B" to "Back"),
        listHeavy = true,
        actions = { EmuButton("Next", onNext, kind = ButtonKind.Go) },
    ) {
        PlanTrack(1, "Frontend")
        Spacer(Modifier.height(10.dp))
        BasicText("Pick your frontends", style = EmuType.Headline)
        Spacer(Modifier.height(4.dp))
        BasicText("Choose as many as you use. Each one gets set up.", style = EmuType.Body)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            choices.forEachIndexed { i, c ->
                OptionCard(c.name, c.blurb, { onToggle(c.id) }, if (i == 0) Modifier.focusRequester(focus) else Modifier,
                    icon = { TileIcon(c.tile, EmuColors.Ink, 38.dp) }) {
                    // One tag: chosen says it all; an unchosen one still shows it's on the device.
                    if (c.chosen) Tag("Chosen", TagKind.Done) else if (c.installed) Tag("Installed", TagKind.Neutral)
                }
            }
            OptionCard("No frontend", "Just emulators. You open each one to play.", onNone,
                icon = { TileIcon("◧", EmuColors.InkSoft, 38.dp) }) { if (chosen.isEmpty()) Tag("Chosen", TagKind.Done) }
        }
        Spacer(Modifier.height(12.dp))
        if (missing.isNotEmpty()) InlineAlert(
            "${missing.joinToString(" and ") { it.name }} ${if (missing.size == 1) "isn't" else "aren't"} installed yet",
            missing.joinToString(" ") { it.note } + " Setup connects ${if (missing.size == 1) "it" else "them"} once installed.",
            AlertKind.YourTurn,
        )
        else BasicText("You can change this any time.", style = EmuType.Body)
    }
}

/** One system row as S07 shows it. */
data class SystemChoice(
    val systemId: String,
    val systemName: String,
    val maker: String,
    val emulatorId: String,
    val emulatorName: String,
    val tileLabel: String,
    val reason: String,
    val on: Boolean,
    val biosNeeded: Boolean,
    val installed: Boolean,
    val alternatives: List<EmulatorOption>,
)

data class EmulatorOption(val id: String, val name: String, val description: String, val recommended: Boolean, val caveat: String?)

/** S07 Systems & emulators. Toggling a system drops its cartridge and emulator tile into the handheld. */
@Composable
fun SystemsScreen(
    systems: List<SystemChoice>,
    bestGuess: Boolean,
    familyName: String?,
    changing: SystemChoice?,
    onToggle: (String) -> Unit,
    onChangeEmulator: (String) -> Unit,
    onPickEmulator: (String, String) -> Unit,
    onCloseSheet: () -> Unit,
    onCheckGames: () -> Unit,
    onNext: () -> Unit,
) {
    val focus = com.ramrom.emumanager.ui.input.initialFocus()
    // X opens the emulator sheet for whichever row has focus.
    var focused by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    ScreenKeys(start = onNext, y = onCheckGames, x = { focused?.let { id -> if ((systems.firstOrNull { it.systemId == id }?.alternatives?.size ?: 0) > 1) onChangeEmulator(id) } })
    val on = systems.filter { it.on }
    val tiles = on.distinctBy { it.emulatorId }.mapIndexed { i, s -> HandheldTile(s.emulatorId, s.tileLabel, accent(i)) }
    Box(Modifier.fillMaxSize()) {
        GuidedScaffold(
            handheld = HandheldState(awake = true, face = Face.LookDown, tiles = tiles, carts = on.take(3).map { cartLabel(it.systemId) }, moreCarts = (on.size - 3).coerceAtLeast(0)),
            hints = listOf("A" to "On/off", "X" to "Change emulator", "Y" to "Check games", "START" to "Next", "B" to "Back"),
            listHeavy = true,
            banner = if (bestGuess) { { Box(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) { InlineAlert(if (familyName == null) "These are general picks; we couldn't tell your chip." else "Recommendations are best guesses for $familyName.", "Change anything you like.", AlertKind.Neutral) } } } else null,
            actions = { EmuButton("Next", onNext, kind = ButtonKind.Go) },
        ) {
            PlanTrack(2, "Systems")
            Spacer(Modifier.height(10.dp))
            BasicText("Pick your systems", style = EmuType.Headline)
            Spacer(Modifier.height(8.dp))
            systems.groupBy { it.maker }.entries.forEachIndexed { gi, (maker, list) ->
                SectionLabel(maker)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    list.forEachIndexed { i, s ->
                        val m = (if (gi == 0 && i == 0) Modifier.focusRequester(focus) else Modifier)
                            .onFocusChanged { if (it.isFocused) focused = s.systemId }
                        SystemRow(s, m, onToggle = { onToggle(s.systemId) }, onChange = { onChangeEmulator(s.systemId) })
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Sheet(changing != null, onDismiss = onCloseSheet) {
            if (changing != null) {
                val sheetFocus = com.ramrom.emumanager.ui.input.initialFocus(changing.systemId)
                BasicText("Emulator for ${changing.systemName}", style = EmuType.Headline2)
                changing.alternatives.forEach { o ->
                    OptionCard(o.name, listOfNotNull(o.description, o.caveat).joinToString(" "), { onPickEmulator(changing.systemId, o.id) },
                        if (o.id == changing.emulatorId) Modifier.focusRequester(sheetFocus) else Modifier) {
                        if (o.recommended) Tag("Recommended", TagKind.Done)
                        if (o.id == changing.emulatorId) Tag("Chosen", TagKind.Neutral)
                    }
                }
            }
        }
    }
}

@Composable
private fun SystemRow(s: SystemChoice, modifier: Modifier, onToggle: () -> Unit, onChange: () -> Unit) {
    ListRow(
        s.systemName, modifier,
        detail = "${s.emulatorName} · ${s.reason}",
        leading = { TileIcon(s.tileLabel, if (s.on) accentFor(s.emulatorId) else EmuColors.Hairline) },
        onClick = onToggle,
    ) {
        if (s.biosNeeded) Tag("BIOS", TagKind.Neutral)
        if (s.installed) Tag("Installed", TagKind.Done)
        if (s.alternatives.size > 1) EmuButton("Change", onChange, small = true, kind = ButtonKind.Ghost)
        Toggle(s.on)
    }
}

private val accents = listOf(EmuColors.Berry, EmuColors.Sky, EmuColors.Tangerine, EmuColors.Grape, EmuColors.YourTurn, EmuColors.Sunshine, EmuColors.Mint)
private fun accent(i: Int) = accents[i % accents.size]
private fun accentFor(id: String) = accents[(id.hashCode() and 0x7fffffff) % accents.size]

/** Short cartridge labels in mono. */
fun cartLabel(systemId: String) = when (systemId) {
    "switch" -> "nsw"; "gc" -> "gc"; "wii" -> "wii"; "n3ds" -> "3ds"; "nds" -> "nds"; "psx", "ps1" -> "ps1"; "ps2" -> "ps2"; "psp" -> "psp"
    else -> systemId.take(3)
}

/** Counts S10 shows; produced by the plan compiler's dry run (P04). */
data class PlanSummary(
    val apps: Int,
    val appsAuto: Int,
    val biosFound: Int,
    val biosNeeded: Int,
    val biosNote: String?,
    val steps: Int,
    val stepsNeedYou: Int,
    val minutes: Int,
    val limited: Boolean,
    val savedMinutesIfConnected: Int,
)

/** S10 Plan review. Start is hidden when planning for another device; Export becomes the main action. */
@Composable
fun PlanReviewScreen(
    summary: PlanSummary,
    handheld: HandheldState,
    otherDevice: String?,
    onStart: () -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit,
    onConnect: () -> Unit,
) {
    val focus = com.ramrom.emumanager.ui.input.initialFocus()
    ScreenKeys(start = if (otherDevice == null) onStart else onExport, x = onExport)
    GuidedScaffold(
        handheld = handheld.copy(awake = otherDevice == null),
        bubble = if (otherDevice == null) "This is going to be fun!" else "See you on the $otherDevice!",
        hints = if (otherDevice == null) listOf("START" to "Start setup", "X" to "Export", "B" to "Edit") else listOf("A" to "Export", "B" to "Edit"),
        listHeavy = true,
        actions = {
            if (otherDevice == null) {
                EmuButton("Start setup", onStart, kind = ButtonKind.Go, focusRequester = focus)
                EmuButton("Save", onSave)
                EmuButton("Export", onExport, kind = ButtonKind.Ghost)
            } else {
                EmuButton("Export plan file", onExport, kind = ButtonKind.Go, focusRequester = focus)
                EmuButton("Save", onSave)
            }
        },
    ) {
        PlanTrack(4, "Review")
        Spacer(Modifier.height(10.dp))
        BasicText(if (otherDevice == null) "Your plan" else "Your plan for an $otherDevice", style = EmuType.Headline)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Apps", "${summary.apps}", if (summary.limited) "you approve each install" else "${summary.appsAuto} install on their own", Modifier.weight(1f))
                StatTile("BIOS", if (summary.biosNeeded == 0) "None" else "${summary.biosFound} of ${summary.biosNeeded}", summary.biosNote ?: "nothing needed", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Steps", "${summary.steps}", "${summary.stepsNeedYou} need you", Modifier.weight(1f))
                StatTile("Your time", "${summary.minutes} min", "hands-on, roughly", Modifier.weight(1f))
            }
        }
        if (otherDevice != null) {
            Spacer(Modifier.height(10.dp))
            BasicText("Open the file on your $otherDevice in RamROM Emu Manager to start.", style = EmuType.Body)
        } else if (summary.limited && summary.savedMinutesIfConnected > 0) {
            Spacer(Modifier.height(10.dp))
            InlineAlert("Connecting Shizuku saves about ${summary.savedMinutesIfConnected} min.", null, AlertKind.YourTurn) {
                EmuButton("Connect", onConnect, small = true)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, detail: String, modifier: Modifier) {
    com.ramrom.emumanager.ui.components.FocusCard(null, modifier, radius = 18.dp) {
        Column(Modifier.fillMaxWidth()) {
            com.ramrom.emumanager.ui.components.Eyebrow(label)
            BasicText(value, style = EmuType.Headline.copy(fontSize = androidx.compose.ui.unit.TextUnit(28f, androidx.compose.ui.unit.TextUnitType.Sp)))
            BasicText(detail, style = EmuType.Body.copy(fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp)))
        }
    }
}

/** S11 Import a plan. Shows how the plan reads on this device before anything is used. */
@Composable
fun ImportPlanScreen(
    import: com.ramrom.emumanager.PlanImport?,
    tiles: List<HandheldTile>,
    error: String?,
    onAccept: () -> Unit,
    onEdit: () -> Unit,
    onPickAgain: () -> Unit,
    onCancel: () -> Unit,
) {
    val focus = com.ramrom.emumanager.ui.input.initialFocus(import, error)
    ScreenKeys(start = if (import != null) onAccept else null)
    GuidedScaffold(
        handheld = HandheldState(
            awake = true, face = when { error != null -> Face.Worried; import?.changes?.isNotEmpty() == true -> Face.Look; else -> Face.Happy },
            tiles = tiles, carts = import?.systems.orEmpty().take(3).map { cartLabel(it.systemId) },
            moreCarts = ((import?.systems?.size ?: 0) - 3).coerceAtLeast(0),
        ),
        bubble = when { error != null -> "Hmm, I can't read that one."; import?.changes?.isEmpty() == true -> "It fits me already!"; else -> "A few tweaks for me." },
        hints = if (import != null) listOf("START" to "Accept", "A" to "Select", "B" to "Cancel") else listOf("A" to "Select", "B" to "Cancel"),
        listHeavy = import?.changes?.isNotEmpty() == true,
        actions = {
            if (import != null) {
                EmuButton("Accept", onAccept, kind = ButtonKind.Go, focusRequester = focus)
                EmuButton("Edit", onEdit)
                EmuButton("Cancel", onCancel, kind = ButtonKind.Ghost)
            } else {
                EmuButton("Choose another file", onPickAgain, kind = ButtonKind.Go, focusRequester = focus)
                EmuButton("Cancel", onCancel, kind = ButtonKind.Ghost)
            }
        },
    ) {
        com.ramrom.emumanager.ui.components.Eyebrow("Plan file")
        Spacer(Modifier.height(8.dp))
        if (import == null) {
            BasicText("This plan can't be opened", style = EmuType.Headline)
            Spacer(Modifier.height(12.dp))
            InlineAlert(error ?: "The file isn't a plan.", "Plans end in .emuplan and come from RamROM Emu Manager's Export.", AlertKind.Blocked)
            return@GuidedScaffold
        }
        BasicText("Made for the ${import.madeFor}", style = EmuType.Headline)
        Spacer(Modifier.height(8.dp))
        BasicText(
            "${import.systems.size} system${if (import.systems.size == 1) "" else "s"}${if (import.frontendNames.isNotEmpty()) " with ${import.frontendNames}" else ""}. " +
                if (import.changes.isEmpty()) "Nothing needs changing for this device." else "${import.changes.size} change${if (import.changes.size == 1) "" else "s"} for this device:",
            style = EmuType.Lead,
        )
        if (import.changes.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                import.changes.forEach { c ->
                    ListRow(c.systemName, detail = listOfNotNull(c.from, c.to).joinToString("  →  ").ifEmpty { null }?.let { "$it · ${c.why}" } ?: c.why)
                }
            }
        }
    }
}

/** One row of S09's matrix. */
data class BiosRow(val systemName: String, val state: BiosRowState, val detail: String)
enum class BiosRowState { Found, WrongVersion, Missing, Optional, InApp, NotChecked }

/** A folder S09 found on its own, for the user to confirm. */
data class BiosGuess(val label: String, val covers: String, val matched: Boolean)

/** S09's own search: running, what it found (best first; empty = nothing), or not allowed to look. */
data class BiosSearchState(val searching: Boolean, val guesses: List<BiosGuess>?, val canSearch: Boolean)

/** S09 BIOS check. Names what's expected, never where to get it. */
@Composable
fun BiosCheckScreen(
    rows: List<BiosRow>,
    scanning: Boolean,
    folderPicked: Boolean,
    error: String?,
    onPickFolder: () -> Unit,
    onNext: () -> Unit,
    search: BiosSearchState = BiosSearchState(false, null, false),
    onUseGuess: (Int) -> Unit = {},
    onAllowSearch: () -> Unit = {},
) {
    val guess = search.guesses?.firstOrNull()?.takeIf { !folderPicked }
    val focus = com.ramrom.emumanager.ui.input.initialFocus(folderPicked, scanning, search.searching, guess)
    ScreenKeys(start = onNext, x = onPickFolder)
    val found = rows.count { it.state == BiosRowState.Found }
    val needed = rows.count { it.state in setOf(BiosRowState.Found, BiosRowState.WrongVersion, BiosRowState.Missing, BiosRowState.NotChecked) }
    GuidedScaffold(
        handheld = HandheldState(awake = true, face = when {
            scanning || search.searching -> Face.LookDown
            guess != null -> Face.Happy
            !folderPicked -> Face.Look
            found == needed -> Face.Joy
            else -> Face.Look
        }),
        bubble = when {
            scanning -> "Checking your files..."
            search.searching -> "Looking around..."
            guess != null -> "Is it this one?"
            !folderPicked -> if (needed == 0) "No BIOS needed!" else "Show me where they are."
            needed == 0 -> "Nothing else needed."
            found == needed -> "All there!"
            else -> "$found of $needed so far."
        },
        hints = listOf("A" to "Select", "X" to if (folderPicked) "Pick another folder" else "Pick folder", "START" to "Next", "B" to "Back"),
        listHeavy = true,
        actions = {
            if (guess != null && needed > 0) {
                EmuButton("Yes, use it", { onUseGuess(0) }, kind = ButtonKind.Go, focusRequester = focus)
                EmuButton("Pick another folder", onPickFolder)
            } else if (!folderPicked && needed > 0) {
                if (!search.canSearch && !search.searching) EmuButton("Look for them", onAllowSearch, kind = ButtonKind.Go, focusRequester = focus)
                EmuButton("Pick folder", onPickFolder, kind = if (search.canSearch) ButtonKind.Go else ButtonKind.Secondary, focusRequester = if (search.canSearch) focus else null)
                EmuButton("Skip for now", onNext)
            } else {
                EmuButton("Next", onNext, kind = ButtonKind.Go, focusRequester = focus)
                if (needed > 0) EmuButton("Pick another folder", onPickFolder)
            }
        },
    ) {
        PlanTrack(3, "BIOS")
        Spacer(Modifier.height(10.dp))
        BasicText(when {
            folderPicked -> "Your BIOS files"
            guess != null -> if (guess.matched) "Found your BIOS files" else "Is this your BIOS folder?"
            search.searching -> "Looking for your BIOS files"
            else -> "Pick the folder with your BIOS files"
        }, style = EmuType.Headline)
        Spacer(Modifier.height(8.dp))
        when {
            error != null -> InlineAlert("Can't read that folder.", error, AlertKind.Blocked)
            scanning -> BasicText("Checking files by their fingerprint, not their name.", style = EmuType.Body)
            search.searching -> BasicText("Checking your storage for files the size of a BIOS, then their fingerprints. Nothing leaves your device.", style = EmuType.Body)
            guess != null -> {
                InlineAlert("In ${guess.label}", "${if (guess.matched) "Covers " else "Has "}${guess.covers}. Is this the folder you keep them in?", AlertKind.YourTurn)
                search.guesses.drop(1).forEachIndexed { i, g ->
                    Spacer(Modifier.height(6.dp))
                    ListRow("Or ${g.label}", detail = g.covers, onClick = { onUseGuess(i + 1) })
                }
            }
            !folderPicked && needed > 0 && search.guesses?.isEmpty() == true ->
                InlineAlert("We didn't spot any BIOS files.", "Pick the folder you keep them in, or skip and add them later.", AlertKind.YourTurn)
            !folderPicked && needed > 0 && !search.canSearch ->
                BasicText("We can look for them ourselves if you let RamROM read your files, or you can pick the folder. Files are matched by fingerprint, and nothing leaves your device.", style = EmuType.Body)
            !folderPicked && needed > 0 -> BasicText("Files are matched by fingerprint, so names don't matter. Nothing leaves your device.", style = EmuType.Body)
            folderPicked && needed > 0 && found == 0 -> InlineAlert("No matching BIOS files in this folder.", "Try the folder you keep them in, or skip and add them later.", AlertKind.YourTurn)
            else -> {}
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            rows.forEach { r ->
                ListRow(r.systemName, detail = r.detail, leading = {
                    TileIcon(
                        when (r.state) { BiosRowState.Found -> "✓"; BiosRowState.WrongVersion -> "!"; BiosRowState.Missing -> "×"; BiosRowState.NotChecked -> "?"; else -> "–" },
                        when (r.state) { BiosRowState.Found -> EmuColors.Mint; BiosRowState.WrongVersion -> EmuColors.Sunshine; BiosRowState.Missing -> EmuColors.Berry; else -> EmuColors.Hairline },
                    )
                }) {
                    when (r.state) {
                        BiosRowState.Found -> Tag("Found", TagKind.Done)
                        BiosRowState.WrongVersion -> Tag("Not recognised", TagKind.Caveat)
                        BiosRowState.Missing -> Tag("Missing", TagKind.Blocked)
                        BiosRowState.Optional -> Tag("Optional", TagKind.Neutral)
                        BiosRowState.InApp -> Tag("Set up in the app", TagKind.Neutral)
                        BiosRowState.NotChecked -> Tag("Needed", TagKind.Neutral)
                    }
                }
            }
        }
    }
}
