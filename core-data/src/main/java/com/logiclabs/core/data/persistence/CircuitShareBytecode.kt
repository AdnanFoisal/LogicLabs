package com.logiclabs.core.data.persistence

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.bridge.topology.AD200Topology
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.UUID
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Result of validating a Logic Labs circuit share code.
 */
data class BytecodeValidationResult(
    val isValid: Boolean,
    val chipCount: Int = 0,
    val wireCount: Int = 0,
    val errorMessage: String? = null
)

/**
 * R3 Compact Circuit Sharing Bytecode Protocol (LLCB).
 *
 * Encodes placed ICs, jumper wires, switch settings, and console states into a high-density
 * binary payload, compresses it with Deflate (ZLIB), and encodes it as URL-safe Base64
 * prepended with the "LOGIC-" prefix.
 *
 * Binary layout (v1):
 * - Magic header: 4 bytes ('L', 'L', 'C', 'B' = 0x4C, 0x4C, 0x43, 0x42)
 * - Version: 1 byte (0x01)
 * - Switches bitmask: 1 byte (bits 0..7 for SW0..SW7)
 * - Console flags: 1 byte (bit 0 = masterPower, bit 1 = clockRunning)
 * - Clock frequency: 4 bytes (IEEE-754 32-bit float in Hz)
 * - Chip count N: 1 byte (0..255)
 * - Packed chips (4 * N bytes):
 *     - Byte 0: lookupId (0..13 in [CHIP_CATALOG_LOOKUP])
 *     - Byte 1: trench (1 or 2)
 *     - Byte 2: startColumn (0..63)
 *     - Byte 3: flags (bit 0 = isRotated180)
 * - Wire count M: 2 bytes (unsigned 16-bit short)
 * - Packed wires (6 * M bytes):
 *     - Bytes 0..1: startSocket (16-bit short, 0..2047)
 *     - Bytes 2..3: endSocket (16-bit short, 0..2047)
 *     - Byte 4: colorByte (ordinal in [WireColor], 0..9)
 *     - Byte 5: routingFlags (bit 0 = isManhattan, bits 1..2 = elevationLevel 0..3)
 */
object CircuitShareBytecode {

    const val PREFIX: String = "LOGIC-"
    const val VERSION: Byte = 0x01
    val MAGIC_HEADER: ByteArray = byteArrayOf(0x4C, 0x4C, 0x43, 0x42) // "LLCB"

    val CHIP_CATALOG_LOOKUP: List<String> = listOf(
        "7400", "7402", "7404", "7408", "7410", "7411", "7420",
        "7432", "7486", "74266", "7483", "7474", "7476", "7448"
    )

    private data class InternalWire(
        val startSocket: Int,
        val endSocket: Int,
        val color: WireColor,
        val elevationLevel: Int,
        val isManhattan: Boolean
    )

    /**
     * Normalizes part number strings (e.g. "SN7400N" -> "7400").
     */
    fun normalizePartNumber(partNumber: String): String {
        val trimmed = partNumber.trim()
        if (trimmed in CHIP_CATALOG_LOOKUP) return trimmed
        val stripped = trimmed.removePrefix("SN").removeSuffix("N").trim()
        if (stripped in CHIP_CATALOG_LOOKUP) return stripped
        return trimmed
    }

    /**
     * Encodes a live [BreadboardCircuit] into a compact "LOGIC-..." share code.
     */
    fun encode(circuit: BreadboardCircuit): String {
        val chips = circuit.placedChips.map {
            SerializedChip(
                id = it.placedIc.id,
                partNumber = it.placedIc.partNumber,
                trench = it.placedIc.trench,
                startColumn = it.placedIc.startColumn,
                isRotated180 = it.placedIc.isRotated180
            )
        }
        val wires = circuit.wires.map {
            InternalWire(
                startSocket = it.startSocket,
                endSocket = it.endSocket,
                color = it.color,
                elevationLevel = it.elevationLevel,
                isManhattan = it.isManhattan
            )
        }
        return encodeBinary(
            chips = chips,
            wires = wires,
            switches = circuit.switches.toList(),
            masterPower = circuit.masterPower,
            clockFrequencyHz = circuit.clockFrequencyHz,
            clockRunning = circuit.clockRunning
        )
    }

    /**
     * Encodes a [SerializedProject] into a compact "LOGIC-..." share code.
     */
    fun encode(project: SerializedProject): String {
        val wires = project.wires.map { w ->
            val color = try {
                WireColor.valueOf(w.colorName)
            } catch (_: Exception) {
                WireColor.RED
            }
            InternalWire(
                startSocket = w.startSocket,
                endSocket = w.endSocket,
                color = color,
                elevationLevel = 0,
                isManhattan = w.isManhattan
            )
        }
        return encodeBinary(
            chips = project.chips,
            wires = wires,
            switches = project.switches,
            masterPower = project.masterPower,
            clockFrequencyHz = project.clockFrequencyHz,
            clockRunning = project.clockRunning
        )
    }

