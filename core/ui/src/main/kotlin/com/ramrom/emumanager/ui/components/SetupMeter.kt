package com.ramrom.emumanager.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramrom.emumanager.ui.theme.EmuColors
import com.ramrom.emumanager.ui.theme.EmuType

/**
 * How set up the device is, counting up from [from] (what the user saw last) to [percent], so a
 * fix shows as the number climbing. [big] for Home; the compact one sits under a heading.
 */
@Composable
fun SetupMeter(percent: Int, from: Int = 0, big: Boolean = true, centered: Boolean = false, bar: Boolean = true, modifier: Modifier = Modifier) {
    val shown = remember { Animatable(from.coerceIn(0, 100).toFloat()) }
    LaunchedEffect(percent) {
        // Longer for a bigger climb, so 0 → 97 has time to feel like something.
        val ms = (400 + kotlin.math.abs(percent - shown.value) * 14).toInt().coerceAtMost(1800)
        shown.animateTo(percent.toFloat(), tween(ms, easing = FastOutSlowInEasing))
    }
    val value = shown.value
    val full = percent >= 100
    val ink = if (full) EmuColors.MintInk else EmuColors.Ink
    Column(modifier, horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText("${value.toInt()}%", style = (if (big) EmuType.Display.copy(fontSize = 56.sp, lineHeight = 56.sp) else EmuType.Headline2).copy(color = ink))
            Spacer(Modifier.width(8.dp))
            BasicText("set up", style = (if (big) EmuType.Lead else EmuType.Body).copy(textAlign = TextAlign.Start), modifier = Modifier.padding(bottom = if (big) 8.dp else 2.dp))
        }
        if (!bar) return@Column
        Spacer(Modifier.height(6.dp))
        Box(Modifier.width(if (big) 240.dp else 180.dp).height(if (big) 8.dp else 6.dp).background(EmuColors.YourTurnWash, RoundedCornerShape(50))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(value / 100f).background(if (full) EmuColors.Mint else EmuColors.YourTurn, RoundedCornerShape(50)))
        }
    }
}
