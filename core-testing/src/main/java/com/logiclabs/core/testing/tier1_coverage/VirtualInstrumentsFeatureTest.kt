package com.logiclabs.core.testing.tier1_coverage

import com.logiclabs.core.testing.harness.BreadboardGeometry
import com.logiclabs.core.testing.harness.OscilloscopeChannel
import com.logiclabs.core.testing.harness.SimulationCircuit
import com.logiclabs.core.testing.harness.TruthTableVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tier 1 Feature Coverage: Virtual Instruments, Test Bench & Verification.
 * Verifies:
 * - Oscilloscope 8,192-point circular buffer & dual-channel probe sampling.
 * - Automated 2^N truth table sweep engine.
 * - Cryptographic HMAC-SHA256 provenance seal generation and tamper detection.
 * Requirement: >= 5 distinct tests.
 */
class VirtualInstrumentsFeatureTest {

    private lateinit var circuit: SimulationCircuit

    @Before
    fun setUp() {
        circuit = SimulationCircuit()
    }

    @Test
    fun testOscilloscopeCircularBufferCapacityAndWrapping() {
        val channel = OscilloscopeChannel(capacity = 8192)
        assertEquals(8192, channel.capacity)

        // Fill buffer completely
        for (i in 0 until 8192) {
            channel.sample(i.toFloat())
        }
        assertEquals(8192L, channel.totalSamples)
        assertEquals(8191.0f, channel.latestSample(), 0.001f)

        // Push 10 more samples -> ring wraps around without expanding memory
        for (i in 1..10) {
            channel.sample(9000f + i)
        }
        assertEquals(8202L, channel.totalSamples)
        assertEquals(9010.0f, channel.latestSample(), 0.001f)
        assertEquals("Buffer array length must stay exactly 8192", 8192, channel.buffer.size)
    }

    @Test
    fun testDualChannelFlyingProbeSampling() {
        // Connect CH1 probe to CLK and CH2 probe to ~CLK
        circuit.probe1Socket = BreadboardGeometry.TERM_CLK
        circuit.probe2Socket = BreadboardGeometry.TERM_CLK_INV

        // Clock = LOW -> CLK=0V, ~CLK=5V
        circuit.clockState = false
        circuit.step()

        assertEquals(0.0f, circuit.scopeCh1.latestSample(), 0.01f)
        assertEquals(5.0f, circuit.scopeCh2.latestSample(), 0.01f)

        // Clock = HIGH -> CLK=5V, ~CLK=0V
        circuit.tickClock()

        assertEquals(5.0f, circuit.scopeCh1.latestSample(), 0.01f)
        assertEquals(0.0f, circuit.scopeCh2.latestSample(), 0.01f)
    }

    @Test
    fun testAutomated2NTruthTableSweep() {
        // Test an AND gate using the automated 2^N sweep
        val u1 = circuit.placeChip("7408", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, BreadboardGeometry.TERM_SW0)
        circuit.wirePin(u1, 2, BreadboardGeometry.TERM_SW1)

        val result = TruthTableVerifier.verify(
            circuit = circuit,
            switchIndices = listOf(0, 1),
            outputReader = {
                listOf(circuit.getPinLevel(u1, 3).toBoolean())
            },
            expectedFunction = { inputs ->
                listOf(inputs[0] && inputs[1])
            }
        )

        assertEquals("2^2 = 4 vectors expected", 4, result.totalVectors)
        assertEquals("All 4 vectors must pass", 4, result.passedVectors)
        assertTrue(result.isAllPassed)
        assertTrue("HMAC hash must be 64-char hex string", result.hmacSha256Hash.length == 64)
    }

    @Test
    fun testHmacSha256TamperDetection() {
        val u1 = circuit.placeChip("7400", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, BreadboardGeometry.TERM_SW0)
        circuit.wirePin(u1, 2, BreadboardGeometry.TERM_SW1)

        // Standard NAND truth table run
        val legitimateResult = TruthTableVerifier.verify(
            circuit = circuit,
            switchIndices = listOf(0, 1),
            outputReader = { listOf(circuit.getPinLevel(u1, 3).toBoolean()) },
            expectedFunction = { inputs -> listOf(!(inputs[0] && inputs[1])) }
        )

        // Altered run (simulating falsified lab data)
        val tamperedResult = TruthTableVerifier.verify(
            circuit = circuit,
            switchIndices = listOf(0, 1),
            outputReader = { listOf(circuit.getPinLevel(u1, 3).toBoolean()) },
            expectedFunction = { inputs -> listOf(inputs[0] && inputs[1]) } // Falsified expectation
        )

        // Tampered report results in differing hash
        assertNotEquals(
            "Cryptographic seal must detect data difference",
            legitimateResult.hmacSha256Hash,
            tamperedResult.hmacSha256Hash
        )
    }

    @Test
    fun testThreeInputGateTruthTableSweep() {
        // Test 7410 (Triple 3-NAND) with 2^3 = 8 vectors
        val u1 = circuit.placeChip("7410", trench = 1, startColumn = 15)
        circuit.wireStandardPower(u1)
        // Gate 1: 1A=1, 1B=2, 1C=13 -> 1Y=12
        circuit.wirePin(u1, 1, BreadboardGeometry.TERM_SW0)
        circuit.wirePin(u1, 2, BreadboardGeometry.TERM_SW1)
        circuit.wirePin(u1, 13, BreadboardGeometry.TERM_SW2)

        val result = TruthTableVerifier.verify(
            circuit = circuit,
            switchIndices = listOf(0, 1, 2),
            outputReader = { listOf(circuit.getPinLevel(u1, 12).toBoolean()) },
            expectedFunction = { inList -> listOf(!(inList[0] && inList[1] && inList[2])) }
        )

        assertEquals(8, result.totalVectors)
        assertEquals(8, result.passedVectors)
        assertTrue(result.isAllPassed)
    }
}
