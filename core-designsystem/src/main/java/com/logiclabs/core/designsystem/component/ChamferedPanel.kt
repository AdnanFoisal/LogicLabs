package com.logiclabs.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.logiclabs.core.designsystem.theme.ChassisBase
import com.logiclabs.core.designsystem.theme.ChassisBevelHighlight
import com.logiclabs.core.designsystem.theme.ChassisBevelShadow
import com.logiclabs.core.designsystem.theme.Dimens

/**
 * The industrial composite panel every chassis-mounted control sits on.
 *
 * A moulded panel reads as solid because its edges catch light unevenly. The virtual
 * light source for this entire app is **top-left**, so the top and left edges get
 * [ChassisBevelHighlight] and the bottom and right edges get [ChassisBevelShadow].
 * That direction is not a per-component choice — flipping it on one panel makes the
 * whole console look like a collage. Keep it consistent.
 *
 * The bevel is drawn inside the clip as a 1px inset arc-and-line pair rather than a
 * `border()`, because a uniform border cannot vary colour around the perimeter.
 */
@Composable
fun ChamferedPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = Dimens.RadiusLg,
    fill: Color = ChassisBase,
    highlight: Color = ChassisBevelHighlight,
    shadow: Color = ChassisBevelShadow,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .drawWithContent {
                drawContent()

                val stroke = Dimens.BevelWidth.toPx()
                val half = stroke / 2f
                val r = cornerRadius.toPx().coerceAtMost(size.minDimension / 2f)
                val d = r * 2f
                val w = size.width
                val h = size.height

                // --- lit edges: top + left -------------------------------------
                // Top run, between the two top corner arcs.
                drawLine(
                    color = highlight,
                    start = Offset(r, half),
                    end = Offset(w - r, half),
                    strokeWidth = stroke
                )
                // Left run.
                drawLine(
                    color = highlight,
                    start = Offset(half, r),
                    end = Offset(half, h - r),
                    strokeWidth = stroke
                )
                // Top-left corner: the full 90 degrees facing the light.
                drawArc(
                    color = highlight,
                    startAngle = 180f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(half, half),
                    size = Size(d, d),
                    style = Stroke(width = stroke)
                )
                // Upper half of each adjacent corner, so the highlight dies out
                // gradually instead of stopping at a hard seam.
                drawArc(
                    color = highlight,
                    startAngle = 270f,
                    sweepAngle = 45f,
                    useCenter = false,
                    topLeft = Offset(w - d - half, half),
                    size = Size(d, d),
                    style = Stroke(width = stroke)
                )
                drawArc(
                    color = highlight,
                    startAngle = 135f,
                    sweepAngle = 45f,
                    useCenter = false,
                    topLeft = Offset(half, h - d - half),
                    size = Size(d, d),
                    style = Stroke(width = stroke)
                )

                // --- shaded edges: bottom + right ------------------------------
                drawLine(
                    color = shadow,
                    start = Offset(r, h - half),
                    end = Offset(w - r, h - half),
                    strokeWidth = stroke
                )
                drawLine(
                    color = shadow,
                    start = Offset(w - half, r),
                    end = Offset(w - half, h - r),
                    strokeWidth = stroke
                )
                // Bottom-right corner faces fully away from the light.
                drawArc(
                    color = shadow,
                    startAngle = 0f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(w - d - half, h - d - half),
                    size = Size(d, d),
                    style = Stroke(width = stroke)
                )
                drawArc(
                    color = shadow,
                    startAngle = 315f,
                    sweepAngle = 45f,
                    useCenter = false,
                    topLeft = Offset(w - d - half, half),
                    size = Size(d, d),
                    style = Stroke(width = stroke)
                )
                drawArc(
                    color = shadow,
                    startAngle = 90f,
                    sweepAngle = 45f,
                    useCenter = false,
                    topLeft = Offset(half, h - d - half),
                    size = Size(d, d),
                    style = Stroke(width = stroke)
                )
            },
        content = content
    )
}
