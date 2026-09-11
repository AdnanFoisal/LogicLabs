package com.logiclabs.core.bridge.catalog

import com.logiclabs.core.bridge.model.ElectricalLevel
import com.logiclabs.core.bridge.model.PinDefinition
import com.logiclabs.core.bridge.model.PinRole

/**
 * Result of evaluating an IC's logic given current input pin levels.
 */
data class EvaluationResult(
    val outputLevels: Map<Int, ElectricalLevel>,
    val nextInternalState: Any? = null,
    val isPowered: Boolean = true,
    val isReversePolarity: Boolean = false,
    val hasThermalWarning: Boolean = false
)

/**
 * Base interface for IC models.
 */
interface ChipModel {
    val partNumber: String
    val description: String
    val pinCount: Int
    val vccPin: Int
    val gndPin: Int
    val pins: Map<Int, PinDefinition>

    fun initialInternalState(): Any? = null

    fun evaluate(
        pinLevels: Map<Int, ElectricalLevel>,
        previousLevels: Map<Int, ElectricalLevel> = emptyMap(),
        internalState: Any? = null
    ): EvaluationResult
}

/**
 * Base helper for standard power verification and TTL floating-input pull-up resolution.
 */
abstract class AbstractChipModel(
    override val partNumber: String,
    override val description: String,
    override val pinCount: Int,
    override val vccPin: Int,
    override val gndPin: Int
) : ChipModel {

    protected fun checkPower(pinLevels: Map<Int, ElectricalLevel>): Pair<Boolean, Boolean> {
        val vcc = pinLevels[vccPin] ?: ElectricalLevel.HIGH_Z
        val gnd = pinLevels[gndPin] ?: ElectricalLevel.HIGH_Z

        // Reverse polarity check
        if (vcc == ElectricalLevel.LOW && gnd == ElectricalLevel.HIGH) {
            return Pair(false, true)
        }

        // Properly powered when VCC is HIGH and GND is LOW
        val isPowered = (vcc == ElectricalLevel.HIGH && gnd == ElectricalLevel.LOW)
        return Pair(isPowered, false)
    }

    protected fun unpoweredOutputs(outputPins: List<Int>, reversePolarity: Boolean = false): EvaluationResult {
        return EvaluationResult(
            outputLevels = outputPins.associateWith { ElectricalLevel.HIGH_Z },
            isPowered = false,
            isReversePolarity = reversePolarity
        )
    }

    /**
     * In bipolar TTL, floating or unconnected input pins (HIGH_Z or null) pull up to internal HIGH.
     */
    protected fun isTtlHigh(pin: Int, levels: Map<Int, ElectricalLevel>): Boolean {
        val lvl = levels[pin]
        return lvl == null || lvl == ElectricalLevel.HIGH || lvl == ElectricalLevel.HIGH_Z
    }
}

/**
 * 7400: Quad 2-Input NAND Gate (14-Pin DIP)
 */
class Chip7400 : AbstractChipModel("7400", "Quad 2-Input NAND Gate", 14, 14, 7) {
    override val pins: Map<Int, PinDefinition> = mapOf(
        1 to PinDefinition(1, "1A", PinRole.INPUT),
        2 to PinDefinition(2, "1B", PinRole.INPUT),
        3 to PinDefinition(3, "1Y", PinRole.OUTPUT),
        4 to PinDefinition(4, "2A", PinRole.INPUT),
        5 to PinDefinition(5, "2B", PinRole.INPUT),
        6 to PinDefinition(6, "2Y", PinRole.OUTPUT),
        7 to PinDefinition(7, "GND", PinRole.POWER_GND),
        8 to PinDefinition(8, "3Y", PinRole.OUTPUT),
        9 to PinDefinition(9, "3A", PinRole.INPUT),
        10 to PinDefinition(10, "3B", PinRole.INPUT),
        11 to PinDefinition(11, "4Y", PinRole.OUTPUT),
        12 to PinDefinition(12, "4A", PinRole.INPUT),
        13 to PinDefinition(13, "4B", PinRole.INPUT),
        14 to PinDefinition(14, "VCC", PinRole.POWER_VCC)
    )

    private val outputPins = listOf(3, 6, 8, 11)

