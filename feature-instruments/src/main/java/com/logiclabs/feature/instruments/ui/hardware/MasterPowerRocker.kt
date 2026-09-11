package com.logiclabs.feature.instruments.ui.hardware

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
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
import com.logiclabs.core.designsystem.theme.ChassisSilkscreen
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.MasterSwitchOff
import com.logiclabs.core.designsystem.theme.MasterSwitchOn
import com.logiclabs.core.designsystem.theme.NeonPilotOff
import com.logiclabs.core.designsystem.theme.NeonPilotOn
import com.logiclabs.core.designsystem.theme.SwitchBezel
import com.logiclabs.core.designsystem.theme.SwitchWell
import com.logiclabs.core.designsystem.theme.TextSecondary

/** Cached trapezoid faces for the two-position power rocker. */
private class PowerRockerGeometry(
    val upperFace: Path,
    val lowerFace: Path,
    val housingRadius: CornerRadius,
    val wellRadius: CornerRadius,
    val pivotY: Float,
)

/**
 * The rocker face is two trapezoids meeting at a pivot line. Whichever half is
 * depressed gets shaded and whichever is raised catches the top-left light, so
 * toggling produces real mechanical relief rather than a colour change.
 */
private fun buildPowerRocker(w: Float, h: Float, rSm: Float, rMd: Float): PowerRockerGeometry {
    val inset = w * 0.12f
    val faceW = w - inset * 2f
    val top = h * 0.10f
    val bottom = h - h * 0.10f
    val pivot = (top + bottom) / 2f
    val chamfer = faceW * 0.14f

    val upper = Path().apply {
        moveTo(inset + chamfer, top)
        lineTo(inset + faceW - chamfer, top)
        lineTo(inset + faceW, pivot)
        lineTo(inset, pivot)
        close()
    }
    val lower = Path().apply {
        moveTo(inset, pivot)
        lineTo(inset + faceW, pivot)
        lineTo(inset + faceW - chamfer, bottom)
        lineTo(inset + chamfer, bottom)
        close()
    }
    return PowerRockerGeometry(upper, lower, CornerRadius(rMd, rMd), CornerRadius(rSm, rSm), pivot)
}

/**
 * Master power: a recessed two-position rocker plus an adjacent red neon pilot
 * lamp that blooms when the bench is live.
 *
 * Drives `BreadboardCircuit.masterPower`; the caller is responsible for calling
 * `circuit.step()` after the write.
 */
@Composable
fun MasterPowerRocker(
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    rockerWidth: Dp = 34.dp,
    rockerHeight: Dp = Dimens.RockerHeight,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }

    val wPx = with(density) { rockerWidth.toPx() }
    val hPx = with(density) { rockerHeight.toPx() }
    val geometry = remember(wPx, hPx) {
        with(density) { buildPowerRocker(wPx, hPx, Dimens.RadiusSm.toPx(), Dimens.RadiusMd.toPx()) }
    }

    // TODO(tokens): switch to Motion.SwitchThrow
    val tilt by animateFloatAsState(
        targetValue = if (isOn) 1f else 0f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "powerTilt",
    )

    Column(
        modifier = modifier
            .sizeIn(minWidth = Dimens.MinTouchTarget, minHeight = Dimens.MinTouchTarget)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Switch,
            ) {
                // The heavy throw is felt only when the supply comes up; powering
                // down is a quiet click-less release.
                if (!isOn) PanelHaptics.heavy(context, view)
                onToggle(!isOn)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // TODO(tokens): switch to LogicLabsType.PanelLabel
        Text(
            text = "POWER",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            fontFamily = FontFamily.Monospace,
            color = ChassisSilkscreen,
        )
        Spacer(modifier = Modifier.height(Dimens.Space1))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space2),
        ) {
            Canvas(modifier = Modifier.size(rockerWidth, rockerHeight)) {
                drawPowerRocker(geometry, tilt)
            }
            Canvas(modifier = Modifier.size(16.dp)) { drawNeonPilot(tilt) }
        }
        Spacer(modifier = Modifier.height(2.dp))
        // TODO(tokens): switch to LogicLabsType.MicroLabel
        Text(
            text = if (isOn) "ON" else "OFF",
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            fontFamily = FontFamily.Monospace,
            color = if (isOn) MasterSwitchOn else TextSecondary,
        )
    }
}

