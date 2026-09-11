package com.logiclabs.app.ui.screens.splash

import android.provider.Settings as AndroidSettings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.logiclabs.core.designsystem.theme.ChassisSilkscreen
import com.logiclabs.core.designsystem.theme.Inter
import com.logiclabs.core.designsystem.theme.JetBrainsMono
import com.logiclabs.core.designsystem.theme.LedOnCore
import com.logiclabs.core.designsystem.theme.PhosphorBloom
import com.logiclabs.core.designsystem.theme.PhosphorCore
import com.logiclabs.core.designsystem.theme.SevenSegLeak
import com.logiclabs.core.designsystem.theme.SevenSegLit
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextTertiary
import com.logiclabs.feature.instruments.ui.hardware.SevenSegFont
import com.logiclabs.feature.tools.feedback.Haptics
import com.logiclabs.feature.tools.feedback.LocalConsoleSfx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.sin

/**
 * The Power-On: the branded cold-start sequence.
 *
 * One clean idea, told in ~3.2 s — the app's whole metaphor in miniature:
 * **current flows in, logic happens, an indicator lights.**
 *
 *  1. A blueprint dot grid; a DIP-14 package fades in at the exact centre of the
 *     screen, settling with a breath.
 *  2. Traces draw in from the screen edges toward the package: VCC from the top to
 *     pin 14, two signal lines from the left to pins 1 and 2, GND from the bottom to
 *     pin 7. Everything converges on the chip.
 *  3. **Current flows inward** — a stream of comets runs along every trace, from the
 *     edges into the package. Arrivals wake the die, then the internal AND / OR / NOT
 *     gates cascade alight, and the silkscreen part number resolves.
 *  4. The logic answers: an output trace draws from pin 3 to a 5 mm LED beside the
 *     chip, a single pulse runs out along it — and the **LED ignites**: red bloom,
 *     white-hot core, soft rays, and a gentle breathing glow. (Relay click + haptic
 *     on ignition.)
 *  5. The wordmark decodes from hexadecimal garble, the POST readout ticks off
 *     VCC / GND / LOGIC CORE, the seven-segment counter settles on "LL", and a soft
 *     phosphor wash hands off to Home.
 *
 * Any tap skips the rest. Users who disable animations system-wide (animator duration
 * scale = 0) get the finished still frame instead of the sequence.
 *
 * ## Performance
 * One-shot 3.2 s sequence honouring the house rule: every [Path] lives in
 * [SplashDrawState], rewound and refilled each frame; comets and the glow are
 * closed-form functions of [t] — no per-frame allocations beyond the wordmark strings.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val view = LocalView.current
    val sfx = LocalConsoleSfx.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    val reduceMotion = remember {
        try {
            AndroidSettings.Global.getFloat(
                context.contentResolver,
                AndroidSettings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        } catch (_: Throwable) {
            false
        }
    }

    val state = remember { SplashDrawState() }
    val anim = remember { Animatable(0f) }
    var finished by remember { mutableStateOf(false) }

    fun finish() {
        if (finished) return
        finished = true
        onFinished()
    }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            delay(700)
            finish()
            return@LaunchedEffect
        }
        try {
            anim.animateTo(1f, tween(durationMillis = TOTAL_MS.toInt(), easing = LinearEasing))
        } catch (_: CancellationException) {
            return@LaunchedEffect
        }
        finish()
    }

    // The relay click and haptic thud land the instant the LED ignites — the payoff
    // beat of the whole sequence. snapshotFlow fires once on the false→true edge; the
    // flag keeps a replayed composition from re-firing it.
    LaunchedEffect(sfx) {
        snapshotFlow { anim.value >= T_LED / TOTAL_MS }.collect { on ->
            if (on && !state.sfxFired) {
                state.sfxFired = true
                sfx?.pop()
                Haptics.thud(view)
            }
        }
    }

    val t = if (reduceMotion) TOTAL_MS else anim.value * TOTAL_MS

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBase)
            .pointerInput(Unit) { detectTapGestures { finish() } }
            .semantics { contentDescription = "Logic Labs powering on" }
    ) {
        val centerYdp = maxHeight * 0.5f
        val statusInsetPx = max(
            WindowInsets.statusBars.getTop(density),
            WindowInsets.displayCutout.getTop(density)
        ).toFloat()

        val labelVcc = remember {
            measurer.measure(
                "+5V",
                TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 9.sp)
            )
        }
        val labelGnd = remember {
            measurer.measure(
                "0V",
                TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 9.sp)
            )
        }
        val labelPart = remember {
            measurer.measure(
                "LL-8008",
                TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 10.sp)
            )
        }

        // The whole sequence: one canvas, redrawn from the animation value read in
        // the draw phase, so it never forces recomposition.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val drawT = if (reduceMotion) TOTAL_MS else anim.value * TOTAL_MS
            drawPowerOn(
                state = state,
                t = drawT,
                centerYPx = with(density) { centerYdp.toPx() },
                statusInsetPx = statusInsetPx,
                labelVcc = labelVcc,
                labelGnd = labelGnd,
                labelPart = labelPart
            )
        }

        // Wordmark block, pinned under the chip-and-LED composition.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .offset(y = centerYdp + 112.dp)
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = wordmarkAt(t),
                style = TextStyle(
                    fontFamily = Inter,
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp,
                    letterSpacing = 4.sp
                ),
                color = lerp(TextTertiary, TextPrimary, frac(t, T_WORD_START, T_WORD_START + 700f))
            )
            Text(
                text = "IDL-800A DIGITAL LOGIC BENCH",
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    letterSpacing = 2.5.sp
                ),
                color = TextTertiary.copy(alpha = frac(t, T_SUBTITLE_START, T_SUBTITLE_START + 250f))
            )
        }

        // POST readout, bottom-left.
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, bottom = 92.dp)
        ) {
            POST_LINES.forEachIndexed { i, line ->
                Text(
                    text = line,
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = 10.sp
                    ),
                    color = TextTertiary.copy(alpha = frac(t, T_POST_START + i * 220f, T_POST_START + i * 220f + 180f))
                )
            }
        }

        // Skip affordance: quiet, and only once there is something to skip.
        Text(
            text = "TAP TO SKIP",
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontWeight = FontWeight.Medium,
                fontSize = 9.sp,
                letterSpacing = 2.sp
            ),
            color = TextTertiary.copy(alpha = 0.5f * frac(t, 700f, 900f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Timeline (milliseconds) and its helpers
// ---------------------------------------------------------------------------

private const val TOTAL_MS = 3700f
private const val T_CHIP_START = 180f
private const val T_CHIP_END = 700f
private const val T_TRACES = 720f            // inflow traces draw in, edge → chip
private const val T_TRACES_END = 1220f
private const val T_FLOW = 1260f             // the inward current stream begins
private const val T_COMET_MS = 260f          // stream cadence per trace (staggered)
private const val T_COMET_TRAVEL = 400f
private val T_DIE_LIT get() = T_FLOW + T_COMET_TRAVEL          // first arrival
private val T_GATE_IGNITE get() = T_DIE_LIT + 180f
private const val T_OUT_TRACE = 1980f        // output trace, pin 3 → LED
private const val T_OUT_TRACE_END = 2280f
private const val T_OUT_PULSE = 2320f        // the logic's answer runs out
private const val T_LED = 2600f              // the LED ignites — payoff
private const val T_SILKSCREEN = 2050f
// The wordmark decode must FINISH inside the timeline: the last of the ten
// characters resolves at T_WORD_START + 9*60 + 90 = 3190 ms, so the fully
// resolved "LOGIC LABS" holds on screen for the final ~200 ms before the wash.
// (At the old 2640/3200 pairing the final "S" resolved at 3270 — past the end —
// and froze as hex garble: "LABS" reading "LAF2".)
private const val T_WORD_START = 2560f
private const val T_SUBTITLE_START = 3000f
private const val T_POST_START = 2150f
// The wash is the final 200 ms; everything before it — resolved wordmark,
// breathing LED, complete POST — holds as a settled beat.
private const val T_FLASH = 3500f

private val POST_LINES = listOf(
    "VCC RAIL .... 5.00 V",
    "GND RAIL .... 0.00 V",
    "LOGIC CORE ...... OK"
)

/** Linear window: 0 before [a], 1 after [b], linear between. */
private fun frac(t: Float, a: Float, b: Float): Float =
    if (b <= a) { if (t >= b) 1f else 0f } else ((t - a) / (b - a)).coerceIn(0f, 1f)

