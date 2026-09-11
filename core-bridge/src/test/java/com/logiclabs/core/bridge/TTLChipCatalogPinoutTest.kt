package com.logiclabs.core.bridge

import com.logiclabs.core.bridge.catalog.ChipModel
import com.logiclabs.core.bridge.catalog.TTLChipCatalog
import com.logiclabs.core.bridge.model.ElectricalLevel
import com.logiclabs.core.bridge.model.PinRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Datasheet truth for the TTL catalog.
 *
 * TTLChipCatalog is the single source of truth for the whole app: the bench's chip picker
 * iterates it, the IC reference tab renders from it, and every courseware preset places parts
 * through it. If a pin map drifts from the real 7400-series datasheets, all three surfaces
 * drift together and nothing else can catch it — so this test pins every part's package,
 * supply pins and full pin naming against the manufacturer datasheets, byte for byte.
 *
 * Pin names are the app's own convention (`1A`, `1Y`, `1~CLR`, `NC`, ...); the pin NUMBER each
 * name sits on is the datasheet fact under test.
 */
class TTLChipCatalogPinoutTest {

    private data class Datasheet(
        val pinCount: Int,
        val vccPin: Int,
        val gndPin: Int,
        /** Pin names for pins 1..pinCount, index 0 = pin 1. */
        val pinNames: List<String>
    )

    private fun ds(pinCount: Int, vccPin: Int, gndPin: Int, vararg pinNames: String) =
        Datasheet(pinCount, vccPin, gndPin, pinNames.toList())

    private val datasheets: Map<String, Datasheet> = mapOf(
        // SN7400 quad 2-input NAND — the canonical quad-gate layout.
        "7400" to ds(
            14, 14, 7,
            "1A", "1B", "1Y", "2A", "2B", "2Y", "GND", "3Y", "3A", "3B", "4Y", "4A", "4B", "VCC"
        ),
        // SN7402 quad 2-input NOR — the inverted pinout: outputs on 1, 4, 10, 13.
        "7402" to ds(
            14, 14, 7,
            "1Y", "1A", "1B", "2Y", "2A", "2B", "GND", "3A", "3B", "3Y", "4A", "4B", "4Y", "VCC"
        ),
        // SN7404 hex inverter — six A/Y pairs.
        "7404" to ds(
            14, 14, 7,
            "1A", "1Y", "2A", "2Y", "3A", "3Y", "GND", "4Y", "4A", "5Y", "5A", "6Y", "6A", "VCC"
        ),
        // SN7408 quad 2-input AND — same layout as the 7400.
        "7408" to ds(
            14, 14, 7,
            "1A", "1B", "1Y", "2A", "2B", "2Y", "GND", "3Y", "3A", "3B", "4Y", "4A", "4B", "VCC"
        ),
        // SN7410 triple 3-input NAND — gate 1 wraps the package: 1,2,13 in, 12 out.
        "7410" to ds(
            14, 14, 7,
            "1A", "1B", "2A", "2B", "2C", "2Y", "GND", "3Y", "3A", "3B", "3C", "1Y", "1C", "VCC"
        ),
        // SN7411 triple 3-input AND — identical pinout to the 7410, outputs not inverted.
        "7411" to ds(
            14, 14, 7,
            "1A", "1B", "2A", "2B", "2C", "2Y", "GND", "3Y", "3A", "3B", "3C", "1Y", "1C", "VCC"
        ),
        // SN7420 dual 4-input NAND — pins 3 and 11 are internally not connected.
        "7420" to ds(
            14, 14, 7,
            "1A", "1B", "NC", "1C", "1D", "1Y", "GND", "2Y", "2A", "2B", "NC", "2C", "2D", "VCC"
        ),
        // SN7432 quad 2-input OR — same layout as the 7400.
        "7432" to ds(
            14, 14, 7,
            "1A", "1B", "1Y", "2A", "2B", "2Y", "GND", "3Y", "3A", "3B", "4Y", "4A", "4B", "VCC"
        ),
        // SN7486 quad 2-input XOR — same layout as the 7400.
        "7486" to ds(
            14, 14, 7,
            "1A", "1B", "1Y", "2A", "2B", "2Y", "GND", "3Y", "3A", "3B", "4Y", "4A", "4B", "VCC"
        ),
        // SN74LS266 quad 2-input XNOR — gates 2/3 mirrored: outputs on 3, 4, 10, 11.
        "74266" to ds(
            14, 14, 7,
            "1A", "1B", "1Y", "2Y", "2A", "2B", "GND", "3A", "3B", "3Y", "4Y", "4A", "4B", "VCC"
        ),
        // SN7483 4-bit binary full adder — 16 pins, supply mid-package on 5 and 12.
        "7483" to ds(
            16, 5, 12,
            "A4", "S3", "A3", "B3", "VCC", "S2", "B2", "A2", "S1", "A1", "B1", "GND", "C0", "C4", "S4", "B4"
        ),
        // SN7474 dual D flip-flop — active-low preset/clear, positive-edge clock.
        "7474" to ds(
            14, 14, 7,
            "1~CLR", "1D", "1CLK", "1~PRE", "1Q", "1~Q", "GND", "2~Q", "2Q", "2~PRE", "2CLK", "2D", "2~CLR", "VCC"
        ),
        // SN7476 dual J-K flip-flop — 16 pins, supply mid-package on 5 and 13.
        "7476" to ds(
            16, 5, 13,
            "1~CLK", "1~PRE", "1~CLR", "1J", "VCC", "2~CLK", "2~PRE", "2~CLR", "2J", "2~Q", "2Q", "2K", "GND", "1~Q", "1Q", "1K"
        ),
        // SN7448 BCD-to-seven-segment decoder — active-high segments for common cathode.
        "7448" to ds(
            16, 16, 8,
            "B", "C", "~LT", "~BI", "~RBI", "D", "A", "GND", "e", "d", "c", "b", "a", "g", "f", "VCC"
        )
    )

