package com.ramrom.emumanager.ui.handheld

import android.graphics.Paint
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.ramrom.emumanager.ui.theme.EmuColors
import com.ramrom.emumanager.ui.theme.EmuMotion
import com.ramrom.emumanager.ui.theme.Figtree
import com.ramrom.emumanager.ui.theme.GeistMono
import com.ramrom.emumanager.ui.theme.LocalMotionEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class Face { Asleep, Look, LookDown, Happy, Worried, Joy }
enum class Badge { Found, Missing }
enum class KeyState { None, Waiting, Connected, Denied }

data class HandheldTile(val id: String, val label: String, val color: Color)

/**
 * Everything the handheld shows. It narrates progress; it never carries information that is not
 * also in the task pane (DESIGN.md §1).
 */
data class HandheldState(
    val awake: Boolean = false,
    val face: Face = Face.Happy,
    val tiles: List<HandheldTile> = emptyList(),
    val ghosts: Int = 0,
    val carts: List<String> = emptyList(),
    val moreCarts: Int = 0,
    val badges: Map<String, Badge> = emptyMap(),
    val yourTurn: String? = null,
    val key: KeyState = KeyState.None,
    val offline: Boolean = false,
    /** Increment to play the hop + confetti once. */
    val celebrate: Int = 0,
)

private const val VW = 400f
private const val VH = 300f
private val CartColors = listOf(EmuColors.Berry, EmuColors.Sky, EmuColors.YourTurn)
private val ConfettiColors = listOf(EmuColors.YourTurn, EmuColors.Tangerine, EmuColors.Mint, EmuColors.Berry, EmuColors.Sunshine, EmuColors.Sky, EmuColors.Grape)
private val Ctl = Color(0xFFE7E9F2)
private val Slate = Color(0xFF5D6379)

/**
 * The Living Handheld (DESIGN.md §5): a generic illustrated handheld drawn in a 400×300 space and
 * scaled to fit. Pieces that appear after the first frame spring in; everything snaps to its final
 * state when animations are off.
 */
@Composable
fun LivingHandheld(state: HandheldState, modifier: Modifier = Modifier) {
    val motion = LocalMotionEnabled.current
    val measurer = rememberTextMeasurer()

    // Entry progress per piece. Pieces present on first composition start landed.
    val first = remember { mutableSetOf<String>() }
    val progress = remember { mutableStateMapOf<String, Animatable<Float, *>>() }
    val ids = state.tiles.map { "t:" + it.id } + state.carts.map { "c:$it" } + state.badges.keys.map { "b:$it" }
    LaunchedEffect(ids) {
        val fresh = ids.filter { it !in progress }
        val initial = first.isEmpty()
        fresh.forEachIndexed { i, id ->
            val a = Animatable(if (initial || !motion) 1f else 0f)
            progress[id] = a
            if (!initial && motion) launch {
                delay((i * if (id.startsWith("c:")) 180L else 120L))
                a.animateTo(1f, tween(if (id.startsWith("b:")) 500 else 650, easing = EmuMotion.Spring))
            }
        }
        first += ids
        progress.keys.filter { it !in ids }.forEach { progress.remove(it) }
    }

    val power = remember { Animatable(if (state.awake) 1f else 0f) }
    LaunchedEffect(state.awake) {
        if (!state.awake) power.snapTo(0f) else if (motion) power.animateTo(1f, tween(700, easing = EmuMotion.Out)) else power.snapTo(1f)
    }

    val hop = remember { Animatable(0f) }
    val burst = remember { Animatable(1f) }
    LaunchedEffect(state.celebrate) {
        if (state.celebrate == 0 || !motion) return@LaunchedEffect
        launch {
            hop.snapTo(0f)
            hop.animateTo(1f, keyframes { durationMillis = 700; 0f at 0; 1f at 210; 0f at 700 using EmuMotion.Spring })
        }
        delay(450); burst.snapTo(0f); burst.animateTo(1f, tween(1300, easing = EmuMotion.Out))
    }

    val loop = rememberInfiniteTransition(label = "handheld")
    val lift by loop.animateFloat(10f, 14f, infiniteRepeatable(tween(1400, easing = EmuMotion.Out), RepeatMode.Reverse), label = "lift")
    val ring by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "ring")
    val keyFloat by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = EmuMotion.Out), RepeatMode.Reverse), label = "key")
    val dash by loop.animateFloat(0f, -18f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "dash")
    val blink by loop.animateFloat(
        1f, 1f,
        infiniteRepeatable(keyframes { durationMillis = 4000; 1f at 3680; .1f at 3800; 1f at 3920 }),
        label = "blink",
    )

    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            fit {
                drawCartSlot(state, progress, measurer)
                translate(top = -14f * (if (motion) hop.value else 0f)) { drawBody(state, power.value) }
                drawDock(state, progress, measurer, if (motion) lift else 12f, if (motion) ring else .3f)
                drawKey(state, if (motion) keyFloat else 0f, if (motion) dash else 0f)
                if (state.offline) drawOffline()
                if (state.celebrate > 0 && burst.value < 1f) drawConfetti(burst.value)
            }
        }
        // Face cross-fades (350 ms) over the screen, on its own layer.
        Crossfade(targetState = if (state.awake) state.face else Face.Asleep, animationSpec = tween(if (motion) 350 else 0), label = "face") { f ->
            Canvas(Modifier.fillMaxSize()) {
                fit { translate(top = -14f * (if (motion) hop.value else 0f)) { drawFace(f, if (motion) blink else 1f, measurer, power.value) } }
            }
        }
    }
}

