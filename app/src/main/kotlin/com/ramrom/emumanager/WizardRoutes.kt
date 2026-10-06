package com.ramrom.emumanager

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ramrom.emumanager.engine.Executor
import com.ramrom.emumanager.engine.StepStatus
import com.ramrom.emumanager.engine.StepType
import com.ramrom.emumanager.engine.run.RunState
import com.ramrom.emumanager.engine.run.StepRunStatus
import com.ramrom.emumanager.nav.Navigator
import com.ramrom.emumanager.nav.Route
import com.ramrom.emumanager.run.ActiveRun
import com.ramrom.emumanager.run.GuidedAction
import com.ramrom.emumanager.run.StepText
import com.ramrom.emumanager.screens.GuidedStepScreen
import com.ramrom.emumanager.screens.WizardRow
import com.ramrom.emumanager.screens.WizardScreen
import com.ramrom.emumanager.ui.handheld.Badge
import com.ramrom.emumanager.ui.handheld.HandheldState
import com.ramrom.emumanager.ui.handheld.HandheldTile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** S12 bound to the live run. */
@Composable
fun WizardRoute(run: ActiveRun, nav: Navigator, onFinished: (complete: Boolean) -> Unit) {
    val state by run.runner.state.collectAsState()
    val scope = rememberCoroutineScope()
    val titles = remember(run) { run.plan.steps.associate { it.id to StepText.copy(it, run.bundle).title } }
    val rows = run.plan.steps.mapNotNull { s ->
        val p = state.stepProgress[s.id] ?: return@mapNotNull null
        WizardRow(s.id, titles.getValue(s.id), p.status, s.executor == Executor.Guided, (p.status as? StepRunStatus.Blocked)?.by?.let(titles::get))
    }
    // Back from another app: anything the user did there (granted, installed) shows up as done.
    LifecycleResumeEffect(run) {
        scope.launch {
            run.plan.steps.filter { s -> state.stepProgress[s.id]?.status is StepRunStatus.NeedsYou && StepText.copy(s, run.bundle).verifiable }
                .forEach { run.runner.recheck(it.id) }
        }
        onPauseOrDispose { }
    }
    WizardScreen(
        state, rows, handheldFor(run, state),
        onOpenStep = { nav.push(Route.Guided(it)) },
        onRetry = { scope.launch { run.runner.retryStep(it) } },
        onSkip = { scope.launch { run.runner.skipStepForNow(it) } },
        onPause = { scope.launch { run.runner.pause() } },
        onResume = { scope.launch { run.runner.resume() } },
        onFinished = { onFinished(state.isComplete) },
    )
}

/** S13 for one step; moves on to the next thing for the user once this one is done or skipped. */
@Composable
fun GuidedRoute(run: ActiveRun, stepId: String, nav: Navigator) {
    val context = LocalContext.current
    val state by run.runner.state.collectAsState()
    val scope = rememberCoroutineScope()
    val step = run.plan.steps.firstOrNull { it.id == stepId } ?: run { LaunchedEffect(Unit) { nav.pop() }; return }
    val copy = remember(step) { StepText.copy(step, run.bundle) }
    val status = state.stepProgress[stepId]?.status ?: StepRunStatus.Pending
    // Set when we send the user elsewhere; on return we look again.
    var away by remember(stepId) { mutableStateOf(false) }
    var returned by remember(stepId) { mutableStateOf(false) }

    val advance = {
        val next = state.stepProgress.values.firstOrNull { it.stepId != stepId && it.status is StepRunStatus.NeedsYou }?.stepId
        if (next != null) nav.replace(Route.Guided(next)) else nav.pop()
    }
    LaunchedEffect(status.isSatisfied) { if (status.isSatisfied) { delay(1000); advance() } }
    LifecycleResumeEffect(stepId) {
        if (away) {
            away = false; returned = true
            scope.launch {
                // Our own file access came back granted: do the step now rather than asking again.
                if (copy.action == GuidedAction.OwnAllFiles && Environment.isExternalStorageManager()) run.runner.runNow(stepId)
                else run.runner.recheck(stepId)
            }
        }
        onPauseOrDispose { }
    }

    val pkg = copy.appId?.let(run.executor::packageOf)
    val goAway = { intent: Intent? ->
        if (intent != null && context.tryStart(intent)) away = true
        else scope.launch { run.runner.recheck(stepId) }
    }
    val guided = run.plan.steps.filter { it.executor == Executor.Guided && it.status == StepStatus.Pending }
    // This step's place among the user's: the ones settled before it, plus one.
    val position = (guided.count { it.id != stepId && state.stepProgress[it.id]?.status?.let { s -> s.isSatisfied || s == StepRunStatus.Skipped } == true } + 1) to guided.size

    GuidedStepScreen(
        copy, status, position, handheldFor(run, state), returned,
        onAction = {
            when (copy.action) {
                GuidedAction.RunNow -> scope.launch { run.runner.runNow(stepId) }
                GuidedAction.OpenApp -> goAway(pkg?.let { context.packageManager.getLaunchIntentForPackage(it) })
                GuidedAction.OpenPage -> goAway(copy.url?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)) })
                GuidedAction.AppSettings -> goAway(pkg?.let { Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$it")) })
                GuidedAction.AllFilesSettings -> goAway(pkg?.let { Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$it")) })
                GuidedAction.OwnAllFiles ->
                    if (Environment.isExternalStorageManager()) scope.launch { run.runner.runNow(stepId) }
                    else goAway(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
                GuidedAction.None -> scope.launch { run.runner.recheck(stepId) }
            }
        },
        onDidIt = { if (status.isSatisfied) advance() else scope.launch { run.runner.markGuidedDone(stepId) } },
        onSkip = { scope.launch { run.runner.skipStepForNow(stepId) }; advance() },
    )
}

private fun Context.tryStart(intent: Intent): Boolean = try { startActivity(intent); true } catch (_: ActivityNotFoundException) { false }

/** The handheld narrates installs: tiles fly in as apps land, ghosts wait for the rest. */
private fun handheldFor(run: ActiveRun, state: RunState): HandheldState {
    val appSteps = run.plan.steps.filter { it.type == StepType.InstallApp || it.type == StepType.GuidedObtainApp }
    val here = appSteps.filter { state.stepProgress[it.id]?.status?.isSatisfied == true }
    val names = (run.bundle.emulators.associate { it.id to it.name } + run.bundle.frontends.associate { it.id to StepText.frontendName(it) })
    val tiles = here.mapIndexed { i, s -> HandheldTile(s.subject, tileLabel(names[s.subject] ?: s.subject), Detection.tileColor(i)) }
    val failedApps = appSteps.filter { state.stepProgress[it.id]?.status is StepRunStatus.Failed }.map { it.subject }
    return HandheldState(
        awake = true, tiles = tiles, ghosts = appSteps.size - here.size,
        badges = failedApps.associateWith { Badge.Missing },
        celebrate = if (state.isComplete) 1 else 0,
    )
}

private fun tileLabel(name: String) = name.filter { it.isLetterOrDigit() }.take(2).replaceFirstChar { it.uppercase() }
