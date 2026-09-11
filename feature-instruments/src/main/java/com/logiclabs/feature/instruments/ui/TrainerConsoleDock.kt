package com.logiclabs.feature.instruments.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.logiclabs.core.designsystem.theme.LogicLabsType
import com.logiclabs.core.designsystem.theme.TextSecondary
import com.logiclabs.feature.instruments.ui.hardware.PanelHaptics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.SimulationMode
import com.logiclabs.core.designsystem.theme.AccentCyan
import com.logiclabs.core.designsystem.theme.AmberCore
import com.logiclabs.core.designsystem.theme.ChassisBevelHighlight
import com.logiclabs.core.designsystem.theme.ChassisBevelShadow
import com.logiclabs.core.designsystem.theme.ChassisSilkscreen
import com.logiclabs.core.designsystem.theme.Dimens
import com.logiclabs.core.designsystem.theme.PhosphorBloom
import com.logiclabs.core.designsystem.theme.ShortCircuitAlert
import com.logiclabs.core.designsystem.theme.SuccessGreen
import com.logiclabs.core.designsystem.theme.SurfaceCard
import com.logiclabs.core.designsystem.theme.SurfaceCardBorder
import com.logiclabs.core.designsystem.theme.SwitchWell
import com.logiclabs.core.designsystem.theme.TextPrimary
import com.logiclabs.core.designsystem.theme.TextTertiary
import com.logiclabs.feature.instruments.ui.hardware.ChassisSurface
import com.logiclabs.feature.instruments.ui.hardware.ClockDial
import com.logiclabs.feature.instruments.ui.hardware.CornerScrews
import com.logiclabs.feature.instruments.ui.hardware.DiagnosticsStrip
import com.logiclabs.feature.instruments.ui.hardware.DragHandle
import com.logiclabs.feature.instruments.ui.hardware.LedLens
import com.logiclabs.feature.instruments.ui.hardware.MasterPowerRocker
import com.logiclabs.feature.instruments.ui.hardware.MomentaryPulser
import com.logiclabs.feature.instruments.ui.hardware.RockerSwitch
import com.logiclabs.feature.instruments.ui.hardware.SectionBlock
import com.logiclabs.feature.instruments.ui.hardware.SevenSegModule
import com.logiclabs.feature.instruments.ui.hardware.recessedWell
import kotlinx.coroutines.delay

/**
 * Mirror of the console-relevant slice of [BreadboardCircuit], held as Compose
 * state.
 *
 * The old dock wrapped its whole subtree in `key(dockVersion, circuitVersion)`,
 * which threw away and rebuilt every node on each switch flip — that killed
 * in-flight press gestures and every animation. Instead the dock reads the model
 * into this snapshot after each `step()`; only the values that actually changed
 * recompose, and interaction state survives.
 */
private class ConsoleSnapshot(circuit: BreadboardCircuit) {
    var masterPower by mutableStateOf(circuit.masterPower)
    val switches: SnapshotStateList<Boolean> = circuit.switches.toList().toMutableStateList()
    var pulserA by mutableStateOf(circuit.pulserAPressed)
    var pulserB by mutableStateOf(circuit.pulserBPressed)
    var clockFrequencyHz by mutableStateOf(circuit.clockFrequencyHz)
    var clockRunning by mutableStateOf(circuit.clockRunning)
    var clockState by mutableStateOf(circuit.clockState)
    val leds: SnapshotStateList<Boolean> = circuit.ledValues.toList().toMutableStateList()
    val ledDuty: SnapshotStateList<Float> = circuit.ledPovDutyCycles.toList().toMutableStateList()
    val seg: SnapshotStateList<Int> = circuit.seg7Digits.toList().toMutableStateList()
    var contention by mutableStateOf(circuit.contentionDetected)
    var reversePolarity by mutableStateOf(circuit.isReversePolarityBurned)
    var oscillationClamped by mutableStateOf(circuit.isOscillationClamped)
    var hasBurnedChips by mutableStateOf(circuit.burnedChipIds.isNotEmpty() || circuit.isReversePolarityBurned)
    var simulationMode by mutableStateOf(circuit.simulationMode)

