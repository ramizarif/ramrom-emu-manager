package com.ramrom.emumanager.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ramrom.emumanager.ui.components.ButtonKind
import com.ramrom.emumanager.ui.components.EmuButton
import com.ramrom.emumanager.ui.components.ListRow
import com.ramrom.emumanager.ui.components.Promise
import com.ramrom.emumanager.ui.components.SectionLabel
import com.ramrom.emumanager.ui.components.Tag
import com.ramrom.emumanager.ui.components.TagKind
import com.ramrom.emumanager.ui.handheld.Face
import com.ramrom.emumanager.ui.handheld.HandheldState
import com.ramrom.emumanager.ui.handheld.LivingHandheld
import com.ramrom.emumanager.ui.input.ScreenKeys
import com.ramrom.emumanager.ui.layout.PageScaffold
import com.ramrom.emumanager.ui.layout.Section
import com.ramrom.emumanager.ui.layout.ToolScaffold
import com.ramrom.emumanager.ui.theme.EmuType
import com.ramrom.emumanager.ui.theme.LocalSizeClass
import com.ramrom.emumanager.ui.theme.SizeClass

/** Empty state: the handheld, a headline, one line, one action. */
@Composable
fun ColumnScope.EmptyState(face: Face, title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    val focus = com.ramrom.emumanager.ui.input.initialFocus(action)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LivingHandheld(HandheldState(awake = true, face = face), Modifier.size(250.dp, 180.dp))
        BasicText(title, style = EmuType.Headline2)
        BasicText(body, Modifier.width(420.dp), style = EmuType.Body.copy(textAlign = TextAlign.Center))
        if (action != null) EmuButton(action, onAction, kind = ButtonKind.Go, focusRequester = focus)
    }
}

/**
 * Top-level sections (L1/R1). In M1 they are honest shells: real content arrives with the wizard
 * (M3), library and settings engine (M3/M4) and customize (M5).
 */
@Composable
fun MainScreen(
    section: Section,
    onSection: (Section) -> Unit,
    onStartSetup: () -> Unit,
    onAbout: () -> Unit,
    onHistory: () -> Unit = {},
    /** A setup run in progress: (done, total, things waiting for the user). */
    run: Triple<Int, Int, Int>? = null,
    /** Set once the device has a plan (set up or adopted); null shows the first-run empty state. */
    home: HomeStatus? = null,
    onHealth: () -> Unit = {},
    onOpenEsde: (() -> Unit)? = null,
    frontendName: String = "",
    onImport: () -> Unit = {},
    onOpenPlan: () -> Unit = {},
    /** S32's content, supplied by the caller (it needs the app's sessions). */
    apps: (@Composable ColumnScope.() -> Unit)? = null,
    library: (@Composable ColumnScope.() -> Unit)? = null,
    accessPanel: @Composable ColumnScope.() -> Unit,
    banner: (@Composable () -> Unit)? = null,
    overlay: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {},
) {
    val sections = Section.entries
    val i = sections.indexOf(section)
    ScreenKeys(
        l1 = { onSection(sections[(i - 1 + sections.size) % sections.size]) },
        r1 = { onSection(sections[(i + 1) % sections.size]) },
    )
    ToolScaffold(section, onSection, hints = listOf("A" to "Select", "L1/R1" to "Sections"), banner = banner, overlay = overlay) {
        when (section) {
            Section.Home -> if (run == null && home != null) HomeBody(home, onHealth, onStartSetup, onOpenEsde, onImport, frontendName) else if (run != null) EmptyState(
                if (run.third > 0) Face.Happy else Face.LookDown, "Setup is ${run.first} of ${run.second} done",
                if (run.third > 0) "${run.third} thing${if (run.third == 1) "" else "s"} need you. Everything else carries on by itself." else "Still working. It keeps going while you do other things.",
                "Carry on with setup", onStartSetup,
            ) else EmptyState(Face.Look, "Setup isn't finished yet", "Run setup to connect your frontends, emulators, folders and BIOS.", "Set up this device", onStartSetup)
            Section.Library -> if (library != null) library() else EmptyState(Face.Look, "No games yet", "Your games show up here once setup has found your ROM folders.")
            Section.Wishlist -> EmptyState(Face.Happy, "Your wishlist is empty", "Add games you want to play. We'll tell you how they run here, and spot them when they land in your library.")
            Section.Customize -> EmptyState(Face.Happy, "Make it yours", "Themes, colours and game art for ES-DE are coming here. Other frontends keep these in their own settings for now.")
            Section.Apps -> if (apps != null) apps() else EmptyState(Face.Look, "No apps set up yet", "Emulators you add during setup are listed here with their versions and updates.")
            Section.Settings -> SettingsBody(accessPanel, onAbout, onHistory, onOpenPlan)
        }
    }
}

/** What S17 shows once the device is set up. [ready] null = health still checking. */
data class HomeStatus(val systems: Int, val ready: Int?, val blocking: Int, val warnings: Int, val tiles: List<com.ramrom.emumanager.ui.handheld.HandheldTile> = emptyList(),
    /** How set up the device is, and what the user saw last time (the meter counts from there). */
    val percent: Int? = null, val fromPercent: Int = 0)

