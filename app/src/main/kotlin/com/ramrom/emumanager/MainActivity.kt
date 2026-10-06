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
import com.ramrom.emumanager.ui.input.KeyRouter
import com.ramrom.emumanager.ui.input.LocalKeyRouter
import com.ramrom.emumanager.ui.input.controllerKeys
import com.ramrom.emumanager.ui.layout.Section
import com.ramrom.emumanager.ui.layout.Sheet
import com.ramrom.emumanager.ui.theme.EmuTheme

class MainActivity : ComponentActivity() {
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
    BackHandler(enabled = nav.canPop || reconnectOpen || pickerOpen) { when { pickerOpen -> pickerOpen = false; reconnectOpen -> reconnectOpen = false; else -> nav.pop() } }

    val openShizuku = {
        context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { context.startActivity(it) }
        Unit
    }

    CompositionLocalProvider(LocalKeyRouter provides router) {
        Box(Modifier.fillMaxSize().controllerKeys(router)) {
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
                Route.ChoosePath -> ChoosePathScreen(existing, restoreUnlocked = false) { path ->
                    when (path) {
                        SetupPath.Adopt, SetupPath.SetUp ->
                            if (mode == AccessMode.Elevated || mode == AccessMode.Limited) finishOnboarding(Route.Main(Section.Home))
                            else nav.push(Route.Access(Route.Main(Section.Home)))
                        SetupPath.PlanOther -> finishOnboarding(Route.Main(Section.Home))
                        SetupPath.Restore -> Unit
                    }
                }
                is Route.Access -> AccessScreen(app.access, onDone = { if (r.next is Route.Main) finishOnboarding(r.next) else nav.reset(r.next) })
                is Route.Main -> MainScreen(
                    r.section, onSection = nav::switchSection,
                    onStartSetup = { nav.push(Route.Access(Route.Main(Section.Home))) },
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
