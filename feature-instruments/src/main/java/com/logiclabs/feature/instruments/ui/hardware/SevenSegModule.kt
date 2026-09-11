package com.logiclabs.feature.instruments.ui.hardware

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logiclabs.core.designsystem.theme.ChassisBevelHighlight
import com.logiclabs.core.designsystem.theme.ChassisBevelShadow
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.LocalRenderEffects
import com.logiclabs.core.designsystem.theme.SevenSegBezel
import com.logiclabs.core.designsystem.theme.SevenSegGlow
import com.logiclabs.core.designsystem.theme.SevenSegLeak
import com.logiclabs.core.designsystem.theme.SevenSegLit
import com.logiclabs.core.designsystem.theme.SevenSegUnlit
import com.logiclabs.core.designsystem.theme.TextTertiary

/**
 * Pre-built segment geometry for one display size.
 *
 * All seven chamfered segment [Path]s plus the decimal-point centre are built
 * once per size and cached in `remember`, so the draw loop allocates nothing.
 */
private class SegmentGeometry(
    val paths: Array<Path>,
    val dpCenter: Offset,
    val dpRadius: Float,
    val strokeUnit: Float,
)

/**
 * Builds the classic seven-segment layout inside [w] x [h]:
 * ```
 *      aaaa
 *     f    b
 *     f    b
 *      gggg
 *     e    c
 *     e    c
 *      dddd   .
 * ```
 * Each segment is a hexagon (chamfered ends), which is how a real LED display
 * is masked — square-ended bars look like a font, not hardware.
 */
private fun buildSegments(w: Float, h: Float): SegmentGeometry {
    // Digit box, inset for the bezel and shifted left to leave room for the DP.
    val padX = w * 0.16f
    val padY = h * 0.12f
    val dpGutter = w * 0.12f
    val left = padX
    val right = w - padX - dpGutter
    val top = padY
    val bottom = h - padY

    val gw = right - left
    val gh = bottom - top
    // Italic slant, like a real display module.
    val slant = gw * 0.10f
    val t = gh * 0.085f              // segment thickness
    val gap = t * 0.36f              // mask gap between adjacent segments
    val midY = top + gh / 2f

    /** Shear applied as a function of y: 0 at the bottom, full slant at the top. */
    fun sx(x: Float, y: Float): Float = x + slant * ((bottom - y) / gh)

    fun horizontal(cy: Float): Path {
        val x0 = left + t * 0.75f + gap
        val x1 = right - t * 0.75f - gap
        val half = t / 2f
        return Path().apply {
            moveTo(sx(x0, cy), cy)
            lineTo(sx(x0 + half, cy - half), cy - half)
            lineTo(sx(x1 - half, cy - half), cy - half)
            lineTo(sx(x1, cy), cy)
            lineTo(sx(x1 - half, cy + half), cy + half)
            lineTo(sx(x0 + half, cy + half), cy + half)
            close()
        }
    }

    fun vertical(cx: Float, yTop: Float, yBottom: Float): Path {
        val y0 = yTop + t * 0.75f + gap
        val y1 = yBottom - t * 0.75f - gap
        val half = t / 2f
        return Path().apply {
            moveTo(sx(cx, y0), y0)
            lineTo(sx(cx + half, y0 + half), y0 + half)
            lineTo(sx(cx + half, y1 - half), y1 - half)
            lineTo(sx(cx, y1), y1)
            lineTo(sx(cx - half, y1 - half), y1 - half)
            lineTo(sx(cx - half, y0 + half), y0 + half)
            close()
        }
    }

    // Index order matches SevenSegFont bit order: a,b,c,d,e,f,g
    val paths = arrayOf(
        horizontal(top + t / 2f),                       // a  top
        vertical(right - t / 2f, top, midY),            // b  upper-right
        vertical(right - t / 2f, midY, bottom),         // c  lower-right
        horizontal(bottom - t / 2f),                    // d  bottom
        vertical(left + t / 2f, midY, bottom),          // e  lower-left
        vertical(left + t / 2f, top, midY),             // f  upper-left
        horizontal(midY),                               // g  middle
    )

    return SegmentGeometry(
        paths = paths,
        dpCenter = Offset(right + dpGutter * 0.55f, bottom - t * 0.35f),
        dpRadius = t * 0.42f,
        strokeUnit = t,
    )
}

/**
 * An authentic seven-segment LED module.
 *
 * Replaces the old implementation, which was a single 44sp monospace hex glyph
 * in a coloured box. Here the display is drawn as seven masked segments:
 * unlit segments stay faintly visible as dark red silhouettes (that is what a
 * real module looks like under ambient light), lit segments carry concentric
 * bloom strokes plus light leak into the surrounding bezel.
 *
 * The glyph comes from [SevenSegFont.segmentsFor] — the view-layer font mirror,
 * so nothing here reads the simulation engine's own table.
 *
 * @param value nibble 0..15 (rendered 0-9, then the 7448 A-F glyphs)
 * @param decimalPoint lights the DP dot
 */
