package com.logiclabs.feature.instruments.ui.hardware

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logiclabs.core.designsystem.theme.ChassisBevelShadow
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.LedBezelRing
import com.logiclabs.core.designsystem.theme.LedLensSpecular
import com.logiclabs.core.designsystem.theme.LedOffForest
import com.logiclabs.core.designsystem.theme.LedOnAura
import com.logiclabs.core.designsystem.theme.LedOnBody
import com.logiclabs.core.designsystem.theme.LedOnCore
import com.logiclabs.core.designsystem.theme.LocalRenderEffects
import com.logiclabs.core.designsystem.theme.PhosphorBloom
import com.logiclabs.core.designsystem.theme.TextTertiary

/** Rise time of the phosphor, ms — LEDs light faster than they fade. */
private const val RiseMs = 40

/** Decay time, ms. Deliberately longer than the rise so POV blinking reads. */
private const val DecayMs = 90

/**
 * A discrete LED indicator lamp with a real lens, bezel ring and light spill.
 *
 * Replaces the old `LedIndicator`, which drew OFF as a saturated
 * `#E53935 → #B71C1C` radial gradient — a bright red disc that looked *lit*.
 * Here OFF is a dark translucent [LedOffForest] lens with a single specular
 * highlight, so an unlit LED is unmistakably unlit.
 *
 * [dutyCycle] comes straight from `BreadboardCircuit.ledPovDutyCycles` and
 * modulates apparent brightness, so a fast-toggling net dims instead of
 * strobing at whatever the frame rate happens to be.
 */
@Composable
fun LedLens(
    isOn: Boolean,
    dutyCycle: Float,
    label: String,
    modifier: Modifier = Modifier,
    lensSize: Dp = Dimens.LedLensSize,
    onColor: Color = LedOnBody,
    auraColor: Color = LedOnAura,
    labelOnColor: Color = PhosphorBloom,
) {
    // Target luminance: 0 when dark, duty-scaled when driven. The floor keeps a
    // very low duty cycle visible rather than invisible.
    val target = if (isOn) dutyCycle.coerceIn(0f, 1f).coerceAtLeast(0.35f) else 0f

    // Bloom gate from Settings: with effects off, the lens still lights but stops
    // spilling onto the chassis.
    val auraScale = if (LocalRenderEffects.current) 1f else 0f

    // Asymmetric afterglow — a single Animatable so rise and decay can differ.
    // TODO(tokens): switch to Motion.LedRise / Motion.LedDecay
    val glow = remember { Animatable(target) }
    LaunchedEffect(target) {
        val rising = target > glow.value
        glow.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = if (rising) RiseMs else DecayMs,
                easing = LinearEasing,
            ),
        )
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Canvas is oversized so the outer aura can spill onto the chassis
        // instead of being clipped at the lens edge.
        Canvas(modifier = Modifier.size(lensSize * 1.9f)) {
            drawLedLens(
                glow = glow.value,
                lensRadius = lensSize.toPx() / 2f,
                onColor = onColor,
                auraColor = auraColor,
                auraScale = auraScale,
            )
        }
        Spacer(modifier = Modifier.height(1.dp))
        // TODO(tokens): switch to LogicLabsType.MicroLabel
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.4.sp,
            fontFamily = FontFamily.Monospace,
            color = if (glow.value > 0.05f) labelOnColor else TextTertiary,
        )
    }
}

/**
 * Lens geometry. Pure draw calls plus per-frame [Brush.radialGradient]s, which
 * are lightweight value objects — the expensive things (paths, layout) are not
 * allocated here.
 */
private fun DrawScope.drawLedLens(
    glow: Float,
    lensRadius: Float,
    onColor: Color,
    auraColor: Color,
    auraScale: Float = 1f,
) {
    if (lensRadius <= 0f) return
    val c = Offset(size.width / 2f, size.height / 2f)

    // --- Two-layer aura, drawn first so the lens sits on top of its own spill.
    if (glow > 0.01f && auraScale > 0.01f) {
        val outer = lensRadius * 3.4f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    auraColor.copy(alpha = 0.16f * glow * auraScale),
                    Color.Transparent,
                ),
                center = c,
                radius = outer,
            ),
            radius = outer,
            center = c,
        )
        val inner = lensRadius * 2.2f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    auraColor.copy(alpha = 0.34f * glow * auraScale),
                    Color.Transparent,
                ),
                center = c,
                radius = inner,
            ),
            radius = inner,
            center = c,
        )
    }

    // --- Bezel: metal ring plus a contact shadow beneath it.
    drawCircle(
        color = ChassisBevelShadow.copy(alpha = 0.85f),
        radius = lensRadius * 1.18f,
        center = Offset(c.x + lensRadius * 0.06f, c.y + lensRadius * 0.10f),
    )
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(LedBezelRing, ChassisBevelShadow),
            start = Offset(c.x - lensRadius, c.y - lensRadius),
            end = Offset(c.x + lensRadius, c.y + lensRadius),
        ),
        radius = lensRadius * 1.14f,
        center = c,
        style = Stroke(width = lensRadius * 0.26f),
    )

    // --- Lens body: cylindrical depth via an off-centre radial gradient.
    if (glow > 0.01f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    LedOnCore.copy(alpha = 0.55f + 0.45f * glow),
                    onColor.copy(alpha = 0.65f + 0.35f * glow),
                    onColor.copy(alpha = 0.85f),
                    LedOffForest,
                ),
                center = Offset(c.x - lensRadius * 0.22f, c.y - lensRadius * 0.26f),
                radius = lensRadius * 1.6f,
            ),
            radius = lensRadius,
            center = c,
        )
        // White-hot die at the centre of the epoxy dome.
        drawCircle(
            color = LedOnCore.copy(alpha = 0.75f * glow),
            radius = lensRadius * 0.30f,
            center = Offset(c.x - lensRadius * 0.05f, c.y - lensRadius * 0.08f),
        )
    } else {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    LedOffForest.copy(alpha = 0.92f),
                    LedOffForest,
                    ChassisBevelShadow,
                ),
                center = Offset(c.x - lensRadius * 0.25f, c.y - lensRadius * 0.30f),
                radius = lensRadius * 1.5f,
            ),
            radius = lensRadius,
            center = c,
        )
    }

    // --- Specular highlight arc, upper-left: consistent light source.
    val specRadius = lensRadius * 0.66f
    drawArc(
        color = LedLensSpecular.copy(alpha = if (glow > 0.01f) 0.5f else 0.32f),
        startAngle = 168f,
        sweepAngle = 86f,
        useCenter = false,
        topLeft = Offset(c.x - specRadius, c.y - specRadius),
        size = Size(specRadius * 2f, specRadius * 2f),
        style = Stroke(width = lensRadius * 0.16f),
    )
}