/** Hermite smoothstep of a 0..1 window. */
private fun smooth(f: Float): Float = f * f * (3f - 2f * f)

private const val WORDMARK = "LOGIC LABS"
private const val SCRAMBLE = "0123456789ABCDEF"

/**
 * The wordmark at time [t]: blanks before its window, hexadecimal garble while the
 * "decoder settles", the true character after. Padding with spaces (not truncating)
 * keeps the monospace string a constant width, so the centred text never jitters.
 */
private fun wordmarkAt(t: Float): String = buildString(WORDMARK.length) {
    WORDMARK.forEachIndexed { i, c ->
        when {
            c == ' ' -> append(' ')
            t >= T_WORD_START + i * 60f + 90f -> append(c)
            t >= T_WORD_START - 120f -> append(SCRAMBLE[((t / 45f).toInt() + i * 3) and 0x0F])
            else -> append(' ')
        }
    }
}

// ---------------------------------------------------------------------------
// Draw state — everything the per-frame draw reuses
// ---------------------------------------------------------------------------

/**
 * The splash's ground. Deliberately NOT theme-backed: the power-on happens in a dark
 * room before any palette exists — the daylight bench only exists once the machine
 * is on.
 */
private val SplashBase = Color(0xFF0A0A0C)

/** Physical package colours — epoxy, lead frame, pads. Theme-invariant by nature. */
private val SplashEpoxy = Color(0xFF1E2129)
private val SplashEpoxyTop = Color(0xFF2A2E3A)
private val SplashLead = Color(0xFF8A92A6)
private val SplashLeadDim = Color(0xFF4A5062)