    override fun evaluate(
        pinLevels: Map<Int, ElectricalLevel>,
        previousLevels: Map<Int, ElectricalLevel>,
        internalState: Any?
    ): EvaluationResult {
        val (isPowered, isReverse) = checkPower(pinLevels)
        if (!isPowered) return unpoweredOutputs(outputPins, isReverse)

        fun nand(aPin: Int, bPin: Int): ElectricalLevel {
            val a = isTtlHigh(aPin, pinLevels)
            val b = isTtlHigh(bPin, pinLevels)
            return if (a && b) ElectricalLevel.LOW else ElectricalLevel.HIGH
        }

        return EvaluationResult(
            outputLevels = mapOf(
                3 to nand(1, 2),
                6 to nand(4, 5),
                8 to nand(9, 10),
                11 to nand(12, 13)
            )
        )
    }
}

/**
 * 7402: Quad 2-Input NOR Gate (14-Pin DIP, Inverted Pinout)
 */
class Chip7402 : AbstractChipModel("7402", "Quad 2-Input NOR Gate (Inverted Pinout)", 14, 14, 7) {
    override val pins: Map<Int, PinDefinition> = mapOf(
        1 to PinDefinition(1, "1Y", PinRole.OUTPUT),
        2 to PinDefinition(2, "1A", PinRole.INPUT),
        3 to PinDefinition(3, "1B", PinRole.INPUT),
        4 to PinDefinition(4, "2Y", PinRole.OUTPUT),
        5 to PinDefinition(5, "2A", PinRole.INPUT),
        6 to PinDefinition(6, "2B", PinRole.INPUT),
        7 to PinDefinition(7, "GND", PinRole.POWER_GND),
        8 to PinDefinition(8, "3A", PinRole.INPUT),
        9 to PinDefinition(9, "3B", PinRole.INPUT),
        10 to PinDefinition(10, "3Y", PinRole.OUTPUT),
        11 to PinDefinition(11, "4A", PinRole.INPUT),
        12 to PinDefinition(12, "4B", PinRole.INPUT),
        13 to PinDefinition(13, "4Y", PinRole.OUTPUT),
        14 to PinDefinition(14, "VCC", PinRole.POWER_VCC)
    )

    private val outputPins = listOf(1, 4, 10, 13)

    override fun evaluate(
        pinLevels: Map<Int, ElectricalLevel>,
        previousLevels: Map<Int, ElectricalLevel>,
        internalState: Any?
    ): EvaluationResult {
        val (isPowered, isReverse) = checkPower(pinLevels)
        if (!isPowered) return unpoweredOutputs(outputPins, isReverse)

        fun nor(aPin: Int, bPin: Int): ElectricalLevel {
            val a = isTtlHigh(aPin, pinLevels)
            val b = isTtlHigh(bPin, pinLevels)
            return if (!a && !b) ElectricalLevel.HIGH else ElectricalLevel.LOW
        }

        return EvaluationResult(
            outputLevels = mapOf(
                1 to nor(2, 3),
                4 to nor(5, 6),
                10 to nor(8, 9),
                13 to nor(11, 12)
            )
        )
    }
}

/**
 * 7404: Hex Inverter (14-Pin DIP)
 */
class Chip7404 : AbstractChipModel("7404", "Hex Inverter", 14, 14, 7) {
    override val pins: Map<Int, PinDefinition> = mapOf(
        1 to PinDefinition(1, "1A", PinRole.INPUT),
        2 to PinDefinition(2, "1Y", PinRole.OUTPUT),
        3 to PinDefinition(3, "2A", PinRole.INPUT),
        4 to PinDefinition(4, "2Y", PinRole.OUTPUT),
        5 to PinDefinition(5, "3A", PinRole.INPUT),
        6 to PinDefinition(6, "3Y", PinRole.OUTPUT),
        7 to PinDefinition(7, "GND", PinRole.POWER_GND),
        8 to PinDefinition(8, "4Y", PinRole.OUTPUT),
        9 to PinDefinition(9, "4A", PinRole.INPUT),
        10 to PinDefinition(10, "5Y", PinRole.OUTPUT),
        11 to PinDefinition(11, "5A", PinRole.INPUT),
        12 to PinDefinition(12, "6Y", PinRole.OUTPUT),
        13 to PinDefinition(13, "6A", PinRole.INPUT),
        14 to PinDefinition(14, "VCC", PinRole.POWER_VCC)
    )

    private val outputPins = listOf(2, 4, 6, 8, 10, 12)

    override fun evaluate(
        pinLevels: Map<Int, ElectricalLevel>,
        previousLevels: Map<Int, ElectricalLevel>,
        internalState: Any?
    ): EvaluationResult {
        val (isPowered, isReverse) = checkPower(pinLevels)
        if (!isPowered) return unpoweredOutputs(outputPins, isReverse)

        fun not(inPin: Int): ElectricalLevel {
            val a = isTtlHigh(inPin, pinLevels)
            return if (!a) ElectricalLevel.HIGH else ElectricalLevel.LOW
        }

        return EvaluationResult(
            outputLevels = mapOf(
                2 to not(1),
                4 to not(3),
                6 to not(5),
                8 to not(9),
                10 to not(11),
                12 to not(13)
            )
        )
    }
}