    @Test
    fun catalogOffersExactlyTheFourteenBenchParts() {
        assertEquals(
            "The bench picker, the IC reference tab and the coursework must all show these parts",
            setOf(
                "7400", "7402", "7404", "7408", "7410", "7411", "7420", "7432",
                "7486", "74266", "7483", "7474", "7476", "7448"
            ),
            TTLChipCatalog.getAllPartNumbers().toSet()
        )
    }

    @Test
    fun everyPinoutMatchesItsDatasheet() {
        for ((part, sheet) in datasheets) {
            val model = TTLChipCatalog.create(part)
            assertNotNull("$part is not in the catalog", model)

            assertEquals("$part package size", sheet.pinCount, model!!.pinCount)
            assertEquals("$part VCC pin", sheet.vccPin, model.vccPin)
            assertEquals("$part GND pin", sheet.gndPin, model.gndPin)
            assertEquals("$part must define exactly pins 1..${sheet.pinCount}", sheet.pinCount, model.pins.size)

            for (pin in 1..sheet.pinCount) {
                assertEquals(
                    "$part pin $pin function",
                    sheet.pinNames[pin - 1],
                    model.pins[pin]?.name
                )
            }
        }
    }

    @Test
    fun pinRolesMatchTheirSignalNames() {
        for (part in TTLChipCatalog.getAllPartNumbers()) {
            val model = TTLChipCatalog.create(part)!!

            assertEquals("$part VCC role", PinRole.POWER_VCC, model.pins[model.vccPin]?.role)
            assertEquals("$part GND role", PinRole.POWER_GND, model.pins[model.gndPin]?.role)

            for ((pin, def) in model.pins) {
                when {
                    def.name == "NC" ->
                        assertEquals("$part pin $pin is silk-screened NC and must be NO_CONNECT", PinRole.NO_CONNECT, def.role)
                    // Every gate output (1Y, S2, 2Q, segment f...) drives a net.
                    def.name.endsWith("Y") || def.name.endsWith("Q") || def.name.startsWith("S") ||
                        def.name == "C4" || (def.name.length == 1 && def.name[0].isLowerCase()) ->
                        assertEquals("$part pin $pin (${def.name}) is an output and must be OUTPUT", PinRole.OUTPUT, def.role)
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Functional spot checks — the pin map above only means something if the evaluation
    // agrees with it, so these drive the documented pins and read the documented outputs.
    // ---------------------------------------------------------------------------------------

    private fun drive(model: ChipModel, highs: Set<Int>, lows: Set<Int>): Map<Int, ElectricalLevel> =
        buildMap {
            put(model.vccPin, ElectricalLevel.HIGH)
            put(model.gndPin, ElectricalLevel.LOW)
            for (p in highs) put(p, ElectricalLevel.HIGH)
            for (p in lows) put(p, ElectricalLevel.LOW)
        }

    private fun level(b: Boolean) = if (b) ElectricalLevel.HIGH else ElectricalLevel.LOW

    @Test
    fun xnor74266OutputIsTheXorComplement() {
        val model = TTLChipCatalog.create("74266")!!
        // Gate 1: inputs on pins 1 and 2, output on pin 3 — the 74266's one un-mirrored gate.
        for (a in listOf(false, true)) {
            for (b in listOf(false, true)) {
                val levels = drive(model, highs = setOfNotNull(1.takeIf { a }, 2.takeIf { b }), lows = setOfNotNull(1.takeIf { !a }, 2.takeIf { !b }))
                val out = model.evaluate(levels).outputLevels[3]
                assertEquals("74266 XNOR($a, $b) on pin 3", level(a == b), out)
            }
        }
    }

    @Test
    fun nand7420NeedsAllFourInputsAndIgnoresItsNcPins() {
        val model = TTLChipCatalog.create("7420")!!
        val inputs = listOf(1, 2, 4, 5) // 1A, 1B, 1C, 1D — pin 3 is NC.

        // All four HIGH -> output LOW on pin 6.
        val allHigh = drive(model, inputs.toSet(), emptySet())
        assertEquals(ElectricalLevel.LOW, model.evaluate(allHigh).outputLevels[6])

        // Any single input LOW -> output HIGH on pin 6.
        for (lowPin in inputs) {
            val levels = drive(model, inputs.toSet() - lowPin, setOf(lowPin))
            assertEquals("7420 with pin $lowPin LOW", ElectricalLevel.HIGH, model.evaluate(levels).outputLevels[6])
        }

        // The NC pins (3 and 11) must not participate: driving them changes nothing.
        val withNcDriven = drive(model, inputs.toSet() + setOf(3, 11), emptySet())
        assertEquals(ElectricalLevel.LOW, model.evaluate(withNcDriven).outputLevels[6])
    }

    @Test
    fun decoder7448RendersTheDecimalDigits() {
        val model = TTLChipCatalog.create("7448")!!
        // BCD inputs: A (LSB) on 7, B on 1, C on 2, D (MSB) on 6. Controls ~LT/~BI/~RBI on
        // 3/4/5, inactive HIGH. Segments a..g on 13, 12, 11, 10, 9, 15, 14.
        val segPins = listOf(13, 12, 11, 10, 9, 15, 14)
        val digitFont = listOf(
            listOf(true, true, true, true, true, true, false),  // 0
            listOf(false, true, true, false, false, false, false), // 1
            listOf(true, true, false, true, true, false, true),  // 2
            listOf(true, true, true, true, false, false, true),  // 3
            listOf(false, true, true, false, false, true, true), // 4
            listOf(true, false, true, true, false, true, true),  // 5
            listOf(true, false, true, true, true, true, true),   // 6
            listOf(true, true, true, false, false, false, false),// 7
            listOf(true, true, true, true, true, true, true),    // 8
            listOf(true, true, true, true, false, true, true)    // 9
        )

        for (digit in 0..9) {
            val highs = buildSet {
                addAll(listOf(3, 4, 5)) // controls inactive
                if (digit and 1 != 0) add(7) // A
                if (digit and 2 != 0) add(1) // B
                if (digit and 4 != 0) add(2) // C
                if (digit and 8 != 0) add(6) // D
            }
            val lows = (setOf(7, 1, 2, 6) - highs)
            val out = model.evaluate(drive(model, highs, lows)).outputLevels
            val rendered = segPins.map { out[it] == ElectricalLevel.HIGH }
            assertEquals("7448 rendering of digit $digit", digitFont[digit], rendered)
        }

        // Lamp test: ~LT (pin 3) pulled LOW lights every segment regardless of the BCD word.
        val lampTest = drive(model, highs = setOf(4, 5), lows = setOf(3))
        val out = model.evaluate(lampTest).outputLevels
        assertTrue("7448 lamp test must light all segments", segPins.all { out[it] == ElectricalLevel.HIGH })
    }

    @Test
    fun adder7483SumsFourBitWordsWithCarry() {
        val model = TTLChipCatalog.create("7483")!!
        fun bitPins(values: IntArray, pins: List<Int>): Pair<Set<Int>, Set<Int>> {
            val highs = pins.filterIndexed { i, _ -> values[i] == 1 }.toSet()
            return highs to pins.toSet() - highs
        }

        // 1001 (9) + 0111 (7) + 1 = 10001 (17): S1 on, S2..S4 off, C4 set.
        val (aHigh, aLow) = bitPins(intArrayOf(1, 0, 0, 1), listOf(10, 8, 3, 1)) // A1..A4
        val (bHigh, bLow) = bitPins(intArrayOf(1, 1, 1, 0), listOf(11, 7, 4, 16)) // B1..B4
        val levels = drive(
            model,
            highs = aHigh + bHigh + setOf(13), // C0 = 1
            lows = aLow + bLow
        )
        val out = model.evaluate(levels).outputLevels
        assertEquals(ElectricalLevel.HIGH, out[9])   // S1
        assertEquals(ElectricalLevel.LOW, out[6])    // S2
        assertEquals(ElectricalLevel.LOW, out[2])    // S3
        assertEquals(ElectricalLevel.LOW, out[15])   // S4
        assertEquals(ElectricalLevel.HIGH, out[14])  // C4

        // 0111 (7) + 0111 (7) + 0 = 1110 (14): S2..S4 on, S1 off, C4 clear.
        val (aHigh2, aLow2) = bitPins(intArrayOf(1, 1, 1, 0), listOf(10, 8, 3, 1))
        val (bHigh2, bLow2) = bitPins(intArrayOf(1, 1, 1, 0), listOf(11, 7, 4, 16))
        val levels2 = drive(model, highs = aHigh2 + bHigh2, lows = aLow2 + bLow2 + setOf(13))
        val out2 = model.evaluate(levels2).outputLevels
        assertEquals(ElectricalLevel.LOW, out2[9])   // S1
        assertEquals(ElectricalLevel.HIGH, out2[6])  // S2
        assertEquals(ElectricalLevel.HIGH, out2[2])  // S3
        assertEquals(ElectricalLevel.HIGH, out2[15]) // S4
        assertEquals(ElectricalLevel.LOW, out2[14])  // C4
    }
}