/** S17 Home: health at a glance and the few things people come back for. */
@Composable
private fun ColumnScope.HomeBody(h: HomeStatus, onHealth: () -> Unit, onSetUpMore: () -> Unit, onOpenEsde: (() -> Unit)?, onImport: () -> Unit, frontendName: String) {
    val focus = com.ramrom.emumanager.ui.input.initialFocus(h.ready)
    val good = h.ready != null && h.blocking == 0 && h.warnings == 0
    val face = when { h.ready == null -> Face.LookDown; good -> Face.Joy; h.blocking > 0 -> Face.Worried; else -> Face.Happy }
    val title = when { h.ready == null -> "Checking your setup"; good -> "Ready to play"; h.blocking > 0 -> "Some games won't start"; else -> "Ready, with a few notes" }
    val line = if (h.ready == null) "One moment." else "${h.ready} of ${h.systems} systems ready" + if (h.blocking + h.warnings > 0) "  ·  ${h.blocking + h.warnings} to look at" else ""
    if (LocalSizeClass.current == SizeClass.Phone) {
        // Portrait: stacked and centred, so the screen isn't a strip of content over empty space.
        Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LivingHandheld(HandheldState(awake = true, tiles = h.tiles, face = face), Modifier.size(280.dp, 200.dp))
            h.percent?.let { com.ramrom.emumanager.ui.components.SetupMeter(it, h.fromPercent, centered = true) }
            BasicText(title, style = EmuType.Headline2.copy(textAlign = TextAlign.Center))
            BasicText(line, style = EmuType.Body.copy(textAlign = TextAlign.Center))
        }
    } else Row(verticalAlignment = Alignment.CenterVertically) {
        LivingHandheld(HandheldState(awake = true, tiles = h.tiles, face = face), Modifier.size(250.dp, 180.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            h.percent?.let { com.ramrom.emumanager.ui.components.SetupMeter(it, h.fromPercent); Spacer(Modifier.height(10.dp)) }
            BasicText(title, style = EmuType.Headline2)
            BasicText(line, style = EmuType.Body)
        }
    }
    Spacer(Modifier.height(6.dp))
    val phone = LocalSizeClass.current == SizeClass.Phone
    @Composable fun Buttons() {
        if (onOpenEsde != null && good) {
            EmuButton("Open $frontendName", onOpenEsde, kind = ButtonKind.Go, focusRequester = focus)
            EmuButton("Health check", onHealth)
        } else {
            EmuButton(if (good || h.ready == null) "Health check" else "See what's wrong", onHealth, kind = ButtonKind.Go, focusRequester = focus)
            if (onOpenEsde != null) EmuButton("Open $frontendName", onOpenEsde)
        }
        EmuButton("Import games", onImport)
        EmuButton("Change your setup", onSetUpMore, kind = ButtonKind.Ghost)
    }
    if (phone) Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { Buttons() }
    else Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Buttons() }
}

private enum class SettingsPane(val title: String) { Device("Device"), Access("Access"), Updates("Updates"), Notifications("Notifications"), Report("Report a problem"), History("Change history"), About("About") }

/** S38 skeleton: sections on the left, the chosen one on the right (Wide); elsewhere a pane replaces the list. */
@Composable
private fun ColumnScope.SettingsBody(accessPanel: @Composable ColumnScope.() -> Unit, onAbout: () -> Unit, onHistory: () -> Unit, onOpenPlan: () -> Unit) {
    val wide = LocalSizeClass.current == SizeClass.Wide
    // Wide always shows a pane; narrower screens show one only once it's picked (it had no way to open).
    var picked by remember { mutableStateOf<SettingsPane?>(null) }
    val pane = picked ?: SettingsPane.Access.takeIf { wide }
    @Composable fun ColumnScope.content(p: SettingsPane) = when (p) {
        SettingsPane.Access -> accessPanel()
        SettingsPane.Device -> {
            BasicText("Set this device up from a plan made on another one, or restore one you exported.", style = EmuType.Body)
            EmuButton("Open a plan file", onOpenPlan, small = true)
        }
        else -> BasicText("Arrives with its milestone.", style = EmuType.Body)
    }
    if (!wide && pane != null) {
        ListRow("‹  Settings", onClick = { picked = null })
        Spacer(Modifier.height(8.dp))
        BasicText(pane.title, style = EmuType.Headline2)
        Spacer(Modifier.height(10.dp))
        content(pane)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(Modifier.width(if (wide) 200.dp else 520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingsPane.entries.forEach { p ->
                ListRow(p.title, onClick = { when (p) { SettingsPane.About -> onAbout(); SettingsPane.History -> onHistory(); else -> picked = p } }) { BasicText("›", style = EmuType.Body) }
            }
        }
        if (wide && pane != null) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(pane.title, style = EmuType.Headline2)
            content(pane)
        }
    }
}

/** S40 stub: version, ground rules, data sources, not-affiliated note. */
@Composable
fun AboutScreen(version: String) {
    PageScaffold("About", hints = listOf("B" to "Back")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LivingHandheld(HandheldState(awake = true, face = Face.Happy), Modifier.size(150.dp, 108.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                BasicText("RamROM Emu Manager", style = EmuType.Headline2)
                BasicText(version, style = EmuType.Detail)
            }
        }
        Promise("Your games stay yours.", "This app works with games and BIOS you already own, and never provides them.")
        SectionLabel("Credits")
        ListRow("Fonts", detail = "Bricolage Grotesque, Figtree, Geist Mono · SIL Open Font License")
        ListRow("Data sources", detail = "ES-DE documentation (MIT) · No-Intro and Redump metadata")
        ListRow("Community reports", detail = "Links to EmuReady, with thanks")
        Spacer(Modifier.height(4.dp))
        BasicText("Not affiliated with Nintendo, Sony, ES-DE or any emulator project. Names are used only to say what works with what.", style = EmuType.Body)
        Row { Tag("No analytics, no uploads", TagKind.Done) }
    }
}