/**
 * 7408: Quad 2-Input AND Gate (14-Pin DIP)
 */
class Chip7408 : AbstractChipModel("7408", "Quad 2-Input AND Gate", 14, 14, 7) {
    override val pins: Map<Int, PinDefinition> = mapOf(
        1 to PinDefinition(1, "1A", PinRole.INPUT),
        2 to PinDefinition(2, "1B", PinRole.INPUT),
        3 to PinDefinition(3, "1Y", PinRole.OUTPUT),
        4 to PinDefinition(4, "2A", PinRole.INPUT),
        5 to PinDefinition(5, "2B", PinRole.INPUT),
        6 to PinDefinition(6, "2Y", PinRole.OUTPUT),
        7 to PinDefinition(7, "GND", PinRole.POWER_GND),
        8 to PinDefinition(8, "3Y", PinRole.OUTPUT),
        9 to PinDefinition(9, "3A", PinRole.INPUT),
        10 to PinDefinition(10, "3B", PinRole.INPUT),
        11 to PinDefinition(11, "4Y", PinRole.OUTPUT),
        12 to PinDefinition(12, "4A", PinRole.INPUT),
        13 to PinDefinition(13, "4B", PinRole.INPUT),
        14 to PinDefinition(14, "VCC", PinRole.POWER_VCC)
    )

    private val outputPins = listOf(3, 6, 8, 11)

    override fun evaluate(
        pinLevels: Map<Int, ElectricalLevel>,
        previousLevels: Map<Int, ElectricalLevel>,
        internalState: Any?
    ): EvaluationResult {
        val (isPowered, isReverse) = checkPower(pinLevels)
        if (!isPowered) return unpoweredOutputs(outputPins, isReverse)

        fun and(aPin: Int, bPin: Int): ElectricalLevel {
            val a = isTtlHigh(aPin, pinLevels)
            val b = isTtlHigh(bPin, pinLevels)
            return if (a && b) ElectricalLevel.HIGH else ElectricalLevel.LOW
        }

        return EvaluationResult(
            outputLevels = mapOf(
                3 to and(1, 2),
                6 to and(4, 5),
                8 to and(9, 10),
                11 to and(12, 13)
            )
        )
    }
}

/**
 * 7410: Triple 3-Input NAND Gate (14-Pin DIP)
 */
class Chip7410 : AbstractChipModel("7410", "Triple 3-Input NAND Gate", 14, 14, 7) {
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

        fun nand3(aPin: Int, bPin: Int, cPin: Int): ElectricalLevel {
            val a = isTtlHigh(aPin, pinLevels)
            val b = isTtlHigh(bPin, pinLevels)
            val c = isTtlHigh(cPin, pinLevels)
            return if (a && b && c) ElectricalLevel.LOW else ElectricalLevel.HIGH
        }

        return EvaluationResult(
            outputLevels = mapOf(
                12 to nand3(1, 2, 13),
                6 to nand3(3, 4, 5),
                8 to nand3(9, 10, 11)
            )
        )
    }
}

/**
 * 7420: Dual 4-Input NAND Gate (14-Pin DIP, Pins 3 & 11 NC)
 */
class Chip7420 : AbstractChipModel("7420", "Dual 4-Input NAND Gate", 14, 14, 7) {
    override val pins: Map<Int, PinDefinition> = mapOf(
        1 to PinDefinition(1, "1A", PinRole.INPUT),
        2 to PinDefinition(2, "1B", PinRole.INPUT),
        3 to PinDefinition(3, "NC", PinRole.NO_CONNECT),
        4 to PinDefinition(4, "1C", PinRole.INPUT),
        5 to PinDefinition(5, "1D", PinRole.INPUT),
        6 to PinDefinition(6, "1Y", PinRole.OUTPUT),
        7 to PinDefinition(7, "GND", PinRole.POWER_GND),
        8 to PinDefinition(8, "2Y", PinRole.OUTPUT),
        9 to PinDefinition(9, "2A", PinRole.INPUT),
        10 to PinDefinition(10, "2B", PinRole.INPUT),
        11 to PinDefinition(11, "NC", PinRole.NO_CONNECT),
        12 to PinDefinition(12, "2C", PinRole.INPUT),
        13 to PinDefinition(13, "2D", PinRole.INPUT),
        14 to PinDefinition(14, "VCC", PinRole.POWER_VCC)
    )

