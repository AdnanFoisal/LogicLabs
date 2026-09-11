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

class CircuitShareBytecodeRoundTripTest {

    @Test
    fun `round trip preserves chips wires colors manhattan switches masterPower and clock frequency`() {
        val circuit = BreadboardCircuit()
        circuit.addChip("7400", trench = 1, startColumn = 10, isRotated180 = false)
        circuit.addChip("7486", trench = 2, startColumn = 25, isRotated180 = true)
        circuit.addWire(10, 200, WireColor.BLUE, isManhattan = true)
        circuit.addWire(350, 600, WireColor.GREEN, isManhattan = false)
        circuit.addWire(AD200Topology.TERM_SW0, 15, WireColor.RED, isManhattan = true)

        circuit.switches[0] = true
        circuit.switches[2] = true
        circuit.switches[7] = true
        circuit.masterPower = false
        circuit.clockFrequencyHz = 440.0
        circuit.clockRunning = false

        val code = CircuitShareBytecode.encode(circuit)
        assertTrue(code.startsWith(CircuitShareBytecode.PREFIX))
        assertFalse(code.contains("="))
        assertFalse(code.contains("+"))
        assertFalse(code.contains("/"))

        val validation = CircuitShareBytecode.validate(code)
        assertTrue(validation.isValid)
        assertEquals(2, validation.chipCount)
        assertEquals(3, validation.wireCount)
        assertNull(validation.errorMessage)

        val project = CircuitShareBytecode.decodeToProject(code)
        assertEquals(2, project.chips.size)
        assertEquals(3, project.wires.size)
        assertFalse(project.masterPower)
        assertFalse(project.clockRunning)
        assertEquals(440.0, project.clockFrequencyHz, 0.001)

        val restored = BreadboardCircuit()
        ProjectPersistence.deserialize(project, restored)

        assertEquals(2, restored.placedChips.size)
        val chip0 = restored.placedChips[0]
        assertEquals("7400", chip0.placedIc.partNumber)
        assertEquals(1, chip0.placedIc.trench)
        assertEquals(10, chip0.placedIc.startColumn)
        assertFalse(chip0.placedIc.isRotated180)

        val chip1 = restored.placedChips[1]
        assertEquals("7486", chip1.placedIc.partNumber)
        assertEquals(2, chip1.placedIc.trench)
        assertEquals(25, chip1.placedIc.startColumn)
        assertTrue(chip1.placedIc.isRotated180)

        assertEquals(3, restored.wires.size)
        val wire0 = restored.wires[0]
        assertEquals(10, wire0.startSocket)
        assertEquals(200, wire0.endSocket)
        assertEquals(WireColor.BLUE, wire0.color)
        assertTrue(wire0.isManhattan)

        val wire1 = restored.wires[1]
        assertEquals(350, wire1.startSocket)
        assertEquals(600, wire1.endSocket)
        assertEquals(WireColor.GREEN, wire1.color)
        assertFalse(wire1.isManhattan)

        val wire2 = restored.wires[2]
        assertEquals(AD200Topology.TERM_SW0, wire2.startSocket)
        assertEquals(15, wire2.endSocket)
        assertEquals(WireColor.RED, wire2.color)
        assertTrue(wire2.isManhattan)

        // Switch states
        assertTrue(restored.switches[0])
        assertFalse(restored.switches[1])
        assertTrue(restored.switches[2])
        assertFalse(restored.switches[3])
        assertFalse(restored.switches[4])
        assertFalse(restored.switches[5])
        assertFalse(restored.switches[6])
        assertTrue(restored.switches[7])

        // Console state
        assertFalse(restored.masterPower)
        assertFalse(restored.clockRunning)
        assertEquals(440.0, restored.clockFrequencyHz, 0.001)
    }