/** Current is cool cyan-green; the LED answers in warm red. */
private val SplashCyan = Color(0xFF22D3EE)
private val LedRubyOff = Color(0xFF2A0A0C)
private val LedRubyOn = Color(0xFFFF3355)
private val LedRubyBloom = Color(0xFFFF6B81)

private class SplashDrawState {
    val gate = Path()
    val die = Path()
    var vignette: Brush? = null
    var vignetteW = -1f
    var vignetteH = -1f
    var sfxFired = false
}

// ---------------------------------------------------------------------------
// The power-on
// ---------------------------------------------------------------------------

/**
 * Draws the whole sequence. Geometry is a landscape DIP-14 at the exact screen
 * centre: pin 1 top-left down the left side to pin 7 bottom-left, pin 8 bottom-right
 * up to pin 14 top-right. Inflow converges from three sides — VCC from the top to
 * pin 14, two signal lines from the left to pins 1 and 2, GND from the bottom to
 * pin 7 — and the logic's answer exits right, from pin 3 to the LED beside the chip.
 */
private fun DrawScope.drawPowerOn(
    state: SplashDrawState,
    t: Float,
    centerYPx: Float,
    statusInsetPx: Float,
    labelVcc: TextLayoutResult,
    labelGnd: TextLayoutResult,
    labelPart: TextLayoutResult
) {
    val u = 1.dp.toPx()
    val cx = size.width / 2f
    val cy = centerYPx

    // --- package geometry (dp) ------------------------------------------------
    val bodyLeft = cx - 85f * u
    val bodyRight = cx + 85f * u
    val bodyTop = cy - 50f * u
    val bodyBottom = cy + 50f * u
    val leadLen = 12f * u
    val padR = 2.3f * u
    val pinY0 = bodyTop + 14f * u
    val pinStep = (bodyBottom - bodyTop - 28f * u) / 6f
    val pinY = { i: Int -> pinY0 + i * pinStep }

    // Inflow endpoints.
    val vccX = bodyRight + leadLen                 // pin 14 pad
    val vccY = pinY(0)
    val in1X = bodyLeft - leadLen                  // pin 1 pad
    val in1Y = pinY(0)
    val in2X = in1X                                // pin 2 pad
    val in2Y = pinY(1)
    val gndX = in1X                                // pin 7 pad
    val gndY = pinY(6)
    val edgeL = -20f * u
    val edgeT = -20f * u
    val edgeB = size.height + 20f * u

    // Outflow: pin 3 (right side, second from top) → elbow → LED beside the chip.
    val outX = bodyRight + leadLen                 // pin 3 pad
    val outY = pinY(2)
    val elbowX = cx + 150f * u
    val ledX = elbowX
    val ledY = cy
    val ledR = 13f * u
    // The LED's return to ground: straight down from the LED to the bottom edge.
    val returnX = ledX + 8f * u

    // --- phase drivers ----------------------------------------------------------
    val chipIn = smooth(frac(t, T_CHIP_START, T_CHIP_END))
    val traces = smooth(frac(t, T_TRACES, T_TRACES_END))
    val dieLit = smooth(frac(t, T_DIE_LIT, T_DIE_LIT + 200f))
    val outTrace = smooth(frac(t, T_OUT_TRACE, T_OUT_TRACE_END))
    val ledIgnite = smooth(frac(t, T_LED, T_LED + 340f))
    val flash = frac(t, T_FLASH, TOTAL_MS)

    // --- blueprint dot grid --------------------------------------------------------
    val gridAlpha = smooth(frac(t, 0f, 600f)) * (0.08f + 0.06f * ledIgnite)
    if (gridAlpha > 0f) {
        val step = 24f * u
        var gx = step
        while (gx < size.width) {
            var gy = step
            while (gy < size.height) {
                drawCircle(PhosphorCore.copy(alpha = gridAlpha), radius = 0.9f * u, center = Offset(gx, gy))
                gy += step
            }
            gx += step
        }
    }

    // --- inflow traces: converge on the package --------------------------------------
    if (traces > 0f) {
        val traceColor = PhosphorCore.copy(alpha = 0.40f + 0.40f * maxOf(dieLit, ledIgnite))
        val w = 2.2f * u

        // VCC: top edge → pin 14 (reveal downward = inward).
        clipRect(top = edgeT, bottom = lerp(edgeT, vccY + 2f * u, traces)) {
            drawLine(traceColor, Offset(vccX, edgeT), Offset(vccX, vccY), w, StrokeCap.Round)
        }
        // GND: bottom edge → pin 7 (reveal upward = inward).
        clipRect(top = lerp(edgeB, gndY - 2f * u, traces), bottom = edgeB) {
            drawLine(traceColor, Offset(gndX, edgeB), Offset(gndX, gndY), w, StrokeCap.Round)
        }
        // Signal 1: left edge → pin 1.
        clipRect(left = edgeL, right = lerp(edgeL, in1X + 2f * u, traces)) {
            drawLine(traceColor, Offset(edgeL, in1Y), Offset(in1X, in1Y), w, StrokeCap.Round)
        }
        // Signal 2: left edge → pin 2.
        clipRect(left = edgeL, right = lerp(edgeL, in2X + 2f * u, traces)) {
            drawLine(traceColor, Offset(edgeL, in2Y), Offset(in2X, in2Y), w, StrokeCap.Round)
        }

        // Rail labels.
        drawText(labelVcc, color = ChassisSilkscreen.copy(alpha = traces),
            topLeft = Offset(vccX + 8f * u, statusInsetPx + 10f * u))
        drawText(labelGnd, color = ChassisSilkscreen.copy(alpha = traces),
            topLeft = Offset(gndX - 26f * u, size.height - 40f * u))
    }

    // --- the package, fading in at the centre with a settling breath ------------------
    if (chipIn > 0f) {
        val settle = 1f + 0.03f * (1f - chipIn)
        withTransform({ translate(cx, cy); scale(settle, settle); translate(-cx, -cy) }) {
            drawPackage(state, t, u, cx, cy, bodyLeft, bodyRight, bodyTop, bodyBottom,
                leadLen, padR, pinY0, pinStep, dieLit)
        }
    }

    // --- current flows inward: the comet stream on every inflow trace -------------------
    if (t >= T_FLOW) {
        drawInflowStream(t, u, vccX, edgeT, vccY, in1X, edgeL, in1Y, in2Y, gndX, edgeB, gndY, dieLit, ledIgnite)
    }

    // --- the output trace and the LED -----------------------------------------------------
    if (outTrace > 0f) {
        val outColor = PhosphorCore.copy(alpha = 0.40f + 0.40f * ledIgnite)
        val w = 2.2f * u
        // Pin 3 → elbow (horizontal), revealed left → right.
        clipRect(left = outX, right = lerp(outX, elbowX, outTrace)) {
            drawLine(outColor, Offset(outX, outY), Offset(elbowX, outY), w, StrokeCap.Round)
        }
        // Elbow → LED (vertical), revealed top → down.
        clipRect(top = outY, bottom = lerp(outY, ledY, outTrace)) {
            drawLine(outColor, Offset(elbowX, outY), Offset(elbowX, ledY), w, StrokeCap.Round)
        }
        // The LED's return to ground — dim, quiet, real.
        val returnAlpha = 0.22f * outTrace
        clipRect(top = ledY, bottom = lerp(ledY, edgeB, outTrace)) {
            drawLine(
                SplashLead.copy(alpha = returnAlpha),
                Offset(returnX, ledY), Offset(returnX, edgeB), 1.4f * u, StrokeCap.Round
            )
        }
    }

    // The answer runs out: one comet, pin 3 → elbow → LED.
    val outS = frac(t, T_OUT_PULSE, T_OUT_PULSE + 300f)
    if (outS > 0f && outS < 1f) {
        val seg1 = (elbowX - outX)
        val seg2 = (ledY - outY)
        val total = seg1 + seg2
        val d = outS * total
        val px: Float
        val py: Float
        val dirX: Float
        val dirY: Float
        if (d <= seg1) {
            px = outX + d; py = outY; dirX = 1f; dirY = 0f
        } else {
            px = elbowX; py = outY + (d - seg1); dirX = 0f; dirY = 1f
        }
        drawComet(u, px, py, dirX, dirY, 1f)
    }

    // --- the LED: the payoff ------------------------------------------------------------
    drawLed(t, u, ledX, ledY, ledR, ledIgnite)

    // --- part-number silkscreen ------------------------------------------------------------
    val silk = frac(t, T_SILKSCREEN, T_SILKSCREEN + 300f)
    if (silk > 0f) {
        drawText(
            labelPart,
            color = lerp(SplashLeadDim, ChassisSilkscreen, silk).copy(alpha = 0.9f),
            topLeft = Offset(cx - labelPart.size.width / 2f, bodyBottom - 21f * u)
        )
    }

    // --- seven-segment POST counter, top-right -----------------------------------------------
    drawPostCounter(t, statusInsetPx, u)

    // --- vignette -------------------------------------------------------------------------------
    if (state.vignette == null || state.vignetteW != size.width || state.vignetteH != size.height) {
        state.vignette = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = max(size.width, size.height) * 0.85f
        )
        state.vignetteW = size.width
        state.vignetteH = size.height
    }
    drawRect(state.vignette!!)

    // --- final wash: the LED's light blooms over the frame, then hands off ------------------------
    if (ledIgnite >= 1f && flash > 0f) {
        drawRect(LedRubyBloom.copy(alpha = 0.08f * flash))
        drawCircle(LedRubyBloom.copy(alpha = 0.20f * flash), radius = (60f + 300f * flash) * u, center = Offset(ledX, ledY))
    }
}