    private val outputPins = listOf(6, 8)

    override fun evaluate(
        pinLevels: Map<Int, ElectricalLevel>,
        previousLevels: Map<Int, ElectricalLevel>,
        internalState: Any?
    ): EvaluationResult {
        val (isPowered, isReverse) = checkPower(pinLevels)
        if (!isPowered) return unpoweredOutputs(outputPins, isReverse)

        fun nand4(aPin: Int, bPin: Int, cPin: Int, dPin: Int): ElectricalLevel {
            val a = isTtlHigh(aPin, pinLevels)
            val b = isTtlHigh(bPin, pinLevels)
            val c = isTtlHigh(cPin, pinLevels)
            val d = isTtlHigh(dPin, pinLevels)
            return if (a && b && c && d) ElectricalLevel.LOW else ElectricalLevel.HIGH
        }

        return EvaluationResult(
            outputLevels = mapOf(
                6 to nand4(1, 2, 4, 5),
                8 to nand4(9, 10, 12, 13)
            )
        )
    }
}

/**
 * 7432: Quad 2-Input OR Gate (14-Pin DIP)
 */
class Chip7432 : AbstractChipModel("7432", "Quad 2-Input OR Gate", 14, 14, 7) {
    override val pins: Map<Int, PinDefinition> = mapOf(
        1 to PinDefinition(1, "1A", PinRole.INPUT),
        2 to PinDefinition(2, "1B", PinRole.INPUT),
        3 to PinDefinition(3, "1Y", PinRole.OUTPUT),
        4 to PinDefinition(4, "2A", PinRole.INPUT),
        5 to PinDefinition(5, "2B", PinRole.INPUT),
        6 to PinDefinition(6, "2Y", PinRole.OUTPUT),
        7 to PinDefinition(7, "GND", PinRole.POWER_GND),
        8 to PinDefinition(8, "3Y", PinRole.OUTPUT),
        9 to PinDefinition(9, "3A", PinRole.INPUT),
        10 to PinDefinition(10, "3B", PinRole.INPUT),
        11 to PinDefinition(11, "4Y", PinRole.OUTPUT),
        12 to PinDefinition(12, "4A", PinRole.INPUT),
        13 to PinDefinition(13, "4B", PinRole.INPUT),
        14 to PinDefinition(14, "VCC", PinRole.POWER_VCC)
    )

    private val outputPins = listOf(3, 6, 8, 11)

    override fun evaluate(
        pinLevels: Map<Int, ElectricalLevel>,
        previousLevels: Map<Int, ElectricalLevel>,
        internalState: Any?
    ): EvaluationResult {
        val (isPowered, isReverse) = checkPower(pinLevels)
        if (!isPowered) return unpoweredOutputs(outputPins, isReverse)

        fun or(aPin: Int, bPin: Int): ElectricalLevel {
            val a = isTtlHigh(aPin, pinLevels)
            val b = isTtlHigh(bPin, pinLevels)
            return if (a || b) ElectricalLevel.HIGH else ElectricalLevel.LOW
        }

        return EvaluationResult(
            outputLevels = mapOf(
                3 to or(1, 2),
                6 to or(4, 5),
                8 to or(9, 10),
                11 to or(12, 13)
            )
        )
    }
}

/**
 * 7486: Quad 2-Input XOR Gate (14-Pin DIP)
 */
class Chip7486 : AbstractChipModel("7486", "Quad 2-Input XOR Gate", 14, 14, 7) {
    override val pins: Map<Int, PinDefinition> = mapOf(
        1 to PinDefinition(1, "1A", PinRole.INPUT),
        2 to PinDefinition(2, "1B", PinRole.INPUT),
        3 to PinDefinition(3, "1Y", PinRole.OUTPUT),
        4 to PinDefinition(4, "2A", PinRole.INPUT),
        5 to PinDefinition(5, "2B", PinRole.INPUT),
        6 to PinDefinition(6, "2Y", PinRole.OUTPUT),
        7 to PinDefinition(7, "GND", PinRole.POWER_GND),
        8 to PinDefinition(8, "3Y", PinRole.OUTPUT),
        9 to PinDefinition(9, "3A", PinRole.INPUT),
        10 to PinDefinition(10, "3B", PinRole.INPUT),
        11 to PinDefinition(11, "4Y", PinRole.OUTPUT),
        12 to PinDefinition(12, "4A", PinRole.INPUT),
        13 to PinDefinition(13, "4B", PinRole.INPUT),
        14 to PinDefinition(14, "VCC", PinRole.POWER_VCC)
    )

