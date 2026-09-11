package com.logiclabs.core.data.persistence

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.WireColor
import kotlinx.serialization.Serializable

/**
 * One placed DIP package, in the exact form the engine can rebuild it from.
 *
 * Every field mirrors a `BreadboardCircuit.addChip` parameter so deserialization is a
 * straight replay with the original id — ids are the netlist's keys, so preserving them
 * is what makes a reloaded project observably identical to the one that was saved.
 */
@Serializable
data class SerializedChip(
    val id: String,
    val partNumber: String,
    val trench: Int,
    val startColumn: Int,
    val isRotated180: Boolean
)

/** One jumper wire, again in `addWire`'s parameter shape. */
@Serializable
data class SerializedWire(
    val id: String,
    val startSocket: Int,
    val endSocket: Int,
    val colorName: String,
    val isManhattan: Boolean
)

/**
 * A whole breadboard, serialized.
 *
 * Format **v2** adds the console state v1 omitted: master power, the clock generator's
 * frequency and run state, and the curriculum experiment the board was built from (if
 * any). All v2 fields carry defaults so a v1 file — the format that shipped before
 * projects had a home — still parses, with the console falling back to powered-on at
 * 1 Hz, which is the bench's factory state.
 */
@Serializable
data class SerializedProject(
    /** Blank on a project that has never been saved; minted by the repository. */
    val id: String = "",
    val version: Int = 2,
    val title: String = "Untitled Circuit",
    val timestampMillis: Long = System.currentTimeMillis(),
    val chips: List<SerializedChip>,
    val wires: List<SerializedWire>,
    val switches: List<Boolean>,
    val masterPower: Boolean = true,
    val clockFrequencyHz: Double = 1.0,
    val clockRunning: Boolean = true,
    val activeLabId: String? = null
)

/**
 * Pure circuit ⇄ data translation with no I/O and no Android types, so it is unit
 * testable on the JVM and reusable by every storage backend (files, drafts, shares).
 */
object ProjectPersistence {

    fun serialize(
        circuit: BreadboardCircuit,
        title: String = "Untitled Circuit",
        activeLabId: String? = null
    ): SerializedProject = SerializedProject(
        title = title,
        chips = circuit.placedChips.map {
            SerializedChip(
                id = it.placedIc.id,
                partNumber = it.placedIc.partNumber,
                trench = it.placedIc.trench,
                startColumn = it.placedIc.startColumn,
                isRotated180 = it.placedIc.isRotated180
            )
        },
        wires = circuit.wires.map {
            SerializedWire(
                id = it.id,
                startSocket = it.startSocket,
                endSocket = it.endSocket,
                colorName = it.color.name,
                isManhattan = it.isManhattan
            )
        },
        switches = circuit.switches.toList(),
        masterPower = circuit.masterPower,
        clockFrequencyHz = circuit.clockFrequencyHz,
        clockRunning = circuit.clockRunning,
        activeLabId = activeLabId
    )

    fun deserialize(project: SerializedProject, circuit: BreadboardCircuit) {
        circuit.clearAll()
        for (c in project.chips) {
            circuit.addChip(c.partNumber, c.trench, c.startColumn, c.isRotated180, c.id)
        }
        for (w in project.wires) {
            val color = try {
                WireColor.valueOf(w.colorName)
            } catch (_: Exception) {
                // A colour name from a future palette (or a hand-edited file) must not
                // take the whole project down; red is the classic default jumper.
                WireColor.RED
            }
            circuit.addWire(w.startSocket, w.endSocket, color, w.isManhattan, w.id)
        }
        for (i in project.switches.indices) {
            if (i in circuit.switches.indices) {
                circuit.switches[i] = project.switches[i]
            }
        }
        circuit.masterPower = project.masterPower
        circuit.clockFrequencyHz = project.clockFrequencyHz
        circuit.clockRunning = project.clockRunning
        circuit.step()
    }
}
