package com.logiclabs.core.bridge.circuit

import com.logiclabs.core.bridge.catalog.ChipModel
import com.logiclabs.core.bridge.catalog.TTLChipCatalog
import com.logiclabs.core.bridge.model.ElectricalLevel
import com.logiclabs.core.bridge.model.JumperWire
import com.logiclabs.core.bridge.model.PassiveComponent
import com.logiclabs.core.bridge.model.PlacedIC
import com.logiclabs.core.bridge.model.PinRole
import com.logiclabs.core.bridge.model.SimulationMode
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.bridge.netlist.NetlistDsu
import com.logiclabs.core.bridge.topology.AD200Topology
import java.util.UUID

class OscilloscopeBuffer(val capacity: Int = 8192) {
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

class BreadboardCircuit {

    val dsu = NetlistDsu()

    val placedChips = mutableListOf<PlacedChipRuntime>()
    val wires = mutableListOf<JumperWire>()
    val passives = mutableListOf<PassiveComponent>()

    // Console Peripherals State
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
    val scopeCh1 = OscilloscopeBuffer()
    val scopeCh2 = OscilloscopeBuffer()
    var probe1Socket: Int? = null
    var probe2Socket: Int? = null

    // Safeguards & Diagnostics
    var isReversePolarityBurned: Boolean = false
    var isOscillationClamped: Boolean = false
    var contentionDetected: Boolean = false
    var burnedChipId: String? = null

    // Simulation Physics Mode (IDEAL vs PRACTICAL)
    var simulationMode: SimulationMode = SimulationMode.IDEAL
    val burnedChipIds: MutableSet<String> = mutableSetOf()
    val chipExplosionTimestamps: MutableMap<String, Long> = mutableMapOf()

    /** Restores all burned ICs back to operational condition, resetting faults and re-running simulation. */
    fun restoreBurnedChips() {
        burnedChipIds.clear()
        chipExplosionTimestamps.clear()
        isReversePolarityBurned = false
        burnedChipId = null
        contentionDetected = false
        for (chip in placedChips) {
            chip.internalState = chip.model.initialInternalState()
        }
        rebuildNetlist()
        step()
    }

    // Net resolved electrical levels
    val netLevels = mutableMapOf<Int, ElectricalLevel>()

    private val stablePinLevelsLastStep = mutableMapOf<String, Map<Int, ElectricalLevel>>()

    data class PlacedChipRuntime(
        val placedIc: PlacedIC,
        val model: ChipModel,
        var internalState: Any? = model.initialInternalState()
    ) {
        fun getPinSocket(pinNumber: Int): Int {
            return AD200Topology.icPinSocket(placedIc.trench, placedIc.startColumn, pinNumber, model.pinCount, placedIc.isRotated180)
        }
    }

    fun addChip(partNumber: String, trench: Int, startColumn: Int, isRotated180: Boolean = false, id: String = UUID.randomUUID().toString()): PlacedChipRuntime {
        val model = TTLChipCatalog.create(partNumber)
            ?: throw IllegalArgumentException("Unknown IC part number: $partNumber")
        val placed = PlacedChipRuntime(
            PlacedIC(id, partNumber, trench, startColumn, isRotated180),
            model
        )
        placedChips.add(placed)
        rebuildNetlist()
        return placed
    }

    fun removeChip(id: String) {
        val chip = placedChips.find { it.placedIc.id == id }
        if (chip != null) {
            val chipSockets = (1..chip.model.pinCount).map { chip.getPinSocket(it) }.toSet()
            wires.removeAll { it.startSocket in chipSockets || it.endSocket in chipSockets }
        }
        placedChips.removeAll { it.placedIc.id == id }
        stablePinLevelsLastStep.remove(id)
        burnedChipIds.remove(id)
        chipExplosionTimestamps.remove(id)
        if (burnedChipId == id) {
            isReversePolarityBurned = false
            burnedChipId = null
        }
        rebuildNetlist()
        step()
    }

