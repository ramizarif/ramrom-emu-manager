package com.ramrom.emumanager.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ramrom.emumanager.ui.components.AlertKind
import com.ramrom.emumanager.ui.components.ButtonKind
import com.ramrom.emumanager.ui.components.EmuButton
import com.ramrom.emumanager.ui.components.Eyebrow
import com.ramrom.emumanager.ui.components.InlineAlert
import com.ramrom.emumanager.ui.components.ListRow
import com.ramrom.emumanager.ui.components.SectionLabel
import com.ramrom.emumanager.ui.components.Tag
import com.ramrom.emumanager.ui.components.TagKind
import com.ramrom.emumanager.ui.handheld.Face
import com.ramrom.emumanager.ui.handheld.HandheldState
import com.ramrom.emumanager.ui.input.ScreenKeys
import com.ramrom.emumanager.ui.input.initialFocus
import com.ramrom.emumanager.ui.layout.GuidedScaffold
import com.ramrom.emumanager.ui.theme.EmuType

/** One system as S16 found it. */
data class FoundSystem(val name: String, val detail: String, val ready: Boolean, val issue: String?)

/** Everything S16 shows. Null = still scanning. */
data class FoundSetup(val systems: List<FoundSystem>, val frontend: String?, val issueCount: Int, val fixable: Boolean)

/** S16 What we found: the existing setup, untouched. */
@Composable
fun AdoptScreen(
    found: FoundSetup?,
    handheld: HandheldState,
    onKeep: () -> Unit,
    onFix: () -> Unit,
    onAddSystems: () -> Unit,
    onSetUpInstead: () -> Unit,
) {
    val empty = found != null && found.systems.isEmpty()
    val focus = initialFocus(found == null, empty)
    ScreenKeys(start = when { found == null -> null; empty -> onSetUpInstead; else -> onKeep }, x = if (found != null && !empty) onAddSystems else null)
    GuidedScaffold(
        handheld = handheld.copy(face = when { found == null -> Face.LookDown; empty -> Face.Look; found.issueCount > 0 -> Face.Happy; else -> Face.Joy }),
        bubble = when { found == null -> "Having a look…"; empty -> "Nothing here yet."; else -> "Nice setup!" },
        hints = if (found == null) listOf("B" to "Back") else if (empty) listOf("A" to "Set up", "B" to "Back") else listOf("START" to "Keep as is", "X" to "Add systems", "B" to "Back"),
        listHeavy = true,
        actions = {
            when {
                found == null -> Unit
                empty -> EmuButton("Set up this device", onSetUpInstead, kind = ButtonKind.Go, focusRequester = focus)
                else -> {
                    EmuButton("Keep as is", onKeep, kind = ButtonKind.Go, focusRequester = focus)
                    if (found.issueCount > 0 && found.fixable) EmuButton("Fix issues", onFix)
                    EmuButton("Add systems", onAddSystems, kind = ButtonKind.Ghost)
                }
            }
        },
    ) {
        Eyebrow("What we found")
        Spacer(Modifier.height(6.dp))
        when {
            found == null -> { BasicText("Looking at this device", style = EmuType.Headline); return@GuidedScaffold }
            empty -> {
                BasicText("No emulators or games yet", style = EmuType.Headline)
                Spacer(Modifier.height(6.dp))
                BasicText("Set this device up from scratch instead. It takes a few minutes.", style = EmuType.Lead)
                return@GuidedScaffold
            }
        }
        val f = found!!
        BasicText("${f.systems.size} system${if (f.systems.size == 1) "" else "s"} set up", style = EmuType.Headline)
        Spacer(Modifier.height(6.dp))
        BasicText(listOfNotNull(f.frontend?.let { "$it found" } ?: "No frontend", if (f.issueCount > 0) "${f.issueCount} thing${if (f.issueCount == 1) "" else "s"} to look at" else null).joinToString("  ·  "), style = EmuType.Lead)
        Spacer(Modifier.height(10.dp))
        InlineAlert("Nothing has been changed.", "Keep it as it is, or let us fix what's off.", AlertKind.Neutral)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Systems", f.systems.size)
            f.systems.forEach { s ->
                ListRow(s.name, detail = s.issue ?: s.detail) { if (s.ready) Tag("Ready", TagKind.Done) else Tag("Check", TagKind.Caveat) }
            }
        }
    }
}