    private val outputPins = listOf(3, 6, 8, 11)

    override fun evaluate(
        pinLevels: Map<Int, ElectricalLevel>,
        previousLevels: Map<Int, ElectricalLevel>,
        internalState: Any?
    ): EvaluationResult {
        val (isPowered, isReverse) = checkPower(pinLevels)
        if (!isPowered) return unpoweredOutputs(outputPins, isReverse)

        fun xor(aPin: Int, bPin: Int): ElectricalLevel {
            val a = isTtlHigh(aPin, pinLevels)
            val b = isTtlHigh(bPin, pinLevels)
            return if (a xor b) ElectricalLevel.HIGH else ElectricalLevel.LOW
        }

        return EvaluationResult(
            outputLevels = mapOf(
                3 to xor(1, 2),
                6 to xor(4, 5),
                8 to xor(9, 10),
                11 to xor(12, 13)
            )
        )
    }
}

/**
 * 7483: 4-Bit Binary Full Adder with Fast Carry (16-Pin DIP)
 * Power: Pin 5 = VCC, Pin 12 = GND
 */
class Chip7483 : AbstractChipModel("7483", "4-Bit Binary Full Adder", 16, 5, 12) {
    override val pins: Map<Int, PinDefinition> = mapOf(
        1 to PinDefinition(1, "A4", PinRole.INPUT),
        2 to PinDefinition(2, "S3", PinRole.OUTPUT),
        3 to PinDefinition(3, "A3", PinRole.INPUT),
        4 to PinDefinition(4, "B3", PinRole.INPUT),
        5 to PinDefinition(5, "VCC", PinRole.POWER_VCC),
        6 to PinDefinition(6, "S2", PinRole.OUTPUT),
        7 to PinDefinition(7, "B2", PinRole.INPUT),
        8 to PinDefinition(8, "A2", PinRole.INPUT),
        9 to PinDefinition(9, "S1", PinRole.OUTPUT),
        10 to PinDefinition(10, "A1", PinRole.INPUT),
        11 to PinDefinition(11, "B1", PinRole.INPUT),
        12 to PinDefinition(12, "GND", PinRole.POWER_GND),
        13 to PinDefinition(13, "C0", PinRole.INPUT),
        14 to PinDefinition(14, "C4", PinRole.OUTPUT),
        15 to PinDefinition(15, "S4", PinRole.OUTPUT),
        16 to PinDefinition(16, "B4", PinRole.INPUT)
    )

    private val outputPins = listOf(9, 6, 2, 15, 14) // S1, S2, S3, S4, C4

    override fun evaluate(
        pinLevels: Map<Int, ElectricalLevel>,
        previousLevels: Map<Int, ElectricalLevel>,
        internalState: Any?
    ): EvaluationResult {
        val (isPowered, isReverse) = checkPower(pinLevels)
        if (!isPowered) return unpoweredOutputs(outputPins, isReverse)

        fun bit(pin: Int): Int = if (isTtlHigh(pin, pinLevels)) 1 else 0

        val a1 = bit(10)
        val a2 = bit(8)
        val a3 = bit(3)
        val a4 = bit(1)

        val b1 = bit(11)
        val b2 = bit(7)
        val b3 = bit(4)
        val b4 = bit(16)

        val c0 = bit(13)

        val aVal = a1 or (a2 shl 1) or (a3 shl 2) or (a4 shl 3)
        val bVal = b1 or (b2 shl 1) or (b3 shl 2) or (b4 shl 3)
        val total = aVal + bVal + c0

        val s1 = (total and 0x01) != 0
        val s2 = (total and 0x02) != 0
        val s3 = (total and 0x04) != 0
        val s4 = (total and 0x08) != 0
        val c4 = (total and 0x10) != 0

        fun toLvl(b: Boolean) = if (b) ElectricalLevel.HIGH else ElectricalLevel.LOW

        return EvaluationResult(
            outputLevels = mapOf(
                9 to toLvl(s1),  // S1
                6 to toLvl(s2),  // S2
                2 to toLvl(s3),  // S3
                15 to toLvl(s4), // S4
                14 to toLvl(c4)  // C4
            )
        )
    }
}

/**
 * 7474: Dual D-Type Positive-Edge-Triggered Flip-Flop (14-Pin DIP)
 */
data class State7474(val q1: Boolean = false, val q2: Boolean = false)

