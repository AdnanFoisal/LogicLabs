package com.logiclabs.core.testing.tier1_coverage

import com.logiclabs.core.testing.harness.ElectricalAssertions.assertPinLevel
import com.logiclabs.core.testing.harness.SimulationCircuit
import com.logiclabs.core.testing.model.ElectricalLevel
import org.junit.Before
import org.junit.Test

/**
 * Tier 1 Feature Coverage: 74xx Complex ICs.
 * Verifies operations for:
 * 7483 (4-bit Binary Full Adder, non-standard power VCC=5, GND=12)
 * 7474 (Dual D-Type Positive-Edge-Triggered Flip-Flop)
 * 7476 (Dual J-K Flip-Flop, non-standard power VCC=5, GND=13)
 * 7448 (BCD-to-7-Segment Decoder, Active-HIGH for common cathode)
 * Requirement: >= 5 distinct tests per feature.
 */
class ComplexChipsFeatureTest {

    private lateinit var circuit: SimulationCircuit

    @Before
    fun setUp() {
        circuit = SimulationCircuit()
    }

    // ==========================================
    // 7483: 4-Bit Binary Full Adder (>= 5 tests)
    // ==========================================

    private fun wire7483Power(chip: com.logiclabs.core.testing.harness.PlacedChip) {
        // Non-standard power: Pin 5 is VCC, Pin 12 is GND
        circuit.wirePin(chip, 5, 1934) // VCC
        circuit.wirePin(chip, 12, 1935) // GND
    }

    private fun set7483Inputs(chip: com.logiclabs.core.testing.harness.PlacedChip, a: Int, b: Int, c0: Boolean) {
        fun toSock(bit: Boolean) = if (bit) 1934 else 1935
        // A1=10, A2=8, A3=3, A4=1
        circuit.wirePin(chip, 10, toSock((a and 1) != 0))
        circuit.wirePin(chip, 8, toSock((a and 2) != 0))
        circuit.wirePin(chip, 3, toSock((a and 4) != 0))
        circuit.wirePin(chip, 1, toSock((a and 8) != 0))

        // B1=11, B2=7, B3=4, B4=16
        circuit.wirePin(chip, 11, toSock((b and 1) != 0))
        circuit.wirePin(chip, 7, toSock((b and 2) != 0))
        circuit.wirePin(chip, 4, toSock((b and 4) != 0))
        circuit.wirePin(chip, 16, toSock((b and 8) != 0))

        // C0=13
        circuit.wirePin(chip, 13, toSock(c0))
    }

    @Test
    fun test7483_ZeroPlusZero() {
        val u1 = circuit.placeChip("7483", trench = 1, startColumn = 10)
        wire7483Power(u1)
        set7483Inputs(u1, a = 0, b = 0, c0 = false)
        circuit.step()

        // S1=9, S2=6, S3=2, S4=15, C4=14
        assertPinLevel(circuit, u1, 9, ElectricalLevel.LOW)  // S1
        assertPinLevel(circuit, u1, 6, ElectricalLevel.LOW)  // S2
        assertPinLevel(circuit, u1, 2, ElectricalLevel.LOW)  // S3
        assertPinLevel(circuit, u1, 15, ElectricalLevel.LOW) // S4
        assertPinLevel(circuit, u1, 14, ElectricalLevel.LOW) // C4
    }

    @Test
    fun test7483_FivePlusThreeEqualsEight() {
        val u1 = circuit.placeChip("7483", trench = 1, startColumn = 10)
        wire7483Power(u1)
        // 5 (0101) + 3 (0011) = 8 (1000)
        set7483Inputs(u1, a = 5, b = 3, c0 = false)
        circuit.step()

        assertPinLevel(circuit, u1, 9, ElectricalLevel.LOW)   // S1 = 0
        assertPinLevel(circuit, u1, 6, ElectricalLevel.LOW)   // S2 = 0
        assertPinLevel(circuit, u1, 2, ElectricalLevel.LOW)   // S3 = 0
        assertPinLevel(circuit, u1, 15, ElectricalLevel.HIGH) // S4 = 1
        assertPinLevel(circuit, u1, 14, ElectricalLevel.LOW)  // C4 = 0
    }