    private fun encodeBinary(
        chips: List<SerializedChip>,
        wires: List<InternalWire>,
        switches: List<Boolean>,
        masterPower: Boolean,
        clockFrequencyHz: Double,
        clockRunning: Boolean
    ): String {
        if (chips.size > 255) {
            throw IllegalArgumentException("Chip count exceeds maximum supported by bytecode protocol (255)")
        }
        if (wires.size > 65535) {
            throw IllegalArgumentException("Wire count exceeds maximum supported by bytecode protocol (65535)")
        }

        val baos = ByteArrayOutputStream()
        val out = DataOutputStream(baos)

        // 1. Magic header (4 bytes)
        out.write(MAGIC_HEADER)

        // 2. Version (1 byte)
        out.writeByte(VERSION.toInt())

        // 3. Switches bitmask (1 byte)
        var switchMask = 0
        for (i in 0 until 8) {
            if (i < switches.size && switches[i]) {
                switchMask = switchMask or (1 shl i)
            }
        }
        out.writeByte(switchMask)

        // 4. Console flags (1 byte: bit 0 = masterPower, bit 1 = clockRunning)
        var consoleFlags = 0
        if (masterPower) consoleFlags = consoleFlags or 0x01
        if (clockRunning) consoleFlags = consoleFlags or 0x02
        out.writeByte(consoleFlags)

        // 5. Clock frequency (4 bytes IEEE-754 float)
        out.writeFloat(clockFrequencyHz.toFloat())

        // 6. Chip count N (1 byte)
        out.writeByte(chips.size)

        // 7. Packed chips (4 * N bytes)
        for (chip in chips) {
            val normalized = normalizePartNumber(chip.partNumber)
            val lookupId = CHIP_CATALOG_LOOKUP.indexOf(normalized)
            if (lookupId < 0) {
                throw IllegalArgumentException("Unsupported IC part number: ${chip.partNumber}")
            }
            if (chip.trench !in 1..2) {
                throw IllegalArgumentException("Invalid trench ${chip.trench} for chip ${chip.partNumber}")
            }
            if (chip.startColumn !in 0..63) {
                throw IllegalArgumentException("Invalid start column ${chip.startColumn} for chip ${chip.partNumber}")
            }

            out.writeByte(lookupId)
            out.writeByte(chip.trench)
            out.writeByte(chip.startColumn)
            val flags = if (chip.isRotated180) 0x01 else 0x00
            out.writeByte(flags)
        }

        // 8. Wire count M (2 bytes short)
        out.writeShort(wires.size and 0xFFFF)

        // 9. Packed wires (6 * M bytes)
        for (wire in wires) {
            if (wire.startSocket !in 0 until AD200Topology.TOTAL_NETLIST_NODES) {
                throw IllegalArgumentException("Start socket out of range: ${wire.startSocket}")
            }
            if (wire.endSocket !in 0 until AD200Topology.TOTAL_NETLIST_NODES) {
                throw IllegalArgumentException("End socket out of range: ${wire.endSocket}")
            }

            out.writeShort(wire.startSocket and 0xFFFF)
            out.writeShort(wire.endSocket and 0xFFFF)
            out.writeByte(wire.color.ordinal.coerceIn(0, WireColor.entries.size - 1))

            var routingFlags = (wire.elevationLevel and 0x03) shl 1
            if (wire.isManhattan) {
                routingFlags = routingFlags or 0x01
            }
            out.writeByte(routingFlags)
        }

        out.flush()
        val uncompressed = baos.toByteArray()

        // Deflate compression
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        val compBaos = ByteArrayOutputStream()
        try {
            deflater.setInput(uncompressed)
            deflater.finish()
            val buf = ByteArray(512)
            while (!deflater.finished()) {
                val count = deflater.deflate(buf)
                compBaos.write(buf, 0, count)
            }
        } finally {
            deflater.end()
        }

        val compressed = compBaos.toByteArray()
        val base64 = Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
        return "$PREFIX$base64"
    }

    /**
     * Decodes a "LOGIC-..." bytecode string into a [SerializedProject].
     * Throws [IllegalArgumentException] if the bytecode is invalid, corrupted, or incompatible.
     */
    fun decodeToProject(code: String): SerializedProject {
        if (!code.startsWith(PREFIX)) {
            throw IllegalArgumentException("Missing required '$PREFIX' prefix")
        }
        val payload = code.removePrefix(PREFIX).trim()
        if (payload.isEmpty()) {
            throw IllegalArgumentException("Share code payload is empty")
        }

        val compressed = try {
            Base64.getUrlDecoder().decode(payload)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid URL-safe Base64 encoding: ${e.message}", e)
        }

        val inflater = Inflater()
        val decompBaos = ByteArrayOutputStream()
        try {
            inflater.setInput(compressed)
            val buf = ByteArray(512)
            while (!inflater.finished()) {
                val count = inflater.inflate(buf)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                }
                decompBaos.write(buf, 0, count)
            }
        } catch (e: DataFormatException) {
            throw IllegalArgumentException("Decompression failed (corrupt Deflate stream): ${e.message}", e)
        } finally {
            inflater.end()
        }