    private fun getChipColumnSockets(chip: PlacedChipRuntime): List<List<Int>> {
        return (1..chip.model.pinCount).map { pin ->
            val pinSock = chip.getPinSocket(pin)
            val block = pinSock / AD200Topology.SOCKETS_PER_BLOCK
            val rem = pinSock % AD200Topology.SOCKETS_PER_BLOCK
            val col = rem / AD200Topology.ROWS_PER_BLOCK
            (0 until AD200Topology.ROWS_PER_BLOCK).map { r ->
                AD200Topology.terminalSocket(block, col, r)
            }
        }
    }

    fun rotateChip(id: String): Boolean {
        val idx = placedChips.indexOfFirst { it.placedIc.id == id }
        if (idx != -1) {
            val current = placedChips[idx]
            val oldPinCols = getChipColumnSockets(current)
            val newPlacedIc = current.placedIc.copy(isRotated180 = !current.placedIc.isRotated180)
            val updated = current.copy(placedIc = newPlacedIc)
            placedChips[idx] = updated
            val newPinCols = getChipColumnSockets(updated)

            val socketMapping = mutableMapOf<Int, Int>()
            for (p in oldPinCols.indices) {
                val oldSocks = oldPinCols[p]
                val newSocks = newPinCols[p]
                for (r in oldSocks.indices) {
                    socketMapping[oldSocks[r]] = newSocks[r]
                }
            }

            for (wIdx in wires.indices) {
                val w = wires[wIdx]
                val newStart = socketMapping[w.startSocket] ?: w.startSocket
                val newEnd = socketMapping[w.endSocket] ?: w.endSocket
                if (newStart != w.startSocket || newEnd != w.endSocket) {
                    wires[wIdx] = w.copy(startSocket = newStart, endSocket = newEnd)
                }
            }
            rebuildNetlist()
            step()
            return true
        }
        return false
    }

    fun moveChip(id: String, newTrench: Int, newStartColumn: Int): Boolean {
        val idx = placedChips.indexOfFirst { it.placedIc.id == id }
        if (idx == -1) return false

        val current = placedChips[idx]
        val half = current.model.pinCount / 2
        val maxCol = AD200Topology.COLUMNS_PER_BLOCK - half

        // Validate boundary constraints
        if (newStartColumn !in 0..maxCol || newTrench !in 1..2) {
            return false
        }

        // Check collision against any other chip in the target trench
        val newRange = newStartColumn until (newStartColumn + half)
        for (other in placedChips) {
            if (other.placedIc.id == id) continue
            if (other.placedIc.trench == newTrench) {
                val otherHalf = other.model.pinCount / 2
                val otherRange = other.placedIc.startColumn until (other.placedIc.startColumn + otherHalf)
                if (maxOf(newRange.first, otherRange.first) < minOf(newRange.last + 1, otherRange.last + 1)) {
                    return false
                }
            }
        }

        val oldPinCols = getChipColumnSockets(current)
        val newPlacedIc = current.placedIc.copy(trench = newTrench, startColumn = newStartColumn)
        val updated = current.copy(placedIc = newPlacedIc)
        placedChips[idx] = updated
        val newPinCols = getChipColumnSockets(updated)

        // Exclude sockets belonging to all other chips
        val otherChipsSockets = placedChips
            .filter { it.placedIc.id != id }
            .flatMap { getChipColumnSockets(it).flatten() }
            .toSet()

        val thisChipOldSockets = oldPinCols.flatten().toSet()
        val hasConnectedWires = wires.any {
            (it.startSocket in thisChipOldSockets && it.startSocket !in otherChipsSockets) ||
            (it.endSocket in thisChipOldSockets && it.endSocket !in otherChipsSockets)
        }

        // Empty chip protection: if no connected wires, skip wire migration completely
        if (hasConnectedWires) {
            val socketMapping = mutableMapOf<Int, Int>()
            for (p in oldPinCols.indices) {
                val oldSocks = oldPinCols[p]
                val newSocks = newPinCols[p]
                for (r in oldSocks.indices) {
                    val oldSock = oldSocks[r]
                    if (oldSock !in otherChipsSockets) {
                        socketMapping[oldSock] = newSocks[r]
                    }
                }
            }

            for (wIdx in wires.indices) {
                val w = wires[wIdx]
                val newStart = socketMapping[w.startSocket] ?: w.startSocket
                val newEnd = socketMapping[w.endSocket] ?: w.endSocket
                if (newStart != w.startSocket || newEnd != w.endSocket) {
                    wires[wIdx] = w.copy(startSocket = newStart, endSocket = newEnd)
                }
            }
        }

        rebuildNetlist()
        step()
        return true
    }