    @Test
    fun `round trip handles empty circuit with zero chips and zero wires`() {
        val circuit = BreadboardCircuit()
        val code = CircuitShareBytecode.encode(circuit)
        assertTrue(code.startsWith("LOGIC-"))

        val validation = CircuitShareBytecode.validate(code)
        assertTrue(validation.isValid)
        assertEquals(0, validation.chipCount)
        assertEquals(0, validation.wireCount)

        val project = CircuitShareBytecode.decodeToProject(code)
        assertEquals(0, project.chips.size)
        assertEquals(0, project.wires.size)
        assertTrue(project.masterPower)
        assertTrue(project.clockRunning)
        assertEquals(1.0, project.clockFrequencyHz, 0.001)
    }

    @Test
    fun `round trip handles all 14 chips in catalog lookup`() {
        val catalog = CircuitShareBytecode.CHIP_CATALOG_LOOKUP
        val circuit = BreadboardCircuit()
        // Place first 7 chips in trench 1 and next 7 in trench 2
        for (i in 0 until 7) {
            circuit.addChip(catalog[i], trench = 1, startColumn = i * 8)
        }
        for (i in 7 until 14) {
            circuit.addChip(catalog[i], trench = 2, startColumn = (i - 7) * 8)
        }

        val code = CircuitShareBytecode.encode(circuit)
        val validation = CircuitShareBytecode.validate(code)
        assertTrue(validation.isValid)
        assertEquals(14, validation.chipCount)

        val restoredProject = CircuitShareBytecode.decodeToProject(code)
        assertEquals(14, restoredProject.chips.size)
        for (i in 0 until 14) {
            assertEquals(catalog[i], restoredProject.chips[i].partNumber)
        }
    }

    @Test
    fun `round trip preserves project object directly`() {
        val original = SerializedProject(
            title = "Direct Project",
            chips = listOf(
                SerializedChip("c1", "7404", 1, 5, isRotated180 = false),
                SerializedChip("c2", "7408", 2, 12, isRotated180 = true)
            ),
            wires = listOf(
                SerializedWire("w1", 10, 20, "YELLOW", isManhattan = false),
                SerializedWire("w2", 30, 40, "PURPLE", isManhattan = true)
            ),
            switches = listOf(false, true, false, true, false, false, false, false),
            masterPower = true,
            clockFrequencyHz = 10000.0,
            clockRunning = true
        )

        val code = CircuitShareBytecode.encode(original)
        val decoded = CircuitShareBytecode.decodeToProject(code)

        assertEquals(2, decoded.chips.size)
        assertEquals("7404", decoded.chips[0].partNumber)
        assertEquals(1, decoded.chips[0].trench)
        assertEquals(5, decoded.chips[0].startColumn)
        assertFalse(decoded.chips[0].isRotated180)

        assertEquals("7408", decoded.chips[1].partNumber)
        assertEquals(2, decoded.chips[1].trench)
        assertEquals(12, decoded.chips[1].startColumn)
        assertTrue(decoded.chips[1].isRotated180)

        assertEquals(2, decoded.wires.size)
        assertEquals(10, decoded.wires[0].startSocket)
        assertEquals(20, decoded.wires[0].endSocket)
        assertEquals("YELLOW", decoded.wires[0].colorName)
        assertFalse(decoded.wires[0].isManhattan)

        assertEquals("PURPLE", decoded.wires[1].colorName)
        assertTrue(decoded.wires[1].isManhattan)

        assertEquals(original.switches, decoded.switches)
        assertTrue(decoded.masterPower)
        assertTrue(decoded.clockRunning)
        assertEquals(10000.0, decoded.clockFrequencyHz, 0.001)
    }