    /** Pulls every observed field back out of the engine after a `step()`. */
    fun sync(circuit: BreadboardCircuit) {
        masterPower = circuit.masterPower
        pulserA = circuit.pulserAPressed
        pulserB = circuit.pulserBPressed
        clockFrequencyHz = circuit.clockFrequencyHz
        clockRunning = circuit.clockRunning
        clockState = circuit.clockState
        for (i in 0 until 8) {
            if (switches[i] != circuit.switches[i]) switches[i] = circuit.switches[i]
            if (leds[i] != circuit.ledValues[i]) leds[i] = circuit.ledValues[i]
            if (ledDuty[i] != circuit.ledPovDutyCycles[i]) ledDuty[i] = circuit.ledPovDutyCycles[i]
        }
        for (i in 0 until 2) {
            if (seg[i] != circuit.seg7Digits[i]) seg[i] = circuit.seg7Digits[i]
        }
        contention = circuit.contentionDetected
        reversePolarity = circuit.isReversePolarityBurned
        oscillationClamped = circuit.isOscillationClamped
        hasBurnedChips = circuit.burnedChipIds.isNotEmpty() || circuit.isReversePolarityBurned
        simulationMode = circuit.simulationMode
    }
}

/**
 * Highest UI tick rate we will drive the clock line at.
 *
 * The rate knob goes to 100 kHz, but a coroutine cannot (and should not) toggle
 * `clockState` 200 000 times a second — above roughly 30 Hz nothing on screen
 * could resolve individual edges anyway. Past that we run the visible toggle at
 * the display rate and let the LED POV duty cycles convey "fast". The engine's
 * own `clockState` semantics are untouched.
 */
private const val MaxUiTickHz = 60.0

/**
 * How far the console is raised.
 *
 * The dock used to be a boolean that defaulted to fully open, which put ~575dp of
 * instrument panel over an 872dp screen and left the breadboard a letterbox. Three
 * detents fix that: it opens at [HALF] so the switches and indicators — the two
 * things a student touches while wiring — are to hand with the board still visible,
 * and a drag up reaches [FULL].
 *
 * [PEEK] is header-only, for when the board needs the whole screen.
 */
private enum class DockDetent { PEEK, HALF, FULL }

/**
 * Fractions of screen height the open console is allowed to occupy.
 *
 * Deliberately fractions rather than fixed dp: the panel's natural height depends on
 * the system font scale, and a hardcoded 300dp would clip its own content at large
 * scales while wasting a tablet's screen. The content scrolls inside whichever
 * detent is active, so nothing is ever unreachable — [HALF] is a viewport onto the
 * panel, not a subset of it.
 */
private const val HalfDetentFraction = 0.32f
private const val FullDetentFraction = 0.62f

/** Vertical drag, in px, that settles the dock into the next detent. */
private const val DetentDragThreshold = 70f

/**
 * The K&H IDL-800A trainer console: master power, pulsers, variable clock,
 * hex displays, logic indicators and the eight data switches.
 *
 * Writes a field then calls `circuit.step()` — the established contract — and
 * re-syncs local state from the engine afterwards.
 *
 * Opens at [DockDetent.HALF] and is dragged or tapped through [DockDetent] —
 * the panel used to default to fully open and cover most of the breadboard. The
 * body scrolls inside the active detent, so half-open hides nothing permanently.
 *
 * @param circuitVersion bumped by the host when the circuit changed elsewhere;
 *        triggers a re-sync rather than a subtree rebuild.
 */
