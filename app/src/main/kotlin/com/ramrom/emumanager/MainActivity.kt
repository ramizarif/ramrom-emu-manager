package com.ramrom.emumanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.ramrom.emumanager.ui.theme.EmuColors
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ramrom.emumanager.access.AccessMode
import com.ramrom.emumanager.nav.Navigator
import com.ramrom.emumanager.nav.Route
import com.ramrom.emumanager.screens.AboutScreen
import com.ramrom.emumanager.screens.AccessLostBanner
import com.ramrom.emumanager.screens.AccessPanel
import com.ramrom.emumanager.screens.AccessScreen
import com.ramrom.emumanager.screens.ChoosePathScreen
import com.ramrom.emumanager.screens.DevicePickerContent
import com.ramrom.emumanager.screens.DeviceScreen
import com.ramrom.emumanager.screens.DeviceSummary
import com.ramrom.emumanager.screens.MainScreen
import com.ramrom.emumanager.screens.ReconnectSheetContent
import com.ramrom.emumanager.screens.SetupPath
import com.ramrom.emumanager.screens.WelcomeScreen
import com.ramrom.emumanager.screens.FrontendScreen
import com.ramrom.emumanager.screens.SystemsScreen
import com.ramrom.emumanager.screens.PlanReviewScreen
import com.ramrom.emumanager.screens.ImportPlanScreen
import com.ramrom.emumanager.screens.BiosCheckScreen
import com.ramrom.emumanager.ui.layout.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import com.ramrom.emumanager.ui.handheld.Face
import com.ramrom.emumanager.ui.handheld.HandheldState
import com.ramrom.emumanager.ui.handheld.HandheldTile
import com.ramrom.emumanager.ui.input.KeyRouter
import com.ramrom.emumanager.ui.input.LocalKeyRouter
import com.ramrom.emumanager.ui.input.controllerKeys
import com.ramrom.emumanager.ui.layout.Section
import com.ramrom.emumanager.ui.layout.Sheet
import com.ramrom.emumanager.ui.theme.EmuTheme

class MainActivity : ComponentActivity() {
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        // Android starts in touch mode and returns to it on any tap; there nothing holds focus, and
        // gamepad buttons (unlike the d-pad) don't leave it, so A would do nothing. Any key leaves
        // touch mode here; screens then focus their main element (initialFocus). That first press is
        // spent on this, so it never triggers something the user can't see.
        if (event.action == android.view.KeyEvent.ACTION_DOWN && window.decorView.isInTouchMode) {
            (findViewById<android.view.ViewGroup>(android.R.id.content).getChildAt(0) ?: window.decorView).requestFocusFromTouch()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EmuTheme { EmuApp() } }
    }
}

@Composable
fun EmuApp() {
    val context = LocalContext.current
    val app = context.applicationContext as EmuApplication
    // Returning users land on Home; first run starts at Welcome.
    val firstRunDone by produceState<Boolean?>(null) { value = app.appState.state.first().firstRunDone }
    when (firstRunDone) {
        null -> Box(Modifier.fillMaxSize().background(EmuColors.Canvas))
        else -> EmuNav(app, if (firstRunDone == true) Route.Main(Section.Home) else Route.Welcome)
    }
}