@Composable
fun SevenSegModule(
    value: Int,
    label: String,
    modifier: Modifier = Modifier,
    width: Dp = Dimens.SevenSegWidth,
    height: Dp = Dimens.SevenSegHeight,
    decimalPoint: Boolean = false,
) {
    val density = LocalDensity.current
    val wPx = with(density) { width.toPx() }
    val hPx = with(density) { height.toPx() }

    // Geometry is hoisted and keyed on size: zero Path allocation per frame.
    val geometry = remember(wPx, hPx) { buildSegments(wPx, hPx) }
    val bezelBrush = remember(hPx) {
        Brush.verticalGradient(
            colors = listOf(Color(0xFF15161A), SevenSegBezel, Color(0xFF08090B)),
            startY = 0f,
            endY = hPx,
        )
    }
    val cornerRadius = remember(density) { with(density) { CornerRadius(Dimens.RadiusSm.toPx(), Dimens.RadiusSm.toPx()) } }
    val bevelPx = remember(density) { with(density) { Dimens.Hairline.toPx() } }

    val segments = SevenSegFont.segmentsFor(value)
    // Bloom gate from Settings: lit segments stay lit; the halo strokes and bezel
    // leak are what switch off.
    val bloom = LocalRenderEffects.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(modifier = Modifier.size(width, height)) {
            // Smoked-glass window in a dark plastic body.
            drawRoundRect(brush = bezelBrush, size = size, cornerRadius = cornerRadius)
            drawRoundRect(
                color = ChassisBevelShadow,
                size = size,
                cornerRadius = cornerRadius,
                style = Stroke(width = bevelPx * 1.5f),
            )
            drawLine(
                ChassisBevelHighlight.copy(alpha = 0.4f),
                Offset(cornerRadius.x, size.height - bevelPx),
                Offset(size.width - cornerRadius.x, size.height - bevelPx),
                bevelPx,
            )

            // Light leak: a lit display washes its own bezel faintly red.
            val litCount = Integer.bitCount(segments and 0x7F)
            if (litCount > 0 && bloom) {
                drawRoundRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SevenSegLeak.copy(alpha = 0.10f + 0.02f * litCount),
                            Color.Transparent,
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = maxOf(size.width, size.height) * 0.8f,
                    ),
                    size = size,
                    cornerRadius = cornerRadius,
                )
            }

            drawSegments(geometry, segments, bloom)

            // Decimal point: same unlit/lit treatment as a segment.
            drawCircle(
                color = if (decimalPoint) SevenSegLit else SevenSegUnlit,
                radius = geometry.dpRadius,
                center = geometry.dpCenter,
            )
            if (decimalPoint && bloom) {
                drawCircle(
                    color = SevenSegGlow,
                    radius = geometry.dpRadius * 1.9f,
                    center = geometry.dpCenter,
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        // TODO(tokens): switch to LogicLabsType.MicroLabel
        Text(
            text = label.uppercase(),
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp,
            fontFamily = FontFamily.Monospace,
            color = TextTertiary,
        )
    }
}

/** Draws all seven segments: silhouettes for dark ones, bloom for lit ones. */
private fun DrawScope.drawSegments(geometry: SegmentGeometry, segments: Int, bloom: Boolean) {
    for (i in 0 until 7) {
        val path = geometry.paths[i]
        val lit = (segments shr i) and 1 == 1
        if (!lit) {
            // Subtly visible dark silhouette — the mask is always there.
            drawPath(path, SevenSegUnlit)
            continue
        }
        if (bloom) {
            // Three concentric decreasing-alpha strokes form the bloom halo.
            drawPath(path, SevenSegGlow.copy(alpha = 0.16f), style = Stroke(width = geometry.strokeUnit * 1.5f))
            drawPath(path, SevenSegGlow.copy(alpha = 0.30f), style = Stroke(width = geometry.strokeUnit * 0.85f))
            drawPath(path, SevenSegGlow.copy(alpha = 0.52f), style = Stroke(width = geometry.strokeUnit * 0.35f))
        }
        drawPath(path, SevenSegLit)
        // Hot centre line along the segment for filament realism.
        drawPath(path, Color.White.copy(alpha = 0.14f), style = Stroke(width = geometry.strokeUnit * 0.12f))
    }
}

/** Two-digit hex group, drawn as one module pair with a shared label. */
@Composable
fun SevenSegPair(
    high: Int,
    low: Int,
    labelHigh: String,
    labelLow: String,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Dimens.Space2),
    ) {
        SevenSegModule(value = high, label = labelHigh)
        SevenSegModule(value = low, label = labelLow)
    }
}
