package com.ramrom.emumanager

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.ramrom.emumanager.ui.components.ButtonKind
import com.ramrom.emumanager.ui.components.EmuButton
import com.ramrom.emumanager.ui.components.Eyebrow
import com.ramrom.emumanager.ui.components.Promise
import com.ramrom.emumanager.ui.components.Tag
import com.ramrom.emumanager.ui.components.TagKind
import com.ramrom.emumanager.ui.handheld.Face
import com.ramrom.emumanager.ui.handheld.HandheldState
import com.ramrom.emumanager.ui.layout.GuidedScaffold
import com.ramrom.emumanager.ui.theme.EmuTheme
import com.ramrom.emumanager.ui.theme.EmuType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { EmuTheme { Onboarding() } }
    }
}

/** M1 vertical slice: S01 Welcome → S02 Device. Real navigation arrives with the shell. */
@Composable
private fun Onboarding() {
    var step by rememberSaveable { mutableStateOf(0) }
    BackHandler(enabled = step > 0) { step-- }
    when (step) {
        0 -> Welcome(onWake = { step = 1 })
        else -> DeviceConfirm(onBack = { step = 0 })
    }
}

@Composable
private fun Welcome(onWake: () -> Unit) {
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

@Composable
private fun DeviceConfirm(onBack: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val soc = Build.SOC_MODEL
    val isThor = Build.MODEL.contains("thor", ignoreCase = true) || Build.DEVICE.contains("thor", ignoreCase = true)
    GuidedScaffold(
        handheld = HandheldState(awake = true, face = if (isThor) Face.Joy else Face.Look),
        bubble = if (isThor) "Hi! Nice to meet you." else "I'm new here!",
        hints = listOf("A" to "Select", "B" to "Back"),
        actions = {
            EmuButton(if (isThor) "Yes, that's it" else "Continue", {}, kind = ButtonKind.Go, focusRequester = focus)
            EmuButton("Choose another", onBack)
        },
    ) {
        Eyebrow("Your device")
        Spacer(Modifier.height(8.dp))
        BasicText(if (isThor) "This looks like an AYN Thor" else "This looks like a ${Build.MANUFACTURER} ${Build.MODEL}", style = EmuType.Headline)
        Spacer(Modifier.height(8.dp))
        BasicText("$soc · ${Build.MODEL}", style = EmuType.Detail)
        Spacer(Modifier.height(12.dp))
        Row { Tag(if (isThor) "Verified device" else "Best-guess settings", if (isThor) TagKind.Done else TagKind.Neutral) }
        Spacer(Modifier.height(10.dp))
        BasicText(
            if (isThor) "Settings marked Verified were tested on this exact model."
            else "Recommendations come from devices with the same chip. You can change anything later.",
            style = EmuType.Body,
        )
    }
}
