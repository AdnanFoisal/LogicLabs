package com.logiclabs.feature.tools.analog

/**
 * Ideal models of the two 4-bit weighted-resistor digital-to-analog converters used by
 * Experiment 12.
 *
 * Same contract as [DiscreteApparatus] and for the same reason: the simulation engine resolves a net
 * by counting HIGH and LOW drivers, so it has no concept of a node *voltage*, which is the entire
 * subject of a DAC experiment. These types therefore sit beside the digital engine rather than
 * inside it — they take the four switch bits and return the millivolts a multimeter would read.
 *
 * Unlike the diode gates, no idealisation is being hidden here. A weighted-resistor network of ideal
 * resistors driven from ideal voltage sources has an exact closed-form solution by superposition, and
 * that is what both models below compute. The only assumptions are that the switch drives a hard
 * 0 V or +5 V rail (see [NodeLevel.fromSwitch]) and, in the active case, that the op-amp is ideal:
 * infinite gain, so the summing node is a virtual ground, and infinite input impedance, so no ladder
 * current is lost into it.
 *
 * Pure Kotlin, no Android types, so the arithmetic stays unit-testable.
 */

/**
 * The bit weights of a 4-bit binary-weighted ladder, expressed as resistances.
 *
 * The whole idea of the topology is that each bit's resistor is *inversely* proportional to that
 * bit's place value, so the current it injects is directly proportional to it: the MSB's 1.25 kOhm
 * passes eight times the current of the LSB's 10 kOhm, matching the 8:1 ratio of their place values.
 * Sum the currents and you have summed the binary number.
 *
 * This exactness is also the topology's practical downfall. Four bits needs an 8:1 spread of
 * resistances; twelve bits needs 2048:1, and the MSB resistor would have to be trimmed to better
 * than one part in 2048 for the converter to stay monotonic — which is why real converters use an
 * R-2R ladder, where every resistor is one of only two values.
 */
object DacLadder {

    /** D3, the most significant bit: 10 kOhm / 8. */
    val r3: Resistor = Resistor("R3 (D3, MSB)", 1_250)

    /** D2: 10 kOhm / 4. */
    val r2: Resistor = Resistor("R2 (D2)", 2_500)

    /** D1: 10 kOhm / 2. */
    val r1: Resistor = Resistor("R1 (D1)", 5_000)

    /** D0, the least significant bit, and the value the rest are derived from. */
    val r0: Resistor = Resistor("R0 (D0, LSB)", 10_000)

    /**
     * Bit weights as conductances in units of 1/10 kOhm, MSB first: 8, 4, 2, 1.
     *
     * Kept as integers rather than being recomputed from the resistances at each call, because the
     * ratios are the *design* — the resistor values follow from them, not the other way round.
     */
    val weights: IntArray = intArrayOf(8, 4, 2, 1)

    /** Sum of the four weights: 15, the largest code a 4-bit converter can represent. */
    const val FULL_SCALE_CODE: Int = 15

    /**
     * The binary code the four switch bits form, MSB first.
     *
     * @param bits exactly four entries, `bits[0]` being D3. Anything shorter is read as zero-padded
     *   on the *right*, i.e. the missing bits are the least significant, which is what a partially
     *   wired ladder physically does.
     */
    fun codeOf(bits: List<Boolean>): Int {
        var code = 0
        for (i in weights.indices) {
            if (i < bits.size && bits[i]) code += weights[i]
        }
        return code
    }
}