    fun updateWireEndpoints(id: String, newStart: Int, newEnd: Int): Boolean {
        val idx = wires.indexOfFirst { it.id == id }
        if (idx != -1) {
            wires[idx] = wires[idx].copy(startSocket = newStart, endSocket = newEnd)
            rebuildNetlist()
            step()
            return true
        }
        return false
    }

    fun flipWire(id: String): Boolean {
        val idx = wires.indexOfFirst { it.id == id }
        if (idx != -1) {
            val w = wires[idx]
            wires[idx] = w.copy(startSocket = w.endSocket, endSocket = w.startSocket)
            rebuildNetlist()
            step()
            return true
        }
        return false
    }

    fun toggleWireRouting(id: String): Boolean {
        val idx = wires.indexOfFirst { it.id == id }
        if (idx != -1) {
            val w = wires[idx]
            wires[idx] = w.copy(isManhattan = !w.isManhattan)
            rebuildNetlist()
            step()
            return true
        }
        return false
    }

    fun rotateWire(id: String): Boolean {
        val idx = wires.indexOfFirst { it.id == id }
        if (idx != -1) {
            val w = wires[idx]
            val s = w.startSocket
            val e = w.endSocket
            if (s < AD200Topology.TOTAL_TERMINAL_SOCKETS && e < AD200Topology.TOTAL_TERMINAL_SOCKETS) {
                val sBlock = s / AD200Topology.SOCKETS_PER_BLOCK
                val sRem = s % AD200Topology.SOCKETS_PER_BLOCK
                val sCol = sRem / AD200Topology.ROWS_PER_BLOCK
                val sRow = sRem % AD200Topology.ROWS_PER_BLOCK

                val eBlock = e / AD200Topology.SOCKETS_PER_BLOCK
                val eRem = e % AD200Topology.SOCKETS_PER_BLOCK
                val eCol = eRem / AD200Topology.ROWS_PER_BLOCK
                val eRow = eRem % AD200Topology.ROWS_PER_BLOCK

                if (sBlock == eBlock) {
                    val dCol = eCol - sCol
                    val dRow = eRow - sRow
                    // 90-degree rotation: (dCol, dRow) -> (-dRow, dCol)
                    val newCol = (sCol - dRow).coerceIn(0, AD200Topology.COLUMNS_PER_BLOCK - 1)
                    val newRow = (sRow + dCol).coerceIn(0, AD200Topology.ROWS_PER_BLOCK - 1)
                    val newEnd = AD200Topology.terminalSocket(sBlock, newCol, newRow)
                    wires[idx] = w.copy(endSocket = newEnd)
                } else {
                    // Across blocks: flip endpoints
                    wires[idx] = w.copy(startSocket = e, endSocket = s)
                }
            } else {
                wires[idx] = w.copy(startSocket = e, endSocket = s)
            }
            rebuildNetlist()
            step()
            return true
        }
        return false
    }

    fun moveWire(id: String, deltaCol: Int, deltaRow: Int): Boolean {
        val idx = wires.indexOfFirst { it.id == id }
        if (idx != -1) {
            val w = wires[idx]
            fun shiftSocket(sock: Int): Int {
                if (sock < AD200Topology.TOTAL_TERMINAL_SOCKETS) {
                    val block = sock / AD200Topology.SOCKETS_PER_BLOCK
                    val rem = sock % AD200Topology.SOCKETS_PER_BLOCK
                    val col = (rem / AD200Topology.ROWS_PER_BLOCK + deltaCol).coerceIn(0, AD200Topology.COLUMNS_PER_BLOCK - 1)
                    val row = (rem % AD200Topology.ROWS_PER_BLOCK + deltaRow).coerceIn(0, AD200Topology.ROWS_PER_BLOCK - 1)
                    return AD200Topology.terminalSocket(block, col, row)
                }
                return sock
            }
            val newStart = shiftSocket(w.startSocket)
            val newEnd = shiftSocket(w.endSocket)
            wires[idx] = w.copy(startSocket = newStart, endSocket = newEnd)
            rebuildNetlist()
            step()
            return true
        }
        return false
    }

