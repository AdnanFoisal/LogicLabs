package com.logiclabs.feature.instruments.ui.hardware

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logiclabs.core.designsystem.theme.AmberBloom
import com.logiclabs.core.designsystem.theme.ChassisBevelHighlight
import com.logiclabs.core.designsystem.theme.ChassisBevelShadow
import com.logiclabs.core.designsystem.theme.ChassisSilkscreen
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.PulserDome
import com.logiclabs.core.designsystem.theme.PulserDomeLive
import com.logiclabs.core.designsystem.theme.PulserDomePressed
import com.logiclabs.core.designsystem.theme.TextTertiary

/**
 * A spring-loaded momentary pushbutton for PULSER A / PULSER B.
 *
 * Fixes the previous `PulserButton`, which used
 * `clickable { onPressChanged(!isPressed) }` — that **latched**: one tap left
 * `pulserAPressed = true` forever, so every edge-triggered lab (flip-flops,
 * counters, one-shots) saw a single permanent high instead of a pulse.
 *
 * Here a raw `pointerInput` gesture drives the model: press down reports
 * `true`, and release (or cancel, or the composable leaving composition while
 * held) reports `false`, exactly like a real spring-return switch.
 */
@Composable
fun MomentaryPulser(
    label: String,
    isPressed: Boolean,
    onPressChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    domeSize: Dp = Dimens.PulserSize,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val callback = rememberUpdatedState(onPressChanged)

    // Local gesture truth, so travel animates even before the model round-trips.
    var held by remember { mutableStateOf(false) }
    val down = held || isPressed

    // Safety net: if we are torn down mid-press, release the pulser so the
    // engine never keeps a stuck-high input.
    DisposableEffect(Unit) {
        onDispose { if (held) callback.value(false) }
    }

    // TODO(tokens): switch to Motion.ButtonPress
    val travel by animateFloatAsState(
        targetValue = if (down) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 1600f),
        label = "pulserTravel",
    )

    val travelPx = remember(density) { with(density) { 2.dp.toPx() } }

    Column(
        modifier = modifier.sizeIn(minWidth = Dimens.MinTouchTarget, minHeight = Dimens.MinTouchTarget),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // TODO(tokens): switch to LogicLabsType.PanelLabel
        Text(
            text = label.uppercase(),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            fontFamily = FontFamily.Monospace,
            color = ChassisSilkscreen,
        )
        Spacer(modifier = Modifier.height(Dimens.Space1))
        Box(
            modifier = Modifier
                .sizeIn(minWidth = Dimens.MinTouchTarget, minHeight = Dimens.MinTouchTarget)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        // --- Press: drives P=1 immediately.
                        val downChange = awaitFirstDown(requireUnconsumed = false)
                        downChange.consume()
                        held = true
                        PanelHaptics.tick(context, view)
                        callback.value(true)
                        // --- Release / cancel: always auto-returns to P=0.
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.all { !it.pressed }) {
                                event.changes.forEach { it.consume() }
                                break
                            }
                        }
                        held = false
                        callback.value(false)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(domeSize)) {
                drawPulserDome(travel, travelPx)
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        // TODO(tokens): switch to LogicLabsType.ReadoutSmall
        Text(
            text = if (down) "P=1" else "P=0",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (down) AmberBloom else TextTertiary,
        )
    }
}

/**
 * Domed pushbutton: chrome retaining ring, radial-gradient cap, and ~2dp of
 * travel with a darkened dome while depressed.
 */
private fun DrawScope.drawPulserDome(travel: Float, travelPx: Float) {
    val c = Offset(size.width / 2f, size.height / 2f + travelPx * travel)
    val r = size.width * 0.40f

    // Fixed housing bore the cap sinks into.
    drawCircle(
        color = ChassisBevelShadow,
        radius = r * 1.20f,
        center = Offset(size.width / 2f, size.height / 2f + travelPx * 0.6f),
    )
    // Chrome retaining ring (does not move).
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(ChassisBevelHighlight, PulserDome, ChassisBevelShadow),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height),
        ),
        radius = r * 1.16f,
        center = Offset(size.width / 2f, size.height / 2f),
        style = Stroke(width = r * 0.26f),
    )

    // Live glow when depressed — the button reads as electrically active.
    if (travel > 0.02f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(PulserDomeLive.copy(alpha = 0.28f * travel), Color.Transparent),
                center = c,
                radius = r * 2.4f,
            ),
            radius = r * 2.4f,
            center = c,
        )
    }

    // Cap: raised dome lit top-left, darkened and flattened when pressed.
    val capTop = lerpColor(Color(0xFF6A707A), PulserDomePressed, travel)
    val capMid = lerpColor(PulserDome, PulserDomePressed, travel)
    val capEdge = lerpColor(Color(0xFF23262B), Color(0xFF14161A), travel)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(capTop, capMid, capEdge),
            center = Offset(c.x - r * 0.30f, c.y - r * 0.34f),
            radius = r * 1.7f,
        ),
        radius = r,
        center = c,
    )
    // Amber ring lights when live.
    if (travel > 0.02f) {
        drawCircle(
            color = PulserDomeLive.copy(alpha = 0.55f * travel),
            radius = r * 0.82f,
            center = c,
            style = Stroke(width = r * 0.10f),
        )
    }
    // Specular arc, upper-left, dimming as the cap goes down into the bore.
    val sr = r * 0.64f
    drawArc(
        color = Color.White.copy(alpha = 0.30f * (1f - travel * 0.7f)),
        startAngle = 170f,
        sweepAngle = 88f,
        useCenter = false,
        topLeft = Offset(c.x - sr, c.y - sr),
        size = Size(sr * 2f, sr * 2f),
        style = Stroke(width = r * 0.14f),
    )
}
