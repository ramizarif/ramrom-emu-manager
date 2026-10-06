package com.ramrom.emumanager.ui.input

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * Controller buttons beyond A (select) and B (back), per UX-RULES §1: X secondary, Y search,
 * L1/R1 sections, Start the screen's main action. The topmost registered screen wins, so a sheet
 * can take over Start while it is open.
 */
class KeyRouter {
    internal class Entry(val handlers: Map<Int, () -> Unit>)
    private val stack = mutableListOf<Entry>()

    internal fun push(e: Entry) { stack += e }
    internal fun remove(e: Entry) { stack -= e }

    fun dispatch(keyCode: Int): Boolean {
        for (i in stack.indices.reversed()) {
            stack[i].handlers[keyCode]?.let { it(); return true }
        }
        return false
    }
}

val LocalKeyRouter = staticCompositionLocalOf { KeyRouter() }

object Pad {
    const val START = KeyEvent.KEYCODE_BUTTON_START
    const val X = KeyEvent.KEYCODE_BUTTON_X
    const val Y = KeyEvent.KEYCODE_BUTTON_Y
    const val L1 = KeyEvent.KEYCODE_BUTTON_L1
    const val R1 = KeyEvent.KEYCODE_BUTTON_R1
    const val L2 = KeyEvent.KEYCODE_BUTTON_L2
    const val R2 = KeyEvent.KEYCODE_BUTTON_R2
}

/** Register this screen's controller actions while it is composed. Null entries are not handled. */
@Composable
fun ScreenKeys(
    start: (() -> Unit)? = null,
    x: (() -> Unit)? = null,
    y: (() -> Unit)? = null,
    l1: (() -> Unit)? = null,
    r1: (() -> Unit)? = null,
) {
    val router = LocalKeyRouter.current
    val s by rememberUpdatedState(start)
    val xx by rememberUpdatedState(x)
    val yy by rememberUpdatedState(y)
    val l by rememberUpdatedState(l1)
    val r by rememberUpdatedState(r1)
    val keys = listOfNotNull(
        start?.let { Pad.START }, x?.let { Pad.X }, y?.let { Pad.Y }, l1?.let { Pad.L1 }, r1?.let { Pad.R1 },
    )
    val entry = remember(keys) {
        KeyRouter.Entry(keys.associateWith { k ->
            { when (k) { Pad.START -> s?.invoke(); Pad.X -> xx?.invoke(); Pad.Y -> yy?.invoke(); Pad.L1 -> l?.invoke(); else -> r?.invoke() }; Unit }
        })
    }
    DisposableEffect(entry) { router.push(entry); onDispose { router.remove(entry) } }
}

/** Root modifier: routes controller key-downs to the topmost registered screen. */
fun Modifier.controllerKeys(router: KeyRouter): Modifier = onPreviewKeyEvent { e ->
    e.type == KeyEventType.KeyDown && router.dispatch(e.key.nativeKeyCode)
}
