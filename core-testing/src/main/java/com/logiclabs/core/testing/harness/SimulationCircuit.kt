package com.logiclabs.core.testing.harness

import com.logiclabs.core.testing.model.ChipCatalog
import com.logiclabs.core.testing.model.ChipModel
import com.logiclabs.core.testing.model.ElectricalLevel
import com.logiclabs.core.testing.model.PinRole

/**
 * Placed chip on the breadboard.
 */
data class PlacedChip(
    val instanceId: String,
    val model: ChipModel,
    val trench: Int,
    val startColumn: Int,
    var internalState: Any? = model.initialInternalState()
) {
    fun getPinSocket(pinNumber: Int): Int {
        return BreadboardGeometry.icPinSocket(trench, startColumn, pinNumber, model.pinCount)
    }
}

/**
 * Jumper wire connection between two breadboard sockets.
 */
data class WireConnection(
    val fromSocket: Int,
    val toSocket: Int,
    val color: String = "RED"
)

/**
 * Passive resistor component.
 */
data class Resistor(
    val socketA: Int,
    val socketB: Int,
    val resistanceOhms: Double
)

/**
 * Virtual oscilloscope probe tracking 8,192 circular samples.
 */
class OscilloscopeChannel(val capacity: Int = 8192) {
    val buffer = FloatArray(capacity)
    var writeIndex = 0
    var totalSamples = 0L

    fun sample(voltage: Float) {
        buffer[writeIndex] = voltage
        writeIndex = (writeIndex + 1) % capacity
        totalSamples++
    }

    fun latestSample(): Float {
        val idx = if (writeIndex == 0) capacity - 1 else writeIndex - 1
        return buffer[idx]
    }

    fun clear() {
        buffer.fill(0f)
        writeIndex = 0
        totalSamples = 0L
    }
}

/**
 * Comprehensive circuit simulator for E2E testing.
 */
class SimulationCircuit {

    val dsu = NetlistDsu()

    // Circuit elements
    val placedChips = mutableListOf<PlacedChip>()
    val wires = mutableListOf<WireConnection>()
    val resistors = mutableListOf<Resistor>()

    // Console Peripherals
    var masterPower: Boolean = true
    val switches = BooleanArray(8) { false }
    var pulserAPressed: Boolean = false
    var pulserBPressed: Boolean = false
    var clockFrequencyHz: Double = 1.0
    var clockRunning: Boolean = true
    var clockState: Boolean = false
    val ledValues = BooleanArray(8) { false }
    val ledPovDutyCycles = FloatArray(8) { 0.0f }
    val seg7Digits = IntArray(2) { 0 }

    // Oscilloscope
    val scopeCh1 = OscilloscopeChannel()
    val scopeCh2 = OscilloscopeChannel()
    var probe1Socket: Int? = null
    var probe2Socket: Int? = null

    // Safeguards & Diagnostics
    var isReversePolarityBurned: Boolean = false
    var isOscillationClamped: Boolean = false
    var contentionDetected: Boolean = false
    val contentionNets = mutableSetOf<Int>()
    var totalEventsLastTick: Int = 0

    // Net resolution cache
    private val netLevels = HashMap<Int, ElectricalLevel>()
    private val stablePinLevelsLastStep = HashMap<String, Map<Int, ElectricalLevel>>()

    companion object {
        const val MAX_EVENTS_PER_TICK = 10_000
    }

    init {
        connectDefaultPower()
    }

    /**
     * Connects default trainer DC power:
     * - Rail 0 (+5V) to DC VCC terminal (1934)
     * - Rail 1 (GND) to DC GND terminal (1935)
     */
    fun connectDefaultPower() {
        val r0 = BreadboardGeometry.railSocket(BreadboardGeometry.RAIL_TOP_VCC_5V, 0)
        val r1 = BreadboardGeometry.railSocket(BreadboardGeometry.RAIL_TOP_GND, 0)
        wires.add(WireConnection(BreadboardGeometry.TERM_POWER_VCC, r0, "RED"))
        wires.add(WireConnection(BreadboardGeometry.TERM_POWER_GND, r1, "BLACK"))
    }

