package com.ramrom.emumanager.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Layout follows window size, never device model (UX-RULES §2). */
enum class SizeClass { Wide, Squarish, Phone }

fun sizeClassFor(width: Dp, height: Dp): SizeClass = when {
    width < 520.dp || height > width * 1.2f -> SizeClass.Phone
    width < 760.dp -> SizeClass.Squarish
    else -> SizeClass.Wide
}

val LocalSizeClass = staticCompositionLocalOf { SizeClass.Wide }

/** False when the system "remove animations" setting is on: show final states instead. */
val LocalMotionEnabled = staticCompositionLocalOf { true }

object EmuMotion {
    /** Pieces landing, milestones. */
    val Spring = CubicBezierEasing(.34f, 1.56f, .64f, 1f)
    /** Everyday transitions, 150–250 ms. */
    val Out = CubicBezierEasing(.22f, 1f, .36f, 1f)
}

@Composable
fun EmuTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val motion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    val scheme = lightColorScheme(
        primary = EmuColors.Ink, onPrimary = EmuColors.Surface,
        secondary = EmuColors.YourTurn, onSecondary = EmuColors.Surface,
        background = EmuColors.Canvas, onBackground = EmuColors.Ink,
        surface = EmuColors.Surface, onSurface = EmuColors.Ink,
        surfaceVariant = EmuColors.Hairline, onSurfaceVariant = EmuColors.InkSoft,
        outline = EmuColors.Hairline, error = EmuColors.AlertRed,
    )
    MaterialTheme(colorScheme = scheme) {
        BoxWithConstraints {
            CompositionLocalProvider(
                LocalSizeClass provides sizeClassFor(maxWidth, maxHeight),
                LocalMotionEnabled provides motion,
            ) { content() }
        }
    }
}
