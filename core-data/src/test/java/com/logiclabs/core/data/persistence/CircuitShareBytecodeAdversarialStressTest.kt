package com.logiclabs.core.data.persistence

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.WireColor
import com.logiclabs.core.bridge.topology.AD200Topology
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import java.util.zip.Deflater

/**
 * Adversarial stress testing harness for R3 CircuitShareBytecode:
 * - 14-chip full catalog round-trip fuzzing with all 10 wire colors, Manhattan & direct routing, diverse switch patterns and clock frequencies.
 * - Corrupted Base64 payloads (special characters, padding, truncated Base64).
 * - Bad magic headers, bad versions, truncated payloads at every byte boundary.
 * - Out-of-range chip lookup IDs, trenches, columns, and socket indexes.
 * - Trailing garbage detection and zero-hang/zero-crash guarantees.
 */
class CircuitShareBytecodeAdversarialStressTest {

    private fun packAndEncodeRaw(bytes: ByteArray): String {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(bytes)
        deflater.finish()
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(512)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            baos.write(buf, 0, count)
        }
        deflater.end()
        val compressed = baos.toByteArray()
        return CircuitShareBytecode.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
    }

    @Test
    fun testComplex14ChipCircuitRoundTripPreservation() {
        val catalog = CircuitShareBytecode.CHIP_CATALOG_LOOKUP
        assertEquals("Full 74xx catalog must have 14 distinct chips", 14, catalog.size)

        val originalCircuit = BreadboardCircuit()

        // Place all 14 chips across both trenches with alternating 180-degree rotations
        for (i in 0 until 7) {
            val chipName = catalog[i]
            val isRotated = (i % 2 == 1)
            originalCircuit.addChip(chipName, trench = 1, startColumn = i * 8, isRotated180 = isRotated)
        }
        for (i in 7 until 14) {
            val chipName = catalog[i]
            val isRotated = (i % 2 == 0)
            originalCircuit.addChip(chipName, trench = 2, startColumn = (i - 7) * 8, isRotated180 = isRotated)
        }

        // Add 28 diverse wires exercising all 10 WireColors, routing modes, and socket types
        val allColors = WireColor.entries
        for (w in 0 until 28) {
            val color = allColors[w % allColors.size]
            val isManhattan = (w % 2 == 0)
            val startSock = when (w % 4) {
                0 -> AD200Topology.TERM_SW0 + (w % 8)
                1 -> AD200Topology.RAIL_TOP_VCC_5V
                2 -> AD200Topology.RAIL_BOT_GND
                else -> originalCircuit.placedChips[w % 14].getPinSocket(1)
            }
            val endSock = when (w % 4) {
                0 -> originalCircuit.placedChips[(w + 3) % 14].getPinSocket(2)
                1 -> originalCircuit.placedChips[(w + 5) % 14].getPinSocket(14)
                2 -> originalCircuit.placedChips[(w + 7) % 14].getPinSocket(7)
                else -> AD200Topology.TERM_LED0 + (w % 8)
            }
            originalCircuit.addWire(startSock, endSock, color, isManhattan = isManhattan)
        }

        // Switches pattern: 10101010 (binary)
        for (s in 0 until 8) {
            originalCircuit.switches[s] = (s % 2 == 0)
        }
        originalCircuit.masterPower = true
        originalCircuit.clockRunning = true
        originalCircuit.clockFrequencyHz = 25000.0

        // Encode to bytecode string
        val code = CircuitShareBytecode.encode(originalCircuit)
        assertTrue("Code must start with LOGIC- prefix", code.startsWith(CircuitShareBytecode.PREFIX))
        assertFalse("URL-safe Base64 must not contain '+'", code.contains("+"))
        assertFalse("URL-safe Base64 must not contain '/'", code.contains("/"))
        assertFalse("URL-safe Base64 without padding must not contain '='", code.contains("="))

        // Validation test
        val validation = CircuitShareBytecode.validate(code)
        assertTrue("Validation must pass for complex 14-chip circuit: ${validation.errorMessage}", validation.isValid)
        assertEquals(14, validation.chipCount)
        assertEquals(28, validation.wireCount)
        assertNull(validation.errorMessage)

        // Decode to SerializedProject
        val decodedProject = CircuitShareBytecode.decodeToProject(code)
        assertEquals(14, decodedProject.chips.size)
        assertEquals(28, decodedProject.wires.size)
        assertTrue(decodedProject.masterPower)
        assertTrue(decodedProject.clockRunning)
        assertEquals(25000.0, decodedProject.clockFrequencyHz, 0.01)

        // Deserialize into a new BreadboardCircuit and verify 100% roundtrip fidelity
        val restoredCircuit = BreadboardCircuit()
        ProjectPersistence.deserialize(decodedProject, restoredCircuit)

        assertEquals(14, restoredCircuit.placedChips.size)
        for (i in 0 until 14) {
            val orig = originalCircuit.placedChips[i].placedIc
            val rest = restoredCircuit.placedChips[i].placedIc
            assertEquals("Chip $i partNumber mismatch", orig.partNumber, rest.partNumber)
            assertEquals("Chip $i trench mismatch", orig.trench, rest.trench)
            assertEquals("Chip $i startColumn mismatch", orig.startColumn, rest.startColumn)
            assertEquals("Chip $i isRotated180 mismatch", orig.isRotated180, rest.isRotated180)
        }

        assertEquals(28, restoredCircuit.wires.size)
        for (i in 0 until 28) {
            val orig = originalCircuit.wires[i]
            val rest = restoredCircuit.wires[i]
            assertEquals("Wire $i startSocket mismatch", orig.startSocket, rest.startSocket)
            assertEquals("Wire $i endSocket mismatch", orig.endSocket, rest.endSocket)
            assertEquals("Wire $i color mismatch", orig.color, rest.color)
            assertEquals("Wire $i isManhattan mismatch", orig.isManhattan, rest.isManhattan)
        }

        for (s in 0 until 8) {
            assertEquals("Switch $s state mismatch", originalCircuit.switches[s], restoredCircuit.switches[s])
        }
        assertEquals(originalCircuit.masterPower, restoredCircuit.masterPower)
        assertEquals(originalCircuit.clockRunning, restoredCircuit.clockRunning)
        assertEquals(originalCircuit.clockFrequencyHz, restoredCircuit.clockFrequencyHz, 0.01)
    }

    @Test
    fun testCorruptedBase64Rejection() {
        val badPayloads = listOf(
            "",
            "LOGIC-",
            "LOGIC-    ",
            "LOGIC-!@#$%^&*()",
            "LOGIC-abc+def/123==",
            "LOGIC-======",
            "LOGIC-Z",
            "NOT_LOGIC_eJxLzs8tVjDSMU9MSzE1NDZKM0g2MUw0MDA3NzYwAABV0AaA",
            "LOGIC-eJxLzs8tVjDSMU9MSzE1NDZKM0g2MUw0MDA3" // partially truncated base64 string
        )

        for (bad in badPayloads) {
            val validation = CircuitShareBytecode.validate(bad)
            assertFalse("Validation must fail for corrupted payload: '$bad'", validation.isValid)
            assertNotNull("Validation error message must be present for: '$bad'", validation.errorMessage)
            assertTrue("Validation error message must not be blank", validation.errorMessage!!.isNotBlank())

            val ex = assertThrows("decodeToProject must throw for: '$bad'", IllegalArgumentException::class.java) {
                CircuitShareBytecode.decodeToProject(bad)
            }
            assertNotNull(ex.message)
        }
    }

    @Test
    fun testBadMagicHeaderRejection() {
        val badMagics = listOf(
            byteArrayOf(0x41, 0x42, 0x43, 0x44), // "ABCD"
            byteArrayOf(0x00, 0x00, 0x00, 0x00), // Zeroes
            byteArrayOf(0x4C, 0x4C, 0x43, 0x41), // "LLCA"
            byteArrayOf(0x58, 0x58, 0x58, 0x58)  // "XXXX"
        )

        for (badMagic in badMagics) {
            val payload = ByteArrayOutputStream().apply {
                write(badMagic)
                write(1) // version 1
                write(0) // switches
                write(3) // console
                DataOutputStream(this).writeFloat(1.0f)
                write(0) // 0 chips
                DataOutputStream(this).writeShort(0) // 0 wires
            }.toByteArray()

            val encoded = packAndEncodeRaw(payload)
            val validation = CircuitShareBytecode.validate(encoded)
            assertFalse("Bad magic must be rejected", validation.isValid)
            assertTrue("Error message must mention magic", validation.errorMessage!!.contains("magic", ignoreCase = true))

            assertThrows(IllegalArgumentException::class.java) {
                CircuitShareBytecode.decodeToProject(encoded)
            }
        }
    }

    @Test
    fun testBadVersionRejection() {
        val badVersions = listOf(0, 2, 3, 99, 255)

        for (ver in badVersions) {
            val payload = ByteArrayOutputStream().apply {
                write(CircuitShareBytecode.MAGIC_HEADER)
                write(ver)
                write(0)
                write(3)
                DataOutputStream(this).writeFloat(10.0f)
                write(0)
                DataOutputStream(this).writeShort(0)
            }.toByteArray()

            val encoded = packAndEncodeRaw(payload)
            val validation = CircuitShareBytecode.validate(encoded)
            assertFalse("Unsupported version $ver must be rejected", validation.isValid)
            assertTrue("Error message must mention version", validation.errorMessage!!.contains("version", ignoreCase = true))

            assertThrows(IllegalArgumentException::class.java) {
                CircuitShareBytecode.decodeToProject(encoded)
            }
        }
    }

    @Test
    fun testTruncatedPayloadRejectionAtEveryBoundary() {
        // Build a complete valid 1-chip 1-wire payload
        val fullPayload = ByteArrayOutputStream().apply {
            write(CircuitShareBytecode.MAGIC_HEADER) // 0..3 (4 bytes)
            write(1) // 4: version
            write(0) // 5: switches
            write(3) // 6: console
            DataOutputStream(this).writeFloat(1.0f) // 7..10: clock (4 bytes)
            write(1) // 11: chipCount = 1
            // Chip 0: 4 bytes (12..15)
            write(0) // lookupId = 0 ("7400")
            write(1) // trench = 1
            write(10) // startColumn = 10
            write(0) // flags = 0
            // WireCount: 2 bytes (16..17)
            val dos = DataOutputStream(this)
            dos.writeShort(1)
            // Wire 0: 6 bytes (18..23)
            dos.writeShort(10) // startSocket
            dos.writeShort(20) // endSocket
            write(2) // color RED
            write(1) // isManhattan
        }.toByteArray()

        assertEquals("Full test payload must be 24 bytes", 24, fullPayload.size)

        // Truncate at every single byte length from 0 up to 23
        for (len in 0 until fullPayload.size) {
            val truncatedBytes = fullPayload.copyOf(len)
            val encoded = packAndEncodeRaw(truncatedBytes)

            val validation = CircuitShareBytecode.validate(encoded)
            assertFalse("Truncated payload at length $len must be rejected", validation.isValid)
            assertNotNull("Error message must be present for length $len", validation.errorMessage)

            assertThrows("decodeToProject must throw for length $len", IllegalArgumentException::class.java) {
                CircuitShareBytecode.decodeToProject(encoded)
            }
        }
    }

    @Test
    fun testOutOfRangeChipValuesRejection() {
        // 1. Invalid IC lookup ID 14 (valid is 0..13)
        val badLookup = ByteArrayOutputStream().apply {
            write(CircuitShareBytecode.MAGIC_HEADER)
            write(1)
            write(0)
            write(3)
            DataOutputStream(this).writeFloat(1.0f)
            write(1) // 1 chip
            write(14) // Invalid lookup ID
            write(1)
            write(10)
            write(0)
            DataOutputStream(this).writeShort(0)
        }.toByteArray()
        val resBadLookup = CircuitShareBytecode.validate(packAndEncodeRaw(badLookup))
        assertFalse(resBadLookup.isValid)
        assertTrue(resBadLookup.errorMessage!!.contains("lookup ID", ignoreCase = true))

        // 2. Invalid trench 0
        val badTrench0 = ByteArrayOutputStream().apply {
            write(CircuitShareBytecode.MAGIC_HEADER)
            write(1)
            write(0)
            write(3)
            DataOutputStream(this).writeFloat(1.0f)
            write(1)
            write(0)
            write(0) // Invalid trench 0
            write(10)
            write(0)
            DataOutputStream(this).writeShort(0)
        }.toByteArray()
        val resBadTrench0 = CircuitShareBytecode.validate(packAndEncodeRaw(badTrench0))
        assertFalse(resBadTrench0.isValid)
        assertTrue(resBadTrench0.errorMessage!!.contains("trench", ignoreCase = true))

        // 3. Invalid trench 3
        val badTrench3 = ByteArrayOutputStream().apply {
            write(CircuitShareBytecode.MAGIC_HEADER)
            write(1)
            write(0)
            write(3)
            DataOutputStream(this).writeFloat(1.0f)
            write(1)
            write(0)
            write(3) // Invalid trench 3
            write(10)
            write(0)
            DataOutputStream(this).writeShort(0)
        }.toByteArray()
        val resBadTrench3 = CircuitShareBytecode.validate(packAndEncodeRaw(badTrench3))
        assertFalse(resBadTrench3.isValid)
        assertTrue(resBadTrench3.errorMessage!!.contains("trench", ignoreCase = true))

        // 4. Invalid startColumn 64 (valid is 0..63)
        val badCol = ByteArrayOutputStream().apply {
            write(CircuitShareBytecode.MAGIC_HEADER)
            write(1)
            write(0)
            write(3)
            DataOutputStream(this).writeFloat(1.0f)
            write(1)
            write(0)
            write(1)
            write(64) // Invalid startColumn 64
            write(0)
            DataOutputStream(this).writeShort(0)
        }.toByteArray()
        val resBadCol = CircuitShareBytecode.validate(packAndEncodeRaw(badCol))
        assertFalse(resBadCol.isValid)
        assertTrue(resBadCol.errorMessage!!.contains("start column", ignoreCase = true))
    }

    @Test
    fun testOutOfRangeWireSocketsRejection() {
        // Socket >= AD200Topology.TOTAL_NETLIST_NODES (2048)
        val badSocketIndices = listOf(2048, 2049, 5000, 65535)

        for (badSock in badSocketIndices) {
            val badWire = ByteArrayOutputStream().apply {
                write(CircuitShareBytecode.MAGIC_HEADER)
                write(1)
                write(0)
                write(3)
                DataOutputStream(this).writeFloat(1.0f)
                write(0) // 0 chips
                val dos = DataOutputStream(this)
                dos.writeShort(1) // 1 wire
                dos.writeShort(badSock) // startSocket out of range
                dos.writeShort(10)      // endSocket
                write(0) // color
                write(0) // routing flags
            }.toByteArray()

            val encoded = packAndEncodeRaw(badWire)
            val res = CircuitShareBytecode.validate(encoded)
            assertFalse("Socket $badSock must be rejected", res.isValid)
            assertTrue("Error message must mention socket out of range", res.errorMessage!!.contains("range", ignoreCase = true))

            assertThrows(IllegalArgumentException::class.java) {
                CircuitShareBytecode.decodeToProject(encoded)
            }
        }
    }

    @Test
    fun testTrailingGarbageRejection() {
        val validBase = ByteArrayOutputStream().apply {
            write(CircuitShareBytecode.MAGIC_HEADER)
            write(1)
            write(0)
            write(3)
            DataOutputStream(this).writeFloat(1.0f)
            write(0) // 0 chips
            DataOutputStream(this).writeShort(0) // 0 wires
        }.toByteArray()

        val garbageLengths = listOf(1, 5, 20)
        for (gLen in garbageLengths) {
            val corruptedWithTrailing = validBase + ByteArray(gLen) { 0xFF.toByte() }
            val encoded = packAndEncodeRaw(corruptedWithTrailing)

            val res = CircuitShareBytecode.validate(encoded)
            assertFalse("Trailing garbage of length $gLen must be rejected", res.isValid)
            assertTrue("Error message must mention trailing bytes", res.errorMessage!!.contains("trailing", ignoreCase = true))

            assertThrows(IllegalArgumentException::class.java) {
                CircuitShareBytecode.decodeToProject(encoded)
            }
        }
    }

    @Test
    fun testWireColorGracefulFallback() {
        // Wire color byte with an unknown index (e.g. 55) should fall back to RED without crashing
        val payloadWithUnknownColor = ByteArrayOutputStream().apply {
            write(CircuitShareBytecode.MAGIC_HEADER)
            write(1)
            write(0)
            write(3)
            DataOutputStream(this).writeFloat(1.0f)
            write(0) // 0 chips
            val dos = DataOutputStream(this)
            dos.writeShort(1) // 1 wire
            dos.writeShort(10)
            dos.writeShort(20)
            write(55) // unknown color ordinal
            write(0)
        }.toByteArray()

        val encoded = packAndEncodeRaw(payloadWithUnknownColor)
        val validation = CircuitShareBytecode.validate(encoded)
        assertTrue("Unknown wire color should fall back safely without error", validation.isValid)

        val project = CircuitShareBytecode.decodeToProject(encoded)
        assertEquals(1, project.wires.size)
        assertEquals(WireColor.RED.name, project.wires[0].colorName)
    }
}