@Composable
private fun EmuNav(app: EmuApplication, start: Route) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nav = remember { Navigator(start) }
    /** Leaving onboarding for a section marks the first run done. */
    val finishOnboarding = { r: Route -> scope.launch { app.appState.markFirstRunDone() }; nav.reset(r) }
    val router = remember { KeyRouter() }
    var device by remember { mutableStateOf(Detection.deviceSummary(context, app.knowledge)) }
    var pickerOpen by remember { mutableStateOf(false) }
    val knownDevices = remember {
        app.knowledge.getBundle()?.devices.orEmpty().filter { it.id != "generic-android" }.map { it.id to (it.names.firstOrNull() ?: it.id) }
    }
    val existing = remember { Detection.existingSetup(context, app.knowledge) }
    val mode by app.access.mode.collectAsState()
    val setup by app.access.setup.collectAsState()
    var reconnectOpen by remember { mutableStateOf(false) }
    // One draft per plan flow; recreated when the flow starts again.
    var draft by remember { mutableStateOf<PlanDraft?>(null) }
    var otherPickerOpen by remember { mutableStateOf(false) }
    var planImport by remember { mutableStateOf<PlanImport?>(null) }
    var planImportError by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(toast) { if (toast != null) { kotlinx.coroutines.delay(2500); toast = null } }
    val pickPlan = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bundle = app.knowledge.getBundle() ?: return@rememberLauncherForActivityResult
        val text = runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
        val file = text.mapCatching { com.ramrom.emumanager.data.PlanFile.decode(it).getOrThrow() }
        val d = PlanDraft.create(context, bundle)
        file.onSuccess { f ->
            planImport = PlanFiles.read(f, bundle, d.familyId); planImportError = null
            draft = d.also { it.apply(planImport!!) }
        }.onFailure { e ->
            app.log.w("plan", "import failed: ${e.message}")
            planImport = null
            planImportError = if (e is com.ramrom.emumanager.data.PlanFileVersionError) e.message else "The file isn't a plan, or it's damaged."
        }
        if (nav.current != Route.PlanImport) nav.push(Route.PlanImport)
    }
    var exportFor by remember { mutableStateOf<String?>(null) }
    val savePlan = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PlanFiles.MIME)) { uri ->
        val d = draft
        if (uri == null || d == null) return@rememberLauncherForActivityResult
        val targetId = app.knowledge.getBundle()?.devices?.firstOrNull { it.names.firstOrNull() == exportFor }?.id
        runCatching { context.contentResolver.openOutputStream(uri)!!.use { it.write(PlanFiles.export(d, targetId, BuildConfig.VERSION_NAME).encodeToByteArray()) } }
            .onSuccess { toast = "Plan saved" }
            .onFailure { app.log.e("plan", "export failed", it); toast = "Couldn't save the plan" }
    }
    val openPlanFile = { pickPlan.launch(arrayOf("*/*")) }
    var biosScanning by remember { mutableStateOf(false) }
    var biosError by remember { mutableStateOf<String?>(null) }
    val scope2 = rememberCoroutineScope()
    val scanBios = { d: PlanDraft, tree: android.net.Uri ->
        biosScanning = true; biosError = null
        scope2.launch {
            runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { d.checkBios(BiosScan.candidates(context, tree, d.biosSizes)) } }
                .onFailure { app.log.w("bios", "scan failed: ${it.message}"); biosError = "Android didn't let us read it. Pick the folder again." }
            biosScanning = false
        }
        Unit
    }
    val pickBiosFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        val d = draft ?: return@rememberLauncherForActivityResult
        if (tree != null) { d.biosFolder = tree; scanBios(d, tree) }
    }
    val startPlan = { other: String?, family: String? ->
        draft = app.knowledge.getBundle()?.let { PlanDraft.create(context, it, family) }
        if (draft != null) nav.push(Route.PlanFrontend(other))
    }
    BackHandler(enabled = nav.canPop || reconnectOpen || pickerOpen || otherPickerOpen || draft?.changing != null) { when { draft?.changing != null -> draft?.changing = null; otherPickerOpen -> otherPickerOpen = false; pickerOpen -> pickerOpen = false; reconnectOpen -> reconnectOpen = false; else -> nav.pop() } }

    val openShizuku = {
        context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { context.startActivity(it) }
        Unit
    }

    CompositionLocalProvider(LocalKeyRouter provides router) {
        Box(
            Modifier.fillMaxSize()
                .controllerKeys(router)
        ) {
            when (val r = nav.current) {
                Route.Welcome -> WelcomeScreen(onWake = { nav.push(Route.Device) })
                Route.Device -> Box(Modifier.fillMaxSize()) {
                    DeviceScreen(device, onConfirm = { nav.push(Route.ChoosePath) }, onChoose = { pickerOpen = true })
                    Sheet(pickerOpen, onDismiss = { pickerOpen = false }) {
                        DevicePickerContent(knownDevices) { id ->
                            pickerOpen = false
                            val name = knownDevices.firstOrNull { it.first == id }?.second
                            // A user-chosen device is never "Verified": we can't confirm the hardware.
                            device = if (name != null) DeviceSummary("This is an $name", "Chosen by you", emptyList(), DeviceSummary.Kind.BestGuess)
                            else device.copy(kind = DeviceSummary.Kind.BestGuess)
                        }
                    }
                }
                Route.ChoosePath -> Box(Modifier.fillMaxSize()) {
                    ChoosePathScreen(existing, restoreUnlocked = false, onOpenPlanFile = openPlanFile) { path ->
                        when (path) {
                            SetupPath.SetUp -> {
                                if (mode == AccessMode.Elevated || mode == AccessMode.Limited) startPlan(null, null)
                                else nav.push(Route.Access(Route.PlanFrontend()))
                            }
                            // Adopt (S12) lands in M3; Home shows what's here until then.
                            SetupPath.Adopt ->
                                if (mode == AccessMode.Elevated || mode == AccessMode.Limited) finishOnboarding(Route.Main(Section.Home))
                                else nav.push(Route.Access(Route.Main(Section.Home)))
                            SetupPath.PlanOther -> otherPickerOpen = true
                            SetupPath.Restore -> Unit
                        }
                    }
                    Sheet(otherPickerOpen, onDismiss = { otherPickerOpen = false }) {
                        DevicePickerContent(knownDevices) { id ->
                            otherPickerOpen = false
                            val profile = app.knowledge.getBundle()?.devices?.firstOrNull { it.id == id }
                            startPlan(profile?.names?.firstOrNull() ?: "other handheld", profile?.chipFamily)
                        }
                    }
                }
                is Route.Access -> AccessScreen(app.access, onDone = {
                    when (val next = r.next) {
                        is Route.Main -> finishOnboarding(next)
                        // Replace the access step so B from S06 goes back to S03, not to S04.
                        is Route.PlanFrontend -> { nav.pop(); startPlan(next.otherDevice, null) }
                        is Route.PlanReview -> { nav.pop(); nav.push(next) }
                        else -> nav.reset(next)
                    }
                })
                is Route.PlanFrontend -> draft?.let { d ->
                    FrontendScreen(d.esdeInstalled, d.useEsde, onChoose = { d.useEsde = it }, onNext = { nav.push(Route.PlanSystems(r.otherDevice)) })
                }
                is Route.PlanSystems -> draft?.let { d ->
                    SystemsScreen(
                        d.systems, bestGuess = device.kind != DeviceSummary.Kind.Verified || r.otherDevice != null, familyName = d.familyId.takeIf { it != "generic" }?.let(Detection::familyName),
                        changing = d.systems.firstOrNull { it.systemId == d.changing },
                        onToggle = d::toggle, onChangeEmulator = { d.changing = it }, onPickEmulator = d::pickEmulator,
                        onCloseSheet = { d.changing = null }, onNext = { nav.push(Route.PlanBios(r.otherDevice)) },
                    )
                }
                Route.PlanImport -> ImportPlanScreen(
                    planImport,
                    draft?.chosen.orEmpty().distinctBy { it.emulatorId }.mapIndexed { i, c -> HandheldTile(c.emulatorId, c.tileLabel, Detection.tileColor(i)) },
                    planImportError,
                    onAccept = { if (mode == AccessMode.Elevated || mode == AccessMode.Limited) nav.push(Route.PlanReview()) else nav.push(Route.Access(Route.PlanReview())) },
                    onEdit = { nav.push(Route.PlanSystems()) },
                    onPickAgain = openPlanFile,
                    onCancel = { nav.pop() },
                )
                is Route.PlanBios -> draft?.let { d ->
                    // A changed plan clears the last result; re-check the same folder without asking.
                    LaunchedEffect(d.bios, d.biosFolder) { val f = d.biosFolder; if (d.bios == null && f != null && !biosScanning) scanBios(d, f) }
                    BiosCheckScreen(
                        BiosRows.rows(d), biosScanning, d.biosFolder != null, biosError,
                        onPickFolder = { pickBiosFolder.launch(null) },
                        onNext = { nav.push(Route.PlanReview(r.otherDevice)) },
                    )
                }
                is Route.PlanReview -> Box(Modifier.fillMaxSize()) { draft?.let { d ->
                    val limited = mode != AccessMode.Elevated
                    val summary = remember(d.systems, d.useEsde, limited) { PlanSummaries.review(app, d, r.otherDevice, limited) }
                    PlanReviewScreen(
                        summary,
                        handheld = HandheldState(
                            awake = true, face = Face.Happy,
                            tiles = d.chosen.distinctBy { it.emulatorId }.mapIndexed { i, s -> HandheldTile(s.emulatorId, s.tileLabel, Detection.tileColor(i)) },
                            carts = d.chosen.take(3).map { com.ramrom.emumanager.screens.cartLabel(it.systemId) }, moreCarts = (d.chosen.size - 3).coerceAtLeast(0),
                        ),
                        otherDevice = r.otherDevice,
                        // Running a plan (S13) lands in M3; until then Start and Save both finish onboarding.
                        onStart = { finishOnboarding(Route.Main(Section.Home)) },
                        onSave = { finishOnboarding(Route.Main(Section.Home)) },
                        onExport = {
                            exportFor = r.otherDevice
                            savePlan.launch("ramrom-plan${r.otherDevice?.let { "-" + it.lowercase().replace(' ', '-') } ?: ""}.${PlanFiles.EXTENSION}")
                        },
                        onConnect = { nav.push(Route.Access(r)) },
                    )
                }; Toast(toast) }
                is Route.Main -> MainScreen(
                    r.section, onSection = nav::switchSection,
                    onStartSetup = {
                        if (mode == AccessMode.Elevated || mode == AccessMode.Limited) startPlan(null, null)
                        else nav.push(Route.Access(Route.PlanFrontend()))
                    },
                    onAbout = { nav.push(Route.About) },
                    accessPanel = { AccessPanel(app.access, onSetUp = { nav.push(Route.Access(r)) }) },
                    banner = if (mode == AccessMode.Disconnected) { { Box(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) { AccessLostBanner { reconnectOpen = true } } } } else null,
                    overlay = {
                        Sheet(reconnectOpen && mode == AccessMode.Disconnected, onDismiss = { reconnectOpen = false }) {
                            ReconnectSheetContent(setup, onOpenShizuku = openShizuku)
                        }
                    },
                )
                Route.About -> AboutScreen("${BuildConfig.VERSION_NAME} · debug")
            }
        }
    }
}
