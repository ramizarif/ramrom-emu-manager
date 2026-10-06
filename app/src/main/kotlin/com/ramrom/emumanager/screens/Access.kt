package com.ramrom.emumanager.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ramrom.emumanager.access.AccessManager
import com.ramrom.emumanager.access.AccessMode
import com.ramrom.emumanager.access.Capability
import com.ramrom.emumanager.access.ShizukuPermission
import com.ramrom.emumanager.access.ShizukuSetup
import com.ramrom.emumanager.ui.components.AlertKind
import com.ramrom.emumanager.ui.components.ButtonKind
import com.ramrom.emumanager.ui.components.CheckItem
import com.ramrom.emumanager.ui.components.CheckState
import com.ramrom.emumanager.ui.components.EmuButton
import com.ramrom.emumanager.ui.components.Eyebrow
import com.ramrom.emumanager.ui.components.InlineAlert
import com.ramrom.emumanager.ui.components.JourneyTrack
import com.ramrom.emumanager.ui.components.ListRow
import com.ramrom.emumanager.ui.components.MiniChip
import com.ramrom.emumanager.ui.components.SectionLabel
import com.ramrom.emumanager.ui.components.Tag
import com.ramrom.emumanager.ui.components.TagKind
import com.ramrom.emumanager.ui.handheld.Face
import com.ramrom.emumanager.ui.handheld.HandheldState
import com.ramrom.emumanager.ui.handheld.KeyState
import com.ramrom.emumanager.ui.layout.GuidedScaffold
import com.ramrom.emumanager.ui.theme.EmuColors
import com.ramrom.emumanager.ui.theme.EmuType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SHIZUKU = "moe.shizuku.privileged.api"

/** Which S04 step is current, derived from what the device reports (never from "I did it"). */
internal enum class AccessStep { Install, DevOptions, Start, Allow, Denied, Connecting, Connected, Failed }

internal fun accessStep(s: ShizukuSetup, connecting: Boolean, connected: Boolean, failed: Boolean): AccessStep = when {
    connected -> AccessStep.Connected
    failed -> AccessStep.Failed
    connecting -> AccessStep.Connecting
    !s.installed -> AccessStep.Install
    !s.devOptionsOn -> AccessStep.DevOptions
    !s.running -> AccessStep.Start
    s.permission == ShizukuPermission.Denied -> AccessStep.Denied
    s.permission == ShizukuPermission.NotAsked -> AccessStep.Allow
    else -> AccessStep.Connecting
}

