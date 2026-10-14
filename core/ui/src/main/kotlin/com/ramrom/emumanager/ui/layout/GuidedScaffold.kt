package com.ramrom.emumanager.ui.layout

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ramrom.emumanager.ui.components.HintBar
import com.ramrom.emumanager.ui.components.SpeechBubble
import com.ramrom.emumanager.ui.handheld.HandheldState
import com.ramrom.emumanager.ui.handheld.LivingHandheld
import com.ramrom.emumanager.ui.theme.EmuColors
import com.ramrom.emumanager.ui.theme.EmuMotion
import com.ramrom.emumanager.ui.theme.LocalMotionEnabled
import com.ramrom.emumanager.ui.theme.LocalSizeClass
import com.ramrom.emumanager.ui.theme.SizeClass

/**
 * The guided layout (brief G1 §5): task pane + living-handheld scene. Wide puts the scene on the
 * right (~54%); Squarish and Phone put it in a band above the task. List-heavy screens pass
 * [listHeavy] to widen the task pane and shrink the band.
 */
@Composable
fun GuidedScaffold(
    handheld: HandheldState,
    hints: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    bubble: String? = null,
    listHeavy: Boolean = false,
    banner: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    task: @Composable ColumnScope.() -> Unit,
) {
    // Content scrolls if it must; the action row stays pinned and reachable (UX-RULES §1).
    val actionRow = @Composable { m: Modifier ->
        Row(m, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
    }
    val pane = @Composable { m: Modifier, withActions: Boolean ->
        Column(m) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), content = task)
            if (withActions) actionRow(Modifier.padding(top = 12.dp))
        }
    }
    val size = LocalSizeClass.current
    Column(modifier.fillMaxSize().background(EmuColors.Canvas).systemBarsPadding()) {
        banner?.invoke()
        val scene = @Composable { m: Modifier -> Scene(handheld, if (listHeavy && size != SizeClass.Wide) null else bubble, m) }
        when (size) {
            SizeClass.Wide -> BoxWithConstraints(Modifier.weight(1f)) {
                // Short wide screens (a 1080p handheld at high density) leave a list only a row or
                // two; the buttons move under the handheld so the list gets the full height.
                val actionsAside = listHeavy && maxHeight < 420.dp
                Row(Modifier.fillMaxSize()) {
                    pane(Modifier.weight(if (listHeavy) .56f else .46f).fillMaxHeight().padding(start = 32.dp, end = 24.dp, top = 24.dp, bottom = 12.dp), !actionsAside)
                    if (actionsAside) Column(Modifier.weight(.44f).fillMaxHeight()) {
                        scene(Modifier.weight(1f).fillMaxWidth())
                        actionRow(Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp))
                    } else scene(Modifier.weight(if (listHeavy) .44f else .54f).fillMaxHeight())
                }
            }
            else -> Column(Modifier.weight(1f)) {
                val band = when { listHeavy -> 112.dp; size == SizeClass.Phone -> 230.dp; else -> 205.dp }
                scene(Modifier.fillMaxWidth().height(band))
                pane(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 26.dp, vertical = 14.dp), true)
            }
        }
        HintBar(hints)
    }
}

@Composable
private fun Scene(state: HandheldState, bubble: String?, modifier: Modifier) {
    val motion = LocalMotionEnabled.current
    val wide = LocalSizeClass.current == SizeClass.Wide
    Box(
        modifier.clipToBounds().drawBehind {
            // Soft radial washes of sky, berry and sunshine behind the handheld.
            fun wash(c: Color, x: Float, y: Float, r: Float) = drawRect(
                Brush.radialGradient(listOf(c.copy(alpha = .22f), Color.Transparent), Offset(size.width * x, size.height * y), size.minDimension * r),
            )
            wash(EmuColors.Sky, .3f, .3f, .55f)
            wash(EmuColors.Berry, .75f, .7f, .5f)
            wash(EmuColors.Sunshine, .8f, .2f, .45f)
        },
    ) {
        LivingHandheld(state, Modifier.fillMaxSize().padding(if (wide) 8.dp else 4.dp))
        AnimatedContent(
            targetState = bubble,
            transitionSpec = {
                (fadeIn(tween(if (motion) 300 else 0, 350)) + scaleIn(tween(if (motion) 500 else 0, 350, EmuMotion.Spring), initialScale = .8f)) togetherWith fadeOut(tween(150))
            },
            modifier = Modifier.align(if (wide) Alignment.TopCenter else Alignment.TopEnd).padding(top = if (wide) 16.dp else 10.dp, end = if (wide) 0.dp else 16.dp),
            label = "bubble",
        ) { text -> if (text != null) SpeechBubble(text) else Spacer(Modifier) }
    }
}