class Chip7474 : AbstractChipModel("7474", "Dual D-Type Flip-Flop", 14, 14, 7) {
    override val pins: Map<Int, PinDefinition> = mapOf(
        1 to PinDefinition(1, "1~CLR", PinRole.INPUT, isInverted = true),
        2 to PinDefinition(2, "1D", PinRole.INPUT),
        3 to PinDefinition(3, "1CLK", PinRole.INPUT, isClock = true),
        4 to PinDefinition(4, "1~PRE", PinRole.INPUT, isInverted = true),
        5 to PinDefinition(5, "1Q", PinRole.OUTPUT),
        6 to PinDefinition(6, "1~Q", PinRole.OUTPUT, isInverted = true),
        7 to PinDefinition(7, "GND", PinRole.POWER_GND),
        8 to PinDefinition(8, "2~Q", PinRole.OUTPUT, isInverted = true),
        9 to PinDefinition(9, "2Q", PinRole.OUTPUT),
        10 to PinDefinition(10, "2~PRE", PinRole.INPUT, isInverted = true),
        11 to PinDefinition(11, "2CLK", PinRole.INPUT, isClock = true),
        12 to PinDefinition(12, "2D", PinRole.INPUT),
        13 to PinDefinition(13, "2~CLR", PinRole.INPUT, isInverted = true),
        14 to PinDefinition(14, "VCC", PinRole.POWER_VCC)
    )

    private val outputPins = listOf(5, 6, 9, 8)

    override fun initialInternalState(): Any = State7474()

    override fun evaluate(
        pinLevels: Map<Int, ElectricalLevel>,
        previousLevels: Map<Int, ElectricalLevel>,
        internalState: Any?
    ): EvaluationResult {
        val (isPowered, isReverse) = checkPower(pinLevels)
        if (!isPowered) return unpoweredOutputs(outputPins, isReverse)

        val state = (internalState as? State7474) ?: State7474()

        fun evalFF(
            clrPin: Int,
            prePin: Int,
            clkPin: Int,
            dPin: Int,
            currentQ: Boolean
        ): Pair<Boolean, Boolean> {
            val clr = isTtlHigh(clrPin, pinLevels)
            val pre = isTtlHigh(prePin, pinLevels)

            // Asynchronous overrides (active LOW)
            if (!pre && clr) return Pair(true, false) // Set
            if (pre && !clr) return Pair(false, true) // Reset
            if (!pre && !clr) return Pair(true, true) // Unstable / Contention mode (both HIGH)

            // Synchronous clock evaluation on positive edge (LOW -> HIGH)
            val prevClk = previousLevels[clkPin]
            val currClk = pinLevels[clkPin]
            val isRisingEdge = (prevClk == ElectricalLevel.LOW && currClk == ElectricalLevel.HIGH)

            val nextQ = if (isRisingEdge) {
                isTtlHigh(dPin, pinLevels)
            } else {
                currentQ
            }

            return Pair(nextQ, !nextQ)
        }

        val (q1, nq1) = evalFF(1, 4, 3, 2, state.q1)
        val (q2, nq2) = evalFF(13, 10, 11, 12, state.q2)

        fun toLvl(b: Boolean) = if (b) ElectricalLevel.HIGH else ElectricalLevel.LOW

        return EvaluationResult(
            outputLevels = mapOf(
                5 to toLvl(q1),
                6 to toLvl(nq1),
                9 to toLvl(q2),
                8 to toLvl(nq2)
            ),
            nextInternalState = State7474(q1, q2)
        )
    }
}

/**
 * 7476: Dual J-K Flip-Flop with Preset & Clear (16-Pin DIP)
 * Power: Pin 5 = VCC, Pin 13 = GND
 */
data class State7476(val q1: Boolean = false, val q2: Boolean = false)

