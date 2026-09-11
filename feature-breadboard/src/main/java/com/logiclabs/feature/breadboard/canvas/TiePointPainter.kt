package com.logiclabs.feature.breadboard.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.ContactClipMetal
import com.logiclabs.core.designsystem.theme.ContactClipRecess
import com.logiclabs.core.designsystem.theme.ContactClipSpecular

/**
 * Draws one AD-200 tie-point: a phosphor-bronze spring clip recessed into the phenolic.
 *
 * Anatomy, outermost first:
 *  1. a round [ContactClipRecess] ring — the moulded well the clip sits in;
 *  2. a **square** clip face filled with a vertical [ContactClipMetal] → [ContactClipRecess]
 *     gradient, so the metal reads as catching light at the top and falling into shadow;
 *  3. a 1px [ContactClipSpecular] highlight along the clip's top edge.
 *
 * Stateless: every brush and geometry value is either an object-level constant or derived from
 * the arguments, so this allocates nothing per call and can be replayed into a cached
 * [android.graphics.Picture] as happily as into a live frame.
 *
 * ### Level of detail
 * [LOD_CLIP_MIN_SCALE] is the zoom below which the three-layer clip collapses to a single dot —
 * at that size the recess ring and the specular edge are sub-pixel and only cost fill rate.
 */
object TiePointPainter {

    /** Below this zoom a tie-point is a single dot instead of a three-layer clip. */
    const val LOD_CLIP_MIN_SCALE = 0.5f

    /** Board-space radius of the moulded recess well. */
    const val RECESS_RADIUS = 6.0f

    /** Board-space side of the square clip face. */
    const val CLIP_SIDE = 7.0f

    private const val HIGHLIGHT_RADIUS = 7.0f

    /**
     * The clip gradient is defined once in a local 0..CLIP_SIDE space and translated per
     * tie-point, which is why it can be a shared immutable brush.
     */
    private val clipBrush: Brush = Brush.verticalGradient(
        0f to ContactClipMetal,
        0.55f to ContactClipMetal,
        1f to ContactClipRecess,
        startY = 0f,
        endY = CLIP_SIDE
    )

    private val clipBrushHighlighted: Brush = Brush.verticalGradient(
        0f to AccentCyan,
        1f to ContactClipMetal,
        startY = 0f,
        endY = CLIP_SIDE
    )

    private val clipSize = Size(CLIP_SIDE, CLIP_SIDE)

    /**
     * Draws a tie-point centred on [cx]/[cy].
     *
     * @param zoomScale current board zoom; drives the LOD path.
     * @param highlighted true when this hole is part of the electrically-selected net.
     */
    fun draw(
        scope: DrawScope,
        cx: Float,
        cy: Float,
        zoomScale: Float,
        highlighted: Boolean = false
    ) {
        if (zoomScale < LOD_CLIP_MIN_SCALE) {
            drawSimplified(scope, cx, cy, highlighted)
            return
        }

        // 1. Moulded recess well.
        scope.drawCircle(
            color = ContactClipRecess,
            radius = RECESS_RADIUS,
            center = Offset(cx, cy)
        )

        // 2. Square spring-clip face with its vertical metal gradient.
        val half = CLIP_SIDE / 2f
        scope.translate(cx - half, cy - half) {
            drawRect(
                brush = if (highlighted) clipBrushHighlighted else clipBrush,
                size = clipSize
            )
        }

        // 3. Top-edge specular: a 1px line where the clip catches the virtual top-left light.
        scope.drawLine(
            color = ContactClipSpecular,
            start = Offset(cx - half, cy - half),
            end = Offset(cx + half, cy - half),
            strokeWidth = 1f
        )

        if (highlighted) {
            scope.drawCircle(
                color = AccentCyan,
                radius = HIGHLIGHT_RADIUS,
                center = Offset(cx, cy),
                style = Stroke(width = 1.4f)
            )
        }
    }

    /** LOD path: one dot, one draw op. */
    private fun drawSimplified(scope: DrawScope, cx: Float, cy: Float, highlighted: Boolean) {
        scope.drawCircle(
            color = if (highlighted) AccentCyan else ContactClipRecess,
            radius = 3.2f,
            center = Offset(cx, cy)
        )
    }

    /**
     * Larger panel-mount socket (switch terminals, DC power, output test points): the same
     * recessed-metal language at a bigger radius, with a round clip face rather than a square one.
     */
    fun drawPanelSocket(
        scope: DrawScope,
        cx: Float,
        cy: Float,
        zoomScale: Float,
        radius: Float,
        bezelColor: Color
    ) {
        val c = Offset(cx, cy)
        scope.drawCircle(color = bezelColor, radius = radius + 3.5f, center = c, style = Stroke(2f))
        scope.drawCircle(color = ContactClipMetal, radius = radius, center = c)
        scope.drawCircle(color = ContactClipRecess, radius = radius * 0.56f, center = c)
        if (zoomScale >= LOD_CLIP_MIN_SCALE) {
            scope.drawArc(
                color = ContactClipSpecular,
                startAngle = 190f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(cx - radius, cy - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = 1.4f)
            )
        }
    }

    private inline fun DrawScope.translate(dx: Float, dy: Float, block: DrawScope.() -> Unit) {
        drawContext.transform.translate(dx, dy)
        block()
        drawContext.transform.translate(-dx, -dy)
    }
}
