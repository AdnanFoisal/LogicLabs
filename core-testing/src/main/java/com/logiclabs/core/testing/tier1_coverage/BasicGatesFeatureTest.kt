package com.logiclabs.core.testing.tier1_coverage

import com.logiclabs.core.testing.harness.ElectricalAssertions.assertPinLevel
import com.logiclabs.core.testing.harness.SimulationCircuit
import com.logiclabs.core.testing.model.ElectricalLevel
import org.junit.Before
import org.junit.Test

/**
 * Tier 1 Feature Coverage: 74xx Basic Logic Gates.
 * Verifies truth tables and pinout mappings for:
 * 7400 (Quad NAND), 7402 (Quad NOR), 7404 (Hex NOT), 7408 (Quad AND),
 * 7410 (Triple 3-NAND), 7420 (Dual 4-NAND), 7432 (Quad OR), 7486 (Quad XOR).
 * Requirement: >= 5 distinct tests per feature.
 */
class BasicGatesFeatureTest {

    private lateinit var circuit: SimulationCircuit

    @Before
    fun setUp() {
        circuit = SimulationCircuit()
    }

    // ==========================================
    // 7400: Quad 2-Input NAND Gate (>= 5 tests)
    // ==========================================

    @Test
    fun test7400_Gate1_TruthTable_00() {
        val u1 = circuit.placeChip("7400", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)
        // 00 -> 1
        circuit.wirePin(u1, 1, circuit.dsu.find(circuit.dsu.find(1935))) // GND
        circuit.wirePin(u1, 2, circuit.dsu.find(1935)) // GND
        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.HIGH, "NAND(0,0) must be 1")
    }

    @Test
    fun test7400_Gate1_TruthTable_01() {
        val u1 = circuit.placeChip("7400", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)
        // 01 -> 1
        circuit.wirePin(u1, 1, 1935) // GND
        circuit.wirePin(u1, 2, 1934) // VCC
        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.HIGH, "NAND(0,1) must be 1")
    }

    @Test
    fun test7400_Gate1_TruthTable_10() {
        val u1 = circuit.placeChip("7400", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)
        // 10 -> 1
        circuit.wirePin(u1, 1, 1934) // VCC
        circuit.wirePin(u1, 2, 1935) // GND
        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.HIGH, "NAND(1,0) must be 1")
    }

    @Test
    fun test7400_Gate1_TruthTable_11() {
        val u1 = circuit.placeChip("7400", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)
        // 11 -> 0
        circuit.wirePin(u1, 1, 1934) // VCC
        circuit.wirePin(u1, 2, 1934) // VCC
        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.LOW, "NAND(1,1) must be 0")
    }

    @Test
    fun test7400_AllFourGatesSimultaneous() {
        val u1 = circuit.placeChip("7400", trench = 1, startColumn = 10)
        circuit.wireStandardPower(u1)
        // Gate 1: 1A=1, 1B=1 -> 1Y=0 (pins 1,2 -> 3)
        circuit.wirePin(u1, 1, 1934); circuit.wirePin(u1, 2, 1934)
        // Gate 2: 2A=0, 2B=1 -> 2Y=1 (pins 4,5 -> 6)
        circuit.wirePin(u1, 4, 1935); circuit.wirePin(u1, 5, 1934)
        // Gate 3: 3A=1, 3B=0 -> 3Y=1 (pins 9,10 -> 8)
        circuit.wirePin(u1, 9, 1934); circuit.wirePin(u1, 10, 1935)
        // Gate 4: 4A=0, 4B=0 -> 4Y=1 (pins 12,13 -> 11)
        circuit.wirePin(u1, 12, 1935); circuit.wirePin(u1, 13, 1935)

        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.LOW)
        assertPinLevel(circuit, u1, 6, ElectricalLevel.HIGH)
        assertPinLevel(circuit, u1, 8, ElectricalLevel.HIGH)
        assertPinLevel(circuit, u1, 11, ElectricalLevel.HIGH)
    }

    // ==========================================
    // 7402: Quad 2-Input NOR Gate (>= 5 tests, Inverted Pinout: Out=1, In=2,3)
    // ==========================================

    @Test
    fun test7402_Gate1_TruthTable_00() {
        val u1 = circuit.placeChip("7402", trench = 1, startColumn = 20)
        circuit.wireStandardPower(u1)
        // Inverted pinout: 1Y=Pin 1, 1A=Pin 2, 1B=Pin 3
        circuit.wirePin(u1, 2, 1935) // 0
        circuit.wirePin(u1, 3, 1935) // 0
        circuit.step()
        assertPinLevel(circuit, u1, 1, ElectricalLevel.HIGH, "NOR(0,0) must be 1")
    }

    @Test
    fun test7402_Gate1_TruthTable_01() {
        val u1 = circuit.placeChip("7402", trench = 1, startColumn = 20)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 2, 1935) // 0
        circuit.wirePin(u1, 3, 1934) // 1
        circuit.step()
        assertPinLevel(circuit, u1, 1, ElectricalLevel.LOW, "NOR(0,1) must be 0")
    }

    @Test
    fun test7402_Gate1_TruthTable_10() {
        val u1 = circuit.placeChip("7402", trench = 1, startColumn = 20)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 2, 1934) // 1
        circuit.wirePin(u1, 3, 1935) // 0
        circuit.step()
        assertPinLevel(circuit, u1, 1, ElectricalLevel.LOW, "NOR(1,0) must be 0")
    }

    @Test
    fun test7402_Gate1_TruthTable_11() {
        val u1 = circuit.placeChip("7402", trench = 1, startColumn = 20)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 2, 1934) // 1
        circuit.wirePin(u1, 3, 1934) // 1
        circuit.step()
        assertPinLevel(circuit, u1, 1, ElectricalLevel.LOW, "NOR(1,1) must be 0")
    }

    @Test
    fun test7402_AllFourGatesInvertedPinout() {
        val u1 = circuit.placeChip("7402", trench = 1, startColumn = 20)
        circuit.wireStandardPower(u1)
        // Gate 1: Pin 1 Out, Pins 2,3 In (0,0 -> 1)
        circuit.wirePin(u1, 2, 1935); circuit.wirePin(u1, 3, 1935)
        // Gate 2: Pin 4 Out, Pins 5,6 In (1,0 -> 0)
        circuit.wirePin(u1, 5, 1934); circuit.wirePin(u1, 6, 1935)
        // Gate 3: Pin 10 Out, Pins 8,9 In (0,1 -> 0)
        circuit.wirePin(u1, 8, 1935); circuit.wirePin(u1, 9, 1934)
        // Gate 4: Pin 13 Out, Pins 11,12 In (0,0 -> 1)
        circuit.wirePin(u1, 11, 1935); circuit.wirePin(u1, 12, 1935)

        circuit.step()
        assertPinLevel(circuit, u1, 1, ElectricalLevel.HIGH)
        assertPinLevel(circuit, u1, 4, ElectricalLevel.LOW)
        assertPinLevel(circuit, u1, 10, ElectricalLevel.LOW)
        assertPinLevel(circuit, u1, 13, ElectricalLevel.HIGH)
    }

    // ==========================================
    // 7404: Hex Inverter (>= 5 tests)
    // ==========================================

    @Test
    fun test7404_Gate1_Inversion() {
        val u1 = circuit.placeChip("7404", trench = 1, startColumn = 5)
        circuit.wireStandardPower(u1)
        // 0 -> 1
        circuit.wirePin(u1, 1, 1935)
        circuit.step()
        assertPinLevel(circuit, u1, 2, ElectricalLevel.HIGH)
    }

    @Test
    fun test7404_Gate2_Inversion() {
        val u1 = circuit.placeChip("7404", trench = 1, startColumn = 5)
        circuit.wireStandardPower(u1)
        // 1 -> 0
        circuit.wirePin(u1, 3, 1934)
        circuit.step()
        assertPinLevel(circuit, u1, 4, ElectricalLevel.LOW)
    }

    @Test
    fun test7404_DoubleInversionBuffer() {
        val u1 = circuit.placeChip("7404", trench = 1, startColumn = 5)
        circuit.wireStandardPower(u1)
        // Inverter 1 (1->2) feeds Inverter 2 (3->4)
        circuit.wirePin(u1, 1, 1935) // In=0
        circuit.addWire(u1.getPinSocket(2), u1.getPinSocket(3)) // 2Y -> 2A
        circuit.step()
        assertPinLevel(circuit, u1, 2, ElectricalLevel.HIGH)
        assertPinLevel(circuit, u1, 4, ElectricalLevel.LOW)
    }

    @Test
    fun test7404_AllSixInvertersSimultaneous() {
        val u1 = circuit.placeChip("7404", trench = 1, startColumn = 5)
        circuit.wireStandardPower(u1)
        // 1A(0)->1Y(1), 2A(1)->2Y(0), 3A(0)->3Y(1), 4A(1)->4Y(0), 5A(0)->5Y(1), 6A(1)->6Y(0)
        circuit.wirePin(u1, 1, 1935)
        circuit.wirePin(u1, 3, 1934)
        circuit.wirePin(u1, 5, 1935)
        circuit.wirePin(u1, 9, 1934)
        circuit.wirePin(u1, 11, 1935)
        circuit.wirePin(u1, 13, 1934)
        circuit.step()

        assertPinLevel(circuit, u1, 2, ElectricalLevel.HIGH)
        assertPinLevel(circuit, u1, 4, ElectricalLevel.LOW)
        assertPinLevel(circuit, u1, 6, ElectricalLevel.HIGH)
        assertPinLevel(circuit, u1, 8, ElectricalLevel.LOW)
        assertPinLevel(circuit, u1, 10, ElectricalLevel.HIGH)
        assertPinLevel(circuit, u1, 12, ElectricalLevel.LOW)
    }

    @Test
    fun test7404_TripleCascadeInversion() {
        val u1 = circuit.placeChip("7404", trench = 1, startColumn = 5)
        circuit.wireStandardPower(u1)
        // 1->2 -> 3->4 -> 5->6: Three inverters in series
        circuit.wirePin(u1, 1, 1934) // In=1
        circuit.addWire(u1.getPinSocket(2), u1.getPinSocket(3))
        circuit.addWire(u1.getPinSocket(4), u1.getPinSocket(5))
        circuit.step()
        assertPinLevel(circuit, u1, 2, ElectricalLevel.LOW)
        assertPinLevel(circuit, u1, 4, ElectricalLevel.HIGH)
        assertPinLevel(circuit, u1, 6, ElectricalLevel.LOW)
    }

    // ==========================================
    // 7408: Quad 2-Input AND Gate (>= 5 tests)
    // ==========================================

    @Test
    fun test7408_Gate1_TruthTable_00() {
        val u1 = circuit.placeChip("7408", trench = 1, startColumn = 35)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1935); circuit.wirePin(u1, 2, 1935)
        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.LOW, "AND(0,0) must be 0")
    }

    @Test
    fun test7408_Gate1_TruthTable_01() {
        val u1 = circuit.placeChip("7408", trench = 1, startColumn = 35)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1935); circuit.wirePin(u1, 2, 1934)
        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.LOW, "AND(0,1) must be 0")
    }

    @Test
    fun test7408_Gate1_TruthTable_10() {
        val u1 = circuit.placeChip("7408", trench = 1, startColumn = 35)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1934); circuit.wirePin(u1, 2, 1935)
        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.LOW, "AND(1,0) must be 0")
    }

    @Test
    fun test7408_Gate1_TruthTable_11() {
        val u1 = circuit.placeChip("7408", trench = 1, startColumn = 35)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1934); circuit.wirePin(u1, 2, 1934)
        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.HIGH, "AND(1,1) must be 1")
    }

    @Test
    fun test7408_AllFourGatesSimultaneous() {
        val u1 = circuit.placeChip("7408", trench = 1, startColumn = 35)
        circuit.wireStandardPower(u1)
        // Gate 1: 00 -> 0
        circuit.wirePin(u1, 1, 1935); circuit.wirePin(u1, 2, 1935)
        // Gate 2: 01 -> 0
        circuit.wirePin(u1, 4, 1935); circuit.wirePin(u1, 5, 1934)
        // Gate 3: 10 -> 0
        circuit.wirePin(u1, 9, 1934); circuit.wirePin(u1, 10, 1935)
        // Gate 4: 11 -> 1
        circuit.wirePin(u1, 12, 1934); circuit.wirePin(u1, 13, 1934)

        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.LOW)
        assertPinLevel(circuit, u1, 6, ElectricalLevel.LOW)
        assertPinLevel(circuit, u1, 8, ElectricalLevel.LOW)
        assertPinLevel(circuit, u1, 11, ElectricalLevel.HIGH)
    }

    // ==========================================
    // 7410: Triple 3-Input NAND Gate (>= 5 tests)
    // ==========================================

    @Test
    fun test7410_Gate1_AllZero() {
        val u1 = circuit.placeChip("7410", trench = 2, startColumn = 10)
        circuit.wireStandardPower(u1)
        // Gate 1: 1A=1, 1B=2, 1C=13 -> 1Y=12
        circuit.wirePin(u1, 1, 1935); circuit.wirePin(u1, 2, 1935); circuit.wirePin(u1, 13, 1935)
        circuit.step()
        assertPinLevel(circuit, u1, 12, ElectricalLevel.HIGH)
    }

    @Test
    fun test7410_Gate1_TwoOnesOneZero() {
        val u1 = circuit.placeChip("7410", trench = 2, startColumn = 10)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1934); circuit.wirePin(u1, 2, 1934); circuit.wirePin(u1, 13, 1935)
        circuit.step()
        assertPinLevel(circuit, u1, 12, ElectricalLevel.HIGH)
    }

    @Test
    fun test7410_Gate1_AllOnes() {
        val u1 = circuit.placeChip("7410", trench = 2, startColumn = 10)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1934); circuit.wirePin(u1, 2, 1934); circuit.wirePin(u1, 13, 1934)
        circuit.step()
        assertPinLevel(circuit, u1, 12, ElectricalLevel.LOW)
    }

    @Test
    fun test7410_Gate2_TruthVerification() {
        val u1 = circuit.placeChip("7410", trench = 2, startColumn = 10)
        circuit.wireStandardPower(u1)
        // Gate 2: 2A=3, 2B=4, 2C=5 -> 2Y=6
        circuit.wirePin(u1, 3, 1934); circuit.wirePin(u1, 4, 1934); circuit.wirePin(u1, 5, 1934)
        circuit.step()
        assertPinLevel(circuit, u1, 6, ElectricalLevel.LOW)
    }

    @Test
    fun test7410_Gate3_TruthVerification() {
        val u1 = circuit.placeChip("7410", trench = 2, startColumn = 10)
        circuit.wireStandardPower(u1)
        // Gate 3: 3A=9, 3B=10, 3C=11 -> 3Y=8
        circuit.wirePin(u1, 9, 1934); circuit.wirePin(u1, 10, 1935); circuit.wirePin(u1, 11, 1934)
        circuit.step()
        assertPinLevel(circuit, u1, 8, ElectricalLevel.HIGH)
    }

    // ==========================================
    // 7420: Dual 4-Input NAND Gate (>= 5 tests)
    // ==========================================

    @Test
    fun test7420_Gate1_AllOnes() {
        val u1 = circuit.placeChip("7420", trench = 2, startColumn = 25)
        circuit.wireStandardPower(u1)
        // Gate 1: 1A=1, 1B=2, 1C=4, 1D=5 -> 1Y=6 (Pin 3 is NC)
        circuit.wirePin(u1, 1, 1934); circuit.wirePin(u1, 2, 1934); circuit.wirePin(u1, 4, 1934); circuit.wirePin(u1, 5, 1934)
        circuit.step()
        assertPinLevel(circuit, u1, 6, ElectricalLevel.LOW)
    }

    @Test
    fun test7420_Gate1_SingleZeroInput() {
        val u1 = circuit.placeChip("7420", trench = 2, startColumn = 25)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1934); circuit.wirePin(u1, 2, 1934); circuit.wirePin(u1, 4, 1935); circuit.wirePin(u1, 5, 1934)
        circuit.step()
        assertPinLevel(circuit, u1, 6, ElectricalLevel.HIGH)
    }

    @Test
    fun test7420_Gate2_AllOnes() {
        val u1 = circuit.placeChip("7420", trench = 2, startColumn = 25)
        circuit.wireStandardPower(u1)
        // Gate 2: 2A=9, 2B=10, 2C=12, 2D=13 -> 2Y=8 (Pin 11 is NC)
        circuit.wirePin(u1, 9, 1934); circuit.wirePin(u1, 10, 1934); circuit.wirePin(u1, 12, 1934); circuit.wirePin(u1, 13, 1934)
        circuit.step()
        assertPinLevel(circuit, u1, 8, ElectricalLevel.LOW)
    }

    @Test
    fun test7420_Gate2_AllZeros() {
        val u1 = circuit.placeChip("7420", trench = 2, startColumn = 25)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 9, 1935); circuit.wirePin(u1, 10, 1935); circuit.wirePin(u1, 12, 1935); circuit.wirePin(u1, 13, 1935)
        circuit.step()
        assertPinLevel(circuit, u1, 8, ElectricalLevel.HIGH)
    }

    @Test
    fun test7420_NcPinsDoNotAffectLogic() {
        val u1 = circuit.placeChip("7420", trench = 2, startColumn = 25)
        circuit.wireStandardPower(u1)
        // Wire Pin 3 (NC) to VCC and Pin 11 (NC) to GND - should have zero impact on 1Y and 2Y
        circuit.wirePin(u1, 3, 1934)
        circuit.wirePin(u1, 11, 1935)
        circuit.wirePin(u1, 1, 1934); circuit.wirePin(u1, 2, 1934); circuit.wirePin(u1, 4, 1934); circuit.wirePin(u1, 5, 1934)
        circuit.step()
        assertPinLevel(circuit, u1, 6, ElectricalLevel.LOW)
    }

    // ==========================================
    // 7432: Quad 2-Input OR Gate (>= 5 tests)
    // ==========================================

    @Test
    fun test7432_Gate1_TruthTable_00() {
        val u1 = circuit.placeChip("7432", trench = 1, startColumn = 48)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1935); circuit.wirePin(u1, 2, 1935)
        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.LOW)
    }

    @Test
    fun test7432_Gate1_TruthTable_01() {
        val u1 = circuit.placeChip("7432", trench = 1, startColumn = 48)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1935); circuit.wirePin(u1, 2, 1934)
        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.HIGH)
    }

    @Test
    fun test7432_Gate1_TruthTable_10() {
        val u1 = circuit.placeChip("7432", trench = 1, startColumn = 48)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1934); circuit.wirePin(u1, 2, 1935)
        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.HIGH)
    }

    @Test
    fun test7432_Gate1_TruthTable_11() {
        val u1 = circuit.placeChip("7432", trench = 1, startColumn = 48)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1934); circuit.wirePin(u1, 2, 1934)
        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.HIGH)
    }

    @Test
    fun test7432_AllFourGatesSimultaneous() {
        val u1 = circuit.placeChip("7432", trench = 1, startColumn = 48)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1935); circuit.wirePin(u1, 2, 1935) // 00 -> 0
        circuit.wirePin(u1, 4, 1935); circuit.wirePin(u1, 5, 1934) // 01 -> 1
        circuit.wirePin(u1, 9, 1934); circuit.wirePin(u1, 10, 1935) // 10 -> 1
        circuit.wirePin(u1, 12, 1934); circuit.wirePin(u1, 13, 1934) // 11 -> 1

        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.LOW)
        assertPinLevel(circuit, u1, 6, ElectricalLevel.HIGH)
        assertPinLevel(circuit, u1, 8, ElectricalLevel.HIGH)
        assertPinLevel(circuit, u1, 11, ElectricalLevel.HIGH)
    }

    // ==========================================
    // 7486: Quad 2-Input XOR Gate (>= 5 tests)
    // ==========================================

    @Test
    fun test7486_Gate1_TruthTable_00() {
        val u1 = circuit.placeChip("7486", trench = 2, startColumn = 45)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1935); circuit.wirePin(u1, 2, 1935)
        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.LOW)
    }

    @Test
    fun test7486_Gate1_TruthTable_01() {
        val u1 = circuit.placeChip("7486", trench = 2, startColumn = 45)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1935); circuit.wirePin(u1, 2, 1934)
        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.HIGH)
    }

    @Test
    fun test7486_Gate1_TruthTable_10() {
        val u1 = circuit.placeChip("7486", trench = 2, startColumn = 45)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1934); circuit.wirePin(u1, 2, 1935)
        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.HIGH)
    }

    @Test
    fun test7486_Gate1_TruthTable_11() {
        val u1 = circuit.placeChip("7486", trench = 2, startColumn = 45)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1934); circuit.wirePin(u1, 2, 1934)
        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.LOW)
    }

    @Test
    fun test7486_AllFourGatesSimultaneous() {
        val u1 = circuit.placeChip("7486", trench = 2, startColumn = 45)
        circuit.wireStandardPower(u1)
        circuit.wirePin(u1, 1, 1935); circuit.wirePin(u1, 2, 1935) // 00 -> 0
        circuit.wirePin(u1, 4, 1935); circuit.wirePin(u1, 5, 1934) // 01 -> 1
        circuit.wirePin(u1, 9, 1934); circuit.wirePin(u1, 10, 1935) // 10 -> 1
        circuit.wirePin(u1, 12, 1934); circuit.wirePin(u1, 13, 1934) // 11 -> 0

        circuit.step()
        assertPinLevel(circuit, u1, 3, ElectricalLevel.LOW)
        assertPinLevel(circuit, u1, 6, ElectricalLevel.HIGH)
        assertPinLevel(circuit, u1, 8, ElectricalLevel.HIGH)
        assertPinLevel(circuit, u1, 11, ElectricalLevel.LOW)
    }
}
