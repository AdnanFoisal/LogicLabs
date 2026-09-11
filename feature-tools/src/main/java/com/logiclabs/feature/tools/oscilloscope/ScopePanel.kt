package com.logiclabs.feature.tools.oscilloscope

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.designsystem.theme.Dimens

/** Hard ceiling on engine steps per display frame, so a slow timebase cannot stall the UI. */
private const val MAX_STEPS_PER_FRAME = 64

/** Nominal display period. Real pacing comes from `withFrameNanos`; this is the fallback. */
private const val NOMINAL_FRAME_MS = 16.667f

/**
 * The complete scope instrument: probe pickers, CRT, front-panel controls, and the
 * live sweep loop that actually advances the simulation.
 *
 * The `:app` layer owns the bottom sheet; this panel is the whole contents of it:
 *
 * ```
 * val settings = rememberScopeSettings()          // or hoist a ScopeSettings yourself
 * ScopePanel(circuit = circuit, settings = settings)
 * ```
 *
 * ## What the sweep loop does
 * While [ScopeSettings.running] is true and the panel is composed, a `withFrameNanos`
 * loop calls `circuit.step()` once per [ScopeSettings.acquisitionIntervalMs] of
 * simulated time, batched per display frame and capped at [MAX_STEPS_PER_FRAME]. Each
 * `step()` pushes one sample into `circuit.scopeCh1` / `scopeCh2`, so the trace sweeps
 * at the display rate and no faster.
 *
 * The loop also mirrors [ScopeSettings.probe1Socket] / [ScopeSettings.probe2Socket]
 * into `circuit.probe1Socket` / `circuit.probe2Socket`. Those engine fields are plain
 * `var`s that nothing in the app ever wrote — only unit tests did — which is why both
 * buffers stayed all-zeros and both traces drew flat.
 *
 * @param onSample optional per-frame hook, e.g. for the caller to bump its own
 *   circuit-changed trigger so the rest of the workbench repaints too.
 */
@Composable
fun ScopePanel(
    circuit: BreadboardCircuit,
    settings: ScopeSettings,
    modifier: Modifier = Modifier,
    onSample: (() -> Unit)? = null
) {
    // Frame counter handed to OscilloscopeView as its redraw key: OscilloscopeBuffer is
    // a plain class, so Compose has no other way to notice that new samples arrived.
    var frame by remember { mutableIntStateOf(0) }

    // Push the picker selections into the engine. Kept as its own effect so changing a
    // probe takes effect immediately, even while STOPped.
    LaunchedEffect(settings.probe1Socket, settings.probe2Socket) {
        circuit.probe1Socket = settings.probe1Socket
        circuit.probe2Socket = settings.probe2Socket
    }

    LaunchedEffect(settings.running, settings.acquisitionIntervalMs, circuit) {
        if (!settings.running) return@LaunchedEffect
        var lastNanos = 0L
        var carryMs = 0f
        while (true) {
            val now = withFrameNanos { it }
            val elapsedMs = if (lastNanos == 0L) NOMINAL_FRAME_MS else (now - lastNanos) / 1_000_000f
            lastNanos = now

            val interval = settings.acquisitionIntervalMs.coerceAtLeast(0.05f)
            carryMs += elapsedMs.coerceIn(0f, 100f)
            var steps = (carryMs / interval).toInt()
            if (steps > MAX_STEPS_PER_FRAME) steps = MAX_STEPS_PER_FRAME
            carryMs -= steps * interval
            if (carryMs < 0f) carryMs = 0f

            repeat(steps) { circuit.step() }
            if (steps > 0) {
                frame++
                onSample?.invoke()
            }
        }
    }

    val window = settings.windowSamples(circuit.scopeCh1.capacity)
    val primaryBuffer = if (settings.channelMode == ScopeChannelMode.CH2) circuit.scopeCh2 else circuit.scopeCh1
    val frequencyHz = rememberTraceFrequencyHz(
        buffer = primaryBuffer,
        sampleIntervalMs = settings.acquisitionIntervalMs,
        windowSamples = window,
        running = settings.running
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space2)
    ) {
        OscilloscopeView(
            ch1Buffer = circuit.scopeCh1,
            ch2Buffer = circuit.scopeCh2,
            timebaseMs = settings.timePerDivMs,
            voltsPerDiv = settings.voltsPerDiv,
            channelMode = settings.channelMode,
            sampleIntervalMs = settings.acquisitionIntervalMs,
            redrawKey = frame
        )

        ScopeControls(settings = settings, frequencyHz = frequencyHz)

        ProbePicker(
            circuit = circuit,
            channel = 1,
            currentSocket = settings.probe1Socket,
            onSelect = { settings.probe1Socket = it }
        )
        ProbePicker(
            circuit = circuit,
            channel = 2,
            currentSocket = settings.probe2Socket,
            onSelect = { settings.probe2Socket = it }
        )
    }
}