private fun DrawScope.drawPowerRocker(geometry: PowerRockerGeometry, tilt: Float) {
    // Housing collar.
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(ChassisBevelHighlight, SwitchBezel, ChassisBevelShadow),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        size = size,
        cornerRadius = geometry.housingRadius,
    )
    // Recess the face sits in.
    val inset = size.width * 0.07f
    drawRoundRect(
        color = SwitchWell,
        topLeft = Offset(inset, inset * 0.6f),
        size = Size(size.width - inset * 2f, size.height - inset * 1.2f),
        cornerRadius = geometry.wellRadius,
    )

    // ON tilts the top half down (depressed), OFF tilts the bottom half down.
    // Depressed = shaded and slightly darker; raised = catching light.
    val upperDepressed = tilt
    val lowerDepressed = 1f - tilt

    drawRockerFace(geometry.upperFace, upperDepressed, isTop = true)
    drawRockerFace(geometry.lowerFace, lowerDepressed, isTop = false)

    // Pivot seam.
    drawLine(
        ChassisBevelShadow,
        Offset(size.width * 0.12f, geometry.pivotY),
        Offset(size.width * 0.88f, geometry.pivotY),
        size.width * 0.035f,
    )

    // Silkscreened I / O marks on the two halves.
    val markW = size.width * 0.05f
    val iY = geometry.pivotY - size.height * 0.20f
    drawLine(
        ChassisSilkscreen.copy(alpha = 0.55f - 0.2f * upperDepressed),
        Offset(size.width / 2f, iY - size.height * 0.055f),
        Offset(size.width / 2f, iY + size.height * 0.055f),
        markW,
    )
    val oY = geometry.pivotY + size.height * 0.20f
    drawCircle(
        color = ChassisSilkscreen.copy(alpha = 0.55f - 0.2f * lowerDepressed),
        radius = size.height * 0.055f,
        center = Offset(size.width / 2f, oY),
        style = Stroke(width = markW),
    )
}

/** One trapezoid face. [depressed] 0 = raised into the light, 1 = pushed in. */
private fun DrawScope.drawRockerFace(path: Path, depressed: Float, isTop: Boolean) {
    val lit = Color(0xFFCFD4DD)
    val shade = Color(0xFF5C6169)
    val start = if (isTop) Offset(0f, 0f) else Offset(0f, size.height)
    val end = if (isTop) Offset(size.width * 0.6f, size.height * 0.55f) else Offset(size.width * 0.6f, size.height * 0.45f)
    drawPath(
        path,
        Brush.linearGradient(
            colors = listOf(
                lerpColor(lit, shade, depressed * 0.85f),
                lerpColor(shade, ChassisBevelShadow, depressed * 0.7f),
            ),
            start = start,
            end = end,
        ),
    )
    // Depressed halves pick up an occlusion wash from the housing lip.
    if (depressed > 0.02f) {
        drawPath(path, Color.Black.copy(alpha = 0.26f * depressed))
    }
    drawPath(path, ChassisBevelShadow.copy(alpha = 0.6f), style = Stroke(width = size.width * 0.02f))
}

/** Red neon pilot lens. Blooms with [live] (0..1). */
private fun DrawScope.drawNeonPilot(live: Float) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.width * 0.30f
    if (live > 0.01f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(NeonPilotOn.copy(alpha = 0.30f * live), Color.Transparent),
                center = c,
                radius = r * 2.8f,
            ),
            radius = r * 2.8f,
            center = c,
        )
    }
    // Chrome collar.
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(ChassisBevelHighlight, ChassisBevelShadow),
            start = Offset(c.x - r, c.y - r),
            end = Offset(c.x + r, c.y + r),
        ),
        radius = r * 1.22f,
        center = c,
        style = Stroke(width = r * 0.30f),
    )
    // Lens.
    drawCircle(
        brush = Brush.radialGradient(
            colors = if (live > 0.01f) {
                listOf(
                    Color(0xFFFFD9DF).copy(alpha = 0.55f + 0.45f * live),
                    NeonPilotOn,
                    MasterSwitchOff.copy(alpha = 0.9f),
                )
            } else {
                listOf(NeonPilotOff.copy(alpha = 0.9f), NeonPilotOff, ChassisBevelShadow)
            },
            center = Offset(c.x - r * 0.25f, c.y - r * 0.3f),
            radius = r * 1.6f,
        ),
        radius = r,
        center = c,
    )
    // Upper-left specular.
    drawArc(
        color = Color.White.copy(alpha = 0.34f),
        startAngle = 168f,
        sweepAngle = 84f,
        useCenter = false,
        topLeft = Offset(c.x - r * 0.62f, c.y - r * 0.62f),
        size = Size(r * 1.24f, r * 1.24f),
        style = Stroke(width = r * 0.18f),
    )
}

/** Local colour lerp so face shading stays allocation-cheap and explicit. */
internal fun lerpColor(a: Color, b: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * f,
        green = a.green + (b.green - a.green) * f,
        blue = a.blue + (b.blue - a.blue) * f,
        alpha = a.alpha + (b.alpha - a.alpha) * f,
    )
}
