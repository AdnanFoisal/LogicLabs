package com.logiclabs.feature.tools

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.feature.tools.courseware.LabCurriculum
import com.logiclabs.feature.tools.testbench.TestBenchVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LabCurriculumProjectsTest {

    private lateinit var circuit: BreadboardCircuit

    @Before
    fun setUp() {
        circuit = BreadboardCircuit()
    }

    @Test
    fun testAllTwelveLabsExist() {
        assertEquals("LabCurriculum must contain exactly 12 projects", 12, LabCurriculum.classicLabs.size)
    }

    @Test
    fun testLab1InverterInstantImplementation() {
        val lab = LabCurriculum.classicLabs[0]
        assertEquals("lab1_not", lab.id)
        lab.buildCircuit(circuit)

        val report = TestBenchVerifier.verify(
            circuit = circuit,
            switchIndices = lab.switchIndices,
            outputReader = { lab.ledIndices.map { circuit.ledValues[it] } },
            expectedFunction = lab.expectedFunction,
            experimentTitle = lab.title
        )
        assertTrue("Lab 1 (NOT Gate) must pass all vectors", report.isAllPassed)
        assertEquals(2, report.totalCount)
        assertEquals(2, report.passedCount)
    }

    @Test
    fun testLab2NandInstantImplementation() {
        val lab = LabCurriculum.classicLabs[1]
        assertEquals("lab2_nand", lab.id)
        lab.buildCircuit(circuit)

        val report = TestBenchVerifier.verify(
            circuit = circuit,
            switchIndices = lab.switchIndices,
            outputReader = { lab.ledIndices.map { circuit.ledValues[it] } },
            expectedFunction = lab.expectedFunction,
            experimentTitle = lab.title
        )
        assertTrue("Lab 2 (NAND Gate) must pass all vectors", report.isAllPassed)
        assertEquals(4, report.totalCount)
        assertEquals(4, report.passedCount)
    }

    @Test
    fun testLab3NorInstantImplementation() {
        val lab = LabCurriculum.classicLabs[2]
        assertEquals("lab3_nor", lab.id)
        lab.buildCircuit(circuit)

        val report = TestBenchVerifier.verify(
            circuit = circuit,
            switchIndices = lab.switchIndices,
            outputReader = { lab.ledIndices.map { circuit.ledValues[it] } },
            expectedFunction = lab.expectedFunction,
            experimentTitle = lab.title
        )
        assertTrue("Lab 3 (NOR Gate) must pass all vectors", report.isAllPassed)
        assertEquals(4, report.totalCount)
        assertEquals(4, report.passedCount)
    }

    @Test
    fun testLab4AndInstantImplementation() {
        val lab = LabCurriculum.classicLabs[3]
        assertEquals("lab4_and", lab.id)
        lab.buildCircuit(circuit)

        val report = TestBenchVerifier.verify(
            circuit = circuit,
            switchIndices = lab.switchIndices,
            outputReader = { lab.ledIndices.map { circuit.ledValues[it] } },
            expectedFunction = lab.expectedFunction,
            experimentTitle = lab.title
        )
        assertTrue("Lab 4 (AND Gate) must pass all vectors", report.isAllPassed)
        assertEquals(4, report.totalCount)
        assertEquals(4, report.passedCount)
    }

    @Test
    fun testLab5OrInstantImplementation() {
        val lab = LabCurriculum.classicLabs[4]
        assertEquals("lab5_or", lab.id)
        lab.buildCircuit(circuit)

        val report = TestBenchVerifier.verify(
            circuit = circuit,
            switchIndices = lab.switchIndices,
            outputReader = { lab.ledIndices.map { circuit.ledValues[it] } },
            expectedFunction = lab.expectedFunction,
            experimentTitle = lab.title
        )
        assertTrue("Lab 5 (OR Gate) must pass all vectors", report.isAllPassed)
        assertEquals(4, report.totalCount)
        assertEquals(4, report.passedCount)
    }

    @Test
    fun testLab6XorInstantImplementation() {
        val lab = LabCurriculum.classicLabs[5]
        assertEquals("lab6_xor", lab.id)
        lab.buildCircuit(circuit)

        val report = TestBenchVerifier.verify(
            circuit = circuit,
            switchIndices = lab.switchIndices,
            outputReader = { lab.ledIndices.map { circuit.ledValues[it] } },
            expectedFunction = lab.expectedFunction,
            experimentTitle = lab.title
        )
        assertTrue("Lab 6 (XOR Gate) must pass all vectors", report.isAllPassed)
        assertEquals(4, report.totalCount)
        assertEquals(4, report.passedCount)
    }

    @Test
    fun testLab7HalfAdderInstantImplementation() {
        val lab = LabCurriculum.classicLabs[6]
        assertEquals("lab7_half_adder", lab.id)
        lab.buildCircuit(circuit)

        val report = TestBenchVerifier.verify(
            circuit = circuit,
            switchIndices = lab.switchIndices,
            outputReader = { lab.ledIndices.map { circuit.ledValues[it] } },
            expectedFunction = lab.expectedFunction,
            experimentTitle = lab.title
        )
        assertTrue("Lab 7 (Half Adder) must pass all vectors", report.isAllPassed)
        assertEquals(4, report.totalCount)
        assertEquals(4, report.passedCount)
    }

    @Test
    fun testLab8NandAndInstantImplementation() {
        val lab = LabCurriculum.classicLabs[7]
        assertEquals("lab8_nand_and", lab.id)
        lab.buildCircuit(circuit)

        val report = TestBenchVerifier.verify(
            circuit = circuit,
            switchIndices = lab.switchIndices,
            outputReader = { lab.ledIndices.map { circuit.ledValues[it] } },
            expectedFunction = lab.expectedFunction,
            experimentTitle = lab.title
        )
        assertTrue("Lab 8 (AND from NAND) must pass all vectors", report.isAllPassed)
        assertEquals(4, report.totalCount)
        assertEquals(4, report.passedCount)
    }

    @Test
    fun testLab9NandOrInstantImplementation() {
        val lab = LabCurriculum.classicLabs[8]
        assertEquals("lab9_nand_or", lab.id)
        lab.buildCircuit(circuit)

        val report = TestBenchVerifier.verify(
            circuit = circuit,
            switchIndices = lab.switchIndices,
            outputReader = { lab.ledIndices.map { circuit.ledValues[it] } },
            expectedFunction = lab.expectedFunction,
            experimentTitle = lab.title
        )
        assertTrue("Lab 9 (OR from NAND De Morgan) must pass all vectors", report.isAllPassed)
        assertEquals(4, report.totalCount)
        assertEquals(4, report.passedCount)
    }

    @Test
    fun testLab10SrLatchInstantImplementation() {
        val lab = LabCurriculum.classicLabs[9]
        assertEquals("lab10_sr_latch", lab.id)
        lab.buildCircuit(circuit)

        // Initial build sets ~S=1, ~R=1. Set ~S=0, ~R=1 -> Q=1, ~Q=0
        circuit.switches[0] = false
        circuit.switches[1] = true
        circuit.step()
        assertTrue("Q (LED0) must be HIGH on Set (~S=0, ~R=1)", circuit.ledValues[0])
        assertFalse("~Q (LED1) must be LOW on Set (~S=0, ~R=1)", circuit.ledValues[1])

        // Hold: ~S=1, ~R=1 -> Q retains 1, ~Q retains 0
        circuit.switches[0] = true
        circuit.switches[1] = true
        circuit.step()
        assertTrue("Q (LED0) must hold HIGH when ~S=1, ~R=1", circuit.ledValues[0])
        assertFalse("~Q (LED1) must hold LOW when ~S=1, ~R=1", circuit.ledValues[1])

        // Reset: ~S=1, ~R=0 -> Q=0, ~Q=1
        circuit.switches[0] = true
        circuit.switches[1] = false
        circuit.step()
        assertFalse("Q (LED0) must be LOW on Reset (~S=1, ~R=0)", circuit.ledValues[0])
        assertTrue("~Q (LED1) must be HIGH on Reset (~S=1, ~R=0)", circuit.ledValues[1])

        // Hold again: ~S=1, ~R=1 -> Q retains 0, ~Q retains 1
        circuit.switches[0] = true
        circuit.switches[1] = true
        circuit.step()
        assertFalse("Q (LED0) must hold LOW when ~S=1, ~R=1", circuit.ledValues[0])
        assertTrue("~Q (LED1) must hold HIGH when ~S=1, ~R=1", circuit.ledValues[1])
    }

    @Test
    fun testLab11DFlipFlopInstantImplementation() {
        val lab = LabCurriculum.classicLabs[10]
        assertEquals("lab11_d_flipflop", lab.id)
        lab.buildCircuit(circuit)

        // Set D=1 (SW0=true), CLK=0 (SW1=false)
        circuit.switches[0] = true
        circuit.switches[1] = false
        circuit.step()

        // Rising clock edge: CLK=1 (SW1=true) -> Q captures D=1
        circuit.switches[1] = true
        circuit.step()
        assertTrue("Q (LED0) must be HIGH after rising clock edge with D=1", circuit.ledValues[0])
        assertFalse("~Q (LED1) must be LOW after rising clock edge with D=1", circuit.ledValues[1])

        // Set D=0 (SW0=false), CLK stays 1 -> Q must hold 1 until next clock edge
        circuit.switches[0] = false
        circuit.step()
        assertTrue("Q (LED0) must hold HIGH while CLK is stable", circuit.ledValues[0])

        // Return CLK to 0 (falling edge, no capture)
        circuit.switches[1] = false
        circuit.step()
        assertTrue("Q (LED0) must hold HIGH on falling clock edge", circuit.ledValues[0])

        // Rising clock edge with D=0 -> Q captures 0
        circuit.switches[1] = true
        circuit.step()
        assertFalse("Q (LED0) must be LOW after rising clock edge with D=0", circuit.ledValues[0])
        assertTrue("~Q (LED1) must be HIGH after rising clock edge with D=0", circuit.ledValues[1])
    }

    @Test
    fun testLab12FullAdderInstantImplementation() {
        val lab = LabCurriculum.classicLabs[11]
        assertEquals("lab12_full_adder", lab.id)
        lab.buildCircuit(circuit)

        val report = TestBenchVerifier.verify(
            circuit = circuit,
            switchIndices = lab.switchIndices,
            outputReader = { lab.ledIndices.map { circuit.ledValues[it] } },
            expectedFunction = lab.expectedFunction,
            experimentTitle = lab.title
        )
        assertTrue("Lab 12 (Full Adder) must pass all vectors", report.isAllPassed)
        assertEquals(4, report.totalCount)
        assertEquals(4, report.passedCount)
    }

    @Test
    fun testUnpoweredCircuitFailsVerificationWithDiagnostics() {
        val lab = LabCurriculum.classicLabs[1]
        lab.buildCircuit(circuit)

        // Turn OFF master power
        circuit.masterPower = false

        val report = TestBenchVerifier.verify(
            circuit = circuit,
            switchIndices = lab.switchIndices,
            outputReader = { lab.ledIndices.map { circuit.ledValues[it] } },
            expectedFunction = lab.expectedFunction,
            experimentTitle = lab.title
        )
        assertFalse("Unpowered circuit must fail verification", report.isAllPassed)
        assertTrue("Report must contain diagnostic error for unpowered circuit", report.diagnostics.any { it.isError })
    }
}