    @Test
    fun test7483_SevenPlusNineWithCarryOut() {
        val u1 = circuit.placeChip("7483", trench = 1, startColumn = 10)
        wire7483Power(u1)
        // 7 (0111) + 9 (1001) = 16 (0000 with C4=1)
        set7483Inputs(u1, a = 7, b = 9, c0 = false)
        circuit.step()

        assertPinLevel(circuit, u1, 9, ElectricalLevel.LOW)   // S1 = 0
        assertPinLevel(circuit, u1, 6, ElectricalLevel.LOW)   // S2 = 0
        assertPinLevel(circuit, u1, 2, ElectricalLevel.LOW)   // S3 = 0
        assertPinLevel(circuit, u1, 15, ElectricalLevel.LOW)  // S4 = 0
        assertPinLevel(circuit, u1, 14, ElectricalLevel.HIGH) // C4 = 1
    }

    @Test
    fun test7483_CarryInPropagation() {
        val u1 = circuit.placeChip("7483", trench = 1, startColumn = 10)
        wire7483Power(u1)
        // 4 (0100) + 4 (0100) + C0(1) = 9 (1001)
        set7483Inputs(u1, a = 4, b = 4, c0 = true)
        circuit.step()

        assertPinLevel(circuit, u1, 9, ElectricalLevel.HIGH)  // S1 = 1
        assertPinLevel(circuit, u1, 6, ElectricalLevel.LOW)   // S2 = 0
        assertPinLevel(circuit, u1, 2, ElectricalLevel.LOW)   // S3 = 0
        assertPinLevel(circuit, u1, 15, ElectricalLevel.HIGH) // S4 = 1
        assertPinLevel(circuit, u1, 14, ElectricalLevel.LOW)  // C4 = 0
    }

    @Test
    fun test7483_MaxSumFifteenPlusFifteenPlusCarryIn() {
        val u1 = circuit.placeChip("7483", trench = 1, startColumn = 10)
        wire7483Power(u1)
        // 15 + 15 + 1 = 31 (S = 1111, C4 = 1)
        set7483Inputs(u1, a = 15, b = 15, c0 = true)
        circuit.step()

        assertPinLevel(circuit, u1, 9, ElectricalLevel.HIGH)  // S1 = 1
        assertPinLevel(circuit, u1, 6, ElectricalLevel.HIGH)  // S2 = 1
        assertPinLevel(circuit, u1, 2, ElectricalLevel.HIGH)  // S3 = 1
        assertPinLevel(circuit, u1, 15, ElectricalLevel.HIGH) // S4 = 1
        assertPinLevel(circuit, u1, 14, ElectricalLevel.HIGH) // C4 = 1
    }

    // ==========================================
    // 7474: Dual D-Type Flip-Flop (>= 5 tests)
    // ==========================================

    @Test
    fun test7474_ClockPositiveEdgeStoreHigh() {
        val u1 = circuit.placeChip("7474", trench = 1, startColumn = 5)
        circuit.wireStandardPower(u1)
        // FF1: Pin 1 (~CLR), Pin 4 (~PRE) inactive (HIGH)
        circuit.wirePin(u1, 1, 1934)
        circuit.wirePin(u1, 4, 1934)
        // D = HIGH (Pin 2)
        circuit.wirePin(u1, 2, 1934)

        // Clock starts LOW
        circuit.wirePin(u1, 3, 1935)
        circuit.step()

        // Clock rises LOW -> HIGH
        circuit.wirePin(u1, 3, 1934)
        circuit.step()

        assertPinLevel(circuit, u1, 5, ElectricalLevel.HIGH, "1Q should be 1 after rising edge")
        assertPinLevel(circuit, u1, 6, ElectricalLevel.LOW, "1~Q should be 0")
    }

    @Test
    fun test7474_ClockPositiveEdgeStoreLow() {
        val u1 = circuit.placeChip("7474", trench = 1, startColumn = 5)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1934); circuit.wirePin(u1, 4, 1934)

        // Preset Q to 1 first using ~PRE=0
        circuit.wirePin(u1, 4, 1935)
        circuit.step()
        assertPinLevel(circuit, u1, 5, ElectricalLevel.HIGH)

        // Release ~PRE back to 1
        circuit.wirePin(u1, 4, 1934)
        // D = LOW
        circuit.wirePin(u1, 2, 1935)
        // Clock LOW then HIGH
        circuit.wirePin(u1, 3, 1935)
        circuit.step()
        circuit.wirePin(u1, 3, 1934)
        circuit.step()

