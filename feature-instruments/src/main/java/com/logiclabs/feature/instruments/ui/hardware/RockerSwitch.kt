package com.logiclabs.feature.instruments.ui.hardware

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logiclabs.core.designsystem.theme.ChassisBevelHighlight
import com.logiclabs.core.designsystem.theme.ChassisBevelShadow
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.PhosphorBloom
import com.logiclabs.core.designsystem.theme.SwitchBezel
import com.logiclabs.core.designsystem.theme.SwitchLeverEdge
import com.logiclabs.core.designsystem.theme.SwitchLeverLit
import com.logiclabs.core.designsystem.theme.SwitchLeverShade
import com.logiclabs.core.designsystem.theme.SwitchWell
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.core.designsystem.theme.TextTertiary

/** Cached lever geometry for one rocker size. */
private class LeverGeometry(
    val top: Path,
    val bottom: Path,
    val wellRadius: CornerRadius,
    val bodyRadius: CornerRadius,
    val travel: Float,
    val leverHeight: Float,
)

/**
 * Builds the two lever faces once per size. The lever is a truncated wedge: the
 * face catching the light and the shaded side face, so it reads as a solid part
 * rather than a flat rectangle.
 */
private fun buildLever(w: Float, h: Float, radiusSm: Float, radiusMd: Float): LeverGeometry {
    val inset = w * 0.14f
    val leverW = w - inset * 2f
    val leverH = h * 0.44f
    val chamfer = leverW * 0.22f

    // Upper face (catches light from top-left): a trapezoid narrowing upward.
    val top = Path().apply {
        moveTo(inset + chamfer, 0f)
        lineTo(inset + leverW - chamfer, 0f)
        lineTo(inset + leverW, leverH * 0.42f)
        lineTo(inset, leverH * 0.42f)
        close()
    }
    // Lower face (in shadow): the body of the wedge falling away.
    val bottom = Path().apply {
        moveTo(inset, leverH * 0.42f)
        lineTo(inset + leverW, leverH * 0.42f)
        lineTo(inset + leverW - chamfer * 0.4f, leverH)
        lineTo(inset + chamfer * 0.4f, leverH)
        close()
    }

    return LeverGeometry(
        top = top,
        bottom = bottom,
        wellRadius = CornerRadius(radiusSm, radiusSm),
        bodyRadius = CornerRadius(radiusMd, radiusMd),
        travel = h - leverH - h * 0.10f,
        leverHeight = leverH,
    )
}

/**
 * One of the eight IDL-800A data switches (SW7..SW0).
 *
 * The whole column is the touch target and is floored at
 * [Dimens.MinTouchTarget] via `sizeIn`, even though the drawn rocker is
 * narrower — the old implementation let the hit area shrink with the graphic.
 */
@Composable
fun RockerSwitch(
    isOn: Boolean,
    label: String,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    rockerWidth: Dp = Dimens.RockerWidth,
    rockerHeight: Dp = Dimens.RockerHeight,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }

    val wPx = with(density) { rockerWidth.toPx() }
    val hPx = with(density) { rockerHeight.toPx() }
    val geometry = remember(wPx, hPx) {
        with(density) { buildLever(wPx, hPx, Dimens.RadiusSm.toPx(), Dimens.RadiusMd.toPx()) }
    }

    // Travel with a slight overshoot so the throw lands with a visible detent.
    // TODO(tokens): switch to Motion.SwitchThrow
    val travel by animateFloatAsState(
        targetValue = if (isOn) 0f else 1f,
        animationSpec = tween(durationMillis = 120, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "rockerTravel",
    )
    val detent by animateFloatAsState(
        targetValue = if (isOn) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.42f,
            stiffness = 900f,
        ),
        label = "rockerDetent",
    )

    Column(
        modifier = modifier
            .sizeIn(minWidth = Dimens.MinTouchTarget, minHeight = Dimens.MinTouchTarget)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Switch,
            ) {
                // Haptic only on the ON throw, matching a real bench habit: you feel
                // the switch latch into 1; releasing to 0 is silent.
                if (!isOn) PanelHaptics.tick(context, view)
                onToggle(!isOn)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // TODO(tokens): switch to LogicLabsType.ReadoutSmall
        Text(
            text = if (isOn) "1" else "0",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (isOn) PhosphorBloom else TextTertiary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Canvas(modifier = Modifier.size(rockerWidth, rockerHeight)) {
            drawRocker(geometry, travel, detent)
        }
        Spacer(modifier = Modifier.height(2.dp))
        // TODO(tokens): switch to LogicLabsType.MicroLabel
        Text(
            text = label,
            fontSize = 8.sp,
            letterSpacing = 0.4.sp,
            fontFamily = FontFamily.Monospace,
            color = TextSecondary,
        )
    }
}

