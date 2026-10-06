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
            Section.Home -> EmptyState(Face.Look, "Setup isn't finished yet", "Run setup to connect ES-DE, your emulators, folders and BIOS.", "Set up this device", onStartSetup)
            Section.Library -> EmptyState(Face.Look, "No games yet", "Your games show up here once setup has found your ROM folders.")
            Section.Wishlist -> EmptyState(Face.Happy, "Your wishlist is empty", "Add games you want to play. We'll tell you how they run here, and spot them when they land in your library.")
            Section.Customize -> EmptyState(Face.Happy, "Make ES-DE yours", "Theme colours, backgrounds and game art, once ES-DE is set up.")
            Section.Apps -> EmptyState(Face.Look, "No apps set up yet", "Emulators you add during setup are listed here with their versions and updates.")
            Section.Settings -> SettingsBody(accessPanel, onAbout)
        }
    }
}

private enum class SettingsPane(val title: String) { Device("Device"), Access("Access"), Updates("Updates"), Notifications("Notifications"), Report("Report a problem"), About("About") }

/** S38 skeleton: sections on the left, the chosen one on the right (Wide); list only elsewhere. */
@Composable
private fun ColumnScope.SettingsBody(accessPanel: @Composable ColumnScope.() -> Unit, onAbout: () -> Unit) {
    var pane by remember { mutableStateOf(SettingsPane.Access) }
    val wide = LocalSizeClass.current == SizeClass.Wide
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Column(Modifier.width(if (wide) 200.dp else 520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SettingsPane.entries.forEach { p ->
                ListRow(p.title, onClick = { if (p == SettingsPane.About) onAbout() else pane = p }) { BasicText("›", style = EmuType.Body) }
            }
        }
        if (wide) Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicText(pane.title, style = EmuType.Headline2)
            when (pane) {
                SettingsPane.Access -> accessPanel()
                else -> BasicText("Arrives with its milestone.", style = EmuType.Body)
            }
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
