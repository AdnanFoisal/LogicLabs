package com.logiclabs.feature.tools.oscilloscope

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logiclabs.core.bridge.circuit.OscilloscopeBuffer
import com.logiclabs.core.designsystem.theme.AmberBloom
import com.logiclabs.core.designsystem.theme.AmberCore
import com.logiclabs.core.designsystem.theme.ChassisBevelHighlight
import com.logiclabs.core.designsystem.theme.ChassisBevelShadow
import com.logiclabs.core.designsystem.theme.ChassisSilkscreen
import com.logiclabs.core.designsystem.theme.CrtBezel
import com.logiclabs.core.designsystem.theme.CrtGraticule
import com.logiclabs.core.designsystem.theme.CrtGraticuleAxis
import com.logiclabs.core.designsystem.theme.CrtScreen
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.PhosphorBloom
import com.logiclabs.core.designsystem.theme.PhosphorCore
import com.logiclabs.core.designsystem.theme.TextSecondary

/**
 * Vertical placement of one trace, expressed in graticule divisions from the centre
 * axis (positive is up). Both channels share one volts-per-division mapping, so a
 * square wave on CH1 and the same wave on CH2 are the same height on screen.
 *
 * This replaces the two arbitrary, unequal baselines the old view hardcoded — CH1 was
 * drawn from `h * 0.75` and CH2 from `h * 0.85`, which made identical signals look
 * like different amplitudes.
 */
private const val CH1_POSITION_DIV = 1.6f
private const val CH2_POSITION_DIV = -2.4f

/** Fraction of one sample-step used for the rounded rise/fall shoulders. */
private const val SLEW_FRACTION = 0.38f

/** Widest a slew shoulder may get, in px, so a slow timebase does not smear edges. */
private const val SLEW_MAX_PX = 7f

private val CornerTickLength = 5.dp

/**
 * Dual-trace CRT oscilloscope display.
 *
 * ## Signature note for the `:app` layer
 * The first three parameters keep their original names, order and defaults, so the
 * existing `OscilloscopeView(ch1Buffer = …, ch2Buffer = …)` call site still compiles
 * untouched. Everything new is optional:
 *
 * ```
 * OscilloscopeView(
 *     ch1Buffer: OscilloscopeBuffer,
 *     ch2Buffer: OscilloscopeBuffer,
 *     timebaseMs: Float = 1.0f,
 *     modifier: Modifier = Modifier,
 *     voltsPerDiv: Float = 5f,
 *     channelMode: ScopeChannelMode = ScopeChannelMode.DUAL,
 *     sampleIntervalMs: Float = ScopeSettings.DEFAULT_ACQUISITION_INTERVAL_MS,
 *     screenHeight: Dp = 168.dp,
 *     showHeader: Boolean = true,
 *     redrawKey: Int = 0
 * )
 * ```
 *
 * @param timebaseMs milliseconds per horizontal division. Together with
 *   [sampleIntervalMs] this decides how many samples of the 8192-entry ring buffer
 *   fill the screen — see [ScopeMath.windowSamples]. The old view ignored this
 *   entirely and always drew the last 200 samples.
 * @param redrawKey bump this to force a redraw. `OscilloscopeBuffer` is a plain class
 *   with no snapshot state, so Compose cannot observe new samples landing in it;
 *   [ScopePanel] passes its frame counter here.
 */