/**
 * **Variation A — the passive ladder.** All four weighted resistors sum into a common node Y, and a
 * load resistor [load] runs from Y to ground. No amplifier.
 *
 * Solved by superposition. Treating each resistor as a conductance in units of 1/10 kOhm, the
 * weighted arms contribute 8 + 4 + 2 + 1 = 15 and the 10 kOhm load contributes 1, so:
 *
 * ```
 * Vy = (sum of Vi * Gi) / (sum of Gi)  =  Vref * code / (15 + 1)  =  Vref * code / 16
 * ```
 *
 * Two things fall out of that, and they are the whole lesson of the variation:
 *
 *  1. **It is perfectly linear.** 312.5 mV per step with a 5 V reference, and every step identical.
 *     A passive weighted ladder is not distorted by its load — the superposition sum stays linear
 *     however heavy the load is.
 *  2. **But the gain depends on that load.** Full scale is 15/16 of the reference, not 15/15, and the
 *     16 in that denominator is *the load resistor*. Swap the 10 kOhm for a 1 kOhm and full scale
 *     collapses to 15/25 of the reference; leave the output open and it rises to the full 15/15.
 *     So the converter has no defined output voltage until you say what is measuring it, and
 *     cascading anything into it changes its calibration. That is what the op-amp in Variation B
 *     fixes, and it is the same argument as Experiment 01's transistor stage after the diode gates.
 */
data class PassiveWeightedDac(
    val load: Resistor = Resistor("RL", 10_000),
    val referenceMillivolts: Int = SemiconductorConstants.SUPPLY_MILLIVOLTS
) {
    /**
     * Load conductance in the same 1/10 kOhm units as [DacLadder.weights], so it can be added
     * straight into the superposition denominator.
     */
    private val loadWeight: Int get() = 10_000 / load.ohms

    /** Total denominator conductance: the four ladder arms plus the load. */
    val denominatorWeight: Int get() = DacLadder.FULL_SCALE_CODE + loadWeight

    fun evaluate(bits: List<Boolean>): NodeLevel =
        NodeLevel(referenceMillivolts * DacLadder.codeOf(bits) / denominatorWeight)

    /** Millivolts per least-significant bit. 312 mV at the default 10 kOhm load and 5 V reference. */
    val stepMillivolts: Int get() = referenceMillivolts / denominatorWeight

    /** The reading at code 15. */
    val fullScaleMillivolts: Int
        get() = referenceMillivolts * DacLadder.FULL_SCALE_CODE / denominatorWeight
}

/**
 * **Variation B — the active summing converter.** The same four weighted resistors feed the
 * inverting input of a uA741; [feedback] runs from the output back to that node; the non-inverting
 * input is grounded.
 *
 * An ideal op-amp holds its two inputs at the same potential, so the summing node sits at a *virtual
 * ground* — 0 V, without being connected to ground. That single fact is what makes the topology
 * better than the passive one:
 *
 *  - Every ladder resistor now has a fixed 0 V on its far end, so the current each bit injects
 *    depends only on its own resistor and its own switch. The arms cannot interact.
 *  - All of that current has nowhere to go but the feedback resistor, because the op-amp's input
 *    draws none. So `Vout = -Rf * sum(Vi / Ri)`, exactly, with no load term anywhere in it.
 *  - The output is driven by the op-amp, so whatever you connect downstream is the *op-amp's*
 *    problem, not the ladder's. The calibration no longer depends on the meter.
 *
 * With Rf = 1.25 kOhm the scale factor is 1.25/10 = 1/8 per LSB unit, so each step is
 * 5 V / 8 = **625 mV** and full scale is 15 x 625 mV = **-9.375 V**. The sign is negative because
 * the topology is an inverting amplifier; a second inverting stage would flip it back, and there is
 * only one op-amp in the kit, which is worth noticing rather than glossing over.
 *
 * That -9.375 V is also why this experiment runs the op-amp from the trainer's +/-12 V rails rather
 * than the +5 V logic supply. A 741 saturates roughly 1.5 V short of its rails, so it can reach
 * about -10.5 V on a -12 V supply — enough headroom, but not much. On a +/-5 V supply it would clip
 * at about -3.5 V and every code above 5 would read the same, which is the most instructive way to
 * get a DAC wrong.
 */
