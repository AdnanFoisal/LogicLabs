package com.logiclabs.app.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.JumperWire
import com.logiclabs.core.bridge.model.SimulationMode
import com.logiclabs.core.bridge.model.WireColor

/** Which pointer gesture the breadboard is currently interpreting. */
enum class BoardMode { ROAM, WIRE_DRAW, WIRE_SELECT }

/** The transient sheet or dialog layered over the bench, if any. */
enum class Overlay { NONE, LAB_CATALOG, CHIP_CATALOG, SCOPE, VERIFIER, BOARD_ACTIONS }

/**
 * Compose-observable mirror of a [BreadboardCircuit].
 *
 * [BreadboardCircuit] is a plain mutable class — deliberately, since the solver runs on
 * flat arrays and must not pay for snapshot writes 30 passes deep. That leaves the view
 * layer with no way to observe it, and the previous screen coped by incrementing a
 * `circuitUpdateTrigger` counter and wrapping whole subtrees in `key(trigger) { … }`.
 * `key` does not recompose a subtree, it *discards and rebuilds* it, so every switch
 * flip destroyed all animation state, every `InteractionSource`, and every
 * `animateFloatAsState` in the console. Nothing could animate, which is exactly what
 * the bench looked like.
 *
 * This holder inverts that. The engine stays unobserved and fast; after each
 * [BreadboardCircuit.step] a single [syncFromCircuit] copies the handful of *output*
 * fields the UI actually renders into snapshot state. Subtrees then recompose on value
 * change, keep their identity, and animate normally.
 *
 * Writes go the other way and follow the engine's own contract: set the field, call
 * `step()`, then sync. [drive] wraps that so no call site can forget a step, which was
 * a real hazard in the old screen.
 *
 * Not thread-safe and not meant to be: every method is called from the composition
 * thread, same as the code it replaces.
 */
@Stable
class WorkbenchState(val circuit: BreadboardCircuit) {

    // -- Mirrored console outputs -------------------------------------------------
    // Read by the dock, the HUD and the canvas. Written only by syncFromCircuit.

    var masterPower: Boolean by mutableStateOf(circuit.masterPower)
        private set

    /**
     * Packed switch bits, LSB = SW0. One Int rather than eight Booleans so a sync is a
     * single snapshot write and a full read costs one state read instead of eight.
     */
    var switchBits: Int by mutableIntStateOf(0)
        private set

    /** Packed LED states, LSB = L0. Same reasoning as [switchBits]. */
    var ledBits: Int by mutableIntStateOf(0)
        private set

    /**
     * Per-LED duty cycle, for afterglow on signals faster than the frame rate. Copied
     * into a plain array behind one version counter — eight individually observable
     * floats would cost more to track than the eight LEDs cost to redraw together.
     */
    val ledDuty: FloatArray = FloatArray(8)

    /** Bumps whenever [ledDuty] contents change, so readers have something to observe. */
    var ledDutyVersion: Int by mutableIntStateOf(0)
        private set

    var seg7A: Int by mutableIntStateOf(0)
        private set

    var seg7B: Int by mutableIntStateOf(0)
        private set

    var pulserA: Boolean by mutableStateOf(false)
        private set

    var pulserB: Boolean by mutableStateOf(false)
        private set

    var clockFrequencyHz: Float by mutableFloatStateOf(circuit.clockFrequencyHz.toFloat())
        private set

    var clockRunning: Boolean by mutableStateOf(circuit.clockRunning)
        private set

    var clockState: Boolean by mutableStateOf(false)
        private set

    // -- Diagnostics --------------------------------------------------------------

    var contentionDetected: Boolean by mutableStateOf(false)
        private set

    var reversePolarityBurned: Boolean by mutableStateOf(false)
        private set

    var oscillationClamped: Boolean by mutableStateOf(false)
        private set

    // -- Board census -------------------------------------------------------------

    var chipCount: Int by mutableIntStateOf(0)
        private set

    var wireCount: Int by mutableIntStateOf(0)
        private set

    /**
     * Version stamp for board *topology* — chips and wires added, removed or moved.
     * The canvas keys its static-layer cache off this, so it does not rebuild when only
     * a switch or LED changed.
     */
    var topologyVersion: Int by mutableIntStateOf(0)
        private set

    /**
     * Version stamp for live simulation and hardware states (switches, LEDs, clock, power).
     * The canvas redraw loop observes this so switch toggles and signal transitions redraw
     * immediately without requiring zoom or pan gestures.
     */
    var renderVersion: Int by mutableIntStateOf(0)
        private set

    // -- View-only interaction state ---------------------------------------------
    // Never touches the engine; lives here so the screen composable holds no state of
    // its own and stays a pure orchestrator.

    var boardMode: BoardMode by mutableStateOf(BoardMode.ROAM)

    var activeWireColor: WireColor by mutableStateOf(WireColor.RED)