    fun addWire(startSocket: Int, endSocket: Int, color: WireColor = WireColor.RED, isManhattan: Boolean = false, id: String = UUID.randomUUID().toString()): JumperWire {
        val sameSocketCount = wires.count {
            (it.startSocket == startSocket && it.endSocket == endSocket) ||
            (it.startSocket == endSocket && it.endSocket == startSocket) ||
            it.startSocket == startSocket || it.endSocket == endSocket
        }
        val elevation = (sameSocketCount % 4)
        val wire = JumperWire(id, startSocket, endSocket, color, elevation, isManhattan)
        wires.add(wire)
        rebuildNetlist()
        return wire
    }

    fun removeWire(id: String) {
        wires.removeAll { it.id == id }
        rebuildNetlist()
    }

    fun clearWires() {
        wires.clear()
        rebuildNetlist()
    }

    fun addResistor(socketA: Int, socketB: Int, ohms: Long = 1000L, id: String = UUID.randomUUID().toString()): PassiveComponent.Resistor {
        val res = PassiveComponent.Resistor(id, socketA, socketB, ohms)
        passives.add(res)
        rebuildNetlist()
        return res
    }

    fun clearAll() {
        placedChips.clear()
        wires.clear()
        passives.clear()
        stablePinLevelsLastStep.clear()
        burnedChipIds.clear()
        chipExplosionTimestamps.clear()
        isReversePolarityBurned = false
        isOscillationClamped = false
        contentionDetected = false
        burnedChipId = null
        rebuildNetlist()
    }

    fun rebuildNetlist() {
        dsu.resetToBase()

        // 1. Merge jumper wires
        for (w in wires) {
            dsu.union(w.startSocket, w.endSocket)
        }

        // 2. Merge low-value passive connections (< 100 ohms acts as jumper)
        for (p in passives) {
            if (p is PassiveComponent.Resistor && p.resistanceOhms < 100L) {
                dsu.union(p.socketA, p.socketB)
            }
        }
    }

