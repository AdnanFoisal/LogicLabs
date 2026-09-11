package com.logiclabs.feature.instruments.ui.hardware

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.AmberCore
import com.logiclabs.core.designsystem.theme.ChassisBevelHighlight
import com.logiclabs.core.designsystem.theme.ChassisBevelShadow
import com.logiclabs.core.designsystem.theme.ChassisSilkscreen
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.PhosphorBloom
import com.logiclabs.core.designsystem.theme.PhosphorCore
import com.logiclabs.core.designsystem.theme.ShortCircuitAlert
import com.logiclabs.core.designsystem.theme.SwitchWell
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.core.designsystem.theme.TextTertiary
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Selectable clock rates, in Hz. Detented — a real function generator's range
 * switch clicks between decades, it does not sweep continuously.
 */
val ClockSteps: DoubleArray = doubleArrayOf(0.5, 1.0, 2.0, 5.0, 10.0, 100.0, 1_000.0, 10_000.0, 100_000.0)

/** Nearest detent index for an arbitrary frequency (log-domain distance). */
fun clockStepIndexFor(hz: Double): Int {
    if (hz <= 0.0) return 1
    var best = 0
    var bestErr = Double.MAX_VALUE
    for (i in ClockSteps.indices) {
        val err = abs(Math.log10(ClockSteps[i]) - Math.log10(hz))
        if (err < bestErr) {
            bestErr = err
            best = i
        }
    }
    return best
}

/** `0.5 Hz`, `10 Hz`, `1.0 kHz`, `100 kHz` — never `100000.0`. */
fun formatClockFrequency(hz: Double): String = when {
    hz >= 1_000.0 -> {
        val k = hz / 1_000.0
        if (k == k.roundToInt().toDouble()) "${k.roundToInt()} kHz" else String.format("%.1f kHz", k)
    }
    hz == hz.roundToInt().toDouble() -> "${hz.roundToInt()} Hz"
    else -> String.format("%.1f Hz", hz)
}

/**
 * The variable clock generator: knurled rotary rate knob, RUN/STOP control and
 * an activity LED driven by the live `clockState` line.
 *
 * This control had **no UI at all** before this phase — `clockFrequencyHz`,
 * `clockRunning` and `clockState` existed in the model with nothing driving
 * them, which is why every flip-flop and counter lab sat dead.
 *
 * Interaction: tap the knob to advance one detent, or drag it round to rotate.
 * Both land on a [ClockSteps] value; nothing in between is reportable.
 */
@Composable
fun ClockDial(
    frequencyHz: Double,
    isRunning: Boolean,
    clockState: Boolean,
    onFrequencyChange: (Double) -> Unit,
    onRunningChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    dialSize: Dp = Dimens.ClockDialSize,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val knobPressed by interaction.collectIsPressedAsState()
    val freqCallback = rememberUpdatedState(onFrequencyChange)

    val stepIndex = clockStepIndexFor(frequencyHz)
    val lastIndex = ClockSteps.size - 1
    // Live detent index for the gesture callbacks below. The knob's
    // `pointerInput` is keyed on Unit on purpose: keying it on the step index
    // restarts the drag detector after every detent and cancels the in-flight
    // gesture, so a single drag could only ever advance one detent.
    val currentStepIndex by rememberUpdatedState(stepIndex)

    // Index mark sweeps 300 degrees across the range, like a real pot.
    val targetAngle = -150f + 300f * (stepIndex.toFloat() / lastIndex)
    // TODO(tokens): switch to Motion.KnobSettle
    val angle by animateFloatAsState(
        targetValue = targetAngle,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "clockKnobAngle",
    )

    // Drag accumulator: ~26dp of travel per detent, so a flick is not a decade jump.
    val dragAccum = remember { mutableFloatStateOf(0f) }

    fun advance(delta: Int) {
        val next = (currentStepIndex + delta).coerceIn(0, lastIndex)
        if (next != currentStepIndex) {
            PanelHaptics.tick(context, view)
            freqCallback.value(ClockSteps[next])
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space3),
    ) {
        // --- Knob -----------------------------------------------------------
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .sizeIn(minWidth = Dimens.MinTouchTarget, minHeight = Dimens.MinTouchTarget)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                    ) {
                        // Tap-to-advance, wrapping at the top of the range.
                        PanelHaptics.tick(context, view)
                        val next = if (currentStepIndex >= lastIndex) 0 else currentStepIndex + 1
                        freqCallback.value(ClockSteps[next])
                    }
                    .pointerInput(Unit) {
                        val perDetent = 26.dp.toPx()
                        detectDragGestures(
                            onDragEnd = { dragAccum.floatValue = 0f },
                            onDragCancel = { dragAccum.floatValue = 0f },
                        ) { _, drag ->
                            // Right / up rotates clockwise = faster.
                            dragAccum.floatValue += drag.x - drag.y
                            while (dragAccum.floatValue >= perDetent) {
                                dragAccum.floatValue -= perDetent
                                advance(1)
                            }
                            while (dragAccum.floatValue <= -perDetent) {
                                dragAccum.floatValue += perDetent
                                advance(-1)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.size(dialSize)) {
                    drawClockKnob(angle, isRunning, knobPressed)
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            // TODO(tokens): switch to LogicLabsType.MicroLabel
            Text(
                text = "RATE",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                fontFamily = FontFamily.Monospace,
                color = ChassisSilkscreen,
            )
        }

        // --- Readout + RUN/STOP + activity LED ------------------------------
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.Space1)) {
            // TODO(tokens): switch to LogicLabsType.Readout
            Text(
                text = formatClockFrequency(frequencyHz),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isRunning) PhosphorBloom else TextSecondary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.Space2),
            ) {
                RunStopButton(isRunning = isRunning, onRunningChange = onRunningChange)
                // Activity lamp: flashes with the live clock line.
                LedLens(
                    isOn = isRunning && clockState,
                    dutyCycle = 1f,
                    label = "CLK",
                    lensSize = 14.dp,
                    onColor = AmberCore,
                    auraColor = AmberCore,
                    labelOnColor = AmberCore,
                )
            }
        }
    }
}