    /**
     * Places a chip by part number at a specified trench (1 or 2) and starting column (0..63).
     */
    fun placeChip(partNumber: String, trench: Int, startColumn: Int, instanceId: String = "U_${placedChips.size + 1}"): PlacedChip {
        val model = ChipCatalog.create(partNumber)
        val chip = PlacedChip(instanceId, model, trench, startColumn)
        placedChips.add(chip)
        return chip
    }

    /**
     * Adds a jumper wire between two sockets.
     */
    fun addWire(fromSocket: Int, toSocket: Int, color: String = "BLUE") {
        wires.add(WireConnection(fromSocket, toSocket, color))
    }

    /**
     * Disconnects any wires touching the specified socket.
     */
    fun removeWiresFrom(socket: Int) {
        wires.removeAll { it.fromSocket == socket || it.toSocket == socket }
    }

    /**
     * Connects an IC pin to a breadboard socket or peripheral terminal.
     * If reconnecting to a power rail, removes conflicting power rail wires to prevent dead shorts.
     */
    fun wirePin(chip: PlacedChip, pinNumber: Int, targetSocket: Int, color: String = "YELLOW") {
        val pinSocket = chip.getPinSocket(pinNumber)
        if (targetSocket == BreadboardGeometry.TERM_POWER_VCC || targetSocket == BreadboardGeometry.TERM_POWER_GND) {
            wires.removeAll { (it.fromSocket == pinSocket || it.toSocket == pinSocket) &&
                (it.fromSocket == BreadboardGeometry.TERM_POWER_VCC || it.toSocket == BreadboardGeometry.TERM_POWER_VCC ||
                 it.fromSocket == BreadboardGeometry.TERM_POWER_GND || it.toSocket == BreadboardGeometry.TERM_POWER_GND) }
        }
        addWire(pinSocket, targetSocket, color)
    }

    /**
     * Wires an IC's standard VCC and GND pins to the top power distribution rails.
     */
    fun wireStandardPower(chip: PlacedChip) {
        val railVcc = BreadboardGeometry.railSocket(BreadboardGeometry.RAIL_TOP_VCC_5V, chip.startColumn)
        val railGnd = BreadboardGeometry.railSocket(BreadboardGeometry.RAIL_TOP_GND, chip.startColumn)
        wirePin(chip, chip.model.vccPin, railVcc, "RED")
        wirePin(chip, chip.model.gndPin, railGnd, "BLACK")
    }

    /**
     * Wires an IC with reversed polarity (VCC to GND, GND to VCC) for boundary safety testing.
     */
    fun wireReversePower(chip: PlacedChip) {
        val railVcc = BreadboardGeometry.railSocket(BreadboardGeometry.RAIL_TOP_VCC_5V, chip.startColumn)
        val railGnd = BreadboardGeometry.railSocket(BreadboardGeometry.RAIL_TOP_GND, chip.startColumn)
        wirePin(chip, chip.model.vccPin, railGnd, "BLACK")
        wirePin(chip, chip.model.gndPin, railVcc, "RED")
    }

    /**
     * Rebuilds the DSU netlist from physical baseline and wires.
     */
    fun rebuildNetlist() {
        dsu.resetToBase()
        for (wire in wires) {
            dsu.union(wire.fromSocket, wire.toSocket)
        }
    }