private inline fun DrawScope.fit(block: DrawScope.() -> Unit) {
    val s = min(size.width / VW, size.height / VH)
    withTransform({
        translate((size.width - VW * s) / 2f, (size.height - VH * s) / 2f)
        scale(s, s, Offset.Zero)
    }) { block() }
}

private fun DrawScope.rr(x: Float, y: Float, w: Float, h: Float, r: Float, color: Color, alpha: Float = 1f) =
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r), alpha = alpha)

private fun DrawScope.label(m: TextMeasurer, text: String, cx: Float, cy: Float, px: Float, color: Color, mono: Boolean = false, weight: FontWeight = FontWeight.Bold) {
    val style = TextStyle(fontFamily = if (mono) GeistMono else Figtree, fontWeight = weight, fontSize = (px / (density * fontScale)).sp, color = color)
    val r = m.measure(text, style)
    drawText(r, topLeft = Offset(cx - r.size.width / 2f, cy - r.size.height / 2f))
}

private fun DrawScope.drawCartSlot(s: HandheldState, p: Map<String, Animatable<Float, *>>, m: TextMeasurer) {
    rr(130f, 72f, 140f, 10f, 4f, Color(0xFFD7DAE6))
    s.carts.forEachIndexed { i, name ->
        val t = p["c:$name"]?.value ?: 1f
        val x = 166f + i * 23f
        val col = CartColors[i % 3]
        translate(top = -46f * (1f - t)) {
            rr(x, 52f, 20f, 24f, 3f, Color.White, t.coerceIn(0f, 1f))
            drawRoundRect(col, Offset(x, 52f), Size(20f, 24f), CornerRadius(3f), style = Stroke(2f), alpha = t.coerceIn(0f, 1f))
            rr(x + 3f, 56f, 14f, 5f, 1.5f, col, t.coerceIn(0f, 1f))
            label(m, name, x + 10f, 68f, 7.5f, EmuColors.Ink, mono = true, weight = FontWeight.Medium)
        }
    }
    if (s.moreCarts > 0) label(m, "+${s.moreCarts}", 166f + s.carts.size * 23f + 10f, 65f, 9f, Slate, mono = true, weight = FontWeight.Medium)
}

