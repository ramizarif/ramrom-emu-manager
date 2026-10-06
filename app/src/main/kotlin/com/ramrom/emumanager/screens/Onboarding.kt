package com.ramrom.emumanager.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.ramrom.emumanager.ui.components.ButtonKind
import com.ramrom.emumanager.ui.components.EmuButton
import com.ramrom.emumanager.ui.components.Eyebrow
import com.ramrom.emumanager.ui.components.OptionCard
import com.ramrom.emumanager.ui.components.Promise
import com.ramrom.emumanager.ui.components.Tag
import com.ramrom.emumanager.ui.components.TagKind
import com.ramrom.emumanager.ui.components.TileIcon
import com.ramrom.emumanager.ui.handheld.Face
import com.ramrom.emumanager.ui.handheld.HandheldState
import com.ramrom.emumanager.ui.handheld.HandheldTile
import com.ramrom.emumanager.ui.layout.GuidedScaffold
import com.ramrom.emumanager.ui.theme.EmuColors
import com.ramrom.emumanager.ui.theme.EmuType

/** S01. The handheld is asleep; one button wakes it. */
@Composable
fun WelcomeScreen(onWake: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    GuidedScaffold(
        handheld = HandheldState(awake = false),
        hints = listOf("A" to "Wake it up"),
        actions = { EmuButton("Wake it up", onWake, kind = ButtonKind.Go, focusRequester = focus) },
    ) {
        BasicText("Let's bring your handheld to life.", style = EmuType.Display)
        Spacer(Modifier.height(12.dp))
        BasicText("I'll set up ES-DE and your emulators, then help with the few things only you can do.", style = EmuType.Lead)
        Spacer(Modifier.height(12.dp))
        Promise("Your games stay yours.", "This app works with games and BIOS you already own, and never provides them.")
    }
}

/** What S02 shows about this device; produced by device detection (P01). */
data class DeviceSummary(
    val title: String,
    val detail: String,
    val facts: List<String>,
    val kind: Kind,
) {
    enum class Kind { Verified, BestGuess, Phone }
}

/** S02. Verified device waves hello; unknown handhelds get a best guess; phones go to planning. */
@Composable
fun DeviceScreen(device: DeviceSummary, onConfirm: () -> Unit, onChoose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val verified = device.kind == DeviceSummary.Kind.Verified
    val phone = device.kind == DeviceSummary.Kind.Phone
    GuidedScaffold(
        handheld = HandheldState(awake = !phone, face = if (verified) Face.Joy else Face.Look),
        bubble = when (device.kind) {
            DeviceSummary.Kind.Verified -> "Hi! Nice to meet you."
            DeviceSummary.Kind.BestGuess -> "I'm new here!"
            DeviceSummary.Kind.Phone -> "I'll be waiting!"
        },
        hints = listOf("A" to "Select", "B" to "Back"),
        actions = {
            EmuButton(if (verified) "Yes, that's it" else "Continue", onConfirm, kind = ButtonKind.Go, focusRequester = focus)
            EmuButton("Choose another", onChoose)
        },
    ) {
        Eyebrow(if (phone) "Planning mode" else "Your device")
        Spacer(Modifier.height(8.dp))
        BasicText(device.title, style = EmuType.Headline)
        Spacer(Modifier.height(8.dp))
        BasicText(device.detail, style = EmuType.Detail)
        if (device.facts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            BasicText(device.facts.joinToString("  ·  "), style = EmuType.Body)
        }
        Spacer(Modifier.height(12.dp))
        Row {
            when (device.kind) {
                DeviceSummary.Kind.Verified -> Tag("Verified device", TagKind.Done)
                DeviceSummary.Kind.BestGuess -> Tag("Best-guess settings", TagKind.Neutral)
                DeviceSummary.Kind.Phone -> Tag("Plan here, set up there", TagKind.Neutral)
            }
        }
        Spacer(Modifier.height(10.dp))
        BasicText(
            when (device.kind) {
                DeviceSummary.Kind.Verified -> "Settings marked Verified were tested on this exact model."
                DeviceSummary.Kind.BestGuess -> "Recommendations come from devices with the same chip. They're usually right, and you can change anything later."
                DeviceSummary.Kind.Phone -> "Plan your setup here and send it to your handheld when it arrives."
            },
            style = EmuType.Body,
        )
    }
}