        assertPinLevel(circuit, u1, 5, ElectricalLevel.LOW, "1Q should store 0")
        assertPinLevel(circuit, u1, 6, ElectricalLevel.HIGH, "1~Q should be 1")
    }

    @Test
    fun test7474_AsyncClearOverridesClock() {
        val u1 = circuit.placeChip("7474", trench = 1, startColumn = 5)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 4, 1934) // ~PRE = 1
        circuit.wirePin(u1, 2, 1934) // D = 1
        circuit.wirePin(u1, 3, 1934) // CLK = 1

        // Assert ~CLR = 0 (Pin 1)
        circuit.wirePin(u1, 1, 1935)
        circuit.step()

        assertPinLevel(circuit, u1, 5, ElectricalLevel.LOW, "1Q must be forced to 0 by ~CLR")
        assertPinLevel(circuit, u1, 6, ElectricalLevel.HIGH, "1~Q must be forced to 1")
    }

    @Test
    fun test7474_AsyncPresetOverridesClock() {
        val u1 = circuit.placeChip("7474", trench = 1, startColumn = 5)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1934) // ~CLR = 1
        circuit.wirePin(u1, 2, 1935) // D = 0
        circuit.wirePin(u1, 3, 1934) // CLK = 1

        // Assert ~PRE = 0 (Pin 4)
        circuit.wirePin(u1, 4, 1935)
        circuit.step()

        assertPinLevel(circuit, u1, 5, ElectricalLevel.HIGH, "1Q must be forced to 1 by ~PRE")
        assertPinLevel(circuit, u1, 6, ElectricalLevel.LOW, "1~Q must be forced to 0")
    }

    @Test
    fun test7474_UnstableStateBothPresetAndClearLow() {
        val u1 = circuit.placeChip("7474", trench = 1, startColumn = 5)
        circuit.wireStandardPower(u1)
        // ~PRE=0 and ~CLR=0 simultaneously
        circuit.wirePin(u1, 1, 1935)
        circuit.wirePin(u1, 4, 1935)
        circuit.step()

        // Per TTL specification, both Q and ~Q are forced HIGH
        assertPinLevel(circuit, u1, 5, ElectricalLevel.HIGH, "Q forced HIGH in unstable state")
        assertPinLevel(circuit, u1, 6, ElectricalLevel.HIGH, "~Q forced HIGH in unstable state")
    }

    // ==========================================
    // 7476: Dual J-K Flip-Flop (>= 5 tests)
    // ==========================================

    private fun wire7476Power(chip: com.logiclabs.core.testing.harness.PlacedChip) {
        // Non-standard power: Pin 5 VCC, Pin 13 GND
        circuit.wirePin(chip, 5, 1934)
        circuit.wirePin(chip, 13, 1935)
    }

    @Test
    fun test7476_SetState_J1_K0() {
        val u1 = circuit.placeChip("7476", trench = 2, startColumn = 10)
        wire7476Power(u1)
        // FF1: Pin 2 (~PRE=1), Pin 3 (~CLR=1), J=1 (Pin 4), K=0 (Pin 16)
        circuit.wirePin(u1, 2, 1934)
        circuit.wirePin(u1, 3, 1934)
        circuit.wirePin(u1, 4, 1934)
        circuit.wirePin(u1, 16, 1935)

        // Negative edge: Clock HIGH -> LOW (Pin 1)
        circuit.wirePin(u1, 1, 1934)
        circuit.step()
        circuit.wirePin(u1, 1, 1935)
        circuit.step()

        assertPinLevel(circuit, u1, 15, ElectricalLevel.HIGH, "1Q should be Set (1)")
        assertPinLevel(circuit, u1, 14, ElectricalLevel.LOW, "1~Q should be 0")
    }

    @Test
    fun test7476_ResetState_J0_K1() {
        val u1 = circuit.placeChip("7476", trench = 2, startColumn = 10)
        wire7476Power(u1)
        // Preset first
        circuit.wirePin(u1, 2, 1935); circuit.wirePin(u1, 3, 1934)
        circuit.step()
        assertPinLevel(circuit, u1, 15, ElectricalLevel.HIGH)

        // Release preset, set J=0, K=1
        circuit.wirePin(u1, 2, 1934)
        circuit.wirePin(u1, 4, 1935)
        circuit.wirePin(u1, 16, 1934)

        // Falling edge on CLK
        circuit.wirePin(u1, 1, 1934)
        circuit.step()
        circuit.wirePin(u1, 1, 1935)
        circuit.step()

        assertPinLevel(circuit, u1, 15, ElectricalLevel.LOW, "1Q should be Reset (0)")
        assertPinLevel(circuit, u1, 14, ElectricalLevel.HIGH, "1~Q should be 1")
    }

    @Test
    fun test7476_LatchState_J0_K0() {
        val u1 = circuit.placeChip("7476", trench = 2, startColumn = 10)
        wire7476Power(u1)
        // Preset to 1
        circuit.wirePin(u1, 2, 1935); circuit.wirePin(u1, 3, 1934)
        circuit.step()
        circuit.wirePin(u1, 2, 1934)

        // J=0, K=0: Latch / No Change
        circuit.wirePin(u1, 4, 1935)
        circuit.wirePin(u1, 16, 1935)
        circuit.wirePin(u1, 1, 1934); circuit.step()
        circuit.wirePin(u1, 1, 1935); circuit.step()

        assertPinLevel(circuit, u1, 15, ElectricalLevel.HIGH, "1Q should remain latched at 1")
    }

    @Test
    fun test7476_ToggleState_J1_K1() {
        val u1 = circuit.placeChip("7476", trench = 2, startColumn = 10)
        wire7476Power(u1)
        circuit.wirePin(u1, 2, 1934); circuit.wirePin(u1, 3, 1934)

        // Clear to 0
        circuit.wirePin(u1, 3, 1935); circuit.step()
        circuit.wirePin(u1, 3, 1934); circuit.step()
        assertPinLevel(circuit, u1, 15, ElectricalLevel.LOW)

        // J=1, K=1 (Toggle mode)
        circuit.wirePin(u1, 4, 1934)
        circuit.wirePin(u1, 16, 1934)

        // First falling clock edge -> Toggle to 1
        circuit.wirePin(u1, 1, 1934); circuit.step()
        circuit.wirePin(u1, 1, 1935); circuit.step()
        assertPinLevel(circuit, u1, 15, ElectricalLevel.HIGH, "1Q should toggle from 0 to 1")

        // Second falling clock edge -> Toggle back to 0
        circuit.wirePin(u1, 1, 1934); circuit.step()
        circuit.wirePin(u1, 1, 1935); circuit.step()
        assertPinLevel(circuit, u1, 15, ElectricalLevel.LOW, "1Q should toggle from 1 to 0")
    }

    @Test
    fun test7476_SecondFlipFlopIndependent() {
        val u1 = circuit.placeChip("7476", trench = 2, startColumn = 10)
        wire7476Power(u1)
        // FF2: Pin 7 (~2PRE), Pin 8 (~2CLR), Pin 6 (2~CLK), Pin 9 (2J), Pin 12 (2K)
        circuit.wirePin(u1, 7, 1934); circuit.wirePin(u1, 8, 1934)
        circuit.wirePin(u1, 9, 1934); circuit.wirePin(u1, 12, 1935) // Set mode

        // Falling edge on 2~CLK
        circuit.wirePin(u1, 6, 1934); circuit.step()
        circuit.wirePin(u1, 6, 1935); circuit.step()

        assertPinLevel(circuit, u1, 11, ElectricalLevel.HIGH, "2Q should be Set (1)")
        assertPinLevel(circuit, u1, 10, ElectricalLevel.LOW, "2~Q should be 0")
    }

    // ==========================================
    // 7448: BCD-to-7-Segment Decoder (>= 5 tests)
    // ==========================================

    private fun wire7448Standard(chip: com.logiclabs.core.testing.harness.PlacedChip) {
        circuit.wirePin(chip, 16, 1934) // VCC
        circuit.wirePin(chip, 8, 1935)  // GND
        // Inactive control pins: ~LT=1 (Pin 3), ~BI=1 (Pin 4), ~RBI=1 (Pin 5)
        circuit.wirePin(chip, 3, 1934)
        circuit.wirePin(chip, 4, 1934)
        circuit.wirePin(chip, 5, 1934)
    }

    private fun set7448Bcd(chip: com.logiclabs.core.testing.harness.PlacedChip, value: Int) {
        fun toSock(bit: Boolean) = if (bit) 1934 else 1935
        // A=7, B=1, C=2, D=6
        circuit.wirePin(chip, 7, toSock((value and 1) != 0))
        circuit.wirePin(chip, 1, toSock((value and 2) != 0))
        circuit.wirePin(chip, 2, toSock((value and 4) != 0))
        circuit.wirePin(chip, 6, toSock((value and 8) != 0))
    }

    @Test
    fun test7448_DecodeZero() {
        val u1 = circuit.placeChip("7448", trench = 1, startColumn = 25)
        wire7448Standard(u1)
        set7448Bcd(u1, 0)
        circuit.step()

        // '0' in common cathode: a,b,c,d,e,f HIGH, g LOW
        assertPinLevel(circuit, u1, 13, ElectricalLevel.HIGH, "a should be 1")
        assertPinLevel(circuit, u1, 12, ElectricalLevel.HIGH, "b should be 1")
        assertPinLevel(circuit, u1, 11, ElectricalLevel.HIGH, "c should be 1")
        assertPinLevel(circuit, u1, 10, ElectricalLevel.HIGH, "d should be 1")
        assertPinLevel(circuit, u1, 9, ElectricalLevel.HIGH, "e should be 1")
        assertPinLevel(circuit, u1, 15, ElectricalLevel.HIGH, "f should be 1")
        assertPinLevel(circuit, u1, 14, ElectricalLevel.LOW, "g should be 0 for '0'")
    }

    @Test
    fun test7448_DecodeEight() {
        val u1 = circuit.placeChip("7448", trench = 1, startColumn = 25)
        wire7448Standard(u1)
        set7448Bcd(u1, 8)
        circuit.step()

        // '8': all segments a..g must be HIGH
        val segPins = listOf(13, 12, 11, 10, 9, 15, 14)
        for (pin in segPins) {
            assertPinLevel(circuit, u1, pin, ElectricalLevel.HIGH, "Segment Pin $pin should be 1 for '8'")
        }
    }

    @Test
    fun test7448_LampTestOverrides() {
        val u1 = circuit.placeChip("7448", trench = 1, startColumn = 25)
        wire7448Standard(u1)
        set7448Bcd(u1, 1) // normally only b,c are HIGH

        // Assert Lamp Test ~LT = 0 (Pin 3)
        circuit.wirePin(u1, 3, 1935)
        circuit.step()

        // All segments should illuminate for test
        val segPins = listOf(13, 12, 11, 10, 9, 15, 14)
        for (pin in segPins) {
            assertPinLevel(circuit, u1, pin, ElectricalLevel.HIGH, "Lamp Test must force Pin $pin to 1")
        }
    }

    @Test
    fun test7448_BlankingInputOverrides() {
        val u1 = circuit.placeChip("7448", trench = 1, startColumn = 25)
        wire7448Standard(u1)
        set7448Bcd(u1, 8)

        // Assert Blanking Input ~BI = 0 (Pin 4)
        circuit.wirePin(u1, 4, 1935)
        circuit.step()

        val segPins = listOf(13, 12, 11, 10, 9, 15, 14)
        for (pin in segPins) {
            assertPinLevel(circuit, u1, pin, ElectricalLevel.LOW, "Blanking Input must force Pin $pin to 0")
        }
    }

    @Test
    fun test7448_NonBcdPatterns_HexC() {
        val u1 = circuit.placeChip("7448", trench = 1, startColumn = 25)
        wire7448Standard(u1)
        // 12 (0x0C) -> font7448[12] = 0x46 (b, c, g are HIGH)
        set7448Bcd(u1, 12)
        circuit.step()

        assertPinLevel(circuit, u1, 13, ElectricalLevel.LOW, "a is 0")
        assertPinLevel(circuit, u1, 12, ElectricalLevel.HIGH, "b is 1")
        assertPinLevel(circuit, u1, 11, ElectricalLevel.HIGH, "c is 1")
        assertPinLevel(circuit, u1, 10, ElectricalLevel.LOW, "d is 0")
        assertPinLevel(circuit, u1, 9, ElectricalLevel.LOW, "e is 0")
        assertPinLevel(circuit, u1, 15, ElectricalLevel.LOW, "f is 0")
        assertPinLevel(circuit, u1, 14, ElectricalLevel.HIGH, "g is 1")
    }
}
