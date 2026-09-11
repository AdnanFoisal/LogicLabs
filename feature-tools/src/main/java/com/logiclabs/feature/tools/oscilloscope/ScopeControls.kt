package com.logiclabs.feature.tools.oscilloscope

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logiclabs.core.bridge.circuit.OscilloscopeBuffer
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.AmberCore
import com.logiclabs.core.designsystem.theme.ChassisBevelHighlight
import com.logiclabs.core.designsystem.theme.ChassisBevelShadow
import com.logiclabs.core.designsystem.theme.ChassisSilkscreen
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.GlassHairline
import com.logiclabs.core.designsystem.theme.PhosphorCore
import com.logiclabs.core.designsystem.theme.ShortCircuitAlert
import com.logiclabs.core.designsystem.theme.SuccessGreen
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.SurfaceRaised
import com.logiclabs.core.designsystem.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Which trace(s) the CRT shows. */
enum class ScopeChannelMode(val label: String) {
    CH1("CH1"),
    CH2("CH2"),
    DUAL("DUAL")
}

/**
 * Front-panel state of the dual-trace scope.
 *
 * Held in a `@Stable` holder so either layer can own it: the `:app` scope sheet can
 * hoist one instance (so settings survive the sheet closing), or a caller can let
 * [ScopePanel] create a throw-away one via [rememberScopeSettings].
 *
 * Every property is snapshot state, so mutating it from a click handler recomposes
 * exactly the readouts that observe it.
 *
 * ### Timebase / acquisition contract
 * One `BreadboardCircuit.step()` pushes exactly one sample into each
 * [OscilloscopeBuffer], so the *acquisition period* of the buffer is
 * [acquisitionIntervalMs] and the horizontal window is
 * `HORIZONTAL_DIVISIONS * timePerDivMs` milliseconds wide — see
 * [ScopeMath.windowSamples]. [ScopePanel] honours [acquisitionIntervalMs] by
 * batching that many `step()` calls per display frame, which is what makes
 * `TIME/DIV` genuinely change the window into the 8192-sample ring buffer instead
 * of the old hardcoded "last 200 samples regardless of timebase".
 */
@Stable
class ScopeSettings(
    timePerDivMs: Float = DEFAULT_TIME_PER_DIV_MS,
    voltsPerDiv: Float = DEFAULT_VOLTS_PER_DIV,
    channelMode: ScopeChannelMode = ScopeChannelMode.DUAL,
    running: Boolean = true,
    probe1Socket: Int? = null,
    probe2Socket: Int? = null,
    acquisitionIntervalMs: Float = DEFAULT_ACQUISITION_INTERVAL_MS
) {
    /** Horizontal sweep speed in milliseconds per division. One of [TIME_PER_DIV_STEPS]. */
    var timePerDivMs: Float by mutableFloatStateOf(timePerDivMs)

    /** Vertical sensitivity in volts per division. One of [VOLTS_PER_DIV_STEPS]. */
    var voltsPerDiv: Float by mutableFloatStateOf(voltsPerDiv)

    /** Which trace(s) are displayed. */
    var channelMode: ScopeChannelMode by mutableStateOf(channelMode)

    /** RUN (true) sweeps live; STOP (false) freezes the acquisition. */
    var running: Boolean by mutableStateOf(running)

    /**
     * Compose-visible mirror of `BreadboardCircuit.probe1Socket`.
     *
     * The engine field is a plain `var` (not snapshot state) so the UI cannot observe
     * it; [ScopePanel] pushes this value into the circuit whenever it changes. Before
     * this existed the probe fields were only ever assigned by unit tests, which is
     * why both traces rendered perfectly flat in the running app.
     */
    var probe1Socket: Int? by mutableStateOf(probe1Socket)

    /** Compose-visible mirror of `BreadboardCircuit.probe2Socket`. */
    var probe2Socket: Int? by mutableStateOf(probe2Socket)

    /** Milliseconds of simulated time per acquired sample (one `circuit.step()`). */
    var acquisitionIntervalMs: Float by mutableFloatStateOf(acquisitionIntervalMs)

    /** Number of samples the current timebase maps onto the screen width. */
    fun windowSamples(capacity: Int): Int =
        ScopeMath.windowSamples(timePerDivMs, acquisitionIntervalMs, capacity)

    fun stepTimePerDiv(delta: Int) {
        val idx = TIME_PER_DIV_STEPS.indexOfFirst { it == timePerDivMs }.let { if (it < 0) 0 else it }
        timePerDivMs = TIME_PER_DIV_STEPS[(idx + delta).coerceIn(0, TIME_PER_DIV_STEPS.lastIndex)]
    }

    fun stepVoltsPerDiv(delta: Int) {
        val idx = VOLTS_PER_DIV_STEPS.indexOfFirst { it == voltsPerDiv }.let { if (it < 0) 0 else it }
        voltsPerDiv = VOLTS_PER_DIV_STEPS[(idx + delta).coerceIn(0, VOLTS_PER_DIV_STEPS.lastIndex)]
    }

    companion object {
        val TIME_PER_DIV_STEPS: List<Float> = listOf(1f, 5f, 10f, 50f)
        val VOLTS_PER_DIV_STEPS: List<Float> = listOf(1f, 2f, 5f)

        const val DEFAULT_TIME_PER_DIV_MS = 10f
        const val DEFAULT_VOLTS_PER_DIV = 5f

        /**
         * Default simulated period of one acquired sample. 1 ms per `step()` gives a
         * 1 kHz acquisition rate — roughly 17 engine steps per 60 Hz display frame,
         * which is affordable for the small lab circuits and fine enough to resolve
         * the 1 ms/div sweep.
         */
        const val DEFAULT_ACQUISITION_INTERVAL_MS = 1f
    }
}