private fun DrawScope.drawBody(s: HandheldState, power: Float) {
    // Soft drop shadow (Handheld elevation): 0 14 18 ink @16%.
    drawIntoCanvas { c ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            setShadowLayer(18f, 0f, 14f, EmuColors.Ink.copy(alpha = .16f).toArgb())
        }
        c.nativeCanvas.drawRoundRect(36f, 76f, 364f, 232f, 62f, 62f, paint)
    }
    rr(96f, 94f, 6f, 6f, 3f, if (s.awake) EmuColors.Mint else Color(0xFFC9CCD8))
    if (s.awake) drawCircle(
        Brush.radialGradient(listOf(EmuColors.Sky.copy(alpha = .35f * power), Color.Transparent), Offset(200f, 146f), 110f),
        radius = 110f, center = Offset(200f, 146f),
    )
    // Screen powers on from a line.
    val h = 108f * (if (s.awake) (0.02f + 0.98f * power) else 1f)
    rr(112f, 92f + (108f - h) / 2f, 176f, h, 12f, if (s.awake) EmuColors.HandheldScreen else Color(0xFF2A2E45))
    // D-pad, sticks.
    rr(62f, 134f, 30f, 10f, 3f, Ctl); rr(72f, 124f, 10f, 30f, 3f, Ctl)
    drawCircle(Ctl, 11f, Offset(77f, 184f)); drawCircle(Ctl, 11f, Offset(323f, 184f))
    // ABXY in the accent family when awake.
    val on = s.awake
    drawCircle(if (on) EmuColors.Sky else Ctl, 7f, Offset(323f, 124f))
    drawCircle(if (on) EmuColors.Mint else Ctl, 7f, Offset(323f, 152f))
    drawCircle(if (on) EmuColors.Sunshine else Ctl, 7f, Offset(309f, 138f))
    drawCircle(if (on) EmuColors.Berry else Ctl, 7f, Offset(337f, 138f))
}

private fun DrawScope.drawFace(f: Face, blink: Float, m: TextMeasurer, power: Float) {
    val ox = -5f
    val white = Color.White
    val stroke = { w: Float -> Stroke(w, cap = StrokeCap.Round) }
    if (f == Face.Asleep) {
        val c = Color(0xFF8A90B8)
        drawLine(c, Offset(180f + ox, 132f), Offset(196f + ox, 132f), 4f, StrokeCap.Round)
        drawLine(c, Offset(214f + ox, 132f), Offset(230f + ox, 132f), 4f, StrokeCap.Round)
        label(m, "z", 248f + ox, 108f, 14f, c)
        label(m, "z", 257f + ox, 97f, 10f, c)
        return
    }
    if (power < .6f) return
    val (lx, ly) = when (f) { Face.LookDown -> -14f to 8f; else -> 0f to 0f }
    if (f == Face.Joy) {
        listOf(180f, 214f).forEach { x ->
            drawPath(Path().apply { moveTo(x + ox, 136f); quadraticBezierTo(x + 8f + ox, 124f, x + 16f + ox, 136f) }, white, style = stroke(4.5f))
        }
    } else {
        val sy = if (f == Face.Worried) 1f else blink
        listOf(181f, 215f).forEach { x ->
            val eh = 22f * sy
            rr(x + lx + ox, 118f + ly + (22f - eh) / 2f, 14f, eh, 7f * sy, white)
        }
    }
    when (f) {
        Face.Worried -> {
            // Brows slope up toward the middle: worried, never angry.
            drawLine(white, Offset(178f + ox, 117f), Offset(194f + ox, 112f), 3.5f, StrokeCap.Round)
            drawLine(white, Offset(232f + ox, 117f), Offset(216f + ox, 112f), 3.5f, StrokeCap.Round)
            drawPath(Path().apply { moveTo(192f + ox, 160f); quadraticBezierTo(205f + ox, 151f, 218f + ox, 160f) }, white, style = stroke(4f))
        }
        Face.Joy -> drawPath(Path().apply { moveTo(186f + ox, 152f); quadraticBezierTo(205f + ox, 174f, 224f + ox, 152f); close() }, white)
        Face.Happy, Face.LookDown -> drawPath(Path().apply { moveTo(190f + ox, 155f); quadraticBezierTo(205f + ox, 167f, 220f + ox, 155f) }, white, style = stroke(4f))
        else -> drawLine(white, Offset(195f + ox, 158f), Offset(215f + ox, 158f), 4f, StrokeCap.Round)
    }
}