    @Test
    fun `validate catches invalid or corrupted bytecode strings`() {
        // 1. Blank string
        val resBlank = CircuitShareBytecode.validate("")
        assertFalse(resBlank.isValid)
        assertNotNull(resBlank.errorMessage)

        // 2. Missing prefix
        val resNoPrefix = CircuitShareBytecode.validate("eJxLzs8tVjDSMU9MSzE1NDZKM0g2MUw0MDA3NzYwAABV0AaA")
        assertFalse(resNoPrefix.isValid)
        assertTrue(resNoPrefix.errorMessage!!.contains("prefix"))

        // 3. Invalid Base64 payload
        val resBadB64 = CircuitShareBytecode.validate("LOGIC-!@#\$%^&*()")
        assertFalse(resBadB64.isValid)

        // 4. Invalid Deflate compressed stream
        val badZlib = Base64.getUrlEncoder().withoutPadding().encodeToString(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        val resBadZlib = CircuitShareBytecode.validate("LOGIC-$badZlib")
        assertFalse(resBadZlib.isValid)
    }

    @Test
    fun `validate catches malformed binary structure`() {
        // Build an uncompressed payload with bad magic header
        fun packAndEncode(bytes: ByteArray): String {
            val deflater = Deflater(Deflater.BEST_COMPRESSION)
            deflater.setInput(bytes)
            deflater.finish()
            val baos = ByteArrayOutputStream()
            val buf = ByteArray(256)
            while (!deflater.finished()) {
                val n = deflater.deflate(buf)
                baos.write(buf, 0, n)
            }
            deflater.end()
            return "LOGIC-" + Base64.getUrlEncoder().withoutPadding().encodeToString(baos.toByteArray())
        }

        // Bad magic header "XXXX"
        val badMagic = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x58, 0x58, 0x58, 0x58)) // XXXX
            write(1) // version
            write(0) // switches
            write(3) // console
            DataOutputStream(this).writeFloat(1.0f) // clock
            write(0) // 0 chips
            DataOutputStream(this).writeShort(0) // 0 wires
        }.toByteArray()
        val resBadMagic = CircuitShareBytecode.validate(packAndEncode(badMagic))
        assertFalse(resBadMagic.isValid)
        assertTrue(resBadMagic.errorMessage!!.contains("magic"))

        // Unsupported version 99
        val badVer = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x4C, 0x4C, 0x43, 0x42)) // LLCB
            write(99) // bad version
            write(0)
            write(3)
            DataOutputStream(this).writeFloat(1.0f)
            write(0)
            DataOutputStream(this).writeShort(0)
        }.toByteArray()
        val resBadVer = CircuitShareBytecode.validate(packAndEncode(badVer))
        assertFalse(resBadVer.isValid)
        assertTrue(resBadVer.errorMessage!!.contains("version"))

        // Truncated header
        val truncated = byteArrayOf(0x4C, 0x4C, 0x43)
        val resTruncated = CircuitShareBytecode.validate(packAndEncode(truncated))
        assertFalse(resTruncated.isValid)
        assertTrue(resTruncated.errorMessage!!.contains("short"))

        // Invalid chip lookup id 50
        val badChip = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x4C, 0x4C, 0x43, 0x42))
            write(1)
            write(0)
            write(3)
            DataOutputStream(this).writeFloat(1.0f)
            write(1) // 1 chip
            write(50) // invalid lookup id
            write(1) // trench
            write(0) // startColumn
            write(0) // flags
            DataOutputStream(this).writeShort(0)
        }.toByteArray()
        val resBadChip = CircuitShareBytecode.validate(packAndEncode(badChip))
        assertFalse(resBadChip.isValid)
        assertTrue(resBadChip.errorMessage!!.contains("lookup ID"))

        // Invalid socket out of range (e.g. 5000)
        val badWire = ByteArrayOutputStream().apply {
            write(byteArrayOf(0x4C, 0x4C, 0x43, 0x42))
            write(1)
            write(0)
            write(3)
            DataOutputStream(this).writeFloat(1.0f)
            write(0) // 0 chips
            val dos = DataOutputStream(this)
            dos.writeShort(1) // 1 wire
            dos.writeShort(5000) // startSocket > 2048
            dos.writeShort(10)
            write(0) // color
            write(0) // flags
        }.toByteArray()
        val resBadWire = CircuitShareBytecode.validate(packAndEncode(badWire))
        assertFalse(resBadWire.isValid)
        assertTrue(resBadWire.errorMessage!!.contains("range"))
    }

    @Test
    fun `decodeToProject throws on invalid code`() {
        assertThrows(IllegalArgumentException::class.java) {
            CircuitShareBytecode.decodeToProject("NOT_LOGIC")
        }
    }
}