@Composable
fun OscilloscopeView(
    ch1Buffer: OscilloscopeBuffer,
    ch2Buffer: OscilloscopeBuffer,
    timebaseMs: Float = 1.0f,
    modifier: Modifier = Modifier,
    voltsPerDiv: Float = 5f,
    channelMode: ScopeChannelMode = ScopeChannelMode.DUAL,
    sampleIntervalMs: Float = ScopeSettings.DEFAULT_ACQUISITION_INTERVAL_MS,
    screenHeight: Dp = 168.dp,
    showHeader: Boolean = true,
    redrawKey: Int = 0
) {
    // Hoisted so the draw loop allocates nothing per frame.
    val ch1Path = remember { Path() }
    val ch2Path = remember { Path() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusLg))
            .background(CrtBezel)
            .border(Dimens.BorderMd, ChassisBevelHighlight, RoundedCornerShape(Dimens.RadiusLg))
            .padding(Dimens.Space3)
    ) {
        if (showHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // TODO(tokens): swap for Type.kt silkscreen style once it lands.
                Text(
                    text = "DUAL-TRACE DIGITAL STORAGE OSCILLOSCOPE",
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = ChassisSilkscreen
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)) {
                    Readout("CH1 ${formatNumber(voltsPerDiv)}V", PhosphorCore)
                    Readout("CH2 ${formatNumber(voltsPerDiv)}V", AmberCore)
                    Readout("${formatNumber(timebaseMs)}ms/DIV", TextSecondary)
                }
            }
            Spacer(modifier = Modifier.height(Dimens.Space2))
        }

        // Curved bezel well: the screen sits inside a recessed, slightly rounded frame.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.RadiusMd))
                .background(ChassisBevelShadow)
                .padding(Dimens.Space1)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(screenHeight)
                    .clip(RoundedCornerShape(Dimens.RadiusSm))
                    .background(CrtScreen)
            ) {
                // redrawKey is read so Compose re-invokes the lambda when it changes.
                @Suppress("UNUSED_EXPRESSION") redrawKey

                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                val tickPx = CornerTickLength.toPx()
                drawGraticule(w, h, tickPx)

                val window = ScopeMath.windowSamples(timebaseMs, sampleIntervalMs, ch1Buffer.capacity)
                val voltsSpan = if (voltsPerDiv > 0f) voltsPerDiv else 5f
                val pxPerDiv = h / ScopeMath.VERTICAL_DIVISIONS

                if (channelMode != ScopeChannelMode.CH2) {
                    buildTrace(ch1Path, ch1Buffer, window, w, h, pxPerDiv, voltsSpan, CH1_POSITION_DIV)
                    drawPhosphorTrace(ch1Path, PhosphorCore, PhosphorBloom)
                }
                if (channelMode != ScopeChannelMode.CH1) {
                    buildTrace(ch2Path, ch2Buffer, window, w, h, pxPerDiv, voltsSpan, CH2_POSITION_DIV)
                    drawPhosphorTrace(ch2Path, AmberCore, AmberBloom)
                }

                // Screen-edge falloff plus the glass vignette, drawn over the traces.
                drawEdgeFalloff(w, h)
            }
        }
    }
}

@Composable
private fun Readout(text: String, color: Color) {
    // TODO(tokens): swap for Type.kt readout style once it lands.
    Text(
        text = text,
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = color
    )
}

/**
 * Faint 8x10 graticule with brighter centre axes and corner tick marks.
 *
 * Stroke widths are in px on purpose: a graticule is a screen-printed overlay on the
 * CRT face and reads best as a hairline at any density.
 */
