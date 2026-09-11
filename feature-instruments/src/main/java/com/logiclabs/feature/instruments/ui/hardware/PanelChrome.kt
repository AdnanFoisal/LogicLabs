package com.logiclabs.feature.instruments.ui.hardware

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logiclabs.core.designsystem.theme.ChassisBase
import com.logiclabs.core.designsystem.theme.ChassisBevelHighlight
import com.logiclabs.core.designsystem.theme.ChassisBevelShadow
import com.logiclabs.core.designsystem.theme.ChassisDivider
import com.logiclabs.core.designsystem.theme.ChassisSilkscreen
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.ScrewBody
import com.logiclabs.core.designsystem.theme.ScrewHighlight
import com.logiclabs.core.designsystem.theme.ScrewSlot

/**
 * Shared chassis chrome for the IDL-800A front panel.
 *
 * Every component in this package lights from a single **top-left** virtual
 * light source: highlights on top and left edges, shadow on bottom and right.
 * Keep new hardware consistent with that or the panel stops reading as one
 * physical object.
 */

/** Chamfer inset used by the chassis bevel, in dp. */
private val BevelInset = 1.dp

/**
 * Painted composite chassis: flat [ChassisBase] fill plus a chamfered inner
 * bevel (highlight top+left, shadow bottom+right) and a very soft top-down
 * sheen so large panels do not read as dead flat colour.
 */
@Composable
fun ChassisSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val bevelPx = remember(density) { with(density) { BevelInset.toPx() } }
    val sheen = remember {
        Brush.verticalGradient(
            0f to Color(0x0AFFFFFF),
            0.35f to Color(0x00FFFFFF),
            1f to Color(0x14000000),
        )
    }
    Box(
        modifier = modifier.drawBehind {
            drawRect(ChassisBase)
            drawRect(sheen)
            drawChassisBevel(bevelPx)
        },
        content = content,
    )
}

/** Bevel primitive so nested wells and raised blocks can share the light source. */
internal fun DrawScope.drawChassisBevel(
    strokePx: Float,
    highlight: Color = ChassisBevelHighlight,
    shadow: Color = ChassisBevelShadow,
    inset: Float = 0f,
) {
    val half = strokePx / 2f
    val l = inset + half
    val t = inset + half
    val r = size.width - inset - half
    val b = size.height - inset - half
    // Top + left catch the light.
    drawLine(highlight, Offset(l, t), Offset(r, t), strokePx)
    drawLine(highlight, Offset(l, t), Offset(l, b), strokePx)
    // Bottom + right fall away.
    drawLine(shadow, Offset(l, b), Offset(r, b), strokePx)
    drawLine(shadow, Offset(r, t), Offset(r, b), strokePx)
}

/**
 * A silkscreened panel group: hairline divider, tracked uppercase label, content.
 *
 * The label is drawn with per-character letter spacing so it reads like screen
 * printing rather than UI text.
 */
@Composable
fun SectionBlock(
    label: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // TODO(tokens): switch to LogicLabsType.PanelLabel
            Text(
                text = label.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                color = ChassisSilkscreen,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(modifier = Modifier.width(Dimens.Space2))
            SilkscreenDivider(modifier = Modifier.weight(1f))
            if (trailing != null) {
                Spacer(modifier = Modifier.width(Dimens.Space2))
                trailing()
            }
        }
        Spacer(modifier = Modifier.height(Dimens.Space2))
        content()
    }
}

/** Hairline rule with a 1px lower highlight, so it reads as an engraved groove. */
@Composable
fun SilkscreenDivider(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val hairline = remember(density) { with(density) { Dimens.Hairline.toPx() } }
    Canvas(modifier = modifier.height(2.dp)) {
        val y = size.height / 2f
        drawLine(ChassisDivider, Offset(0f, y), Offset(size.width, y), hairline)
        drawLine(
            ChassisBevelHighlight.copy(alpha = 0.35f),
            Offset(0f, y + hairline),
            Offset(size.width, y + hairline),
            hairline,
        )
    }
}

/** Dashed engraved rule for internal sub-groupings. */
@Composable
fun DashedRule(modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val hairline = remember(density) { with(density) { Dimens.Hairline.toPx() } }
    val dash = remember(density) {
        with(density) { PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) }
    }
    Canvas(modifier = modifier.height(1.dp)) {
        drawLine(
            ChassisDivider,
            Offset(0f, size.height / 2f),
            Offset(size.width, size.height / 2f),
            hairline,
            pathEffect = dash,
        )
    }
}

/**
 * A single Phillips-ish panel screw: radial-gradient body, dark slot, upper-left
 * specular arc. Drawn, never an asset.
 */