class Chip7476 : AbstractChipModel("7476", "Dual J-K Flip-Flop", 16, 5, 13) {
    override val pins: Map<Int, PinDefinition> = mapOf(
        1 to PinDefinition(1, "1~CLK", PinRole.INPUT, isClock = true, isInverted = true),
        2 to PinDefinition(2, "1~PRE", PinRole.INPUT, isInverted = true),
        3 to PinDefinition(3, "1~CLR", PinRole.INPUT, isInverted = true),
        4 to PinDefinition(4, "1J", PinRole.INPUT),
        5 to PinDefinition(5, "VCC", PinRole.POWER_VCC),
        6 to PinDefinition(6, "2~CLK", PinRole.INPUT, isClock = true, isInverted = true),
        7 to PinDefinition(7, "2~PRE", PinRole.INPUT, isInverted = true),
        8 to PinDefinition(8, "2~CLR", PinRole.INPUT, isInverted = true),
        9 to PinDefinition(9, "2J", PinRole.INPUT),
        10 to PinDefinition(10, "2~Q", PinRole.OUTPUT, isInverted = true),
        11 to PinDefinition(11, "2Q", PinRole.OUTPUT),
        12 to PinDefinition(12, "2K", PinRole.INPUT),
        13 to PinDefinition(13, "GND", PinRole.POWER_GND),
        14 to PinDefinition(14, "1~Q", PinRole.OUTPUT, isInverted = true),
        15 to PinDefinition(15, "1Q", PinRole.OUTPUT),
        16 to PinDefinition(16, "1K", PinRole.INPUT)
    )

    private val outputPins = listOf(15, 14, 11, 10)

    override fun initialInternalState(): Any = State7476()

    override fun evaluate(
        pinLevels: Map<Int, ElectricalLevel>,
        previousLevels: Map<Int, ElectricalLevel>,
        internalState: Any?
    ): EvaluationResult {
        val (isPowered, isReverse) = checkPower(pinLevels)
        if (!isPowered) return unpoweredOutputs(outputPins, isReverse)

        val state = (internalState as? State7476) ?: State7476()

        fun evalJK(
            clrPin: Int,
            prePin: Int,
            clkPin: Int,
            jPin: Int,
            kPin: Int,
            currentQ: Boolean
        ): Pair<Boolean, Boolean> {
            val clr = isTtlHigh(clrPin, pinLevels)
            val pre = isTtlHigh(prePin, pinLevels)

            // Asynchronous overrides (active LOW)
            if (!pre && clr) return Pair(true, false) // Set
            if (pre && !clr) return Pair(false, true) // Reset
            if (!pre && !clr) return Pair(true, true) // Unstable (both HIGH)

            // Negative-edge clock trigger (HIGH -> LOW)
            val prevClk = previousLevels[clkPin]
            val currClk = pinLevels[clkPin]
            val isFallingEdge = (prevClk == ElectricalLevel.HIGH && currClk == ElectricalLevel.LOW)

            val nextQ = if (isFallingEdge) {
                val j = isTtlHigh(jPin, pinLevels)
                val k = isTtlHigh(kPin, pinLevels)
                when {
                    !j && !k -> currentQ
                    !j && k -> false
                    j && !k -> true
                    else -> !currentQ // Toggle!
                }
            } else {
                currentQ
            }

            return Pair(nextQ, !nextQ)
        }

        val (q1, nq1) = evalJK(3, 2, 1, 4, 16, state.q1)
        val (q2, nq2) = evalJK(8, 7, 6, 9, 12, state.q2)

        fun toLvl(b: Boolean) = if (b) ElectricalLevel.HIGH else ElectricalLevel.LOW

        return EvaluationResult(
            outputLevels = mapOf(
                15 to toLvl(q1),
                14 to toLvl(nq1),
                11 to toLvl(q2),
                10 to toLvl(nq2)
            ),
            nextInternalState = State7476(q1, q2)
        )
    }
}

/**
 * 7448: BCD-to-7-Segment Decoder / Driver (16-Pin DIP, Active HIGH outputs for Common Cathode)
 */
class Chip7448 : AbstractChipModel("7448", "BCD-to-7-Segment Decoder", 16, 16, 8) {
    override val pins: Map<Int, PinDefinition> = mapOf(
        1 to PinDefinition(1, "B", PinRole.INPUT),
        2 to PinDefinition(2, "C", PinRole.INPUT),
        3 to PinDefinition(3, "~LT", PinRole.INPUT, isInverted = true),
        4 to PinDefinition(4, "~BI", PinRole.INPUT, isInverted = true),
        5 to PinDefinition(5, "~RBI", PinRole.INPUT, isInverted = true),
        6 to PinDefinition(6, "D", PinRole.INPUT),
        7 to PinDefinition(7, "A", PinRole.INPUT),
        8 to PinDefinition(8, "GND", PinRole.POWER_GND),
        9 to PinDefinition(9, "e", PinRole.OUTPUT),
        10 to PinDefinition(10, "d", PinRole.OUTPUT),
        11 to PinDefinition(11, "c", PinRole.OUTPUT),
        12 to PinDefinition(12, "b", PinRole.OUTPUT),
        13 to PinDefinition(13, "a", PinRole.OUTPUT),
        14 to PinDefinition(14, "g", PinRole.OUTPUT),
        15 to PinDefinition(15, "f", PinRole.OUTPUT),
        16 to PinDefinition(16, "VCC", PinRole.POWER_VCC)
    )