/** Found by the quick read-only scan on S03 (P03 lite). */
data class ExistingSetup(val frontendInstalled: Boolean, val emulatorTiles: List<HandheldTile>)

enum class SetupPath { Adopt, SetUp, PlanOther, Restore }

/** S03. The scan's result decides which option is recommended and focused. */
@Composable
fun ChoosePathScreen(existing: ExistingSetup?, restoreUnlocked: Boolean, onOpenPlanFile: () -> Unit, onPick: (SetupPath) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val found = existing != null && (existing.frontendInstalled || existing.emulatorTiles.isNotEmpty())
    val count = existing?.emulatorTiles?.size ?: 0
    com.ramrom.emumanager.ui.input.ScreenKeys(y = onOpenPlanFile)
    GuidedScaffold(
        handheld = HandheldState(awake = true, face = if (found) Face.LookDown else Face.Happy, tiles = existing?.emulatorTiles.orEmpty()),
        bubble = if (found) "I already have some emulators!" else null,
        hints = listOf("A" to "Select", "Y" to "Open a plan file", "B" to "Back"),
        listHeavy = true,
    ) {
        BasicText("What would you like to do?", style = EmuType.Headline)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            val adopt = @Composable { m: Modifier ->
                OptionCard(
                    "I already have a setup",
                    if (found) "We found ${if (existing!!.frontendInstalled) "ES-DE and " else ""}$count emulator${if (count == 1) "" else "s"}. See what's set up, fix what isn't."
                    else "See what's on this device and fix what isn't.",
                    { onPick(SetupPath.Adopt) }, m,
                    icon = { TileIcon("⌕", EmuColors.Mint, 38.dp) },
                ) { if (found) Tag("Best fit", TagKind.YourTurn) }
            }
            val setUp = @Composable { m: Modifier ->
                OptionCard("Set up this device", "Pick a frontend and emulators; we do the rest.", { onPick(SetupPath.SetUp) }, m,
                    icon = { TileIcon("✦", EmuColors.YourTurn, 38.dp) })
            }
            if (found) { adopt(Modifier.focusRequester(focus)); setUp(Modifier) } else { setUp(Modifier.focusRequester(focus)); adopt(Modifier) }
            OptionCard("Plan for another device", "Make a plan here, send it to your handheld.", { onPick(SetupPath.PlanOther) },
                icon = { TileIcon("→", EmuColors.Tangerine, 38.dp) })
            OptionCard("Restore from backup", null, { onPick(SetupPath.Restore) }, dimmed = !restoreUnlocked,
                icon = { TileIcon("↺", EmuColors.InkSoft, 38.dp) }) { if (!restoreUnlocked) Tag("Unlock", TagKind.Lock) }
            EmuButton("I have a plan file", onOpenPlanFile, small = true, kind = ButtonKind.Ghost)
        }
    }
}

/** S02 picker: every known device plus a catch-all by chip. Shown in a sheet. */
@Composable
fun ColumnScope.DevicePickerContent(devices: List<Pair<String, String>>, onPick: (String?) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BasicText("Which handheld is this?", style = EmuType.Headline2)
    Column(Modifier.androidxVerticalScroll(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        devices.forEachIndexed { i, (id, name) ->
            OptionCard(name, null, { onPick(id) }, if (i == 0) Modifier.focusRequester(focus) else Modifier,
                icon = { TileIcon("◧", listOf(EmuColors.Berry, EmuColors.Sky, EmuColors.Tangerine, EmuColors.Grape)[i % 4], 38.dp) })
        }
        OptionCard("Another Snapdragon handheld", "Recommendations by chip, marked as best guesses.", { onPick(null) },
            icon = { TileIcon("?", EmuColors.InkSoft, 38.dp) })
    }
}

@Composable
private fun Modifier.androidxVerticalScroll(): Modifier =
    this.then(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()))