    fun step() {
        rebuildNetlist()
        isOscillationClamped = false
        contentionDetected = false

        if (!masterPower || isReversePolarityBurned) {
            netLevels.clear()
            ledValues.fill(false)
            ledPovDutyCycles.fill(0.0f)
            seg7Digits.fill(0)
            scopeCh1.sample(0.0f)
            scopeCh2.sample(0.0f)
            return
        }

        contentionDetected = false
        val maxStabilizationPasses = 30
        var pass = 0
        var stateChanged = true

        // Initialize iteration pin levels from previous step's stable levels
        val chipIterationPrevPins = mutableMapOf<String, Map<Int, ElectricalLevel>>()
        for (chip in placedChips) {
            chipIterationPrevPins[chip.placedIc.id] = stablePinLevelsLastStep[chip.placedIc.id] ?: emptyMap()
        }

        fun applyExternalDrivers(drivers: MutableMap<Int, ElectricalLevel>) {
            // VCC & GND terminals
            drivers[AD200Topology.TERM_POWER_VCC] = ElectricalLevel.HIGH
            drivers[AD200Topology.TERM_POWER_GND] = ElectricalLevel.LOW

            // Switches SW0..SW7
            for (i in 0..7) {
                val swSocket = AD200Topology.TERM_SW0 + i
                drivers[swSocket] = if (switches[i]) ElectricalLevel.HIGH else ElectricalLevel.LOW
            }

            // Pulsers A and B (cross-coupled active-LOW pushbuttons: normally P=0, ~P=1; when pressed P=1, ~P=0)
            drivers[AD200Topology.TERM_PULSER_A_P] = if (pulserAPressed) ElectricalLevel.HIGH else ElectricalLevel.LOW
            drivers[AD200Topology.TERM_PULSER_A_N] = if (pulserAPressed) ElectricalLevel.LOW else ElectricalLevel.HIGH
            drivers[AD200Topology.TERM_PULSER_B_P] = if (pulserBPressed) ElectricalLevel.HIGH else ElectricalLevel.LOW
            drivers[AD200Topology.TERM_PULSER_B_N] = if (pulserBPressed) ElectricalLevel.LOW else ElectricalLevel.HIGH

            // Stepped Clock Generator
            if (clockRunning && clockFrequencyHz > 0.0) {
                drivers[AD200Topology.TERM_CLK] = if (clockState) ElectricalLevel.HIGH else ElectricalLevel.LOW
                drivers[AD200Topology.TERM_CLK_INV] = if (!clockState) ElectricalLevel.HIGH else ElectricalLevel.LOW
            }
        }

        // 1. Update external drivers in netLevels without wiping retained chip output states
        val initDrivers = mutableMapOf<Int, ElectricalLevel>()
        applyExternalDrivers(initDrivers)
        for ((sock, lvl) in initDrivers) {
            val root = dsu.find(sock)
            netLevels[root] = lvl
        }

        // 2. Multi-pass relaxation loop
        while (stateChanged && pass < maxStabilizationPasses) {
            pass++
            stateChanged = false

            val currentDrivers = mutableMapOf<Int, ElectricalLevel>()
            applyExternalDrivers(currentDrivers)

            // Evaluate all placed ICs based on current netLevels
            for (chip in placedChips) {
                // If chip is already burned, skip evaluating outputs (dead chip)
                if (chip.placedIc.id in burnedChipIds) {
                    continue
                }

                val pinLevels = mutableMapOf<Int, ElectricalLevel>()
                for (pinNum in 1..chip.model.pinCount) {
                    val sock = chip.getPinSocket(pinNum)
                    val net = dsu.find(sock)
                    pinLevels[pinNum] = netLevels[net] ?: ElectricalLevel.HIGH_Z
                }

                val prevPins = chipIterationPrevPins[chip.placedIc.id] ?: emptyMap()
                val result = chip.model.evaluate(pinLevels, prevPins, chip.internalState)
                chip.internalState = result.nextInternalState
                chipIterationPrevPins[chip.placedIc.id] = HashMap(pinLevels)

                if (result.isReversePolarity) {
                    if (simulationMode == SimulationMode.PRACTICAL) {
                        if (chip.placedIc.id !in burnedChipIds) {
                            burnedChipIds.add(chip.placedIc.id)
                            chipExplosionTimestamps[chip.placedIc.id] = System.currentTimeMillis()
                        }
                    }
                    isReversePolarityBurned = true
                    burnedChipId = chip.placedIc.id
                    netLevels.clear()
                    ledValues.fill(false)
                    ledPovDutyCycles.fill(0.0f)
                    seg7Digits.fill(0)
                    return
                }

                if (result.isPowered) {
                    for ((pinNum, lvl) in result.outputLevels) {
                        val sock = chip.getPinSocket(pinNum)
                        currentDrivers[sock] = lvl
                    }
                }
            }

            // Snapshot old net levels and re-resolve with new drivers
            val oldNetLevels = HashMap(netLevels)
            resolveNets(currentDrivers)

            // In PRACTICAL mode: check if any output pin of an IC encountered CONTENTION (short to opposing rail/driver)
            if (simulationMode == SimulationMode.PRACTICAL) {
                for (chip in placedChips) {
                    if (chip.placedIc.id in burnedChipIds) continue
                    for (pinNum in 1..chip.model.pinCount) {
                        if (chip.model.pins[pinNum]?.role == PinRole.OUTPUT) {
                            val sock = chip.getPinSocket(pinNum)
                            val net = dsu.find(sock)
                            if (netLevels[net] == ElectricalLevel.CONTENTION) {
                                burnedChipIds.add(chip.placedIc.id)
                                chipExplosionTimestamps[chip.placedIc.id] = System.currentTimeMillis()
                                burnedChipId = chip.placedIc.id
                                stateChanged = true
                                break
                            }
                        }
                    }
                }
            }

            // Determine if any net level changed
            val allNets = (oldNetLevels.keys + netLevels.keys)
            for (net in allNets) {
                val oldLvl = oldNetLevels[net] ?: ElectricalLevel.HIGH_Z
                val newLvl = netLevels[net] ?: ElectricalLevel.HIGH_Z
                if (oldLvl != newLvl) {
                    stateChanged = true
                    break
                }
            }
        }

        if (pass >= maxStabilizationPasses) {
            isOscillationClamped = true
        }

        // Cache stable pin levels for subsequent step's edge detection
        for (chip in placedChips) {
            val stablePins = mutableMapOf<Int, ElectricalLevel>()
            for (pinNum in 1..chip.model.pinCount) {
                val sock = chip.getPinSocket(pinNum)
                val net = dsu.find(sock)
                stablePins[pinNum] = netLevels[net] ?: ElectricalLevel.HIGH_Z
            }
            stablePinLevelsLastStep[chip.placedIc.id] = stablePins
        }

        // 3. Update LEDs
        for (i in 0..7) {
            val ledSocket = AD200Topology.TERM_LED0 + i
            val net = dsu.find(ledSocket)
            val lvl = netLevels[net] ?: ElectricalLevel.LOW
            val active = (lvl == ElectricalLevel.HIGH)
            ledValues[i] = active
            ledPovDutyCycles[i] = if (active) 1.0f else 0.0f
        }

        // 4. Update 7-segment displays
        seg7Digits[0] = decodeBCD(
            AD200Topology.TERM_SEG_A_BCD_A,
            AD200Topology.TERM_SEG_A_BCD_B,
            AD200Topology.TERM_SEG_A_BCD_C,
            AD200Topology.TERM_SEG_A_BCD_D
        )
        seg7Digits[1] = decodeBCD(
            AD200Topology.TERM_SEG_B_BCD_A,
            AD200Topology.TERM_SEG_B_BCD_B,
            AD200Topology.TERM_SEG_B_BCD_C,
            AD200Topology.TERM_SEG_B_BCD_D
        )

        // 5. Sample Oscilloscope Probes
        probe1Socket?.let { sock ->
            val lvl = getSocketLevel(sock)
            scopeCh1.sample(if (lvl == ElectricalLevel.HIGH) 5.0f else 0.0f)
        }
        probe2Socket?.let { sock ->
            val lvl = getSocketLevel(sock)
            scopeCh2.sample(if (lvl == ElectricalLevel.HIGH) 5.0f else 0.0f)
        }
    }

