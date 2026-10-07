package com.ramrom.emumanager.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ramrom.emumanager.engine.health.HealthIssue
import com.ramrom.emumanager.engine.health.IssueSeverity
import com.ramrom.emumanager.run.HealthReport
import com.ramrom.emumanager.ui.components.AlertKind
import com.ramrom.emumanager.ui.components.ButtonKind
import com.ramrom.emumanager.ui.components.EmuButton
import com.ramrom.emumanager.ui.components.Eyebrow
import com.ramrom.emumanager.ui.components.InlineAlert
import com.ramrom.emumanager.ui.components.ListRow
import com.ramrom.emumanager.ui.components.SectionLabel
import com.ramrom.emumanager.ui.components.Tag
import com.ramrom.emumanager.ui.components.TagKind
import com.ramrom.emumanager.ui.components.ValueBox
import com.ramrom.emumanager.ui.handheld.Face
import com.ramrom.emumanager.ui.handheld.HandheldState
import com.ramrom.emumanager.ui.input.ScreenKeys
import com.ramrom.emumanager.ui.input.initialFocus
import com.ramrom.emumanager.ui.layout.GuidedScaffold
import com.ramrom.emumanager.ui.theme.EmuColors
import com.ramrom.emumanager.ui.theme.EmuType

/** S14 Health check report. [report] null = still checking. */
@Composable
fun HealthScreen(
    report: HealthReport?,
    handheld: HandheldState,
    canFixWithSetup: Boolean,
    /** The frontend "Open" starts; null when none is installed. */
    frontendName: String?,
    onOpenIssue: (HealthIssue) -> Unit,
    onFixWithSetup: () -> Unit,
    onRecheck: () -> Unit,
    onOpenEsde: () -> Unit,
    onDone: () -> Unit,
) {
    val focus = initialFocus(report == null, report?.allGood)
    val primary: Pair<String, () -> Unit>? = when {
        report == null -> null
        report.allGood && frontendName != null -> "Open $frontendName" to onOpenEsde
        report.allGood -> "Done" to onDone
        canFixWithSetup -> "Fix what we can" to onFixWithSetup
        else -> report.blocking.firstOrNull()?.let { "Fix: ${it.title}" to { onOpenIssue(it) } } ?: ("Done" to onDone)
    }
    ScreenKeys(start = primary?.second, y = onRecheck)
    GuidedScaffold(
        handheld = handheld.copy(face = when { report == null -> Face.LookDown; report.allGood -> Face.Joy; report.blocking.isNotEmpty() -> Face.Worried; else -> Face.Happy },
            celebrate = if (report?.allGood == true) 1 else 0),
        bubble = when { report == null -> "Checking…"; report.allGood -> "Ready to play!"; report.blocking.isNotEmpty() -> "A few things to sort."; else -> "Nearly there." },
        hints = listOfNotNull(primary?.let { "START" to it.first.substringBefore(':') }, "A" to "Open", "Y" to "Check again", "B" to "Back"),
        listHeavy = true,
        actions = {
            if (primary != null) EmuButton(primary.first, primary.second, kind = ButtonKind.Go, focusRequester = focus)
            EmuButton("Check again", onRecheck)
            if (report?.allGood == true && frontendName != null) EmuButton("Done", onDone, kind = ButtonKind.Ghost)
        },
    ) {
        Eyebrow("Health check")
        Spacer(Modifier.height(6.dp))
        if (report == null) { BasicText("Looking at your setup", style = EmuType.Headline); return@GuidedScaffold }
        BasicText(
            when {
                report.allGood -> "Everything's ready"
                report.blocking.isNotEmpty() -> "${report.blocking.size} thing${if (report.blocking.size == 1) "" else "s"} stop games running"
                else -> "Ready, with a few notes"
            },
            style = EmuType.Headline,
        )
        Spacer(Modifier.height(6.dp))
        com.ramrom.emumanager.ui.components.SetupMeter(report.score.percent, report.score.percent, big = false)
        Spacer(Modifier.height(4.dp))
        BasicText("${report.readyCount} of ${report.systems.size} systems ready", style = EmuType.Lead)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (report.frontends.isNotEmpty()) {
                SectionLabel("Frontends", report.frontends.size)
                report.frontends.forEach { f ->
                    ListRow(f.name, detail = f.top?.title ?: "Installed and finding your games", onClick = f.top?.let { t -> { onOpenIssue(t) } }) {
                        when {
                            f.ready -> Tag("Ready", TagKind.Done)
                            f.top?.severity == IssueSeverity.BLOCKER -> Tag("Missing", TagKind.Blocked)
                            else -> Tag("Check", TagKind.Caveat)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            SectionLabel("Systems", report.systems.size)
            report.systems.forEach { s ->
                ListRow(s.name, detail = s.top?.takeIf { !s.ready }?.title ?: listOfNotNull(s.emulatorName, s.note?.title).joinToString("  ·  "), onClick = s.top?.let { t -> { onOpenIssue(t) } }) {
                    when {
                        s.ready -> Tag("Ready", TagKind.Done)
                        s.top?.severity == IssueSeverity.BLOCKER -> Tag("Blocked", TagKind.Blocked)
                        else -> Tag("Check", TagKind.Caveat)
                    }
                }
            }
            IssueGroup("Stops games", report.blocking, TagKind.Blocked, onOpenIssue)
            IssueGroup("Worth fixing", report.warnings, TagKind.Caveat, onOpenIssue)
            IssueGroup("Tips", report.tips, TagKind.Neutral, onOpenIssue)
        }
    }
}

@Composable
private fun IssueGroup(label: String, issues: List<HealthIssue>, kind: TagKind, onOpen: (HealthIssue) -> Unit) {
    if (issues.isEmpty()) return
    Spacer(Modifier.height(6.dp))
    SectionLabel(label, issues.size)
    issues.forEach { i -> ListRow(i.title, detail = i.detail, onClick = { onOpen(i) }) { Tag(when (kind) { TagKind.Blocked -> "Blocking"; TagKind.Caveat -> "Warning"; else -> "Tip" }, kind) } }
}

/** What S15 can offer for an issue. */
data class IssueFix(val label: String, val run: () -> Unit, val value: Pair<String, String>? = null, val note: String? = null)

/** S15 Issue detail + fix. */
@Composable
fun IssueScreen(issue: HealthIssue, fix: IssueFix?, handheld: HandheldState, resolved: Boolean, onBack: () -> Unit) {
    val focus = initialFocus(issue.id, resolved)
    ScreenKeys(start = if (resolved) onBack else fix?.run)
    GuidedScaffold(
        handheld = handheld.copy(face = if (resolved) Face.Joy else if (issue.severity == IssueSeverity.BLOCKER) Face.Worried else Face.Happy),
        bubble = if (resolved) "Fixed!" else null,
        hints = listOfNotNull(fix?.takeIf { !resolved }?.let { "A" to it.label }, "B" to "Back"),
        actions = {
            when {
                resolved -> EmuButton("Back to the report", onBack, kind = ButtonKind.Go, focusRequester = focus)
                fix != null -> { EmuButton(fix.label, fix.run, kind = ButtonKind.Go, focusRequester = focus); EmuButton("Back", onBack, kind = ButtonKind.Ghost) }
                else -> EmuButton("Back", onBack, kind = ButtonKind.Go, focusRequester = focus)
            }
        },
    ) {
        Row { Tag(when (issue.severity) { IssueSeverity.BLOCKER -> "Stops games"; IssueSeverity.WARNING -> "Worth fixing"; IssueSeverity.INFO -> "Tip" },
            when (issue.severity) { IssueSeverity.BLOCKER -> TagKind.Blocked; IssueSeverity.WARNING -> TagKind.Caveat; IssueSeverity.INFO -> TagKind.Neutral }) }
        Spacer(Modifier.height(10.dp))
        BasicText(issue.title, style = EmuType.Headline)
        Spacer(Modifier.height(6.dp))
        BasicText(issue.detail, style = EmuType.Lead)
        if (resolved) { Spacer(Modifier.height(14.dp)); InlineAlert("Fixed", "It's sorted.", AlertKind.Done); return@GuidedScaffold }
        fix?.value?.let { (label, v) -> Spacer(Modifier.height(14.dp)); ValueBox(label, v, Modifier.widthIn(max = 460.dp)) }
        if (issue.evidence.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SectionLabel("What we saw")
            issue.evidence.forEach { BasicText(it, style = EmuType.Detail.copy(color = EmuColors.InkSoft)) }
        }
        fix?.note?.let { Spacer(Modifier.height(12.dp)); BasicText(it, style = EmuType.Hint) }
    }
}