@Composable
fun TrainerConsoleDock(
    circuit: BreadboardCircuit,
    circuitVersion: Int = 0,
    onCircuitChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    var detent by remember { mutableStateOf(DockDetent.HALF) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val snapshot = remember(circuit) { ConsoleSnapshot(circuit) }

    // Host-driven changes (chip placement, wiring, load) land here.
    LaunchedEffect(circuitVersion) { snapshot.sync(circuit) }

    /** Write-then-step-then-sync: the one way this dock mutates the engine. */
    fun commit(mutate: () -> Unit) {
        mutate()
        circuit.step()
        snapshot.sync(circuit)
        onCircuitChanged()
    }

    // --- Clock driver -------------------------------------------------------
    // Toggles clockState at the selected rate, capped at MaxUiTickHz.
    LaunchedEffect(snapshot.clockRunning, snapshot.clockFrequencyHz, snapshot.masterPower) {
        val hz = snapshot.clockFrequencyHz
        if (!snapshot.clockRunning || !snapshot.masterPower || hz <= 0.0) return@LaunchedEffect
        val effectiveHz = if (hz > MaxUiTickHz) MaxUiTickHz else hz
        // Two half-periods per cycle.
        val halfPeriodMs = (1000.0 / (effectiveHz * 2.0)).toLong().coerceAtLeast(8L)
        while (true) {
            delay(halfPeriodMs)
            circuit.clockState = !circuit.clockState
            circuit.step()
            snapshot.sync(circuit)
            onCircuitChanged()
        }
    }

    // --- Slide-to-dock gesture ---------------------------------------------
    // Drag down steps toward PEEK, drag up toward FULL, one detent per pull. The
    // accumulator resets on every settle so a long drag walks through the detents
    // rather than skipping to an end stop.
    val dragAccum = remember { mutableFloatStateOf(0f) }
    val draggableState = rememberDraggableState { delta ->
        dragAccum.floatValue += delta
        if (dragAccum.floatValue > DetentDragThreshold) {
            detent = when (detent) {
                DockDetent.FULL -> DockDetent.HALF
                DockDetent.HALF -> DockDetent.PEEK
                DockDetent.PEEK -> DockDetent.PEEK
            }
            dragAccum.floatValue = 0f
        } else if (dragAccum.floatValue < -DetentDragThreshold) {
            detent = when (detent) {
                DockDetent.PEEK -> DockDetent.HALF
                DockDetent.HALF -> DockDetent.FULL
                DockDetent.FULL -> DockDetent.FULL
            }
            dragAccum.floatValue = 0f
        }
    }

    // Content height per detent. PEEK is 0 and the panel body is skipped entirely
    // once the animation reaches it, so a collapsed dock composes nothing but its
    // own header. In landscape mode, the console acts as a full-height rack unit.
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenHeight = configuration.screenHeightDp.dp
    val openHeight by animateDpAsState(
        targetValue = when {
            isLandscape -> screenHeight
            detent == DockDetent.PEEK -> 0.dp
            detent == DockDetent.HALF -> screenHeight * HalfDetentFraction
            else -> screenHeight * FullDetentFraction
        },
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "dockHeight",
    )

    // TODO(tokens): switch to Motion.DockSettle
    val chevronRotation by animateFloatAsState(
        targetValue = if (isLandscape || detent == DockDetent.FULL) 0f else 180f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "dockChevron",
    )

    ChassisSurface(
        modifier = modifier
            .then(if (isLandscape) Modifier.fillMaxHeight().widthIn(min = 280.dp) else Modifier.fillMaxWidth())
            // Inset contract, cutout-aware:
            //  - portrait: the dock hugs the bottom, so navigationBars ∪ displayCutout
            //    on the bottom (+ horizontal for corner cutouts) is all it needs.
            //  - landscape: the dock is a full-height right-hand rack column, so it also
            //    takes the status bar at its top — without this the panel header renders
            //    underneath the persistent transparent status bar.
            // windowInsetsPadding consumes what it applies, and the bench's outer
            // container already consumes the horizontal cutout, so nothing pads twice.
            .windowInsetsPadding(
                if (isLandscape) {
                    WindowInsets.statusBars
                        .union(WindowInsets.navigationBars)
                        .union(WindowInsets.displayCutout)
                } else {
                    WindowInsets.navigationBars
                        .union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                }
            )
            .clip(
                if (isLandscape) RoundedCornerShape(topStart = Dimens.RadiusXl, bottomStart = Dimens.RadiusXl)
                else RoundedCornerShape(topStart = Dimens.RadiusXl, topEnd = Dimens.RadiusXl)
            ),
    ) {
        // Corner screws are painted over the whole panel in one draw pass.
        CornerScrews(modifier = Modifier.matchParentSize())

        Column(modifier = if (isLandscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
            ConsoleHeader(
                detent = if (isLandscape) DockDetent.FULL else detent,
                isLive = snapshot.masterPower,
                hasFault = snapshot.contention || snapshot.reversePolarity || snapshot.hasBurnedChips,
                chevronRotation = chevronRotation,
                leds = snapshot.leds,
                ledDuty = snapshot.ledDuty,
                switches = snapshot.switches,
                onToggleSwitch = { i, on -> commit { circuit.switches[i] = on } },
                onToggle = {
                    if (!isLandscape) {
                        detent = when (detent) {
                            DockDetent.PEEK -> DockDetent.HALF
                            DockDetent.HALF -> DockDetent.FULL
                            DockDetent.FULL -> DockDetent.PEEK
                        }
                    }
                },
                modifier = if (!isLandscape) {
                    Modifier.draggable(
                        state = draggableState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { dragAccum.floatValue = 0f },
                    )
                } else Modifier,
            )

            if (isLandscape || openHeight > 0.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isLandscape) Modifier.weight(1f) else Modifier.height(openHeight))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Dimens.Space4, vertical = Dimens.Space2),
                    verticalArrangement = Arrangement.spacedBy(Dimens.Space3),
                ) {
                    // --- HORIZONTAL RACK TABS ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Dimens.Space1),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)
                    ) {
                        val tabs = listOf("I/O CONSOLE", "SIGNALS & CLOCK", "DIAGNOSTICS")
                        tabs.forEachIndexed { i, title ->
                            val selected = selectedTab == i
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(Dimens.RadiusPill))
                                    .background(if (selected) AccentCyan.copy(alpha = 0.18f) else SurfaceCard)
                                    .border(
                                        width = if (selected) 1.dp else Dimens.Hairline,
                                        color = if (selected) AccentCyan else SurfaceCardBorder,
                                        shape = RoundedCornerShape(Dimens.RadiusPill)
                                    )
                                    .clickable {
                                        if (selectedTab != i) {
                                            PanelHaptics.tick(context, view)
                                            selectedTab = i
                                        }
                                    }
                                    .padding(vertical = Dimens.Space2),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = title,
                                    style = LogicLabsType.SwitchPlate,
                                    color = if (selected) AccentCyan else TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    when (selectedTab) {
                        0 -> {
                            // Tab 0: I/O CONSOLE
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Dimens.Space4),
                            ) {
                                SectionBlock(label = "Power", modifier = Modifier.weight(0.9f)) {
                                    MasterPowerRocker(
                                        isOn = snapshot.masterPower,
                                        onToggle = { on -> commit { circuit.masterPower = on } },
                                    )
                                }
                                SectionBlock(label = "Pulsers", modifier = Modifier.weight(1.3f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space3)) {
                                        MomentaryPulser(
                                            label = "PULSER A",
                                            isPressed = snapshot.pulserA,
                                            onPressChanged = { p -> commit { circuit.pulserAPressed = p } },
                                        )
                                        MomentaryPulser(
                                            label = "PULSER B",
                                            isPressed = snapshot.pulserB,
                                            onPressChanged = { p -> commit { circuit.pulserBPressed = p } },
                                        )
                                    }
                                }
                            }

                            SectionBlock(label = "Logic Indicators (L7..L0)") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .recessedWell(fill = SwitchWell)
                                        .padding(vertical = Dimens.Space1),
                                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                                ) {
                                    for (i in 7 downTo 0) {
                                        LedLens(
                                            isOn = snapshot.leds[i],
                                            dutyCycle = snapshot.ledDuty[i],
                                            label = "L$i",
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }

                            SectionBlock(label = "Data Switches (SW7..SW0)") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .recessedWell(fill = SwitchWell)
                                        .padding(vertical = Dimens.Space2),
                                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                                ) {
                                    for (i in 7 downTo 0) {
                                        RockerSwitch(
                                            isOn = snapshot.switches[i],
                                            label = "SW$i",
                                            onToggle = { on -> commit { circuit.switches[i] = on } },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                        1 -> {
                            // Tab 1: SIGNALS & CLOCK
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Dimens.Space4),
                                verticalAlignment = Alignment.Top,
                            ) {
                                SectionBlock(label = "Clock Generator", modifier = Modifier.weight(1.4f)) {
                                    ClockDial(
                                        frequencyHz = snapshot.clockFrequencyHz,
                                        isRunning = snapshot.clockRunning,
                                        clockState = snapshot.clockState,
                                        onFrequencyChange = { hz -> commit { circuit.clockFrequencyHz = hz } },
                                        onRunningChange = { run -> commit { circuit.clockRunning = run } },
                                    )
                                }
                                SectionBlock(label = "7-Segment Display", modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.Space2)) {
                                        SevenSegModule(value = snapshot.seg[0], label = "HEX A")
                                        SevenSegModule(value = snapshot.seg[1], label = "HEX B")
                                    }
                                }
                            }
                        }
                        2 -> {
                            // Tab 2: DIAGNOSTICS
                            SectionBlock(label = "Trainer Diagnostic Monitor") {
                                DiagnosticsStrip(
                                    contention = snapshot.contention,
                                    reversePolarity = snapshot.reversePolarity,
                                    oscillationClamped = snapshot.oscillationClamped,
                                    burnedChip = snapshot.hasBurnedChips,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .recessedWell(fill = SwitchWell, cornerRadius = Dimens.RadiusSm)
                                        .padding(horizontal = Dimens.Space3, vertical = Dimens.Space2),
                                )
                            }
                            if (snapshot.hasBurnedChips) {
                                SectionBlock(label = "IC Silicon Damage Detected") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .recessedWell(fill = Color(0xFF1E0808), cornerRadius = Dimens.RadiusSm)
                                            .border(Dimens.Hairline, ShortCircuitAlert.copy(alpha = 0.6f), RoundedCornerShape(Dimens.RadiusSm))
                                            .padding(Dimens.Space3),
                                        verticalArrangement = Arrangement.spacedBy(Dimens.Space2)
                                    ) {
                                        Text(
                                            text = "FATAL: IC DESTROYED (PRACTICAL MODE)",
                                            style = LogicLabsType.TechnicalSm,
                                            color = ShortCircuitAlert,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Reverse polarity or active gate contention exceeded maximum thermal limits. IC outputs are floating in high-impedance state.",
                                            style = LogicLabsType.TechnicalXs,
                                            color = TextSecondary
                                        )
                                        Button(
                                            onClick = {
                                                commit { circuit.restoreBurnedChips() }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = ShortCircuitAlert),
                                            shape = RoundedCornerShape(Dimens.RadiusSm),
                                            modifier = Modifier.fillMaxWidth().height(36.dp)
                                        ) {
                                            Text(
                                                text = "REPLACE DESTROYED IC(S)",
                                                style = LogicLabsType.TechnicalSm,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                            SectionBlock(label = "Simulation Physics Mode") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .recessedWell(fill = SwitchWell, cornerRadius = Dimens.RadiusSm)
                                        .padding(Dimens.Space3),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ACTIVE: ${snapshot.simulationMode.name}",
                                        style = LogicLabsType.TechnicalSm,
                                        color = if (snapshot.simulationMode == SimulationMode.PRACTICAL) AmberCore else AccentCyan
                                    )
                                    Text(
                                        text = if (snapshot.simulationMode == SimulationMode.PRACTICAL) "Silicon failure rules active" else "Academic fault-tolerant mode",
                                        style = LogicLabsType.TechnicalXs,
                                        color = ChassisSilkscreen
                                    )
                                }
                            }
                            SectionBlock(label = "Supply Rails Status") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .recessedWell(fill = SwitchWell, cornerRadius = Dimens.RadiusSm)
                                        .padding(Dimens.Space3),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (snapshot.masterPower) "+5.0V DC REGULATED (ACTIVE)" else "0.0V DC (POWER OFF)",
                                        style = LogicLabsType.TechnicalSm,
                                        color = if (snapshot.masterPower) PhosphorBloom else TextTertiary
                                    )
                                    Text(
                                        text = "VCC / GND RAILS 0..3",
                                        style = LogicLabsType.TechnicalXs,
                                        color = ChassisSilkscreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Dock header: grab handle, live status lamp, panel nameplate, persistent micro I/O strip, detent chevron. */
@Composable
private fun ConsoleHeader(
    detent: DockDetent,
    isLive: Boolean,
    hasFault: Boolean,
    chevronRotation: Float,
    leds: List<Boolean>,
    ledDuty: List<Float>,
    switches: List<Boolean>,
    onToggleSwitch: (Int, Boolean) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Dimens.Space2, bottom = Dimens.Space1),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DragHandle(
            modifier = Modifier.clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onToggle
            )
        )
        Spacer(modifier = Modifier.height(Dimens.Space1))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onToggle
                )
                .padding(horizontal = Dimens.Space4),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(isLive = isLive, hasFault = hasFault)
                Spacer(modifier = Modifier.width(Dimens.Space2))
                Text(
                    text = "K&H IDL-800A DIGITAL LAB TRAINER",
                    style = LogicLabsType.TechnicalSm,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (detent) {
                        DockDetent.PEEK -> "RAISE"
                        DockDetent.HALF -> "FULL"
                        DockDetent.FULL -> "DOCK"
                    },
                    style = LogicLabsType.TechnicalXs,
                    fontWeight = FontWeight.Bold,
                    color = ChassisSilkscreen,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(Dimens.Space1))
                Canvas(modifier = Modifier.size(12.dp)) {
                    rotate(chevronRotation) {
                        val w = size.width
                        val h = size.height
                        drawLine(
                            AccentCyan,
                            Offset(w * 0.18f, h * 0.36f),
                            Offset(w * 0.5f, h * 0.68f),
                            w * 0.14f,
                        )
                        drawLine(
                            AccentCyan,
                            Offset(w * 0.5f, h * 0.68f),
                            Offset(w * 0.82f, h * 0.36f),
                            w * 0.14f,
                        )
                    }
                }
            }
        }

        // Persistent Micro I/O Strip: accessible directly in PEEK mode
        PersistentMicroIoStrip(
            leds = leds,
            ledDuty = ledDuty,
            switches = switches,
            onToggleSwitch = onToggleSwitch
        )
    }
}