/** Latching RUN/STOP control with a machined face. */
@Composable
private fun RunStopButton(
    isRunning: Boolean,
    onRunningChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .sizeIn(minWidth = Dimens.MinTouchTarget, minHeight = 30.dp)
            .clip(RoundedCornerShape(Dimens.RadiusSm))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Switch,
            ) {
                PanelHaptics.tick(context, view)
                onRunningChange(!isRunning)
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(width = 62.dp, height = 26.dp)) {
            val r = 4.dp.toPx()
            val cr = androidx.compose.ui.geometry.CornerRadius(r, r)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = if (isRunning) {
                        listOf(Color(0xFF1F3A28), Color(0xFF122318))
                    } else {
                        listOf(Color(0xFF2A2E36), Color(0xFF171A1F))
                    },
                    startY = 0f,
                    endY = size.height,
                ),
                size = size,
                cornerRadius = cr,
            )
            drawRoundRect(
                color = if (isRunning) PhosphorCore.copy(alpha = 0.6f) else ChassisBevelHighlight,
                size = size,
                cornerRadius = cr,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        // TODO(tokens): switch to LogicLabsType.ButtonLabel
        Text(
            text = if (isRunning) "RUN" else "STOP",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            fontFamily = FontFamily.Monospace,
            color = if (isRunning) PhosphorBloom else TextPrimary,
            modifier = Modifier.padding(horizontal = Dimens.Space2),
        )
    }
}

/** Number of knurl ridges around the knob skirt. */
private const val KnurlCount = 28

/** Number of tick marks on the surrounding scale ring. */
private const val TickCount = 9

/**
 * Knurled aluminium knob in a tick ring, with a rotating index mark.
 * All geometry is trig-on-primitives; no paths, no per-frame allocation beyond
 * cheap gradient descriptors.
 */