    /**
     * Evaluates the digital simulation until settling or until MAX_EVENTS_PER_TICK is reached.
     */
    fun step(maxIterations: Int = MAX_EVENTS_PER_TICK) {
        rebuildNetlist()
        isOscillationClamped = false
        contentionDetected = false
        contentionNets.clear()

        if (!masterPower || isReversePolarityBurned) {
            netLevels.clear()
            updatePeripherals()
            return
        }

        // Helper to drive external sources (power, switches, clock)
        fun driveExternal(highMap: HashMap<Int, Int>, lowMap: HashMap<Int, Int>) {
            fun drive(socket: Int, level: ElectricalLevel) {
                val root = dsu.find(socket)
                when (level) {
                    ElectricalLevel.HIGH -> highMap[root] = (highMap[root] ?: 0) + 1
                    ElectricalLevel.LOW -> lowMap[root] = (lowMap[root] ?: 0) + 1
                    else -> Unit
                }
            }

            drive(BreadboardGeometry.TERM_POWER_VCC, ElectricalLevel.HIGH)
            drive(BreadboardGeometry.TERM_POWER_GND, ElectricalLevel.LOW)

            for (i in 0..7) {
                val term = BreadboardGeometry.TERM_SW0 + i
                val lvl = if (switches[i]) ElectricalLevel.HIGH else ElectricalLevel.LOW
                drive(term, lvl)
            }

            drive(BreadboardGeometry.TERM_PULSER_A_P, if (pulserAPressed) ElectricalLevel.HIGH else ElectricalLevel.LOW)
            drive(BreadboardGeometry.TERM_PULSER_A_N, if (pulserAPressed) ElectricalLevel.LOW else ElectricalLevel.HIGH)
            drive(BreadboardGeometry.TERM_PULSER_B_P, if (pulserBPressed) ElectricalLevel.HIGH else ElectricalLevel.LOW)
            drive(BreadboardGeometry.TERM_PULSER_B_N, if (pulserBPressed) ElectricalLevel.LOW else ElectricalLevel.HIGH)

            if (clockRunning && clockFrequencyHz > 0.0) {
                drive(BreadboardGeometry.TERM_CLK, if (clockState) ElectricalLevel.HIGH else ElectricalLevel.LOW)
                drive(BreadboardGeometry.TERM_CLK_INV, if (clockState) ElectricalLevel.LOW else ElectricalLevel.HIGH)
            }
        }

        // Resolve external drivers into netLevels before starting chip evaluation
        val initDriversHigh = HashMap<Int, Int>()
        val initDriversLow = HashMap<Int, Int>()
        driveExternal(initDriversHigh, initDriversLow)
        for (root in (initDriversHigh.keys + initDriversLow.keys)) {
            val h = initDriversHigh[root] ?: 0
            val l = initDriversLow[root] ?: 0
            netLevels[root] = when {
                h > 0 && l > 0 -> ElectricalLevel.CONFLICT
                h > 0 -> ElectricalLevel.HIGH
                l > 0 -> ElectricalLevel.LOW
                else -> ElectricalLevel.HIGH_Z
            }
        }

        // If quiescent state is uninitialized, initialize with current quiescent levels
        if (stablePinLevelsLastStep.isEmpty()) {
            for (chip in placedChips) {
                val initPins = HashMap<Int, ElectricalLevel>()
                for ((pinNum, _) in chip.model.pins) {
                    val sock = chip.getPinSocket(pinNum)
                    initPins[pinNum] = netLevels[dsu.find(sock)] ?: ElectricalLevel.HIGH_Z
                }
                stablePinLevelsLastStep[chip.instanceId] = initPins
            }
        }

        // Initialize iteration pin levels from previous step's stable levels
        val chipIterationPrevPins = HashMap<String, Map<Int, ElectricalLevel>>()
        for (chip in placedChips) {
            chipIterationPrevPins[chip.instanceId] = stablePinLevelsLastStep[chip.instanceId] ?: emptyMap()
        }

        var iteration = 0
        var stateChanged = true

        while (stateChanged && iteration < maxIterations) {
            iteration++
            stateChanged = false

            val driversHigh = HashMap<Int, Int>()
            val driversLow = HashMap<Int, Int>()

            fun drive(socket: Int, level: ElectricalLevel) {
                val root = dsu.find(socket)
                when (level) {
                    ElectricalLevel.HIGH -> driversHigh[root] = (driversHigh[root] ?: 0) + 1
                    ElectricalLevel.LOW -> driversLow[root] = (driversLow[root] ?: 0) + 1
                    else -> Unit
                }
            }

            driveExternal(driversHigh, driversLow)

            // Evaluate all chips
            for (chip in placedChips) {
                val currentPins = HashMap<Int, ElectricalLevel>()
                for ((pinNum, _) in chip.model.pins) {
                    val sock = chip.getPinSocket(pinNum)
                    val root = dsu.find(sock)
                    currentPins[pinNum] = netLevels[root] ?: ElectricalLevel.HIGH_Z
                }

                val prevPins = chipIterationPrevPins[chip.instanceId] ?: emptyMap()
                val eval = chip.model.evaluate(currentPins, prevPins, chip.internalState)
                chip.internalState = eval.nextInternalState
                chipIterationPrevPins[chip.instanceId] = HashMap(currentPins)

                if (eval.isReversePolarity) {
                    isReversePolarityBurned = true
                    netLevels.clear()
                    updatePeripherals()
                    return
                }

                if (eval.isPowered) {
                    for ((outPin, outLvl) in eval.outputLevels) {
                        val outSock = chip.getPinSocket(outPin)
                        drive(outSock, outLvl)
                    }
                }
            }

            // Resolve electrical net states
            val allActiveRoots = (driversHigh.keys + driversLow.keys).toSet()
            for (root in allActiveRoots) {
                val numHigh = driversHigh[root] ?: 0
                val numLow = driversLow[root] ?: 0

                val resolved = when {
                    numHigh > 0 && numLow > 0 -> {
                        contentionDetected = true
                        contentionNets.add(root)
                        ElectricalLevel.CONFLICT
                    }
                    numHigh > 0 -> ElectricalLevel.HIGH
                    numLow > 0 -> ElectricalLevel.LOW
                    else -> ElectricalLevel.HIGH_Z
                }

                val oldLvl = netLevels[root] ?: ElectricalLevel.HIGH_Z
                if (oldLvl != resolved) {
                    netLevels[root] = resolved
                    stateChanged = true
                }
            }
        }

        totalEventsLastTick = iteration
        if (iteration >= maxIterations && stateChanged) {
            isOscillationClamped = true
        }

        // Store stable pin levels for next step
        stablePinLevelsLastStep.clear()
        stablePinLevelsLastStep.putAll(chipIterationPrevPins)

        updatePeripherals()
    }

