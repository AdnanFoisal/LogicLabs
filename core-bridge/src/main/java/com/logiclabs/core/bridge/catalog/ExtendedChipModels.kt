package com.logiclabs.core.bridge.catalog

import com.logiclabs.core.bridge.model.ElectricalLevel
import com.logiclabs.core.bridge.model.PinDefinition
import com.logiclabs.core.bridge.model.PinRole

// Gate arrays added after the twelve classic labs were sealed with HMAC baselines. They live
// here rather than in TTLChipCatalog.kt so that file's diff stays confined to its two new
// registry entries: every byte of behaviour it already had is pinned by those digests.

/**
 * 7411: Triple 3-Input AND Gate (14-Pin DIP)
 *
 * Same package layout as the 7410, outputs simply not inverted — which means gate 1 is the
 * awkward one: its inputs straddle the ends of the package (pins 1, 2, 13) and its output
 * comes back on pin 12, not on the pin 3 an unwary reader expects from the quad parts.
 */
class Chip7411 : AbstractChipModel("7411", "Triple 3-Input AND Gate", 14, 14, 7) {
    override val pins: Map<Int, PinDefinition> = mapOf(
        1 to PinDefinition(1, "1A", PinRole.INPUT),
        2 to PinDefinition(2, "1B", PinRole.INPUT),
        3 to PinDefinition(3, "2A", PinRole.INPUT),
        4 to PinDefinition(4, "2B", PinRole.INPUT),
        5 to PinDefinition(5, "2C", PinRole.INPUT),
        6 to PinDefinition(6, "2Y", PinRole.OUTPUT),
        7 to PinDefinition(7, "GND", PinRole.POWER_GND),
        8 to PinDefinition(8, "3Y", PinRole.OUTPUT),
        9 to PinDefinition(9, "3A", PinRole.INPUT),
        10 to PinDefinition(10, "3B", PinRole.INPUT),
        11 to PinDefinition(11, "3C", PinRole.INPUT),
        12 to PinDefinition(12, "1Y", PinRole.OUTPUT),
        13 to PinDefinition(13, "1C", PinRole.INPUT),
        14 to PinDefinition(14, "VCC", PinRole.POWER_VCC)
    )

    private val outputPins = listOf(12, 6, 8)

    override fun evaluate(
        pinLevels: Map<Int, ElectricalLevel>,
        previousLevels: Map<Int, ElectricalLevel>,
        internalState: Any?
    ): EvaluationResult {
        val (isPowered, isReverse) = checkPower(pinLevels)
        if (!isPowered) return unpoweredOutputs(outputPins, isReverse)

        fun and3(aPin: Int, bPin: Int, cPin: Int): ElectricalLevel {
            val a = isTtlHigh(aPin, pinLevels)
            val b = isTtlHigh(bPin, pinLevels)
            val c = isTtlHigh(cPin, pinLevels)
            return if (a && b && c) ElectricalLevel.HIGH else ElectricalLevel.LOW
        }

        return EvaluationResult(
            outputLevels = mapOf(
                12 to and3(1, 2, 13),
                6 to and3(3, 4, 5),
                8 to and3(9, 10, 11)
            )
        )
    }
}

/**
 * 74266: Quad 2-Input XNOR Gate (14-Pin DIP, Non-Standard Pinout)
 *
 * Do not carry the 7486 pin map over to this part. Gates 2 and 3 are mirrored, so the outputs
 * land on pins 3, 4, 10 and 11 instead of 3, 6, 8 and 11, and pins 5, 6, 8, 9 are all inputs.
 * Wiring a '266 as if it were an XOR drops a gate output straight onto a neighbour's input.
 *
 * The real 'LS266 has open-collector outputs: HIGH is a released output that an external
 * pull-up resistor takes to +5V. This engine has no weak-driver tier — a resistor under 100 ohms
 * is merged as a jumper and anything larger is inert — so a released output would simply read
 * floating and light no LED. The outputs are therefore modelled as ordinary totem-pole drivers.
 * The simplification is only visible to a circuit that wire-ANDs two '266 outputs onto one net,
 * which the courseware never does and which the contention detector would flag as a short.
 */
class Chip74266 : AbstractChipModel("74266", "Quad 2-Input XNOR Gate (Open Collector)", 14, 14, 7) {
    override val pins: Map<Int, PinDefinition> = mapOf(
        1 to PinDefinition(1, "1A", PinRole.INPUT),
        2 to PinDefinition(2, "1B", PinRole.INPUT),
        3 to PinDefinition(3, "1Y", PinRole.OUTPUT),
        4 to PinDefinition(4, "2Y", PinRole.OUTPUT),
        5 to PinDefinition(5, "2A", PinRole.INPUT),
        6 to PinDefinition(6, "2B", PinRole.INPUT),
        7 to PinDefinition(7, "GND", PinRole.POWER_GND),
        8 to PinDefinition(8, "3A", PinRole.INPUT),
        9 to PinDefinition(9, "3B", PinRole.INPUT),
        10 to PinDefinition(10, "3Y", PinRole.OUTPUT),
        11 to PinDefinition(11, "4Y", PinRole.OUTPUT),
        12 to PinDefinition(12, "4A", PinRole.INPUT),
        13 to PinDefinition(13, "4B", PinRole.INPUT),
        14 to PinDefinition(14, "VCC", PinRole.POWER_VCC)
    )

    private val outputPins = listOf(3, 4, 10, 11)

    override fun evaluate(
        pinLevels: Map<Int, ElectricalLevel>,
        previousLevels: Map<Int, ElectricalLevel>,
        internalState: Any?
    ): EvaluationResult {
        val (isPowered, isReverse) = checkPower(pinLevels)
        if (!isPowered) return unpoweredOutputs(outputPins, isReverse)

        fun xnor(aPin: Int, bPin: Int): ElectricalLevel {
            val a = isTtlHigh(aPin, pinLevels)
            val b = isTtlHigh(bPin, pinLevels)
            return if (a == b) ElectricalLevel.HIGH else ElectricalLevel.LOW
        }

        return EvaluationResult(
            outputLevels = mapOf(
                3 to xnor(1, 2),
                4 to xnor(5, 6),
                10 to xnor(8, 9),
                11 to xnor(12, 13)
            )
        )
    }
}