/**
 * @param travel 0 = lever up (logic 1), 1 = lever down (logic 0)
 * @param detent 0..1 with spring overshoot, used for the snap flash on the rail
 */
private fun DrawScope.drawRocker(geometry: LeverGeometry, travel: Float, detent: Float) {
    // --- Bezel: raised metal collar, lit top-left.
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(ChassisBevelHighlight, SwitchBezel, ChassisBevelShadow),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        size = size,
        cornerRadius = geometry.bodyRadius,
    )
    // --- Recessed well the lever travels in.
    val wellInset = size.width * 0.10f
    drawRoundRect(
        color = SwitchWell,
        topLeft = Offset(wellInset, wellInset * 0.7f),
        size = Size(size.width - wellInset * 2f, size.height - wellInset * 1.4f),
        cornerRadius = geometry.wellRadius,
    )
    // Inverted bevel inside the well (shadow on top): it is a hole, not a bump.
    drawLine(
        ChassisBevelShadow,
        Offset(wellInset, wellInset * 0.7f),
        Offset(size.width - wellInset, wellInset * 0.7f),
        size.width * 0.045f,
    )

    // --- Detent notch marks on the well wall; flash on snap.
    val notchAlpha = 0.25f + 0.45f * (1f - kotlin.math.abs(detent * 2f - 1f))
    val notchY = size.height * 0.5f
    drawLine(
        ChassisBevelHighlight.copy(alpha = notchAlpha),
        Offset(wellInset, notchY),
        Offset(wellInset + size.width * 0.10f, notchY),
        size.width * 0.03f,
    )
    drawLine(
        ChassisBevelHighlight.copy(alpha = notchAlpha),
        Offset(size.width - wellInset - size.width * 0.10f, notchY),
        Offset(size.width - wellInset, notchY),
        size.width * 0.03f,
    )

    // --- Lever, translated along its travel.
    val dy = size.height * 0.08f + geometry.travel * travel
    translate(0f, dy) {
        // Contact shadow under the lever, so it floats above the well floor.
        translate(size.width * 0.03f, size.height * 0.03f) {
            drawPath(geometry.top, ChassisBevelShadow.copy(alpha = 0.8f))
            drawPath(geometry.bottom, ChassisBevelShadow.copy(alpha = 0.8f))
        }
        // Upper face: lit.
        drawPath(
            geometry.top,
            Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.55f), SwitchLeverLit),
                start = Offset(0f, 0f),
                end = Offset(size.width, geometry.leverHeight * 0.42f),
            ),
        )
        // Lower face: shaded.
        drawPath(
            geometry.bottom,
            Brush.verticalGradient(
                colors = listOf(SwitchLeverShade, SwitchLeverEdge),
                startY = geometry.leverHeight * 0.42f,
                endY = geometry.leverHeight,
            ),
        )
        // Crisp edge where the two faces meet.
        drawPath(geometry.top, SwitchLeverEdge, style = Stroke(width = size.width * 0.025f))
    }
}