private fun DrawScope.drawDock(s: HandheldState, p: Map<String, Animatable<Float, *>>, m: TextMeasurer, lift: Float, ring: Float) {
    rr(60f, 244f, 280f, 38f, 14f, Color.White, .7f)
    if (s.tiles.isEmpty() && s.ghosts == 0) {
        label(m, "your emulators will land here", 200f, 263f, 11f, Slate.copy(alpha = .7f), weight = FontWeight.Normal)
        return
    }
    s.tiles.forEachIndexed { i, t ->
        val x = 74f + i * 33f
        val y = 250f
        val e = p["t:" + t.id]?.value ?: 1f
        val turn = s.yourTurn == t.id
        withTransform({
            translate(-160f * (1f - e), -40f * (1f - e) - if (turn) lift else 0f)
            scale(.5f + .5f * e, .5f + .5f * e, Offset(x + 13f, y + 13f))
        }) {
            if (turn) drawCircle(EmuColors.YourTurn, 16f * (1f + .6f * ring), Offset(x + 13f, y + 13f), alpha = .9f * (1f - ring), style = Stroke(2.5f))
            rr(x, y, 26f, 26f, 7f, t.color, e.coerceIn(0f, 1f))
            label(m, t.label, x + 13f, y + 13.5f, 10f, Color.White)
            s.badges[t.id]?.let { b ->
                val bp = (p["b:" + t.id]?.value ?: 1f)
                scale(bp, bp, Offset(x + 25f, y + 1f)) {
                    drawCircle(Color.White, 9.5f, Offset(x + 25f, y + 1f))
                    drawCircle(if (b == Badge.Found) EmuColors.Mint else EmuColors.AlertRed, 7.5f, Offset(x + 25f, y + 1f))
                    label(m, if (b == Badge.Found) "B" else "!", x + 25f, y + 1.5f, 9f, Color.White, weight = FontWeight.ExtraBold)
                }
            }
        }
    }
    repeat(s.ghosts) { g ->
        val x = 74f + (s.tiles.size + g) * 33f
        drawRoundRect(Color(0xFFB9BDCC), Offset(x, 250f), Size(26f, 26f), CornerRadius(7f),
            style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))))
    }
}

private fun DrawScope.drawKey(s: HandheldState, float: Float, dash: Float) {
    if (s.key == KeyState.None) return
    val col = when (s.key) { KeyState.Connected -> EmuColors.Mint; KeyState.Denied -> EmuColors.AlertRed; else -> EmuColors.YourTurn }
    val wire = Path().apply { moveTo(362f, 118f); cubicTo(350f, 118f, 345f, 140f, 330f, 140f) }
    drawPath(wire, col, style = Stroke(2f, pathEffect = if (s.key == KeyState.Connected) null else PathEffect.dashPathEffect(floatArrayOf(4f, 5f), if (s.key == KeyState.Waiting) dash else 0f)))
    val dx = if (s.key == KeyState.Waiting) 6f * float else 0f
    val dy = if (s.key == KeyState.Waiting) -4f * float else 0f
    translate(dx, dy) {
        drawCircle(col, 15f, Offset(374f, 112f))
        val k = Stroke(2.4f, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(368f, 112f), Offset(376f, 112f), 2.4f, StrokeCap.Round)
        drawLine(Color.White, Offset(373f, 108f), Offset(373f, 116f), 2.4f, StrokeCap.Round)
        drawPath(Path().apply { moveTo(377f, 109f); lineTo(381f, 112f); lineTo(377f, 115f) }, Color.White, style = k)
    }
}

private fun DrawScope.drawOffline() {
    drawCircle(Color.White, 17f, Offset(34f, 56f))
    val st = Stroke(2.2f, cap = StrokeCap.Round)
    translate(18f, 40f) {
        drawPath(Path().apply { moveTo(7f, 14f); quadraticBezierTo(16f, 6f, 25f, 14f) }, Slate, style = st)
        drawPath(Path().apply { moveTo(10.5f, 17.5f); quadraticBezierTo(16f, 12.5f, 21.5f, 17.5f) }, Slate, style = st)
        drawLine(Slate, Offset(8f, 8f), Offset(24f, 24f), 2.2f, StrokeCap.Round)
    }
}

private fun DrawScope.drawConfetti(t: Float) {
    for (i in 0 until 26) {
        val a = i / 26f * (2 * Math.PI).toFloat()
        val d = 90f + (i % 5) * 22f
        val x = 200f + cos(a) * d * t
        val y = 138f + sin(a) * d * .8f * t
        withTransform({ translate(x, y); rotate((i * 47 % 360) * t, Offset(4.5f, 4.5f)) }) {
            drawRoundRect(ConfettiColors[i % ConfettiColors.size], Offset.Zero, Size(9f, 9f), CornerRadius(3f), alpha = 1f - t)
        }
    }
}