    var selectedWireId: String? by mutableStateOf(null)
        private set

    var selectedChipId: String? by mutableStateOf(null)
        private set

    var overlay: Overlay by mutableStateOf(Overlay.NONE)

    /**
     * True while the bench shows the boolean (gate-level schematic) rendering of the
     * circuit instead of the physical breadboard. This is a view *version* switch, not
     * a mode: the board topology, selection and console state are untouched, so
     * toggling back restores the breadboard exactly as it was (re-fitted to content).
     */
    var viewingDiagram: Boolean by mutableStateOf(false)

    /** True while a verification report exists and passed — drives the HUD badge. */
    var lastVerificationPassed: Boolean? by mutableStateOf(null)

    /**
     * LED afterglow gate, driven from the Settings screen. When false, the POV duty
     * cycles are snapped to hard on/off before publishing, so fast signals read as
     * crisp logic levels instead of persistence-of-vision glow.
     */
    var afterglowEnabled: Boolean = true

    /**
     * Simulation mode: IDEAL (tolerant, educational) vs PRACTICAL (real-life IC destruction physics).
     */
    var simulationMode: SimulationMode by mutableStateOf(circuit.simulationMode)
        private set

    /** True when at least one IC has been destroyed in PRACTICAL mode. */
    var hasBurnedChips: Boolean by mutableStateOf(circuit.burnedChipIds.isNotEmpty() || circuit.isReversePolarityBurned)
        private set

    fun driveSimulationMode(mode: SimulationMode) {
        drive { it.simulationMode = mode }
    }

    fun toggleSimulationMode() {
        val next = if (simulationMode == SimulationMode.IDEAL) SimulationMode.PRACTICAL else SimulationMode.IDEAL
        driveSimulationMode(next)
    }

    fun restoreBurnedChips() {
        driveTopology { it.restoreBurnedChips() }
    }

    // -- Reads --------------------------------------------------------------------

    fun switchAt(index: Int): Boolean = (switchBits shr index) and 1 == 1

    fun ledAt(index: Int): Boolean = (ledBits shr index) and 1 == 1

    /** Snapshot-safe read of [ledDuty]; touch [ledDutyVersion] so callers re-run. */
    fun ledDutyAt(index: Int): Float {
        @Suppress("UNUSED_EXPRESSION") ledDutyVersion
        return ledDuty[index]
    }

    /**
     * The live wire list. Backed by [SnapshotStateList] so the context ribbon and the
     * canvas observe insertions without a version counter, and kept in step with
     * `circuit.wires` by [syncFromCircuit].
     */
    val wires: SnapshotStateList<JumperWire> = circuit.wires.toMutableStateList()

    fun selectedWire(): JumperWire? =
        selectedWireId?.let { id -> wires.firstOrNull { it.id == id } }

    // -- Writes -------------------------------------------------------------------

    /**
     * The one sanctioned way to mutate the engine from the view layer: apply [block] to
     * the circuit, settle it, then republish. Callers never call `step()` themselves, so
     * a mutation cannot be left unsolved or unsynced.
     */
    inline fun drive(block: (BreadboardCircuit) -> Unit) {
        block(circuit)
        circuit.step()
        syncFromCircuit()
    }

    /** As [drive], but also marks board topology dirty so cached layers rebuild. */
    inline fun driveTopology(block: (BreadboardCircuit) -> Unit) {
        block(circuit)
        circuit.step()
        syncTopology()
        syncFromCircuit()
    }

    // Named drive* rather than set*: each mirrored field above has a `private set`, whose
    // generated JVM setter would collide with a `setMasterPower(Boolean)` of our own. The
    // prefix also reads correctly — these write the engine, they do not assign a property.

    fun driveMasterPower(on: Boolean) = drive { it.masterPower = on }

    fun driveSwitch(index: Int, on: Boolean) = drive { it.switches[index] = on }

    fun toggleSwitch(index: Int) = driveSwitch(index, !switchAt(index))

    fun drivePulserA(pressed: Boolean) = drive { it.pulserAPressed = pressed }

    fun drivePulserB(pressed: Boolean) = drive { it.pulserBPressed = pressed }

    fun driveClockFrequency(hz: Float) = drive { it.clockFrequencyHz = hz.toDouble() }

    fun driveClockRunning(running: Boolean) = drive { it.clockRunning = running }

    /** Advances the clock one half-cycle. Driven by the console's clock timer. */
    fun tickClock() = drive { it.clockState = !it.clockState }

    fun selectWire(id: String?) {
        selectedWireId = id
        if (id != null) {
            selectedChipId = null
            wires.firstOrNull { it.id == id }?.let { activeWireColor = it.color }
        }
    }

    fun selectChip(id: String?) {
        selectedChipId = id
        if (id != null) selectedWireId = null
    }

    fun clearSelection() {
        selectedWireId = null
        selectedChipId = null
    }