/** The DIP package: body, notch, pin-1 dot, leads, the die and its gates. */
private fun DrawScope.drawPackage(
    state: SplashDrawState,
    t: Float,
    u: Float,
    cx: Float, cy: Float,
    bodyLeft: Float, bodyRight: Float, bodyTop: Float, bodyBottom: Float,
    leadLen: Float, padR: Float, pinY0: Float, pinStep: Float,
    dieLit: Float
) {
    val bodyW = bodyRight - bodyLeft
    val bodyH = bodyBottom - bodyTop
    val corner = 7f * u

    val bodyColor = lerp(SplashEpoxy, SplashEpoxyTop, 0.35f + 0.65f * dieLit)
    drawRoundRect(
        color = bodyColor,
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyW, bodyH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
    )
    drawRoundRect(
        color = SplashLead.copy(alpha = 0.35f + 0.25f * dieLit),
        topLeft = Offset(bodyLeft, bodyTop),
        size = Size(bodyW, bodyH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
        style = Stroke(width = 1.8f * u)
    )

    drawCircle(Color(0xFF101319), radius = 8f * u, center = Offset(cx, bodyTop))
    val pin1Lit = smooth(frac(t, T_DIE_LIT + 40f, T_DIE_LIT + 260f))
    drawCircle(
        color = lerp(SplashLeadDim, PhosphorCore, pin1Lit),
        radius = 2.8f * u,
        center = Offset(bodyLeft + 13f * u, bodyTop + 15f * u)
    )

    for (i in 0 until 7) {
        val y = pinY0 + i * pinStep
        val lead = lerp(SplashLeadDim, SplashLead, 0.5f + 0.5f * dieLit)
        drawLine(lead, Offset(bodyLeft, y), Offset(bodyLeft - leadLen, y), 2.6f * u, StrokeCap.Round)
        drawLine(lead, Offset(bodyRight, y), Offset(bodyRight + leadLen, y), 2.6f * u, StrokeCap.Round)
        drawCircle(SplashLead, padR, Offset(bodyLeft - leadLen, y))
        drawCircle(SplashLead, padR, Offset(bodyRight + leadLen, y))
    }

    if (dieLit > 0f) {
        val die = state.die
        die.rewind()
        val dw = 62f * u
        val dh = 21f * u
        val dl = cx - dw / 2f
        val dt = cy + 4f * u - dh / 2f
        die.addRect(androidx.compose.ui.geometry.Rect(dl, dt, dl + dw, dt + dh))
        drawPath(die, PhosphorCore.copy(alpha = 0.20f + 0.30f * dieLit))
        drawPath(die, PhosphorCore.copy(alpha = 0.55f + 0.35f * dieLit), style = Stroke(1.5f * u))
        for (k in 1 until 5) {
            val rx = dl + dw * k / 5f
            drawLine(PhosphorCore.copy(alpha = 0.45f * dieLit),
                Offset(rx, dt + dh), Offset(rx, dt + dh + 7f * u), 1.1f * u)
        }
    }

    drawGate(state, u, cx - 46f * u, cy - 18f * u, GateGlyph.AND, smooth(frac(t, T_GATE_IGNITE, T_GATE_IGNITE + 190f)))
    drawGate(state, u, cx, cy - 18f * u, GateGlyph.OR, smooth(frac(t, T_GATE_IGNITE + 240f, T_GATE_IGNITE + 430f)))
    drawGate(state, u, cx + 46f * u, cy - 18f * u, GateGlyph.NOT, smooth(frac(t, T_GATE_IGNITE + 480f, T_GATE_IGNITE + 670f)))
}

private enum class GateGlyph { AND, OR, NOT }

/** One mini gate symbol inside the package, dim epoxy-line until its ignition window. */
private fun DrawScope.drawGate(
    state: SplashDrawState,
    u: Float,
    x: Float, y: Float,
    glyph: GateGlyph,
    ignite: Float
) {
    val g = state.gate
    g.rewind()
    val w = 25f * u
    val h = 15f * u

    when (glyph) {
        GateGlyph.AND -> {
            g.moveTo(x - w / 2f, y - h / 2f)
            g.lineTo(x, y - h / 2f)
            g.cubicTo(x + w / 2f, y - h / 2f, x + w / 2f, y + h / 2f, x, y + h / 2f)
            g.lineTo(x - w / 2f, y + h / 2f)
            g.close()
        }
        GateGlyph.OR -> {
            g.moveTo(x - w / 2f, y - h / 2f)
            g.quadraticBezierTo(x + w * 0.1f, y, x - w / 2f, y + h / 2f)
            g.quadraticBezierTo(x + w * 0.55f, y + h / 2f, x + w / 2f, y)
            g.quadraticBezierTo(x + w * 0.55f, y - h / 2f, x - w / 2f, y - h / 2f)
            g.close()
        }
        GateGlyph.NOT -> {
            val tw = 15f * u
            val th = 11f * u
            g.moveTo(x - tw / 2f, y - th / 2f)
            g.lineTo(x + tw / 2f, y)
            g.lineTo(x - tw / 2f, y + th / 2f)
            g.close()
        }
    }

    drawPath(g, lerp(SplashLeadDim, PhosphorCore, ignite).copy(alpha = 0.85f),
        style = Stroke(1.7f * u, join = StrokeJoin.Round))
    if (ignite > 0f) {
        drawPath(g, PhosphorBloom.copy(alpha = 0.20f * ignite),
            style = Stroke(4.2f * u, join = StrokeJoin.Round))
        if (glyph == GateGlyph.NOT) {
            drawCircle(
                lerp(SplashLeadDim, PhosphorCore, ignite).copy(alpha = 0.85f),
                radius = 2.4f * u,
                center = Offset(x + 11.6f * u, y),
                style = Stroke(1.5f * u)
            )
        }
    }
}

/**
 * The inward current: a fresh comet enters each inflow trace every [T_COMET_MS],
 * staggered round-robin (VCC, IN1, GND, IN2), so several are always in flight and
 * everything visibly converges on the package. Energy settles once the chip is lit.
 */
private fun DrawScope.drawInflowStream(
    t: Float,
    u: Float,
    vccX: Float, edgeT: Float, vccY: Float,
    inX: Float, edgeL: Float, in1Y: Float, in2Y: Float,
    gndX: Float, edgeB: Float, gndY: Float,
    dieLit: Float,
    ledIgnite: Float
) {
    val elapsed = t - T_FLOW
    val waves = (elapsed / T_COMET_MS).toInt() + 1
    for (k in 0 until waves) {
        val energy = when {
            k == 0 -> 1f
            ledIgnite >= 1f -> 0.35f
            else -> 0.55f + 0.35f * dieLit
        }
        val base = T_FLOW + k * T_COMET_MS

        // VCC: top edge → pin 14 (downward).
        frac(t, base, base + T_COMET_TRAVEL).let { s ->
            if (s > 0f && s < 1f) drawComet(u, vccX, lerp(edgeT, vccY, s), 0f, 1f, energy)
        }
        // Signal 1: left edge → pin 1 (rightward).
        frac(t, base + 65f, base + 65f + T_COMET_TRAVEL).let { s ->
            if (s > 0f && s < 1f) drawComet(u, lerp(edgeL, inX, s), in1Y, 1f, 0f, energy)
        }
        // GND: bottom edge → pin 7 (upward).
        frac(t, base + 130f, base + 130f + T_COMET_TRAVEL).let { s ->
            if (s > 0f && s < 1f) drawComet(u, gndX, lerp(edgeB, gndY, s), 0f, -1f, energy)
        }
        // Signal 2: left edge → pin 2 (rightward).
        frac(t, base + 195f, base + 195f + T_COMET_TRAVEL).let { s ->
            if (s > 0f && s < 1f) drawComet(u, lerp(edgeL, inX, s), in2Y, 1f, 0f, energy)
        }
    }
}

/**
 * A comet at (x, y) travelling along the unit vector (dirX, dirY): cyan halo, bright
 * head, and a tail trailing opposite the travel direction.
 */
private fun DrawScope.drawComet(u: Float, x: Float, y: Float, dirX: Float, dirY: Float, energy: Float) {
    val tail = 22f * u
    drawLine(
        PhosphorCore.copy(alpha = 0.20f * energy),
        Offset(x - dirX * tail * 0.55f, y - dirY * tail * 0.55f),
        Offset(x - dirX * tail, y - dirY * tail),
        5f * u,
        StrokeCap.Round
    )
    drawLine(
        PhosphorCore.copy(alpha = 0.45f * energy),
        Offset(x - dirX * tail * 0.25f, y - dirY * tail * 0.25f),
        Offset(x - dirX * tail * 0.55f, y - dirY * tail * 0.55f),
        3.2f * u,
        StrokeCap.Round
    )
    drawCircle(SplashCyan.copy(alpha = 0.14f * energy), radius = 9f * u, center = Offset(x, y))
    drawCircle(PhosphorBloom.copy(alpha = 0.28f * energy), radius = 7f * u, center = Offset(x, y))
    drawCircle(LedOnCore.copy(alpha = 0.95f * energy), radius = 2.9f * u, center = Offset(x, y))
}

/**
 * The indicator LED beside the chip — the sequence's payoff. Dark ruby lens until the
 * output pulse arrives, then a red bloom with a white-hot core, soft rays, and a
 * gentle breathing glow once fully lit.
 */
private fun DrawScope.drawLed(
    t: Float,
    u: Float,
    ledX: Float, ledY: Float, ledR: Float,
    ignite: Float
) {
    // Leads: the dome sits on the output trace's end, with the return leaving below.
    drawLine(SplashLead, Offset(ledX, ledY - ledR - 6f * u), Offset(ledX, ledY - ledR), 2f * u, StrokeCap.Round)
    // Bezel ring.
    drawCircle(SplashLead.copy(alpha = 0.6f), radius = ledR + 3f * u, center = Offset(ledX, ledY), style = Stroke(1.6f * u))

    if (ignite <= 0f) {
        // Dark ruby lens, unlit: deep red with a cold specular fleck.
        drawCircle(LedRubyOff, radius = ledR, center = Offset(ledX, ledY))
        drawCircle(Color(0x33FFFFFF), radius = ledR * 0.30f, center = Offset(ledX - ledR * 0.3f, ledY - ledR * 0.35f))
        return
    }

    // Ignited: layered bloom that breathes once settled.
    val breathe = if (ignite >= 1f) 1f + 0.06f * sin(t / 260f) else 1f
    val a = ignite
    drawCircle(LedRubyOn.copy(alpha = 0.08f * a), radius = (34f * u + 26f * u * a) * breathe, center = Offset(ledX, ledY))
    drawCircle(LedRubyBloom.copy(alpha = 0.16f * a), radius = (20f * u + 12f * u * a) * breathe, center = Offset(ledX, ledY))
    drawCircle(LedRubyOn.copy(alpha = 0.42f * a), radius = ledR * breathe, center = Offset(ledX, ledY))
    // Soft rays around the dome.
    for (k in 0 until 6) {
        val ang = (k / 6f) * 6.2832f + 0.52f
        drawLine(
            LedRubyBloom.copy(alpha = 0.22f * a),
            Offset(ledX + (ledR + 5f * u) * kotlin.math.cos(ang), ledY + (ledR + 5f * u) * kotlin.math.sin(ang)),
            Offset(ledX + (ledR + 13f * u) * kotlin.math.cos(ang), ledY + (ledR + 13f * u) * kotlin.math.sin(ang)),
            1.6f * u,
            StrokeCap.Round
        )
    }
    // White-hot core + dome fill.
    drawCircle(lerp(LedRubyOff, LedRubyOn, a), radius = ledR, center = Offset(ledX, ledY))
    drawCircle(LedOnCore.copy(alpha = 0.55f * a), radius = ledR * 0.45f, center = Offset(ledX, ledY))
    drawCircle(LedOnCore.copy(alpha = 0.95f * a), radius = ledR * 0.22f, center = Offset(ledX, ledY))
}

// ---------------------------------------------------------------------------
// Seven-segment POST counter
// ---------------------------------------------------------------------------

/**
 * The two-digit seven-segment counter: a memory-test sweep from 00 while the POST
 * runs, settling on "LL" (segments E-F-D — the closest a 7448 gets to an L) once
 * power lands.
 */
private fun DrawScope.drawPostCounter(
    t: Float,
    statusInsetPx: Float,
    u: Float
) {
    val appear = frac(t, 250f, 500f)
    if (appear <= 0f) return

    val powered = t >= T_LED
    val value = (frac(t, 250f, T_LED) * 255f).toInt()
    val maskHi = if (powered) LL_MASK else SevenSegFont.segmentsFor(value shr 4)
    val maskLo = if (powered) LL_MASK else SevenSegFont.segmentsFor(value)

    val digitW = 13f * u
    val digitH = 22f * u
    val thickness = 2.6f * u
    val right = size.width - 18f * u
    val cy = statusInsetPx + 26f * u

    drawSevenSegDigit(
        cx = right - digitW / 2f, cy = cy, w = digitW, h = digitH,
        mask = maskLo, thickness = thickness, alpha = appear
    )
    drawSevenSegDigit(
        cx = right - digitW * 1.5f - 6f * u, cy = cy, w = digitW, h = digitH,
        mask = maskHi, thickness = thickness, alpha = appear
    )
}

/** Segments E, F and D — the 7-seg approximation of a lowercase "L". */
private const val LL_MASK = SevenSegFont.SEG_E or SevenSegFont.SEG_F or SevenSegFont.SEG_D

private fun DrawScope.drawSevenSegDigit(
    cx: Float, cy: Float, w: Float, h: Float,
    mask: Int, thickness: Float, alpha: Float
) {
    val x0 = cx - w / 2f
    val x1 = cx + w / 2f
    val y0 = cy - h / 2f
    val y1 = cy + h / 2f
    val g = thickness * 0.9f

    fun seg(bit: Int, ax: Float, ay: Float, bx: Float, by: Float) {
        val lit = mask and bit != 0
        val color = if (lit) SevenSegLit else SevenSegLeak
        drawLine(
            color = color.copy(alpha = if (lit) alpha else alpha * 0.6f),
            start = Offset(ax, ay),
            end = Offset(bx, by),
            strokeWidth = thickness,
            cap = StrokeCap.Butt
        )
    }

    seg(SevenSegFont.SEG_A, x0 + g, y0, x1 - g, y0)
    seg(SevenSegFont.SEG_B, x1, y0 + g, x1, cy - g)
    seg(SevenSegFont.SEG_C, x1, cy + g, x1, y1 - g)
    seg(SevenSegFont.SEG_D, x0 + g, y1, x1 - g, y1)
    seg(SevenSegFont.SEG_E, x0, cy + g, x0, y1 - g)
    seg(SevenSegFont.SEG_F, x0, y0 + g, x0, cy - g)
    seg(SevenSegFont.SEG_G, x0 + g, cy, x1 - g, cy)
}
