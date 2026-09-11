package com.logiclabs.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.ScrewBody
import com.logiclabs.core.designsystem.theme.ScrewHighlight
import com.logiclabs.core.designsystem.theme.ScrewSlot

/**
 * A machined Phillips screw head, drawn entirely in vectors — no bitmaps, so it stays
 * crisp at any density and costs nothing to load.
 *
 * Anchors the corners of chassis panels. Purely decorative: it takes no click handler
 * and no semantics, because a screw is not a control.
 *
 * Lit from the **top-left** like every other surface in the app: the radial body
 * gradient is offset up and left, and the specular arc rides the upper-left rim.
 */
@Composable
fun PanelScrew(
    modifier: Modifier = Modifier,
    size: Dp = Dimens.ScrewSize,
    cross: Boolean = true
) {
    Canvas(modifier = modifier.size(size)) {
        val d = this.size.minDimension
        val r = d / 2f
        val c = Offset(r, r)

        // Body: radial falloff biased toward the light so the head reads domed.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ScrewHighlight, ScrewBody),
                center = Offset(r * 0.62f, r * 0.62f),
                radius = d * 0.85f
            ),
            radius = r,
            center = c
        )

        // Recessed rim, darkest on the shaded side.
        drawCircle(
            color = ScrewSlot.copy(alpha = 0.55f),
            radius = r,
            center = c,
            style = Stroke(width = d * 0.07f)
        )

        // Driver slot(s), sunk below the surface.
        val slotLen = d * 0.62f
        val slotWidth = d * 0.13f
        drawLine(
            color = ScrewSlot,
            start = Offset(r - slotLen / 2f, r),
            end = Offset(r + slotLen / 2f, r),
            strokeWidth = slotWidth
        )
        if (cross) {
            drawLine(
                color = ScrewSlot,
                start = Offset(r, r - slotLen / 2f),
                end = Offset(r, r + slotLen / 2f),
                strokeWidth = slotWidth
            )
        }

        // Specular arc: a short catch of light on the upper-left rim only.
        val inset = d * 0.14f
        drawArc(
            color = Color.White.copy(alpha = 0.34f),
            startAngle = 165f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(d - inset * 2f, d - inset * 2f),
            style = Stroke(width = d * 0.09f)
        )
    }
}