    /**
     * Pulses the clock once (rising edge then falling edge) and steps the simulation.
     */
    fun tickClock() {
        clockState = !clockState
        step()
    }

    /**
     * Samples oscilloscope probes and updates LED displays and 7-segment modules.
     */
    private fun updatePeripherals() {
        // Update 8x Buffered LEDs
        for (i in 0..7) {
            val sock = BreadboardGeometry.TERM_LED0 + i
            val lvl = getSocketLevel(sock)
            val isLit = (lvl == ElectricalLevel.HIGH)
            ledValues[i] = isLit
            val target = if (isLit) 1.0f else 0.0f
            ledPovDutyCycles[i] = (ledPovDutyCycles[i] * 0.7f) + (target * 0.3f)
        }

        // Update Oscilloscope Probes
        probe1Socket?.let { sock ->
            val lvl = getSocketLevel(sock)
            scopeCh1.sample(lvl.voltageApprox.toFloat())
        }
        probe2Socket?.let { sock ->
            val lvl = getSocketLevel(sock)
            scopeCh2.sample(lvl.voltageApprox.toFloat())
        }
    }

    /**
     * Reads the current resolved electrical level at a breadboard socket.
     */
    fun getSocketLevel(socketIndex: Int): ElectricalLevel {
        if (!masterPower || isReversePolarityBurned) return ElectricalLevel.UNPOWERED
        val root = dsu.find(socketIndex)
        return netLevels[root] ?: ElectricalLevel.HIGH_Z
    }

    /**
     * Reads the resolved electrical level at an IC pin.
     */
    fun getPinLevel(chip: PlacedChip, pinNumber: Int): ElectricalLevel {
        val sock = chip.getPinSocket(pinNumber)
        return getSocketLevel(sock)
    }

    /**
     * Resets the entire breadboard and simulation state.
     */
    fun reset() {
        placedChips.clear()
        wires.clear()
        resistors.clear()
        dsu.resetToBase()
        connectDefaultPower()
        switches.fill(false)
        pulserAPressed = false
        pulserBPressed = false
        clockState = false
        isReversePolarityBurned = false
        isOscillationClamped = false
        contentionDetected = false
        contentionNets.clear()
        netLevels.clear()
        stablePinLevelsLastStep.clear()
        scopeCh1.clear()
        scopeCh2.clear()
    }
}