/** S04 Access setup: the Shizuku stepper, plus the limited-mode alternative. */
@Composable
fun AccessScreen(manager: AccessManager, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val setup by manager.setup.collectAsState()
    val mode by manager.mode.collectAsState()
    val caps by manager.capabilities.collectAsState()
    var connecting by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var limitedView by remember { mutableStateOf(false) }

    // Detect each part on our own: poll while this screen is visible, refresh on return.
    LifecycleResumeEffect(Unit) { manager.refresh(); onPauseOrDispose { } }
    LaunchedEffect(Unit) { while (true) { delay(1000); manager.refresh() } }
    LaunchedEffect(setup.permission, setup.running) {
        if (setup.running && setup.permission == ShizukuPermission.Granted && mode != AccessMode.Elevated && !connecting) {
            connecting = true
            failed = !manager.connect()
            connecting = false
        }
    }

    val step = accessStep(setup, connecting, mode == AccessMode.Elevated, failed)
    if (limitedView) {
        LimitedModeView(onUse = { manager.chooseLimited(); onDone() }, onConnect = { limitedView = false })
        return
    }
    val focus = com.ramrom.emumanager.ui.input.initialFocus(step)

    fun state(done: Boolean, now: Boolean, bad: Boolean = false) = when { done -> CheckState.Done; bad -> CheckState.Blocked; now -> CheckState.Now; else -> CheckState.Todo }

    GuidedScaffold(
        handheld = HandheldState(
            awake = true,
            face = when (step) { AccessStep.Connected -> Face.Joy; AccessStep.Denied, AccessStep.Failed -> Face.Worried; else -> Face.Look },
            key = when (step) { AccessStep.Connected -> KeyState.Connected; AccessStep.Denied, AccessStep.Failed -> KeyState.Denied; else -> KeyState.Waiting },
        ),
        bubble = when (step) {
            AccessStep.Connected -> "I'm in! Thank you."
            AccessStep.Denied -> "Aw, I wasn't let in."
            AccessStep.Failed -> "Hmm, that didn't work."
            AccessStep.Start, AccessStep.Connecting -> "Almost there…"
            else -> "Let me in so I can do the fiddly bits!"
        },
        hints = listOf("A" to "Select", "B" to "Back"),
        actions = {
            when (step) {
                AccessStep.Install -> EmuButton("Get Shizuku", { openShizukuStore(context) }, kind = ButtonKind.Go, focusRequester = focus)
                AccessStep.DevOptions -> EmuButton("Open About device", { open(context, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)) }, kind = ButtonKind.Go, focusRequester = focus)
                AccessStep.Start -> EmuButton("Open Shizuku", { open(context, context.packageManager.getLaunchIntentForPackage(SHIZUKU)) }, kind = ButtonKind.Go, focusRequester = focus)
                AccessStep.Allow -> EmuButton("Allow access", { manager.requestPermission() }, kind = ButtonKind.Go, focusRequester = focus)
                AccessStep.Denied -> EmuButton("Ask again", { manager.requestPermission() }, kind = ButtonKind.Go, focusRequester = focus)
                AccessStep.Failed -> EmuButton("Try again", { failed = false; scope.launch { connecting = true; failed = !manager.connect(); connecting = false } }, kind = ButtonKind.Go, focusRequester = focus)
                AccessStep.Connecting -> EmuButton("Connecting…", {}, focusRequester = focus)
                AccessStep.Connected -> EmuButton("Continue", onDone, kind = ButtonKind.Go, focusRequester = focus)
            }
            if (step != AccessStep.Connected) EmuButton("Use limited mode", { limitedView = true }, kind = if (step == AccessStep.Denied) ButtonKind.Secondary else ButtonKind.Ghost)
        },
    ) {
        JourneyTrack(1, 5, "Step 1 of 5 · Connect")
        Spacer(Modifier.height(12.dp))
        BasicText(
            when (step) { AccessStep.Connected -> "You're connected"; AccessStep.Denied -> "Access wasn't allowed"; else -> "Let it make changes for you" },
            style = EmuType.Headline,
        )
        Spacer(Modifier.height(12.dp))
        if (step == AccessStep.Connected) {
            ConnectedBody(caps)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                CheckItem("Shizuku installed", state(setup.installed, step == AccessStep.Install))
                CheckItem("Developer options on", state(setup.devOptionsOn, step == AccessStep.DevOptions))
                CheckItem("Start Shizuku", state(setup.running, step == AccessStep.Start), detail = if (step == AccessStep.Start) "about 20 s" else null)
                CheckItem("Allow access", state(false, step == AccessStep.Allow || step == AccessStep.Connecting, bad = step == AccessStep.Denied || step == AccessStep.Failed))
            }
            Spacer(Modifier.height(12.dp))
            BasicText(instruction(step), style = EmuType.Body)
        }
    }
}

private fun instruction(step: AccessStep) = buildAnnotatedString {
    fun b(t: String) { pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = EmuColors.Ink)); append(t); pop() }
    when (step) {
        AccessStep.Install -> { append("Shizuku is a free app that lets this one make changes Android normally keeps for computers. Install it, then come back.") }
        AccessStep.DevOptions -> { append("Open About device and tap "); b("Build number"); append(" 7 times. We'll notice when it's on.") }
        AccessStep.Start -> { append("Open Shizuku and tap "); b("Start"); append(". If it asks to pair, use the code in its notification. Wi-Fi needs to be on.") }
        AccessStep.Allow -> { append("Shizuku will ask if this app can use it. Choose "); b("Allow"); append(".") }
        AccessStep.Denied -> { append("Shizuku asked and "); b("Deny"); append(" was chosen. You can ask again, or carry on in limited mode.") }
        AccessStep.Connecting -> append("Connecting to Shizuku…")
        AccessStep.Failed -> append("Shizuku is running but didn't answer. Try again, or restart Shizuku.")
        AccessStep.Connected -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.ConnectedBody(caps: Set<Capability>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        capabilityLabels(caps).forEach { MiniChip(it) }
    }
    Spacer(Modifier.height(12.dp))
    BasicText("Android turns this off when you restart. Reconnecting takes about 20 seconds.", style = EmuType.Body)
}

internal fun capabilityLabels(caps: Set<Capability>): List<String> = buildList {
    if (Capability.WRITE_APP_DATA in caps) add("Change emulator settings")
    if (Capability.INSTALL_SILENT in caps) add("Install apps without prompts")
    if (Capability.GRANT_RUNTIME_PERMISSION in caps || Capability.SET_APPOP in caps) add("Set app permissions")
    if (Capability.FORCE_STOP in caps) add("Close apps before editing")
}

/**
 * Limited mode: what stays automatic and what becomes guided, side by side. The automatic parts
 * need "All files access", so that is asked for here, before continuing.
 */
