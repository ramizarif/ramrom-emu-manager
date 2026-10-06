package com.ramrom.emumanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ramrom.emumanager.ui.theme.EmuColors
import com.ramrom.emumanager.ui.theme.EmuShape
import com.ramrom.emumanager.ui.theme.EmuType

enum class ButtonKind { Go, Secondary, Ghost }

/**
 * Buttons per DESIGN.md §5: ink "go", white secondary, text-only ghost. Focus draws a 3dp
 * Your-Turn Blue ring at a 3dp offset; a focused go button also gets the blue glow.
 * Targets are never below 48dp (Arm's Length rule).
 */
@Composable
fun EmuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Secondary,
    small: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(if (small) 11.dp else EmuShape.Md)
    val (bg, fg, border) = when (kind) {
        ButtonKind.Go -> Triple(EmuColors.Ink, EmuColors.Surface, EmuColors.Ink)
        ButtonKind.Secondary -> Triple(EmuColors.Surface, EmuColors.Ink, EmuColors.Hairline)
        ButtonKind.Ghost -> Triple(Color.Transparent, EmuColors.InkSoft, Color.Transparent)
    }
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusRing(focused, 14.dp)
            .then(if (focused && kind == ButtonKind.Go) Modifier.shadow(14.dp, shape, ambientColor = EmuColors.YourTurn, spotColor = EmuColors.YourTurn) else Modifier)
            .background(bg, shape)
            .border(2.dp, border, shape)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .heightIn(min = if (small) 40.dp else 48.dp)
            .padding(horizontal = if (kind == ButtonKind.Ghost) 8.dp else if (small) 13.dp else 19.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = EmuType.Button.copy(color = fg, fontSize = if (small) 14.sp else 16.sp), maxLines = 1, softWrap = false)
    }
}

/** The 3dp blue focus ring with a 3dp gap, drawn outside the element. */
fun Modifier.focusRing(focused: Boolean, radius: androidx.compose.ui.unit.Dp): Modifier =
    if (!focused) this else drawBehind {
        val gap = 3.dp.toPx() + 1.5.dp.toPx()
        drawRoundRect(
            color = EmuColors.YourTurn,
            topLeft = Offset(-gap, -gap),
            size = androidx.compose.ui.geometry.Size(size.width + gap * 2, size.height + gap * 2),
            cornerRadius = CornerRadius(radius.toPx() + gap),
            style = Stroke(width = 3.dp.toPx()),
        )
    }

enum class TagKind { Done, YourTurn, Blocked, Neutral, Lock, Community, Caveat }

/** Status always carries a word and, for done/blocked, an icon (Status Has A Shape rule). */
@Composable
fun Tag(text: String, kind: TagKind, modifier: Modifier = Modifier) {
    val (fg, bg) = when (kind) {
        TagKind.Done -> EmuColors.MintInk to EmuColors.MintWash
        TagKind.YourTurn -> EmuColors.YourTurn to EmuColors.YourTurnWash
        TagKind.Blocked -> EmuColors.AlertRed to EmuColors.AlertWash
        TagKind.Neutral -> EmuColors.InkSoft to EmuColors.Hairline
        TagKind.Lock -> EmuColors.LockInk to EmuColors.LockWash
        TagKind.Community -> EmuColors.CommunityInk to EmuColors.CommunityWash
        TagKind.Caveat -> EmuColors.CaveatInk to EmuColors.CaveatWash
    }
    val prefix = when (kind) { TagKind.Done -> "✓ "; TagKind.Blocked -> "! "; else -> "" }
    BasicText(
        prefix + text,
        modifier.background(bg, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
        style = EmuType.Label.copy(color = fg, letterSpacing = 0.sp, fontSize = 12.5.sp),
    )
}

@Composable
fun YourTurnTag(text: String, modifier: Modifier = Modifier) = BasicText(
    text.uppercase(),
    modifier.background(EmuColors.YourTurnWash, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
    style = EmuType.Label.copy(color = EmuColors.YourTurn, letterSpacing = 0.06.sp),
)

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) = BasicText(text.uppercase(), modifier, style = EmuType.Label)

enum class CheckState { Done, Now, Todo, Blocked }

/** Checklist row: 24dp rounded marker, title, optional mono detail on the right. */
@Composable
fun CheckItem(text: String, state: CheckState, detail: String? = null, modifier: Modifier = Modifier) {
    val marker = when (state) {
        CheckState.Done -> EmuColors.Mint
        CheckState.Now -> EmuColors.YourTurn
        CheckState.Todo -> EmuColors.Hairline
        CheckState.Blocked -> EmuColors.AlertRed
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).background(marker, RoundedCornerShape(EmuShape.Sm)), contentAlignment = Alignment.Center) {
            val glyph = when (state) { CheckState.Done -> "✓"; CheckState.Blocked -> "!"; else -> "" }
            BasicText(glyph, style = EmuType.Label.copy(color = EmuColors.Surface, fontSize = 13.sp, letterSpacing = 0.sp))
        }
        Spacer(Modifier.width(10.dp))
        BasicText(
            text, Modifier.weight(1f),
            style = EmuType.Title.copy(
                fontSize = 16.sp,
                fontWeight = if (state == CheckState.Todo) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = if (state == CheckState.Todo) EmuColors.InkFaint else EmuColors.Ink,
            ),
        )
        if (detail != null) BasicText(detail, style = EmuType.Detail.copy(fontSize = 13.sp, color = EmuColors.InkFaint))
    }
}

