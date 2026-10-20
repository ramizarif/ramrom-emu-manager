package com.ramrom.emumanager.ui.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramrom.emumanager.ui.components.HintBar
import com.ramrom.emumanager.ui.handheld.Face
import com.ramrom.emumanager.ui.handheld.HandheldState
import com.ramrom.emumanager.ui.handheld.LivingHandheld
import com.ramrom.emumanager.ui.theme.EmuColors
import com.ramrom.emumanager.ui.theme.EmuMotion
import com.ramrom.emumanager.ui.theme.EmuType
import com.ramrom.emumanager.ui.theme.LocalMotionEnabled
import com.ramrom.emumanager.ui.theme.LocalSizeClass
import com.ramrom.emumanager.ui.theme.SizeClass

/** Top-level sections switched with L1/R1 (SCREENS.md navigation map). */
enum class Section(val title: String, val built: Boolean = true) {
    Home("Home"), Library("Library"), Wishlist("Wishlist", built = false), Customize("Customize", built = false), Apps("Apps"), Settings("Settings");

    companion object {
        /** The tabs L1/R1 move between. Sections not built yet stay out of the beta. */
        val tabs get() = entries.filter { it.built }
    }
}

@Composable
private fun Glyph(text: String, dark: Boolean = false) = BasicText(
    text,
    Modifier.background(if (dark) EmuColors.Ink else EmuColors.Hairline, CircleShape).padding(horizontal = 7.dp, vertical = 3.dp),
    style = EmuType.Label.copy(fontSize = 11.sp, letterSpacing = 0.sp, color = if (dark) Color.White else EmuColors.InkSoft),
)

/** Section bar: L1 · tabs · R1. On Squarish/Phone only the current section shows. */
@Composable
fun SectionBar(current: Section, onSelect: (Section) -> Unit, modifier: Modifier = Modifier) {
    val compact = LocalSizeClass.current != SizeClass.Wide
    Row(
        modifier.fillMaxWidth().height(50.dp).background(EmuColors.Canvas)
            .drawBehind { drawLine(EmuColors.Hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Glyph("L1")
        Section.tabs.filter { !compact || it == current }.forEach { s ->
            val on = s == current
            BasicText(
                if (compact) s.title + " ▾" else s.title,
                Modifier
                    .background(if (on) EmuColors.Surface else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable(remember { MutableInteractionSource() }, null) { onSelect(s) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                style = EmuType.Title.copy(fontSize = 15.sp, color = if (on) EmuColors.Ink else EmuColors.InkSoft),
            )
        }
        Glyph("R1")
    }
}

/** Back bar for pages: B glyph, title, optional trailing content. */
@Composable
fun PageBar(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().height(50.dp)
            .drawBehind { drawLine(EmuColors.Hairline, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph("B")
        Spacer(Modifier.width(12.dp))
        BasicText(title, Modifier.weight(1f), style = EmuType.Headline2.copy(fontSize = 18.sp))
        trailing()
    }
}

/**
 * Body + optional 280dp side panel. Wide shows the side panel; Squarish/Phone hide it (its
 * content is reachable from X, per brief G2–G11 §1).
 */
@Composable
private fun ColumnScope.Body(side: (@Composable ColumnScope.() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    val wide = LocalSizeClass.current == SizeClass.Wide
    Row(Modifier.weight(1f).fillMaxWidth()) {
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
        if (side != null && wide) Column(
            Modifier.width(280.dp).fillMaxHeight().background(EmuColors.Surface)
                .drawBehind { drawLine(EmuColors.Hairline, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx()) }
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = side,
        )
    }
}

/** Tool layout: a top-level section. */
@Composable
fun ToolScaffold(
    section: Section,
    onSection: (Section) -> Unit,
    hints: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    banner: (@Composable () -> Unit)? = null,
    side: (@Composable ColumnScope.() -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(EmuColors.Canvas).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            SectionBar(section, onSection)
            banner?.invoke()
            Body(side, content)
            HintBar(hints)
        }
        overlay()
    }
}

/** Page layout: opened from a section; B goes back. */
@Composable
fun PageScaffold(
    title: String,
    hints: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
    banner: (@Composable () -> Unit)? = null,
    side: (@Composable ColumnScope.() -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().background(EmuColors.Canvas).systemBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            PageBar(title, trailing = trailing)
            banner?.invoke()
            Body(side, content)
            HintBar(hints)
        }
        overlay()
    }
}

/** Bottom sheet over a scrim. Rendered by the caller inside an overlay slot. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun BoxScope.Sheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val motion = LocalMotionEnabled.current
    AnimatedVisibility(visible, enter = fadeIn(tween(if (motion) 200 else 0)), exit = fadeOut(tween(if (motion) 150 else 0))) {
        Box(Modifier.fillMaxSize().background(EmuColors.Ink.copy(alpha = .4f)).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
    }
    AnimatedVisibility(
        visible, Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(if (motion) 450 else 0, easing = EmuMotion.Out)) { it / 2 } + fadeIn(tween(if (motion) 300 else 0)),
        exit = slideOutVertically(tween(if (motion) 200 else 0)) { it } + fadeOut(tween(if (motion) 150 else 0)),
    ) {
        Column(
            // Focus stays inside an open sheet; the screen behind is dimmed and shouldn't be reachable.
            Modifier.focusProperties { exit = { androidx.compose.ui.focus.FocusRequester.Cancel } }
                .focusGroup()
                .widthIn(max = 560.dp).fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(EmuColors.Canvas, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .padding(start = 26.dp, end = 26.dp, top = 10.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(40.dp, 4.dp).background(EmuColors.Hairline, CircleShape))
            content()
        }
    }
}

/** Ink toast with the mini handheld face and an optional glyph action (e.g. Y Undo). */
@Composable
fun BoxScope.Toast(message: String?, action: Pair<String, String>? = null) {
    val motion = LocalMotionEnabled.current
    AnimatedVisibility(
        message != null, Modifier.align(Alignment.BottomCenter).padding(bottom = 52.dp),
        enter = slideInVertically(tween(if (motion) 500 else 0, easing = EmuMotion.Spring)) { it } + fadeIn(),
        exit = fadeOut(tween(150)),
    ) {
        Row(
            Modifier.shadow(16.dp, RoundedCornerShape(16.dp)).background(EmuColors.Ink, RoundedCornerShape(16.dp)).padding(start = 10.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LivingHandheld(HandheldState(awake = true, face = Face.Happy), Modifier.size(40.dp, 30.dp))
            Spacer(Modifier.width(10.dp))
            BasicText(message ?: "", style = EmuType.Title.copy(fontSize = 15.sp, color = Color.White))
            if (action != null) {
                Spacer(Modifier.width(12.dp))
                BasicText(action.first, Modifier.background(Color.White, CircleShape).padding(horizontal = 7.dp, vertical = 3.dp), style = EmuType.Label.copy(fontSize = 11.sp, letterSpacing = 0.sp, color = EmuColors.Ink))
                Spacer(Modifier.width(6.dp))
                BasicText(action.second, style = EmuType.Hint.copy(color = Color.White))
            }
        }
    }
}