@Composable
private fun LimitedModeView(onUse: () -> Unit, onConnect: () -> Unit) {
    val context = LocalContext.current
    var filesOk by remember { mutableStateOf(android.os.Environment.isExternalStorageManager()) }
    LifecycleResumeEffect(Unit) { filesOk = android.os.Environment.isExternalStorageManager(); onPauseOrDispose { } }
    val focus = com.ramrom.emumanager.ui.input.initialFocus(filesOk)
    GuidedScaffold(
        handheld = HandheldState(awake = true, face = Face.Happy),
        bubble = "No problem, I'll guide you.",
        hints = listOf("A" to "Select", "B" to "Back"),
        actions = {
            if (filesOk) EmuButton("Use limited mode", onUse, kind = ButtonKind.Go, focusRequester = focus)
            else EmuButton("Allow file access", {
                open(context, Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
            }, kind = ButtonKind.Go, focusRequester = focus)
            EmuButton("Connect instead", onConnect)
        },
    ) {
        Eyebrow("Limited mode")
        Spacer(Modifier.height(8.dp))
        BasicText("Everything still gets set up", style = EmuType.Headline)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText("Still automatic", style = EmuType.Title)
                listOf("Folders and importing", "BIOS check", "ES-DE game art").forEach { BasicText("• $it", style = EmuType.Body) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                BasicText("Guided instead", style = EmuType.Title)
                listOf("Emulator settings", "Installing apps", "Theme changes").forEach { BasicText("• $it", style = EmuType.Body) }
            }
        }
        Spacer(Modifier.height(14.dp))
        CheckItem("Allow access to your files", if (filesOk) CheckState.Done else CheckState.Now)
        if (!filesOk) {
            Spacer(Modifier.height(6.dp))
            BasicText("Android will ask in Settings. Turn on “Allow access to manage all files”, then come back.", style = EmuType.Body)
        }
    }
}

/** S05 banner: shown on any screen while elevated access is off after having been on. */
@Composable
fun AccessLostBanner(onReconnect: () -> Unit) {
    InlineAlert(
        "Elevated access is off.", "Android turns it off after a restart.", AlertKind.YourTurn,
        Modifier.padding(horizontal = 0.dp),
    ) { EmuButton("Reconnect", onReconnect, small = true) }
}

/** S05 reconnect sheet content: only the steps that matter after a restart. */
@Composable
fun ColumnScope.ReconnectSheetContent(setup: ShizukuSetup, onOpenShizuku: () -> Unit) {
    val focus = com.ramrom.emumanager.ui.input.initialFocus()
    BasicText("Wake me back up", style = EmuType.Headline2)
    CheckItem("Wi-Fi on", CheckState.Done)
    CheckItem("Start Shizuku", if (setup.running) CheckState.Done else CheckState.Now, detail = if (setup.running) null else "about 20 s")
    CheckItem("Check access", if (setup.running) CheckState.Now else CheckState.Todo)
    Row { EmuButton("Open Shizuku", onOpenShizuku, kind = ButtonKind.Go, focusRequester = focus) }
}

/** S38 Access panel. */
@Composable
fun ColumnScope.AccessPanel(manager: AccessManager, onSetUp: () -> Unit) {
    val mode by manager.mode.collectAsState()
    val caps by manager.capabilities.collectAsState()
    ListRow(
        when (mode) { AccessMode.Elevated -> "Elevated access (Shizuku)"; AccessMode.Limited -> "Limited mode"; AccessMode.Disconnected -> "Elevated access is off"; AccessMode.NotSetUp -> "Not set up yet" },
        detail = when (mode) { AccessMode.Elevated -> "Connected"; AccessMode.Disconnected -> "Android turns it off after a restart"; else -> null },
    ) {
        when (mode) {
            AccessMode.Elevated -> Tag("On", TagKind.Done)
            AccessMode.Disconnected -> Tag("Off", TagKind.YourTurn)
            else -> Tag("Limited", TagKind.Neutral)
        }
    }
    if (mode == AccessMode.Elevated) {
        SectionLabel("What it allows")
        capabilityLabels(caps).forEach { CheckItem(it, CheckState.Done) }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EmuButton(if (mode == AccessMode.Elevated) "Reconnect" else "Connect Shizuku", onSetUp)
    }
}

private fun open(context: Context, intent: Intent?) {
    if (intent == null) return
    try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: ActivityNotFoundException) { }
}

private fun openShizukuStore(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$SHIZUKU"))
    try { context.startActivity(market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: ActivityNotFoundException) { open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases"))) }
}