        val uncompressed = decompBaos.toByteArray()
        // Minimum header size: 4 (magic) + 1 (ver) + 1 (switches) + 1 (console) + 4 (clock) + 1 (chipCount) + 2 (wireCount) = 14 bytes
        if (uncompressed.size < 14) {
            throw IllegalArgumentException("Bytecode payload too short: ${uncompressed.size} bytes (minimum is 14)")
        }

        val inStream = DataInputStream(ByteArrayInputStream(uncompressed))

        // 1. Verify Magic Header
        val magic = ByteArray(4)
        inStream.readFully(magic)
        if (!magic.contentEquals(MAGIC_HEADER)) {
            throw IllegalArgumentException("Invalid magic header: expected 'LLCB'")
        }

        // 2. Version
        val version = inStream.readUnsignedByte()
        if (version != VERSION.toInt()) {
            throw IllegalArgumentException("Unsupported bytecode version: $version (expected $VERSION)")
        }

        // 3. Switches bitmask
        val switchMask = inStream.readUnsignedByte()
        val switches = (0 until 8).map { (switchMask and (1 shl it)) != 0 }

        // 4. Console flags
        val consoleFlags = inStream.readUnsignedByte()
        val masterPower = (consoleFlags and 0x01) != 0
        val clockRunning = (consoleFlags and 0x02) != 0

        // 5. Clock frequency
        val clockFrequencyHz = inStream.readFloat().toDouble()

        // 6. Chip count N
        val chipCount = inStream.readUnsignedByte()
        val minRequiredSize = 14 + (chipCount * 4)
        if (uncompressed.size < minRequiredSize) {
            throw IllegalArgumentException("Truncated chip data: expected at least $minRequiredSize bytes, got ${uncompressed.size}")
        }

        // 7. Packed chips
        val chips = mutableListOf<SerializedChip>()
        for (i in 0 until chipCount) {
            val lookupId = inStream.readUnsignedByte()
            if (lookupId !in CHIP_CATALOG_LOOKUP.indices) {
                throw IllegalArgumentException("Unknown IC lookup ID $lookupId at index $i")
            }
            val trench = inStream.readUnsignedByte()
            if (trench !in 1..2) {
                throw IllegalArgumentException("Invalid trench $trench for chip at index $i")
            }
            val startColumn = inStream.readUnsignedByte()
            if (startColumn !in 0..63) {
                throw IllegalArgumentException("Invalid start column $startColumn for chip at index $i")
            }
            val flags = inStream.readUnsignedByte()
            val isRotated180 = (flags and 0x01) != 0

            chips.add(
                SerializedChip(
                    id = UUID.randomUUID().toString(),
                    partNumber = CHIP_CATALOG_LOOKUP[lookupId],
                    trench = trench,
                    startColumn = startColumn,
                    isRotated180 = isRotated180
                )
            )
        }

        // 8. Wire count M
        val wireCount = inStream.readUnsignedShort()
        val totalExpectedSize = 14 + (chipCount * 4) + (wireCount * 6)
        if (uncompressed.size < totalExpectedSize) {
            throw IllegalArgumentException("Truncated wire data: expected $totalExpectedSize bytes, got ${uncompressed.size}")
        }

        // 9. Packed wires
        val wires = mutableListOf<SerializedWire>()
        for (i in 0 until wireCount) {
            val startSocket = inStream.readUnsignedShort()
            val endSocket = inStream.readUnsignedShort()
            if (startSocket !in 0 until AD200Topology.TOTAL_NETLIST_NODES ||
                endSocket !in 0 until AD200Topology.TOTAL_NETLIST_NODES
            ) {
                throw IllegalArgumentException("Wire socket out of range at index $i: start=$startSocket, end=$endSocket")
            }

            val colorByte = inStream.readUnsignedByte()
            val wireColor = if (colorByte in WireColor.entries.indices) {
                WireColor.entries[colorByte]
            } else {
                WireColor.RED
            }

            val routingFlags = inStream.readUnsignedByte()
            val isManhattan = (routingFlags and 0x01) != 0

            wires.add(
                SerializedWire(
                    id = UUID.randomUUID().toString(),
                    startSocket = startSocket,
                    endSocket = endSocket,
                    colorName = wireColor.name,
                    isManhattan = isManhattan
                )
            )
        }

        if (inStream.available() > 0) {
            throw IllegalArgumentException("Corrupt bytecode: unexpected trailing bytes (${inStream.available()} remaining)")
        }

        return SerializedProject(
            title = "Shared Circuit",
            chips = chips,
            wires = wires,
            switches = switches,
            masterPower = masterPower,
            clockFrequencyHz = clockFrequencyHz,
            clockRunning = clockRunning
        )
    }

    /**
     * Validates a candidate share code without throwing exceptions.
     */
    fun validate(code: String): BytecodeValidationResult {
        return try {
            val project = decodeToProject(code)
            BytecodeValidationResult(
                isValid = true,
                chipCount = project.chips.size,
                wireCount = project.wires.size,
                errorMessage = null
            )
        } catch (e: Exception) {
            BytecodeValidationResult(
                isValid = false,
                chipCount = 0,
                wireCount = 0,
                errorMessage = e.message ?: "Invalid circuit code"
            )
        }
    }
}