    private val outputPins = listOf(13, 12, 11, 10, 9, 15, 14) // a, b, c, d, e, f, g

    private val font7448 = intArrayOf(
        0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07,
        0x7F, 0x6F, 0x5C, 0x4C, 0x46, 0x4D, 0x78, 0x00
    )

    override fun evaluate(
        pinLevels: Map<Int, ElectricalLevel>,
        previousLevels: Map<Int, ElectricalLevel>,
        internalState: Any?
    ): EvaluationResult {
        val (isPowered, isReverse) = checkPower(pinLevels)
        if (!isPowered) return unpoweredOutputs(outputPins, isReverse)

        val lt = isTtlHigh(3, pinLevels)   // Lamp test (active LOW)
        val bi = isTtlHigh(4, pinLevels)   // Blanking input (active LOW)
        val rbi = isTtlHigh(5, pinLevels)  // Ripple blanking input (active LOW)

        fun segMap(mask: Int): Map<Int, ElectricalLevel> {
            fun bit(pos: Int) = if ((mask and (1 shl pos)) != 0) ElectricalLevel.HIGH else ElectricalLevel.LOW
            return mapOf(
                13 to bit(0), // a
                12 to bit(1), // b
                11 to bit(2), // c
                10 to bit(3), // d
                9 to bit(4),  // e
                15 to bit(5), // f
                14 to bit(6)  // g
            )
        }

        // 1. Blanking input overrides all
        if (!bi) {
            return EvaluationResult(outputPins.associateWith { ElectricalLevel.LOW })
        }

        // 2. Lamp test overrides if BI is inactive
        if (!lt) {
            return EvaluationResult(outputPins.associateWith { ElectricalLevel.HIGH })
        }

        // 3. Normal BCD decoding
        // Note: For BCD inputs, check driven HIGH level (0 when grounded or floating in test setup unless pulled up)
        fun bcdBit(pin: Int): Int {
            val lvl = pinLevels[pin]
            return if (lvl == ElectricalLevel.HIGH) 1 else 0
        }

        val a = bcdBit(7)
        val b = bcdBit(1)
        val c = bcdBit(2)
        val d = bcdBit(6)
        val bcdValue = a or (b shl 1) or (c shl 2) or (d shl 3)

        // Ripple blanking: if BCD=0 and ~RBI is LOW, blank display
        if (bcdValue == 0 && !rbi) {
            return EvaluationResult(outputPins.associateWith { ElectricalLevel.LOW })
        }

        val pattern = font7448[bcdValue and 0x0F]
        return EvaluationResult(outputLevels = segMap(pattern))
    }
}

/**
 * Chip factory catalog for instantiating IC models by part number.
 */
object ChipCatalog {
    private val catalog = mapOf<String, () -> ChipModel>(
        "7400" to { Chip7400() },
        "7402" to { Chip7402() },
        "7404" to { Chip7404() },
        "7408" to { Chip7408() },
        "7410" to { Chip7410() },
        "7420" to { Chip7420() },
        "7432" to { Chip7432() },
        "7483" to { Chip7483() },
        "7486" to { Chip7486() },
        "7474" to { Chip7474() },
        "7476" to { Chip7476() },
        "7448" to { Chip7448() }
    )

    fun create(partNumber: String): ChipModel {
        val factory = catalog[partNumber]
            ?: throw IllegalArgumentException("Unknown TTL part number: $partNumber. Available: ${catalog.keys}")
        return factory()
    }

    fun allPartNumbers(): Set<String> = catalog.keys
}

object TTLChipCatalog {
    private val chips = mapOf<String, () -> ChipModel>(
        "7400" to { Chip7400() },
        "7402" to { Chip7402() },
        "7404" to { Chip7404() },
        "7408" to { Chip7408() },
        "7410" to { Chip7410() },
        "7411" to { Chip7411() },
        "7420" to { Chip7420() },
        "7432" to { Chip7432() },
        "7486" to { Chip7486() },
        "74266" to { Chip74266() },
        "7483" to { Chip7483() },
        "7474" to { Chip7474() },
        "7476" to { Chip7476() },
        "7448" to { Chip7448() }
    )

    fun getAllPartNumbers(): List<String> = chips.keys.toList()

    fun create(partNumber: String): ChipModel? = chips[partNumber]?.invoke()

    fun getInfo(partNumber: String): ChipModel? = create(partNumber)
}