/** Persistent Micro I/O Strip: 8 micro switches SW7–SW0 and 8 micro LEDs L7–L0. */
@Composable
private fun PersistentMicroIoStrip(
    leds: List<Boolean>,
    ledDuty: List<Float>,
    switches: List<Boolean>,
    onToggleSwitch: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val view = LocalView.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.Space4, vertical = 2.dp)
            .recessedWell(fill = SwitchWell, cornerRadius = Dimens.RadiusSm)
            .padding(horizontal = Dimens.Space2, vertical = 3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // Micro LEDs row (L7 downTo L0)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 7 downTo 0) {
                MicroLedItem(
                    index = i,
                    isOn = leds.getOrElse(i) { false },
                    dutyCycle = ledDuty.getOrElse(i) { 0f },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Micro Switches row (SW7 downTo SW0)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 7 downTo 0) {
                val isOn = switches.getOrElse(i) { false }
                MicroSwitchItem(
                    index = i,
                    isOn = isOn,
                    onToggle = { nextState ->
                        if (!isOn && nextState) {
                            PanelHaptics.tick(context, view)
                        }
                        onToggleSwitch(i, nextState)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MicroLedItem(
    index: Int,
    isOn: Boolean,
    dutyCycle: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(7.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f
            if (isOn || dutyCycle > 0.05f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(PhosphorBloom.copy(alpha = 0.5f), Color.Transparent),
                        center = c,
                        radius = r * 2.2f
                    ),
                    radius = r * 2.2f,
                    center = c
                )
                drawCircle(color = PhosphorBloom, radius = r, center = c)
            } else {
                drawCircle(color = Color(0xFF14241B), radius = r, center = c)
            }
            drawCircle(color = Color(0xFF2A2D35), radius = r, center = c, style = Stroke(0.6.dp.toPx()))
        }
        Text(
            text = "L$index",
            style = LogicLabsType.TechnicalXs.copy(fontSize = 7.sp, lineHeight = 8.sp),
            color = if (isOn) PhosphorBloom else ChassisSilkscreen
        )
    }
}

@Composable
private fun MicroSwitchItem(
    index: Int,
    isOn: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.RadiusSm))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Switch,
                onClick = { onToggle(!isOn) }
            )
            .padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(11.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (isOn) PhosphorBloom.copy(alpha = 0.25f) else SurfaceCard)
                .border(
                    width = 0.8.dp,
                    color = if (isOn) PhosphorBloom else SurfaceCardBorder,
                    shape = RoundedCornerShape(2.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isOn) "1" else "0",
                style = LogicLabsType.TechnicalXs.copy(fontSize = 7.sp, lineHeight = 8.sp, fontWeight = FontWeight.Bold),
                color = if (isOn) PhosphorBloom else TextTertiary
            )
        }
        Spacer(Modifier.height(1.dp))
        Text(
            text = "SW$index",
            style = LogicLabsType.TechnicalXs.copy(fontSize = 7.sp, lineHeight = 8.sp),
            color = ChassisSilkscreen
        )
    }
}

/** Bench status lamp: green live, red fault, dark when the bench is powered down. */
@Composable
private fun StatusDot(isLive: Boolean, hasFault: Boolean) {
    val color = when {
        hasFault -> ShortCircuitAlert
        isLive -> SuccessGreen
        else -> TextTertiary
    }
    Canvas(modifier = Modifier.size(9.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        if (isLive || hasFault) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.5f), Color.Transparent),
                    center = c,
                    radius = r * 2.8f,
                ),
                radius = r * 2.8f,
                center = c,
            )
        }
        drawCircle(color = color, radius = r * 0.72f, center = c)
        drawCircle(
            color = ChassisBevelShadow,
            radius = r * 0.72f,
            center = c,
            style = Stroke(width = r * 0.24f),
        )
        drawCircle(
            color = ChassisBevelHighlight.copy(alpha = 0.3f),
            radius = r * 0.34f,
            center = Offset(c.x - r * 0.22f, c.y - r * 0.24f),
        )
    }
}
