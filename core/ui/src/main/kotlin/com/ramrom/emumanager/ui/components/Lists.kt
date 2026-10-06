package com.ramrom.emumanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramrom.emumanager.ui.theme.EmuColors
import com.ramrom.emumanager.ui.theme.EmuMotion
import com.ramrom.emumanager.ui.theme.EmuShape
import com.ramrom.emumanager.ui.theme.EmuType
import com.ramrom.emumanager.ui.theme.LocalMotionEnabled

/** Coloured rounded square with a short label: emulator tiles, list icons. */
@Composable
fun TileIcon(label: String, color: Color, size: Dp = 30.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(color, RoundedCornerShape(size * .33f)), contentAlignment = Alignment.Center) {
        BasicText(label, style = EmuType.Label.copy(color = Color.White, letterSpacing = 0.sp, fontSize = (size.value * .4f).sp))
    }
}

/** Shared focusable surface: white, rounded, hairline shadow, blue ring when focused. */
@Composable
fun FocusCard(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    radius: Dp = EmuShape.Md,
    background: Color = EmuColors.Surface,
    content: @Composable RowScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(radius)
    Row(
        modifier
            .focusRing(focused, radius)
            .drawBehind { drawRoundRect(EmuColors.Hairline, Offset(0f, 1.dp.toPx()), size, androidx.compose.ui.geometry.CornerRadius(radius.toPx())) }
            .background(background, shape)
            .then(if (onClick != null) Modifier.clickable(source, null, onClick = onClick) else Modifier)
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** List row (`.rw`): leading icon, title, optional detail (mono when exact), trailing slot. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    monoDetail: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    FocusCard(onClick, modifier.fillMaxWidth()) {
        if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            BasicText(title, style = EmuType.Title.copy(fontSize = 16.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (detail != null) BasicText(
                detail,
                style = if (monoDetail) EmuType.Detail.copy(fontSize = 12.5.sp) else EmuType.Body.copy(fontSize = 13.5.sp, lineHeight = 18.sp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

/** Larger choice card (`.opt`): 38dp icon, title, description, trailing tag. */
@Composable
fun OptionCard(
    title: String,
    description: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    dimmed: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    FocusCard(onClick, modifier.fillMaxWidth().then(if (dimmed) Modifier.clip(RoundedCornerShape(16.dp)) else Modifier), radius = 16.dp) {
        if (icon != null) { icon(); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
            BasicText(title, style = EmuType.Title.copy(fontSize = 17.sp, color = if (dimmed) EmuColors.InkSoft else EmuColors.Ink))
            if (description != null) BasicText(description, style = EmuType.Body.copy(fontSize = 14.sp, lineHeight = 19.sp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}

enum class AlertKind { Blocked, YourTurn, Done, Neutral }

/** Inline message (`.alert`). Never a dialog where an inline step will do. */
@Composable
fun InlineAlert(title: String, body: String?, kind: AlertKind = AlertKind.Blocked, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    val bg = when (kind) { AlertKind.Blocked -> EmuColors.AlertWash; AlertKind.YourTurn -> EmuColors.YourTurnWash; AlertKind.Done -> EmuColors.MintWash; AlertKind.Neutral -> EmuColors.Hairline }
    Row(modifier.fillMaxWidth().background(bg, RoundedCornerShape(EmuShape.Md)).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (kind == AlertKind.Blocked) { BasicText("!", style = EmuType.Title.copy(color = EmuColors.AlertRed)); Spacer(Modifier.width(10.dp)) }
        Column(Modifier.weight(1f)) {
            BasicText(title, style = EmuType.Title.copy(fontSize = 15.sp))
            if (body != null) BasicText(body, style = EmuType.Body.copy(color = EmuColors.Ink))
        }
        if (action != null) { Spacer(Modifier.width(10.dp)); action() }
    }
}

/** White pill with a coloured dot (`.mini`). */
@Composable
fun MiniChip(text: String, dot: Color = EmuColors.Mint, modifier: Modifier = Modifier) {
    Row(modifier.background(EmuColors.Surface, CircleShape).padding(start = 8.dp, end = 11.dp, top = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(dot, CircleShape))
        Spacer(Modifier.width(6.dp))
        BasicText(text, style = EmuType.Title.copy(fontSize = 13.sp))
    }
}

/** Progress bar with the sky → blue fill; animates its value. */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color? = null) {
    val motion = LocalMotionEnabled.current
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(if (motion) 400 else 0, easing = EmuMotion.Out), label = "progress")
    Box(modifier.fillMaxWidth().height(8.dp).background(EmuColors.Hairline, CircleShape)) {
        Box(
            Modifier.fillMaxWidth(f).height(8.dp).background(
                if (color != null) Brush.horizontalGradient(listOf(color, color)) else Brush.horizontalGradient(listOf(EmuColors.Sky, EmuColors.YourTurn)),
                CircleShape,
            ),
        )
    }
}

/** On/off switch: ink when on. */
@Composable
fun Toggle(on: Boolean, modifier: Modifier = Modifier) {
    val motion = LocalMotionEnabled.current
    val x by animateFloatAsState(if (on) 1f else 0f, tween(if (motion) 180 else 0), label = "toggle")
    Box(modifier.size(40.dp, 24.dp).background(if (on) EmuColors.Ink else EmuColors.Hairline, CircleShape)) {
        Box(Modifier.padding(start = (3 + 16 * x).dp, top = 3.dp).size(18.dp).background(Color.White, CircleShape))
    }
}

/** Uppercase section label with an optional mono count (`.sect`). */
@Composable
fun SectionLabel(text: String, count: Int? = null, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(text.uppercase(), style = EmuType.Label)
        if (count != null) { Spacer(Modifier.width(8.dp)); BasicText(count.toString(), style = EmuType.Detail.copy(fontSize = 12.sp, color = EmuColors.InkFaint)) }
    }
}

/** Key/value facts in mono (`.kv`). */
@Composable
fun KeyValues(pairs: List<Pair<String, String>>, modifier: Modifier = Modifier, mono: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        pairs.forEach { (k, v) ->
            Row {
                BasicText(k, Modifier.width(76.dp), style = EmuType.Body.copy(fontSize = 14.sp, color = EmuColors.InkFaint))
                // Mono for exact values (paths, versions); prose reads better in the body face.
                BasicText(v, style = if (mono) EmuType.Detail.copy(fontSize = 13.sp, color = EmuColors.Ink) else EmuType.Body.copy(fontSize = 14.sp, color = EmuColors.Ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Segmented control (`.seg`). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.background(EmuColors.Hairline, RoundedCornerShape(13.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, o ->
            val source = remember { MutableInteractionSource() }
            val focused by source.collectIsFocusedAsState()
            BasicText(
                o,
                Modifier.focusRing(focused, 10.dp)
                    .background(if (i == selected) EmuColors.Surface else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable(source, null) { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                style = EmuType.Title.copy(fontSize = 14.sp, color = if (i == selected) EmuColors.Ink else EmuColors.InkSoft),
            )
        }
    }
}
