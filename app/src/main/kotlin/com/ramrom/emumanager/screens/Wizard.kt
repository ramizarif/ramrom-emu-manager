package com.ramrom.emumanager.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramrom.emumanager.engine.run.RunState
import com.ramrom.emumanager.engine.run.StepRunStatus
import com.ramrom.emumanager.run.StepCopy
import com.ramrom.emumanager.ui.components.AlertKind
import com.ramrom.emumanager.ui.components.ButtonKind
import com.ramrom.emumanager.ui.components.EmuButton
import com.ramrom.emumanager.ui.components.Eyebrow
import com.ramrom.emumanager.ui.components.InlineAlert
import com.ramrom.emumanager.ui.components.JourneyTrack
import com.ramrom.emumanager.ui.components.ListRow
import com.ramrom.emumanager.ui.components.ProgressBar
import com.ramrom.emumanager.ui.components.SectionLabel
import com.ramrom.emumanager.ui.components.Tag
import com.ramrom.emumanager.ui.components.TagKind
import com.ramrom.emumanager.ui.components.ValueBox
import com.ramrom.emumanager.ui.handheld.Face
import com.ramrom.emumanager.ui.handheld.HandheldState
import com.ramrom.emumanager.ui.input.ScreenKeys
import com.ramrom.emumanager.ui.input.initialFocus
import com.ramrom.emumanager.ui.layout.GuidedScaffold
import com.ramrom.emumanager.ui.layout.Sheet
import com.ramrom.emumanager.ui.theme.EmuColors
import com.ramrom.emumanager.ui.theme.EmuType

/** One step as S12 lists it. */
data class WizardRow(val stepId: String, val title: String, val status: StepRunStatus, val guided: Boolean, val blockedBy: String? = null)

