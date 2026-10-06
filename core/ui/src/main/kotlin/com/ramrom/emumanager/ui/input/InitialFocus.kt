package com.ramrom.emumanager.ui.input

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager

/**
 * The element a screen focuses first. Android starts windows in touch mode, where nothing can take
 * focus, so a request made on entry silently fails; this asks again whenever input switches to keys
 * (the first controller press, or coming back from a touch). [keys] re-request when the screen's
 * main element changes.
 */
@Composable
fun initialFocus(vararg keys: Any?): FocusRequester {
    val focus = remember { FocusRequester() }
    val mode = LocalInputModeManager.current.inputMode
    LaunchedEffect(mode, *keys) { if (mode == InputMode.Keyboard) runCatching { focus.requestFocus() } }
    return focus
}