@Composable
fun PanelScrew(
    modifier: Modifier = Modifier,
    size: Dp = Dimens.ScrewSize,
) {
    Canvas(modifier = modifier.size(size)) { drawPanelScrew() }
}

/** Screw geometry, reusable from any [DrawScope] (e.g. corner placement). */
internal fun DrawScope.drawPanelScrew(
    center: Offset = Offset(size.width / 2f, size.height / 2f),
    radius: Float = minOf(size.width, size.height) / 2f,
) {
    if (radius <= 0f) return
    // Recess shadow under the head.
    drawCircle(
        color = ChassisBevelShadow.copy(alpha = 0.7f),
        radius = radius,
        center = Offset(center.x + radius * 0.08f, center.y + radius * 0.10f),
    )
    // Machined body, lit from the top-left.
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(ScrewHighlight, ScrewBody, ScrewSlot),
            center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
            radius = radius * 1.7f,
        ),
        radius = radius * 0.9f,
        center = center,
    )
    // Cross slot.
    val slotW = radius * 0.22f
    val slotLen = radius * 0.62f
    drawLine(ScrewSlot, Offset(center.x - slotLen, center.y), Offset(center.x + slotLen, center.y), slotW)
    drawLine(ScrewSlot, Offset(center.x, center.y - slotLen), Offset(center.x, center.y + slotLen), slotW)
    // Upper-left specular arc on the rim.
    drawArc(
        color = Color.White.copy(alpha = 0.28f),
        startAngle = 170f,
        sweepAngle = 100f,
        useCenter = false,
        topLeft = Offset(center.x - radius * 0.9f, center.y - radius * 0.9f),
        size = Size(radius * 1.8f, radius * 1.8f),
        style = Stroke(width = radius * 0.18f),
    )
}

/** Evenly spaced screw row, e.g. along a panel edge. */
@Composable
fun PanelScrewRow(
    count: Int,
    modifier: Modifier = Modifier,
    screwSize: Dp = Dimens.ScrewSize,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        repeat(count) { PanelScrew(size = screwSize) }
    }
}

/**
 * Corner screws for a panel: place inside a [Box] filling the panel and this
 * paints four screws inset from each corner in one draw pass (no extra layout
 * nodes, no per-corner composables).
 */
@Composable
fun CornerScrews(
    modifier: Modifier = Modifier,
    inset: Dp = Dimens.Space3,
    screwSize: Dp = Dimens.ScrewSize,
) {
    val density = LocalDensity.current
    val insetPx = remember(density, inset) { with(density) { inset.toPx() } }
    val radiusPx = remember(density, screwSize) { with(density) { screwSize.toPx() / 2f } }
    Canvas(modifier = modifier) {
        val r = radiusPx
        val xs = floatArrayOf(insetPx + r, size.width - insetPx - r)
        val ys = floatArrayOf(insetPx + r, size.height - insetPx - r)
        for (x in xs) for (y in ys) drawPanelScrew(Offset(x, y), r)
    }
}

/** 36×4dp rounded grab bar for the dock header. */
@Composable
fun DragHandle(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier
            .width(Dimens.DragHandleWidth)
            .height(Dimens.DragHandleHeight),
    ) {
        val r = size.height / 2f
        drawRoundRect(
            color = ChassisBevelShadow,
            topLeft = Offset(0f, 1f),
            size = Size(size.width, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                listOf(ChassisSilkscreen.copy(alpha = 0.85f), ChassisBevelHighlight),
                startY = 0f,
                endY = size.height,
            ),
            size = Size(size.width, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
        )
    }
}

/**
 * Recessed well: an inverted bevel (shadow top+left, highlight bottom+right)
 * over a dark fill. Used behind LED banks and switch rows.
 */
fun Modifier.recessedWell(
    fill: Color,
    cornerRadius: Dp = Dimens.RadiusMd,
): Modifier = this.drawBehind {
    val r = cornerRadius.toPx()
    val cr = androidx.compose.ui.geometry.CornerRadius(r, r)
    drawRoundRect(color = fill, size = size, cornerRadius = cr)
    val stroke = 1.dp.toPx()
    // Inverted light source: a well is the negative of a raised block.
    drawRoundRect(
        color = ChassisBevelShadow,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = cr,
        style = Stroke(width = stroke),
    )
    val rect = Rect(Offset.Zero, size)
    drawLine(
        ChassisBevelHighlight.copy(alpha = 0.5f),
        Offset(r, rect.bottom - stroke / 2f),
        Offset(rect.right - r, rect.bottom - stroke / 2f),
        stroke,
    )
}