/** S12 Setup wizard: Needs you · Running and up next · Done. */
@Composable
fun WizardScreen(
    state: RunState,
    rows: List<WizardRow>,
    handheld: HandheldState,
    onOpenStep: (String) -> Unit,
    onRetry: (String) -> Unit,
    onSkip: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinished: () -> Unit,
    /** The device's setup percentage, re-read as steps finish: the number people watch climb. */
    percent: Int? = null,
    fromPercent: Int = 0,
) {
    val needs = rows.filter { it.status is StepRunStatus.NeedsYou || it.status is StepRunStatus.Failed }
    val moving = rows.filter { it.status is StepRunStatus.Running || it.status == StepRunStatus.Verifying || it.status == StepRunStatus.Pending || it.status is StepRunStatus.Blocked }
    val done = rows.filter { it.status.isSatisfied || it.status == StepRunStatus.Skipped }
    var showDone by remember { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<WizardRow?>(null) }
    val nextYours = needs.firstOrNull { it.status is StepRunStatus.NeedsYou }
    val failed = state.failedCount > 0
    val focus = initialFocus(state.isComplete, nextYours?.stepId)
    val primary: Pair<String, () -> Unit> = when {
        state.isComplete -> "See how it went" to onFinished
        nextYours != null -> "Next step" to { onOpenStep(nextYours.stepId) }
        state.isPaused -> "Carry on" to onResume
        // Nothing for the user and nothing moving: whatever's left was skipped or failed. Done for now.
        !state.isRunning -> "Finish for now" to onFinished
        else -> "Pause" to onPause
    }
    ScreenKeys(start = primary.second, x = if (state.isPaused) onResume else onPause)
    Box(Modifier.fillMaxSize()) {
        GuidedScaffold(
            handheld = handheld.copy(
                face = when {
                    state.isComplete -> Face.Joy
                    failed -> Face.Worried
                    nextYours != null -> Face.Happy
                    else -> Face.LookDown
                },
                yourTurn = nextYours?.title,
            ),
            bubble = when {
                state.isComplete -> "All set. Let's play!"
                failed -> "Something needs a look."
                nextYours != null && state.runningCount == 0 -> "Your turn!"
                else -> null
            },
            hints = listOf("A" to "Open", "START" to primary.first.substringBefore(':'), "X" to if (state.isPaused) "Carry on" else "Pause"),
            listHeavy = true,
            actions = {
                EmuButton(primary.first, primary.second, kind = ButtonKind.Go, focusRequester = focus)
                if (!state.isComplete && primary.second != onPause && primary.second != onResume && state.isRunning) {
                    EmuButton(if (state.isPaused) "Carry on" else "Pause", if (state.isPaused) onResume else onPause)
                }
                // Waiting on the user: they can look at what already works and come back later.
                if (!state.isComplete && primary.second != onFinished) EmuButton("What works now", onFinished, kind = ButtonKind.Ghost)
            },
        ) {
            Eyebrow("Setup")
            Spacer(Modifier.height(6.dp))
            BasicText(
                when {
                    state.isComplete -> "All done"
                    state.isPaused -> "Paused"
                    nextYours != null && state.runningCount == 0 -> "Your turn"
                    // What's left waits on something skipped: nothing will move until the user says so.
                    !state.isRunning -> "Done for now"
                    else -> "Setting things up"
                },
                style = EmuType.Headline,
            )
            percent?.let { com.ramrom.emumanager.ui.components.SetupMeter(it, fromPercent, big = false, bar = false) }
            Spacer(Modifier.height(10.dp))
            ProgressBar(state.overallProgress)
            Spacer(Modifier.height(6.dp))
            val minutes = (state.estimatedSecondsLeft + 59) / 60
            BasicText(
                listOfNotNull(
                    "${state.doneCount} of ${rows.size} done",
                    rows.count { it.status == StepRunStatus.Skipped }.takeIf { it > 0 }?.let { "$it skipped" },
                    if (!state.isComplete && minutes > 0) "about $minutes min left" else null,
                ).joinToString("  ·  "),
                style = EmuType.Hint,
            )
            if (!state.isComplete) BasicText("Keeps running if you leave this screen.", style = EmuType.Hint.copy(color = EmuColors.InkFaint))
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (needs.isNotEmpty()) {
                    SectionLabel("Needs you", needs.size)
                    needs.forEach { r -> StepRow(r, onClick = { if (r.status is StepRunStatus.Failed) sheetFor = r else onOpenStep(r.stepId) }) }
                    Spacer(Modifier.height(4.dp))
                }
                if (moving.isNotEmpty()) {
                    SectionLabel(if (state.isRunning) "Running and up next" else "Waiting on a skipped step", moving.size)
                    moving.forEach { r -> StepRow(r, onClick = { sheetFor = r }) }
                    Spacer(Modifier.height(4.dp))
                }
                if (done.isNotEmpty()) {
                    SectionLabel(if (done.any { it.status == StepRunStatus.Skipped }) "Done and skipped" else "Done", done.size)
                    if (showDone) done.forEach { r -> StepRow(r, onClick = { if (r.status == StepRunStatus.Skipped) sheetFor = r }) }
                    else ListRow("Show what's done", detail = "${done.size} step${if (done.size == 1) "" else "s"}", onClick = { showDone = true })
                }
            }
        }
        Sheet(sheetFor != null, onDismiss = { sheetFor = null }) {
            val r = sheetFor
            if (r != null) {
                BasicText(r.title, style = EmuType.Headline2)
                val reason = (r.status as? StepRunStatus.Failed)?.reason
                BasicText(
                    when (val s = r.status) {
                        is StepRunStatus.Failed -> "It didn't work: ${s.reason}"
                        StepRunStatus.Skipped -> "You skipped this. Anything that needed it is waiting."
                        is StepRunStatus.Blocked -> "Waiting on a step you skipped: ${r.blockedBy ?: s.by}."
                        is StepRunStatus.Running -> "Running now."
                        StepRunStatus.Pending -> "Starts when the steps before it finish."
                        else -> statusWord(s)
                    },
                    style = EmuType.Body,
                )
                val sheetFocus = initialFocus(r.stepId)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (r.status) {
                        is StepRunStatus.Failed, StepRunStatus.Skipped -> {
                            EmuButton("Try again", { onRetry(r.stepId); sheetFor = null }, kind = ButtonKind.Go, focusRequester = sheetFocus)
                            if (r.status is StepRunStatus.Failed) EmuButton("Skip for now", { onSkip(r.stepId); sheetFor = null })
                        }
                        StepRunStatus.Pending, is StepRunStatus.Blocked -> {
                            EmuButton("Close", { sheetFor = null }, kind = ButtonKind.Go, focusRequester = sheetFocus)
                            if (r.status == StepRunStatus.Pending) EmuButton("Skip for now", { onSkip(r.stepId); sheetFor = null })
                        }
                        else -> EmuButton("Close", { sheetFor = null }, kind = ButtonKind.Go, focusRequester = sheetFocus)
                    }
                }
                if (reason != null) Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
private fun StepRow(r: WizardRow, onClick: () -> Unit) {
    ListRow(
        r.title,
        detail = when (val s = r.status) {
            is StepRunStatus.Failed -> s.reason
            is StepRunStatus.NeedsYou -> s.reason.takeIf { it.isNotBlank() && it != r.title }
            is StepRunStatus.Blocked -> "Waits on ${r.blockedBy ?: "a skipped step"}"
            else -> null
        },
        onClick = onClick,
    ) {
        when (val s = r.status) {
            is StepRunStatus.Running -> Column(Modifier.width(90.dp), horizontalAlignment = Alignment.End) {
                BasicText(if (s.progress > 0f) "${(s.progress * 100).toInt()}%" else "Working", style = EmuType.Hint)
                ProgressBar(s.progress)
            }
            StepRunStatus.Verifying -> Tag("Checking", TagKind.Neutral)
            is StepRunStatus.NeedsYou -> Tag("Your turn", TagKind.YourTurn)
            is StepRunStatus.Failed -> Tag("Failed", TagKind.Blocked)
            StepRunStatus.Done -> Tag("Done", TagKind.Done)
            StepRunStatus.AlreadyDone -> Tag("Already here", TagKind.Done)
            StepRunStatus.Skipped -> Tag("Skipped", TagKind.Neutral)
            is StepRunStatus.Blocked -> Tag("Waiting", TagKind.Neutral)
            StepRunStatus.Pending -> if (r.guided) Tag("Needs you later", TagKind.Neutral) else Tag("Up next", TagKind.Neutral)
        }
    }
}

private fun statusWord(s: StepRunStatus) = when (s) {
    StepRunStatus.Done, StepRunStatus.AlreadyDone -> "Done."
    StepRunStatus.Verifying -> "Checking it worked."
    is StepRunStatus.NeedsYou -> s.reason
    else -> ""
}

/** S13 Guided step: one thing only the user can do, then we check it. */
@Composable
fun GuidedStepScreen(
    copy: StepCopy,
    status: StepRunStatus,
    position: Pair<Int, Int>,
    handheld: HandheldState,
    /** True once the user has been away (opened the app/page) and come back. */
    returned: Boolean,
    onAction: () -> Unit,
    onDidIt: () -> Unit,
    onSkip: () -> Unit,
    /** Offered once per step in limited mode: lets the step chip float over the other app. */
    onAllowOverlay: (() -> Unit)? = null,
    /** The folder the app was given instead of the one we asked for, if any. */
    pickedInstead: String? = null,
) {
    val done = status.isSatisfied
    val checking = status == StepRunStatus.Verifying
    val running = status as? StepRunStatus.Running
    val notSeen = (status as? StepRunStatus.NeedsYou)?.reason?.startsWith("We couldn't see") == true ||
        (copy.verifiable && returned && status is StepRunStatus.NeedsYou)
    val focus = initialFocus(copy.title, done)
    val hasAction = copy.action != com.ramrom.emumanager.run.GuidedAction.None
    // "I did it" only when we can't check ourselves, or our check came up empty (S13 spec).
    val offerDidIt = !copy.verifiable || notSeen || !hasAction
    ScreenKeys(start = if (hasAction && !done) onAction else onDidIt, y = if (done) null else onSkip)
    GuidedScaffold(
        handheld = handheld.copy(face = when { done -> Face.Joy; notSeen -> Face.Worried; else -> Face.Happy }, yourTurn = if (done) null else copy.title),
        bubble = when { done -> "Got it!"; checking -> "Checking…"; notSeen -> "Not seeing it yet."; else -> null },
        hints = if (done) listOf("A" to "Done", "B" to "Back")
        else listOfNotNull(if (hasAction) "A" to copy.actionLabel else null, "Y" to "Skip for now", "B" to "Back"),
        actions = {
            when {
                done -> EmuButton("Done", onDidIt, kind = ButtonKind.Go, focusRequester = focus)
                hasAction -> {
                    EmuButton(copy.actionLabel, onAction, kind = ButtonKind.Go, focusRequester = focus)
                    if (offerDidIt) EmuButton("I did it", onDidIt)
                }
                else -> EmuButton("I did it", onDidIt, kind = ButtonKind.Go, focusRequester = focus)
            }
            // Three buttons don't fit the guided column at full labels; Y still says "Skip for now".
            if (!done) EmuButton(if (hasAction && offerDidIt) "Skip" else "Skip for now", onSkip, kind = ButtonKind.Ghost)
        },
    ) {
        JourneyTrack(position.first.coerceAtMost(position.second).coerceAtLeast(1), position.second.coerceIn(1, 9), "${position.first} of ${position.second} for you")
        Spacer(Modifier.height(12.dp))
        BasicText(copy.title, style = EmuType.Headline)
        if (copy.why.isNotEmpty()) { Spacer(Modifier.height(6.dp)); BasicText(copy.why, style = EmuType.Lead) }
        if (copy.value != null) { Spacer(Modifier.height(14.dp)); ValueBox(copy.valueLabel, copy.value, Modifier.widthIn(max = 420.dp)) }
        if (copy.instructions.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                copy.instructions.take(5).forEachIndexed { i, line ->
                    Row {
                        BasicText("${i + 1}.", Modifier.width(24.dp), style = EmuType.Body.copy(color = EmuColors.Ink, fontSize = 15.sp))
                        BasicText(line, style = EmuType.Body.copy(color = EmuColors.Ink))
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        when {
            done -> InlineAlert("Got it", "Moving on.", AlertKind.Done)
            checking -> InlineAlert("Checking…", null, AlertKind.Neutral)
            running != null -> Column { BasicText("Working on it…", style = EmuType.Hint); Spacer(Modifier.height(4.dp)); ProgressBar(running.progress) }
            notSeen -> InlineAlert(
                "We don't see it yet",
                pickedInstead?.let { "It was given $it. Pick ${copy.value ?: "the folder above"} instead, then try again." }
                    ?: copy.value?.let { "Check you picked $it exactly, then try again." } ?: (status as? StepRunStatus.NeedsYou)?.reason?.takeIf { !it.startsWith("We couldn't see") },
                AlertKind.YourTurn,
            )
            status is StepRunStatus.Failed -> InlineAlert("It didn't work", status.reason, AlertKind.Blocked)
            status is StepRunStatus.NeedsYou && status.reason.isNotBlank() && status.reason != copy.title && !copy.instructions.contains(status.reason) ->
                BasicText(status.reason, style = EmuType.Hint)
        }
        if (onAllowOverlay != null && !done) {
            Spacer(Modifier.height(10.dp))
            com.ramrom.emumanager.ui.components.ListRow("Keep this step on screen", detail = "Shows it over the other app. Needs \"Display over other apps\".", onClick = onAllowOverlay)
        }
    }
}