data class ActiveWeightedDac(
    val feedback: Resistor = Resistor("Rf", 1_250),
    val referenceMillivolts: Int = SemiconductorConstants.SUPPLY_MILLIVOLTS,
    val negativeSupplyMillivolts: Int = -12_000,
    val positiveSupplyMillivolts: Int = 12_000,
    val saturationHeadroomMillivolts: Int = 1_500
) {
    /**
     * Signed output in millivolts, clipped at the op-amp's saturation limits.
     *
     * The clip is not decoration. It is the modelled failure mode: a summing amplifier whose gain
     * and reference ask for more swing than its rails can provide stops converting and simply
     * saturates, and every code past that point reads identically.
     */
    fun evaluate(bits: List<Boolean>): Int {
        val ideal = -(referenceMillivolts * feedback.ohms * DacLadder.codeOf(bits)) / DacLadder.r0.ohms
        val floor = negativeSupplyMillivolts + saturationHeadroomMillivolts
        val ceiling = positiveSupplyMillivolts - saturationHeadroomMillivolts
        return ideal.coerceIn(floor, ceiling)
    }

    /** Millivolts per least-significant bit, unsigned. 625 mV with the default 1.25 kOhm feedback. */
    val stepMillivolts: Int
        get() = referenceMillivolts * feedback.ohms / DacLadder.r0.ohms

    /** The reading at code 15, signed and clipped — so it tells the truth if the rails cannot reach. */
    val fullScaleMillivolts: Int
        get() = evaluate(listOf(true, true, true, true))

    /** True when the converter's full-scale demand exceeds what the supply rails can deliver. */
    val clipsAtFullScale: Boolean
        get() = -(stepMillivolts * DacLadder.FULL_SCALE_CODE) <
            negativeSupplyMillivolts + saturationHeadroomMillivolts
}

/** The exact bench inventory Experiment 12 issues. */
object DacApparatus {

    /** Variation A: the four weighted arms into a common node with a 10 kOhm load to ground. */
    val passive: PassiveWeightedDac = PassiveWeightedDac()

    /** Variation B: the same four arms into a uA741 summing amplifier with 1.25 kOhm feedback. */
    val active: ActiveWeightedDac = ActiveWeightedDac()

    /**
     * The four thresholds the board's bar-graph indicators sit at, as fractions of full scale,
     * expressed as the smallest code that lights each lamp: 8, 4, 2, 1.
     *
     * These are what makes the experiment *verifiable* rather than merely displayable. A comparator
     * at half of full scale changes state exactly when the MSB does — but only if the converter is
     * monotonic and linear. Sweeping all sixteen codes and confirming each lamp switches at its own
     * code and nowhere else is a real linearity check, and it is a check the digital truth-table
     * verifier can actually perform.
     */
    val barGraphThresholdCodes: IntArray = intArrayOf(8, 4, 2, 1)

    /**
     * The bar-graph pattern for a code, derived from the analog reading rather than from the bits.
     *
     * Deliberately routed through [PassiveWeightedDac.evaluate] and the threshold voltages instead of
     * being written as `listOf(d3, d3 || d2, ...)`. Writing it that way would make the truth table an
     * independent restatement of the algebra, which could drift from the analog model; deriving it
     * means a preset's expected outputs and its voltmeter readings cannot disagree.
     */
    fun barGraph(bits: List<Boolean>): List<Boolean> {
        val reading = passive.evaluate(bits).millivolts
        return barGraphThresholdCodes.map { code ->
            reading >= passive.referenceMillivolts * code / passive.denominatorWeight
        }
    }

    /**
     * The same four thresholds applied to the active converter's output.
     *
     * Compared on *magnitude*, because [ActiveWeightedDac] is an inverting stage and its output runs
     * negative — a comparator watching that node would be wired to trip on the downward excursion.
     * Reading `-9.375 V` as "larger" than `-4.375 V` is the physically honest comparison for this
     * topology, and stating it here rather than silently negating keeps the sign visible.
     *
     * The pattern comes out identical to [barGraph] across all sixteen codes, which is the result
     * worth having: the op-amp changed the scale factor, the output polarity and the load
     * sensitivity, and left the *code-to-threshold* mapping alone. That is what it means for a
     * converter to be linear, and it is checkable rather than assertable because both variations are
     * swept against their own model.
     */
    fun activeBarGraph(bits: List<Boolean>): List<Boolean> {
        val magnitude = -active.evaluate(bits)
        return barGraphThresholdCodes.map { code -> magnitude >= active.stepMillivolts * code }
    }
}