/** Remembers a [ScopeSettings] for callers that do not want to hoist it themselves. */
@Composable
fun rememberScopeSettings(
    timePerDivMs: Float = ScopeSettings.DEFAULT_TIME_PER_DIV_MS,
    voltsPerDiv: Float = ScopeSettings.DEFAULT_VOLTS_PER_DIV,
    channelMode: ScopeChannelMode = ScopeChannelMode.DUAL,
    running: Boolean = true
): ScopeSettings = remember {
    ScopeSettings(
        timePerDivMs = timePerDivMs,
        voltsPerDiv = voltsPerDiv,
        channelMode = channelMode,
        running = running
    )
}

/** Pure horizontal/vertical scaling maths, shared by the view and the panel. */
object ScopeMath {
    const val HORIZONTAL_DIVISIONS = 10
    const val VERTICAL_DIVISIONS = 8

    /** Never sweep fewer than this many samples, or a fast timebase draws nothing. */
    const val MIN_WINDOW_SAMPLES = 8

    /**
     * Samples of the ring buffer that fill the screen width:
     * `10 divisions * ms/div / ms-per-sample`, clamped to the buffer capacity.
     */
    fun windowSamples(timePerDivMs: Float, sampleIntervalMs: Float, capacity: Int): Int {
        if (capacity <= MIN_WINDOW_SAMPLES) return capacity.coerceAtLeast(1)
        val interval = if (sampleIntervalMs > 0f) sampleIntervalMs else 1f
        val span = (HORIZONTAL_DIVISIONS * timePerDivMs) / interval
        return span.roundToInt().coerceIn(MIN_WINDOW_SAMPLES, capacity)
    }

    /**
     * Rising-edge frequency of the samples currently on screen, in Hz.
     * Measured between the first and last rising edge so a partial cycle at either
     * end of the window does not bias the result. Returns 0 for a flat trace.
     */
    fun measureFrequencyHz(
        buffer: FloatArray,
        capacity: Int,
        writeIndex: Int,
        count: Int,
        sampleIntervalMs: Float,
        threshold: Float = 2.5f
    ): Float {
        if (count < 4 || capacity <= 0) return 0f
        val interval = if (sampleIntervalMs > 0f) sampleIntervalMs else 1f
        var firstEdge = -1
        var lastEdge = -1
        var edges = 0
        var prev = buffer[((writeIndex - count) + capacity) % capacity]
        for (i in 1 until count) {
            val v = buffer[((writeIndex - count + i) + capacity) % capacity]
            if (prev < threshold && v >= threshold) {
                if (firstEdge < 0) firstEdge = i
                lastEdge = i
                edges++
            }
            prev = v
        }
        if (edges < 2 || lastEdge <= firstEdge) return 0f
        val spanMs = (lastEdge - firstEdge) * interval
        if (spanMs <= 0f) return 0f
        return (edges - 1) * 1000f / spanMs
    }
}

/**
 * Polls the visible window of [buffer] on a slow cadence and reports its rising-edge
 * frequency. Deliberately *not* per-frame: the readout only needs to be legible.
 */
@Composable
fun rememberTraceFrequencyHz(
    buffer: OscilloscopeBuffer,
    sampleIntervalMs: Float,
    windowSamples: Int,
    running: Boolean,
    refreshMs: Long = 250L
): Float {
    var hz by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(buffer, sampleIntervalMs, windowSamples, running, refreshMs) {
        while (true) {
            hz = ScopeMath.measureFrequencyHz(
                buffer = buffer.buffer,
                capacity = buffer.capacity,
                writeIndex = buffer.writeIndex,
                count = windowSamples,
                sampleIntervalMs = sampleIntervalMs
            )
            if (!running) break
            delay(refreshMs)
        }
    }
    return hz
}

/**
 * Industrial front panel for the scope: TIME/DIV and VOLTS/DIV segmented steppers,
 * the CH1/CH2/DUAL source toggle, RUN/STOP and a live frequency readout.
 *
 * Before this existed, `timebaseMs` was a parameter nobody passed and CH1/CH2/TB were
 * static `Text` labels with no behaviour behind them.
 */