private fun DrawScope.drawGraticule(w: Float, h: Float, tickPx: Float) {
    val cols = ScopeMath.HORIZONTAL_DIVISIONS
    val rows = ScopeMath.VERTICAL_DIVISIONS
    val colStep = w / cols
    val rowStep = h / rows

    for (c in 1 until cols) {
        val x = c * colStep
        drawLine(CrtGraticule, Offset(x, 0f), Offset(x, h), strokeWidth = 1f)
    }
    for (r in 1 until rows) {
        val y = r * rowStep
        drawLine(CrtGraticule, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
    }

    // Centre axes, brighter than the rest of the grid.
    val cx = w / 2f
    val cy = h / 2f
    drawLine(CrtGraticuleAxis, Offset(cx, 0f), Offset(cx, h), strokeWidth = 1.5f)
    drawLine(CrtGraticuleAxis, Offset(0f, cy), Offset(w, cy), strokeWidth = 1.5f)

    // Corner registration ticks.
    drawLine(CrtGraticuleAxis, Offset(0f, 0f), Offset(tickPx, 0f), strokeWidth = 2f)
    drawLine(CrtGraticuleAxis, Offset(0f, 0f), Offset(0f, tickPx), strokeWidth = 2f)
    drawLine(CrtGraticuleAxis, Offset(w, 0f), Offset(w - tickPx, 0f), strokeWidth = 2f)
    drawLine(CrtGraticuleAxis, Offset(w, 0f), Offset(w, tickPx), strokeWidth = 2f)
    drawLine(CrtGraticuleAxis, Offset(0f, h), Offset(tickPx, h), strokeWidth = 2f)
    drawLine(CrtGraticuleAxis, Offset(0f, h), Offset(0f, h - tickPx), strokeWidth = 2f)
    drawLine(CrtGraticuleAxis, Offset(w, h), Offset(w - tickPx, h), strokeWidth = 2f)
    drawLine(CrtGraticuleAxis, Offset(w, h), Offset(w, h - tickPx), strokeWidth = 2f)
}

/**
 * Fills [path] with the visible window of [buffer].
 *
 * Vertical mapping is shared by both channels: `y = centre - positionDiv*pxPerDiv -
 * (volts / voltsPerDiv) * pxPerDiv`. Each channel differs only by its
 * [positionDiv] offset, so equal signals draw at equal height.
 *
 * Logic edges are drawn as short cubic shoulders rather than a vertical `lineTo`, so
 * a rise looks slew-limited instead of infinitely steep. The old view's
 * instantaneous verticals were the main reason the trace read as a chart, not a scope.
 */
private fun buildTrace(
    path: Path,
    buffer: OscilloscopeBuffer,
    window: Int,
    w: Float,
    h: Float,
    pxPerDiv: Float,
    voltsPerDiv: Float,
    positionDiv: Float
) {
    path.reset()
    val capacity = buffer.capacity
    val count = window.coerceIn(1, capacity)
    if (count < 2) return

    val samples = buffer.buffer
    val start = buffer.writeIndex - count
    val stepX = w / (count - 1)
    val baseY = (h / 2f) - positionDiv * pxPerDiv
    val slew = minOf(stepX * SLEW_FRACTION, SLEW_MAX_PX)

    fun yAt(i: Int): Float {
        val v = samples[((start + i) % capacity + capacity) % capacity]
        return baseY - (v / voltsPerDiv) * pxPerDiv
    }

    var prevY = yAt(0)
    path.moveTo(0f, prevY)

    for (i in 1 until count) {
        val x = i * stepX
        val y = yAt(i)
        if (y == prevY) {
            path.lineTo(x, y)
        } else {
            // Flat run up to the shoulder, then a cubic through the transition. The
            // control points sit on the two flat levels, which gives a symmetric
            // S-curve with continuous tangents at both ends.
            val x0 = x - slew
            if (x0 > 0f) path.lineTo(x0, prevY)
            val x1 = x + slew
            path.cubicTo(x, prevY, x, y, x1.coerceAtMost(w), y)
            prevY = y
        }
    }
    path.lineTo(w, prevY)
}

/**
 * Three-pass phosphor stroke: a wide dim bloom, a mid halo, then the bright core.
 * Decreasing width with increasing alpha is what makes the trace read as light
 * emitted by a screen rather than a drawn line.
 */
private fun DrawScope.drawPhosphorTrace(path: Path, core: Color, bloom: Color) {
    drawPath(path, bloom.copy(alpha = 0.14f), style = OuterBloomStroke)
    drawPath(path, bloom.copy(alpha = 0.34f), style = InnerBloomStroke)
    drawPath(path, core, style = CoreStroke)
}

/** Radial vignette + top/bottom falloff, as if the glass curves away at the edges. */
private fun DrawScope.drawEdgeFalloff(w: Float, h: Float) {
    drawRect(
        brush = Brush.radialGradient(
            0.55f to Color.Transparent,
            1f to VignetteEdge,
            center = Offset(w / 2f, h / 2f),
            radius = maxOf(w, h) * 0.72f
        ),
        size = Size(w, h)
    )
    drawRect(
        brush = Brush.verticalGradient(
            0f to ScreenGlare,
            0.35f to Color.Transparent,
            1f to Color.Transparent
        ),
        size = Size(w, h)
    )
}

// Object-constant strokes and brushes: never reallocated inside the draw loop.
private val OuterBloomStroke = Stroke(width = 7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val InnerBloomStroke = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val CoreStroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
private val VignetteEdge = Color(0x99000000)
private val ScreenGlare = Color(0x0AFFFFFF)