private fun DrawScope.drawClockKnob(angleDeg: Float, isRunning: Boolean, pressed: Boolean = false) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val outer = size.minDimension / 2f
    // Pressed feedback: the knob shrinks a hair inside its (unscaled) tick
    // ring, so a grab is visible even before the first detent advances.
    val knobR = outer * 0.72f * if (pressed) 0.94f else 1f

    // --- Tick ring on the chassis behind the knob.
    for (i in 0 until TickCount) {
        val a = -150f + 300f * (i.toFloat() / (TickCount - 1))
        val major = i == 0 || i == TickCount - 1 || i == 4
        val len = if (major) outer * 0.20f else outer * 0.12f
        val rad = Math.toRadians(a.toDouble() - 90.0)
        val cos = Math.cos(rad).toFloat()
        val sin = Math.sin(rad).toFloat()
        drawLine(
            color = if (major) ChassisSilkscreen else ChassisSilkscreen.copy(alpha = 0.45f),
            start = Offset(c.x + cos * (outer - len), c.y + sin * (outer - len)),
            end = Offset(c.x + cos * (outer - outer * 0.03f), c.y + sin * (outer - outer * 0.03f)),
            strokeWidth = if (major) outer * 0.05f else outer * 0.03f,
            cap = StrokeCap.Round,
        )
    }

    // --- Recess the knob is seated in.
    drawCircle(color = SwitchWell, radius = knobR * 1.12f, center = c)
    drawCircle(
        color = ChassisBevelShadow,
        radius = knobR * 1.12f,
        center = c,
        style = Stroke(width = outer * 0.04f),
    )
    // Contact shadow, offset down-right from the top-left light.
    drawCircle(
        color = Color.Black.copy(alpha = 0.45f),
        radius = knobR,
        center = Offset(c.x + outer * 0.05f, c.y + outer * 0.07f),
    )

    // --- Knurled skirt: ridges lit on their upper-left flank.
    rotate(degrees = angleDeg, pivot = c) {
        for (i in 0 until KnurlCount) {
            val a = (360f / KnurlCount) * i
            val rad = Math.toRadians(a.toDouble())
            val cos = Math.cos(rad).toFloat()
            val sin = Math.sin(rad).toFloat()
            // Facing the light source (up-left) means a brighter ridge.
            val facing = ((-cos - sin) / 2f + 0.5f).coerceIn(0f, 1f)
            drawLine(
                color = lerpColor(Color(0xFF2A2D33), Color(0xFF8B919C), facing),
                start = Offset(c.x + cos * knobR * 0.80f, c.y + sin * knobR * 0.80f),
                end = Offset(c.x + cos * knobR, c.y + sin * knobR),
                strokeWidth = outer * 0.055f,
                cap = StrokeCap.Round,
            )
        }
    }

    // --- Knob cap: brushed metal dome.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF787E89), Color(0xFF4B5058), Color(0xFF23262B)),
            center = Offset(c.x - knobR * 0.32f, c.y - knobR * 0.36f),
            radius = knobR * 1.5f,
        ),
        radius = knobR * 0.80f,
        center = c,
    )
    drawCircle(
        color = ChassisBevelHighlight.copy(alpha = 0.5f),
        radius = knobR * 0.80f,
        center = c,
        style = Stroke(width = outer * 0.02f),
    )

    // --- Index mark: a pointer engraved into the cap, rotating with the knob.
    rotate(degrees = angleDeg, pivot = c) {
        val markColor = if (isRunning) PhosphorBloom else AccentCyan
        drawLine(
            color = Color.Black.copy(alpha = 0.6f),
            start = Offset(c.x, c.y - knobR * 0.20f),
            end = Offset(c.x, c.y - knobR * 0.76f),
            strokeWidth = outer * 0.075f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = markColor,
            start = Offset(c.x, c.y - knobR * 0.22f),
            end = Offset(c.x, c.y - knobR * 0.74f),
            strokeWidth = outer * 0.045f,
            cap = StrokeCap.Round,
        )
    }

    // --- Upper-left specular sweep on the cap.
    val sr = knobR * 0.60f
    drawArc(
        color = Color.White.copy(alpha = 0.22f),
        startAngle = 168f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(c.x - sr, c.y - sr),
        size = Size(sr * 2f, sr * 2f),
        style = Stroke(width = outer * 0.05f),
    )
}

/**
 * Diagnostics readout for the fault flags the engine raises. Kept here so the
 * dock stays layout-only.
 */
@Composable
fun DiagnosticsStrip(
    contention: Boolean,
    reversePolarity: Boolean,
    oscillationClamped: Boolean,
    burnedChip: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Space3),
    ) {
        DiagnosticLamp("CONTENTION", contention, ShortCircuitAlert)
        DiagnosticLamp("REV POLARITY", reversePolarity, ShortCircuitAlert)
        DiagnosticLamp("OSC CLAMP", oscillationClamped, AmberCore)
        if (burnedChip) {
            DiagnosticLamp("IC BURNED", true, ShortCircuitAlert)
        }
    }
}

@Composable
private fun DiagnosticLamp(label: String, active: Boolean, activeColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(10.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f
            if (active) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(activeColor.copy(alpha = 0.45f), Color.Transparent),
                        center = c,
                        radius = r * 2.6f,
                    ),
                    radius = r * 2.6f,
                    center = c,
                )
            }
            drawCircle(
                color = if (active) activeColor else Color(0xFF23262B),
                radius = r * 0.78f,
                center = c,
            )
            drawCircle(
                color = ChassisBevelShadow,
                radius = r * 0.78f,
                center = c,
                style = Stroke(width = r * 0.22f),
            )
        }
        Spacer(modifier = Modifier.width(Dimens.Space1))
        // TODO(tokens): switch to LogicLabsType.MicroLabel
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = 0.6.sp,
            fontFamily = FontFamily.Monospace,
            color = if (active) activeColor else TextTertiary,
        )
    }
}