@Composable
fun ScopeControls(
    settings: ScopeSettings,
    modifier: Modifier = Modifier,
    frequencyHz: Float = 0f
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.RadiusMd))
            .background(SurfaceCard)
            .border(Dimens.Hairline, GlassHairline, RoundedCornerShape(Dimens.RadiusMd))
            .padding(Dimens.Space3),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space3)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space4)
        ) {
            SegmentGroup(
                label = "TIME/DIV",
                modifier = Modifier.weight(1.35f),
                options = ScopeSettings.TIME_PER_DIV_STEPS,
                selected = settings.timePerDivMs,
                accent = AccentCyan,
                caption = { "${formatNumber(it)}ms" },
                onSelect = { settings.timePerDivMs = it }
            )
            SegmentGroup(
                label = "VOLTS/DIV",
                modifier = Modifier.weight(1f),
                options = ScopeSettings.VOLTS_PER_DIV_STEPS,
                selected = settings.voltsPerDiv,
                accent = AmberCore,
                caption = { "${formatNumber(it)}V" },
                onSelect = { settings.voltsPerDiv = it }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.Space4),
            verticalAlignment = Alignment.Bottom
        ) {
            SegmentGroup(
                label = "SOURCE",
                modifier = Modifier.weight(1.15f),
                options = ScopeChannelMode.entries.toList(),
                selected = settings.channelMode,
                accent = PhosphorCore,
                caption = { it.label },
                onSelect = { settings.channelMode = it }
            )

            Column(modifier = Modifier.weight(0.75f)) {
                PanelLabel("SWEEP")
                Spacer(modifier = Modifier.height(Dimens.Space1))
                BenchKey(
                    modifier = Modifier.fillMaxWidth(),
                    text = if (settings.running) "RUN" else "STOP",
                    selected = true,
                    accent = if (settings.running) SuccessGreen else ShortCircuitAlert,
                    onClick = { settings.running = !settings.running }
                )
            }

            Column(modifier = Modifier.weight(0.9f)) {
                PanelLabel("FREQ")
                Spacer(modifier = Modifier.height(Dimens.Space1))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.MinTouchTarget)
                        .clip(RoundedCornerShape(Dimens.RadiusSm))
                        .background(SurfaceRaised)
                        .border(Dimens.Hairline, ChassisBevelShadow, RoundedCornerShape(Dimens.RadiusSm)),
                    contentAlignment = Alignment.Center
                ) {
                    // TODO(tokens): swap for Type.kt readout style once it lands.
                    Text(
                        text = formatFrequency(frequencyHz),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (frequencyHz > 0f) PhosphorCore else TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> SegmentGroup(
    label: String,
    options: List<T>,
    selected: T,
    accent: androidx.compose.ui.graphics.Color,
    caption: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        PanelLabel(label)
        Spacer(modifier = Modifier.height(Dimens.Space1))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Dimens.RadiusSm))
                .background(ChassisBevelShadow)
                .padding(1.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            for (option in options) {
                BenchKey(
                    modifier = Modifier.weight(1f),
                    text = caption(option),
                    selected = option == selected,
                    accent = accent,
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}

@Composable
private fun BenchKey(
    text: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            // Dimens.MinTouchTarget is the accessibility floor for every key on the panel.
            .heightIn(min = Dimens.MinTouchTarget)
            .widthIn(min = 40.dp)
            .clip(RoundedCornerShape(Dimens.RadiusSm))
            .background(if (selected) SurfaceRaised else SurfaceCard)
            .border(
                width = Dimens.Hairline,
                color = if (selected) accent.copy(alpha = 0.7f) else ChassisBevelHighlight,
                shape = RoundedCornerShape(Dimens.RadiusSm)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = Dimens.Space1),
        contentAlignment = Alignment.Center
    ) {
        // TODO(tokens): swap for Type.kt key label style once it lands.
        Text(
            text = text,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) accent else TextSecondary,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PanelLabel(text: String) {
    // TODO(tokens): swap for Type.kt silkscreen style once it lands.
    Text(
        text = text,
        fontSize = 9.sp,
        letterSpacing = 1.2.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = ChassisSilkscreen
    )
}

internal fun formatNumber(value: Float): String {
    val rounded = value.roundToInt()
    return if (kotlin.math.abs(value - rounded) < 0.05f) rounded.toString() else String.format("%.1f", value)
}

internal fun formatFrequency(hz: Float): String = when {
    hz <= 0f -> "-- Hz"
    hz < 10f -> String.format("%.2f Hz", hz)
    hz < 1000f -> String.format("%.1f Hz", hz)
    else -> String.format("%.2f kHz", hz / 1000f)
}

/** Kept internal so [ScopePanel] and the view agree on the CH1/CH2 identity colours. */
internal val Ch1TraceAccent = PhosphorCore
internal val Ch2TraceAccent = AmberCore