/** Five short bars: done = ink, current = blue and wider; label beside it. */
@Composable
fun JourneyTrack(current: Int, total: Int, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..total) {
            val color = when { i < current -> EmuColors.Ink; i == current -> EmuColors.YourTurn; else -> EmuColors.Hairline }
            Box(Modifier.padding(end = 5.dp).width(if (i == current) 30.dp else 18.dp).height(6.dp).background(color, CircleShape))
        }
        Spacer(Modifier.width(5.dp))
        BasicText(label, style = EmuType.Hint.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 13.sp))
    }
}

/** White box with a dashed blue border: whatever the user must pick or type exactly. */
@Composable
fun ValueBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(EmuColors.Surface, RoundedCornerShape(EmuShape.Md))
            .drawBehind {
                drawRoundRect(
                    EmuColors.YourTurn, cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
                )
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        BasicText(label.uppercase(), style = EmuType.Label.copy(color = EmuColors.YourTurn, fontSize = 11.sp))
        BasicText(value, style = EmuType.Value)
    }
}

/** A plain statement with a coloured dot, e.g. the bring-your-own promise. */
@Composable
fun Promise(bold: String, rest: String, dot: Color = EmuColors.Mint, modifier: Modifier = Modifier) {
    Row(
        modifier.background(EmuColors.Surface, RoundedCornerShape(EmuShape.Md)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(dot, CircleShape))
        Spacer(Modifier.width(10.dp))
        BasicText(
            androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = EmuColors.Ink, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)); append(bold); pop()
                append(" "); append(rest)
            },
            style = EmuType.Body.copy(fontSize = 14.5.sp),
        )
    }
}

/** Bottom hint bar: only the buttons that act on this screen, as glyph + verb. */
@Composable
fun HintBar(hints: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(38.dp).background(EmuColors.Canvas)
            .drawBehind { drawLine(EmuColors.Hairline, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        hints.forEach { (glyph, verb) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    glyph,
                    Modifier.heightIn(min = 22.dp).background(EmuColors.Ink, CircleShape).padding(horizontal = 7.dp, vertical = 3.dp),
                    style = TextStyle(color = EmuColors.Surface, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, textAlign = TextAlign.Center, fontFamily = com.ramrom.emumanager.ui.theme.Figtree),
                )
                Spacer(Modifier.width(7.dp))
                BasicText(verb, style = EmuType.Hint)
            }
        }
    }
}

/** A white speech bubble in the handheld's voice. */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text,
        modifier
            .shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = EmuColors.Ink.copy(alpha = .3f), spotColor = EmuColors.Ink.copy(alpha = .3f))
            .background(EmuColors.Surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 15.dp, vertical = 9.dp),
        style = EmuType.Title.copy(fontSize = 15.sp),
    )
}