    fun getSocketLevel(socket: Int): ElectricalLevel {
        val root = dsu.find(socket)
        return netLevels[root] ?: ElectricalLevel.HIGH_Z
    }

    private fun resolveNets(driverLevels: Map<Int, ElectricalLevel>) {
        netLevels.clear()
        val netHighs = mutableMapOf<Int, Int>()
        val netLows = mutableMapOf<Int, Int>()

        for ((sock, lvl) in driverLevels) {
            val root = dsu.find(sock)
            when (lvl) {
                ElectricalLevel.HIGH -> netHighs[root] = (netHighs[root] ?: 0) + 1
                ElectricalLevel.LOW -> netLows[root] = (netLows[root] ?: 0) + 1
                else -> {}
            }
        }

        val allRoots = (netHighs.keys + netLows.keys)
        for (root in allRoots) {
            val h = netHighs[root] ?: 0
            val l = netLows[root] ?: 0
            if (h > 0 && l > 0) {
                contentionDetected = true
                netLevels[root] = ElectricalLevel.CONTENTION
            } else if (h > 0) {
                netLevels[root] = ElectricalLevel.HIGH
            } else if (l > 0) {
                netLevels[root] = ElectricalLevel.LOW
            } else {
                netLevels[root] = ElectricalLevel.HIGH_Z
            }
        }
    }

    private fun decodeBCD(pinA: Int, pinB: Int, pinC: Int, pinD: Int): Int {
        val a = if (getSocketLevel(pinA) == ElectricalLevel.HIGH) 1 else 0
        val b = if (getSocketLevel(pinB) == ElectricalLevel.HIGH) 2 else 0
        val c = if (getSocketLevel(pinC) == ElectricalLevel.HIGH) 4 else 0
        val d = if (getSocketLevel(pinD) == ElectricalLevel.HIGH) 8 else 0
        return a or b or c or d
    }
}