    // -- Sync ---------------------------------------------------------------------

    /**
     * Republishes engine outputs into snapshot state.
     *
     * Every assignment is guarded by an equality check. Compose already skips writes of
     * an identical value, but the guard also keeps the cheap packed ints from being
     * recomputed into new boxes, and makes the no-change case genuinely free — which
     * matters because the clock calls this at up to 100kHz-nominal rates.
     */
    fun syncFromCircuit() {
        val c = circuit
        var changed = false

        if (masterPower != c.masterPower) {
            masterPower = c.masterPower
            changed = true
        }

        var sw = 0
        for (i in 0..7) if (c.switches[i]) sw = sw or (1 shl i)
        if (switchBits != sw) {
            switchBits = sw
            changed = true
        }

        var led = 0
        for (i in 0..7) if (c.ledValues[i]) led = led or (1 shl i)
        if (ledBits != led) {
            ledBits = led
            changed = true
        }

        var dutyChanged = false
        for (i in 0..7) {
            // Afterglow off snaps duty to the instantaneous logic level.
            val d = if (afterglowEnabled) {
                c.ledPovDutyCycles[i]
            } else {
                if (c.ledValues[i]) 1f else 0f
            }
            if (ledDuty[i] != d) {
                ledDuty[i] = d
                dutyChanged = true
            }
        }
        if (dutyChanged) {
            ledDutyVersion++
            changed = true
        }

        if (seg7A != c.seg7Digits[0]) {
            seg7A = c.seg7Digits[0]
            changed = true
        }
        if (seg7B != c.seg7Digits[1]) {
            seg7B = c.seg7Digits[1]
            changed = true
        }

        if (pulserA != c.pulserAPressed) {
            pulserA = c.pulserAPressed
            changed = true
        }
        if (pulserB != c.pulserBPressed) {
            pulserB = c.pulserBPressed
            changed = true
        }

        val hz = c.clockFrequencyHz.toFloat()
        if (clockFrequencyHz != hz) {
            clockFrequencyHz = hz
            changed = true
        }
        if (clockRunning != c.clockRunning) {
            clockRunning = c.clockRunning
            changed = true
        }
        if (clockState != c.clockState) {
            clockState = c.clockState
            changed = true
        }

        if (contentionDetected != c.contentionDetected) {
            contentionDetected = c.contentionDetected
            changed = true
        }
        if (reversePolarityBurned != c.isReversePolarityBurned) {
            reversePolarityBurned = c.isReversePolarityBurned
            changed = true
        }
        if (oscillationClamped != c.isOscillationClamped) {
            oscillationClamped = c.isOscillationClamped
            changed = true
        }

        val burned = c.burnedChipIds.isNotEmpty() || c.isReversePolarityBurned
        if (hasBurnedChips != burned) {
            hasBurnedChips = burned
            changed = true
        }
        if (simulationMode != c.simulationMode) {
            simulationMode = c.simulationMode
            changed = true
        }

        if (changed) {
            renderVersion++
        }
    }

    /**
     * Republishes board topology: the wire list, the census, and the version stamp the
     * canvas caches against. Separate from [syncFromCircuit] because it is comparatively
     * expensive and only a handful of actions can change topology.
     */
    fun syncTopology() {
        val c = circuit

        // Rebuild only on an actual difference. JumperWire is a data class, so this is
        // a structural comparison and catches colour and routing edits as well as
        // insertions — which in-place `wires[i] = copy(...)` edits would otherwise miss.
        if (wires.size != c.wires.size || !wires.matches(c.wires)) {
            wires.clear()
            wires.addAll(c.wires)
        }

        if (chipCount != c.placedChips.size) chipCount = c.placedChips.size
        if (wireCount != c.wires.size) wireCount = c.wires.size

        topologyVersion++
        renderVersion++

        // A removed wire or chip must not stay selected — the old screen kept dangling
        // ids alive and then rendered ribbons for equipment that no longer existed.
        val selWire = selectedWireId
        if (selWire != null && c.wires.none { it.id == selWire }) selectedWireId = null
        val selChip = selectedChipId
        if (selChip != null && c.placedChips.none { it.placedIc.id == selChip }) {
            selectedChipId = null
        }
    }

    private fun List<JumperWire>.matches(other: List<JumperWire>): Boolean {
        if (size != other.size) return false
        for (i in indices) if (this[i] != other[i]) return false
        return true
    }
}

/**
 * Creates and remembers a [WorkbenchState] over a remembered [BreadboardCircuit],
 * seeded with one full sync so the first frame renders settled hardware rather than
 * an all-zero board.
 */
@Composable
fun rememberWorkbenchState(): WorkbenchState {
    val circuit = remember { BreadboardCircuit() }
    return remember(circuit) {
        WorkbenchState(circuit).also {
            it.syncTopology()
            it.syncFromCircuit()
        }
    }
}
