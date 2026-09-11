package com.logiclabs.feature.tools.analog

import java.util.Locale

/**
 * Ideal-switching models of the handful of discrete semiconductors used by Experiment 01.
 *
 * Everything else in this app is TTL: a chip either drives a net HIGH or drives it LOW, and the
 * netlist DSU resolves nets by counting drivers. That model has no notion of a *node voltage*,
 * which is exactly what a diode-logic experiment is about — the whole lesson is that a diode gate
 * gets the Boolean answer right while quietly ruining the voltage levels, and that it takes a
 * transistor stage to put them back. So these types exist alongside the digital engine rather
 * than inside it: they take the switch bits, and return both the Boolean the LED indicators need
 * and the millivolt figure a voltmeter can honestly display.
 *
 * They are deliberately *not* an analog solver. There is no nodal analysis here and no iteration.
 * Each device is a switch with a fixed offset: a silicon diode either conducts (dropping a
 * constant) or blocks, and the NPN is either saturated or cut off. Every node voltage below is
 * therefore closed-form arithmetic over the input bits, which is all the ideal-switch abstraction
 * can support and all this experiment needs.
 *
 * Pure Kotlin on purpose — no Android types, so the models stay unit-testable and reusable.
 */

/**
 * The physical constants the ideal-switch abstraction rests on. Each one is a modelling
 * *assumption*, not a measurement, so each is stated with the assumption it encodes.
 */
object SemiconductorConstants {

    /**
     * The trainer's fixed logic supply. The IDL-800A's +5 V rail is regulated and, at the few
     * milliamps these gates draw, sits close enough to nominal that we treat it as exactly 5.000 V
     * and ignore rail sag entirely.
     */
    const val SUPPLY_MILLIVOLTS: Int = 5_000

    /** Ground reference. Assumed to be a true 0 V — no ground-return IR drop is modelled. */
    const val GROUND_MILLIVOLTS: Int = 0

    /**
     * Forward drop of a small-signal silicon diode (1N4148 class) at the ~1-5 mA these gates pass.
     *
     * A real diode's drop is logarithmic in current, roughly 0.6 V at 1 mA and 0.75 V at 10 mA.
     * We collapse that curve to one number: the diode is a perfect switch that either blocks or
     * conducts at exactly this drop. The assumption costs us about +/-70 mV of accuracy on the
     * node voltage, which is far smaller than the ~700 mV level degradation the experiment is
     * trying to demonstrate, so it does not affect the lesson.
     */
    const val SILICON_FORWARD_DROP_MILLIVOLTS: Int = 700

    /**
     * Base-emitter turn-on voltage of a small-signal NPN (2N3904 class).
     *
     * Same idealisation as the diode drop, for the same reason — the B-E junction *is* a diode.
     * Below this the transistor is treated as fully off; at or above it, fully on. No linear
     * active region is modelled, because an RTL inverter driven from logic levels never lingers
     * in one.
     */
    const val NPN_VBE_ON_MILLIVOLTS: Int = 700

    /**
     * Collector-emitter saturation voltage of a saturated small-signal NPN.
     *
     * Datasheet V_CE(sat) for a 2N3904 is 0.2 V at I_C = 10 mA with I_B = 1 mA (forced beta 10).
     * The inverter below runs far more base current than it needs, so it is deeper into
     * saturation than that test condition and its real V_CE(sat) would be slightly *lower*.
     * Taking the datasheet 0.2 V is therefore the pessimistic, and safely honest, choice.
     */
    const val NPN_VCE_SAT_MILLIVOLTS: Int = 200

    /**
     * Highest voltage a 74-series TTL input is guaranteed to read as LOW.
     *
     * Quoted here because it is the number that makes the diode-AND gate interesting: that gate's
     * LOW output sits 700 mV above ground, leaving only 100 mV of noise margin against this
     * limit. Not used to decide the Boolean — it is the yardstick the description holds the
     * measured levels up against.
     */
    const val TTL_VIL_MAX_MILLIVOLTS: Int = 800

    /**
     * Lowest voltage a 74-series TTL input is guaranteed to read as HIGH.
     *
     * This *is* the Boolean decision threshold used below. Deciding "is this node HIGH?" by
     * asking what a downstream TTL gate would make of it keeps the Boolean the LED shows and the
     * millivolts the voltmeter shows consistent with each other by construction.
     */
    const val TTL_VIH_MIN_MILLIVOLTS: Int = 2_000
}

/**
 * A node reading, exposed both ways at once.
 *
 * [isHigh] is what the existing LED indicators and the truth-table verifier consume; [millivolts]
 * is what a voltmeter displays. They are never computed independently — [isHigh] is always
 * derived from [millivolts] against [SemiconductorConstants.TTL_VIH_MIN_MILLIVOLTS] — so the two
 * readouts cannot disagree.
 */
data class NodeLevel(
    val millivolts: Int
) {
    /** True when a downstream 74-series input would be guaranteed to read this node as HIGH. */
    val isHigh: Boolean
        get() = millivolts >= SemiconductorConstants.TTL_VIH_MIN_MILLIVOLTS

    /**
     * True when a downstream 74-series input would be guaranteed to read this node as LOW.
     * A node can be neither [isHigh] nor [isLow] — that is the forbidden band, and a diode gate
     * driving into it is precisely the failure this experiment teaches.
     */
    val isLow: Boolean
        get() = millivolts <= SemiconductorConstants.TTL_VIL_MAX_MILLIVOLTS

    val volts: Double
        get() = millivolts / 1000.0

    /** Voltmeter-style text, e.g. `"4.30 V"`. Forced to US formatting so the decimal point is a point. */
    fun formatVolts(): String = String.format(Locale.US, "%.2f V", volts)

    companion object {
        /** The ideal logic level a console toggle switch presents: a hard rail, either 0 V or +5 V. */
        fun fromSwitch(closed: Boolean): Int =
            if (closed) SemiconductorConstants.SUPPLY_MILLIVOLTS else SemiconductorConstants.GROUND_MILLIVOLTS
    }
}

/**
 * A small-signal silicon diode as an ideal switch: it conducts when its anode is at least
 * [forwardDropMillivolts] above its cathode, and blocks otherwise. No reverse leakage, no junction
 * capacitance, no recovery time — a diode-logic gate fed from hand-thrown toggle switches is
 * quasi-static, so none of those would change a reading.
 */
data class SignalDiode(
    val designator: String,
    val forwardDropMillivolts: Int = SemiconductorConstants.SILICON_FORWARD_DROP_MILLIVOLTS
) {
    /** True when this junction is forward-biased hard enough to carry current. */
    fun conducts(anodeMillivolts: Int, cathodeMillivolts: Int): Boolean =
        (anodeMillivolts - cathodeMillivolts) >= forwardDropMillivolts
}

/**
 * An ideal linear resistor. Present so the bill of materials is real and so the currents quoted in
 * the lab notes are computed rather than asserted; it does not participate in any node solve,
 * because in every topology here the resistor's only job is to define the node's resting level
 * when every semiconductor around it is off.
 */
data class Resistor(
    val designator: String,
    val ohms: Int
) {
    /**
     * Ohm's law in integer units: millivolts / ohms is milliamps, so scaling by 1000 gives
     * microamps and keeps the arithmetic exact for the values this experiment uses.
     */
    fun currentMicroamps(acrossMillivolts: Int): Int = acrossMillivolts * 1000 / ohms
}

/**
 * A small-signal NPN bipolar transistor as a two-state switch.
 *
 * Cut off below [vbeOnMillivolts] of base-emitter drive; saturated at [vceSatMillivolts] above it.
 * The linear active region is not modelled at all. That is legitimate *here* specifically because
 * the RTL inverter below is designed with a forced beta far under unity (see
 * [RtlInverter.forcedBetaHundredths]) — with that much base overdrive the device slams between
 * the two end states and never sits in between.
 */
data class NpnTransistor(
    val designator: String,
    val vbeOnMillivolts: Int = SemiconductorConstants.NPN_VBE_ON_MILLIVOLTS,
    val vceSatMillivolts: Int = SemiconductorConstants.NPN_VCE_SAT_MILLIVOLTS
) {
    /** True when the base-emitter junction is forward-biased enough to turn the device on. */
    fun isConducting(baseMillivolts: Int, emitterMillivolts: Int): Boolean =
        (baseMillivolts - emitterMillivolts) >= vbeOnMillivolts
}

/**
 * **Diode OR.** Both diode *anodes* face the inputs; the *cathodes* are commoned and that common
 * cathode is the output node Y. [pullDown] runs from Y to ground.
 *
 * Derivation, by cases on the switch bits:
 *  - Both inputs at 0 V. The pull-down holds Y at ground, so each diode sees V_AK = 0 V, below the
 *    700 mV it needs. Both block, nothing sources current into Y, and Y stays at **0.00 V**.
 *  - One input at +5 V. That diode conducts and Y rises until the junction is exactly at its
 *    forward drop: Y = 5.00 - 0.70 = **4.30 V**. The other diode now sees its anode at 0 V and its
 *    cathode at 4.30 V — reverse-biased, so it blocks and the LOW input does *not* drag Y down.
 *    Blocking that back-feed is the entire reason the diodes are there; two switches wired straight
 *    together would just fight each other.
 *  - Both inputs at +5 V. Both conduct, both clamp Y to the same 4.30 V. No change.
 *
 * So Y = max(inputs) - one forward drop, floored at ground. The Boolean is a clean OR, but every
 * HIGH is delivered 700 mV *below* the rail: the gate has lost a diode drop of high-side noise
 * margin, and cascading a second stage would lose another.
 */
data class DiodeOrGate(
    val d1: SignalDiode,
    val d2: SignalDiode,
    val pullDown: Resistor,
    val supplyMillivolts: Int = SemiconductorConstants.SUPPLY_MILLIVOLTS
) {
    fun evaluate(inputAMillivolts: Int, inputBMillivolts: Int): NodeLevel {
        val highestAnode = maxOf(inputAMillivolts, inputBMillivolts)
        // Whichever input is highest owns the node; the drop is that diode's, and the pull-down
        // stops the node from going below ground when neither diode conducts.
        val clamped = highestAnode - d1.forwardDropMillivolts
        return NodeLevel(maxOf(clamped, SemiconductorConstants.GROUND_MILLIVOLTS))
    }

    fun evaluate(a: Boolean, b: Boolean): NodeLevel =
        evaluate(NodeLevel.fromSwitch(a), NodeLevel.fromSwitch(b))

    /** Current the conducting diode pushes through [pullDown] when the gate is HIGH. */
    fun pullDownCurrentMicroamps(): Int =
        pullDown.currentMicroamps(supplyMillivolts - d1.forwardDropMillivolts)
}

/**
 * **Diode AND.** Both diode *cathodes* face the inputs; the *anodes* are commoned and that common
 * anode is the output node Y. [pullUp] runs from Y up to +5 V.
 *
 * Derivation, by cases on the switch bits:
 *  - Either input at 0 V. That diode is forward-biased by the pull-up and conducts, so Y is clamped
 *    one forward drop *above* the LOW input: Y = 0.00 + 0.70 = **0.70 V**. Current flows from +5 V
 *    through the pull-up, through the diode, into the switch, which sinks it to ground. A single
 *    LOW input is enough to pull Y down, which is what makes this an AND.
 *  - Both inputs at +5 V. For either diode to conduct, Y would have to reach 5.00 + 0.70 = 5.70 V —
 *    and it cannot, because the only thing that can raise Y is the pull-up, whose far end *is*
 *    the 5.00 V rail. So both diodes go off, the pull-up carries no current, it therefore drops no
 *    voltage, and Y rests at the rail: **5.00 V**. The naive "V_in + 0.7" answer is not merely
 *    clipped by the supply, it never happens; the clamp simply stops being active.
 *
 * So Y = min(inputs) + one forward drop, ceilinged at the supply. Note where this gate's damage
 * shows up: its HIGH is a perfect 5.00 V, but its LOW is lifted 700 mV off ground — only 100 mV
 * clear of the 800 mV a TTL input is guaranteed to still read as LOW. The diode OR loses high-side
 * margin; the diode AND loses low-side margin. Neither can be cascaded far.
 */
data class DiodeAndGate(
    val d1: SignalDiode,
    val d2: SignalDiode,
    val pullUp: Resistor,
    val supplyMillivolts: Int = SemiconductorConstants.SUPPLY_MILLIVOLTS
) {
    fun evaluate(inputAMillivolts: Int, inputBMillivolts: Int): NodeLevel {
        val lowestCathode = minOf(inputAMillivolts, inputBMillivolts)
        // Whichever input is lowest owns the node. The supply ceiling is not a cosmetic clamp: it
        // is the pull-up's far end, and an unloaded pull-up drops nothing.
        val clamped = lowestCathode + d1.forwardDropMillivolts
        return NodeLevel(minOf(clamped, supplyMillivolts))
    }

    fun evaluate(a: Boolean, b: Boolean): NodeLevel =
        evaluate(NodeLevel.fromSwitch(a), NodeLevel.fromSwitch(b))

    /** Current the pull-up pushes into a LOW input through the conducting diode. */
    fun pullUpCurrentMicroamps(): Int =
        pullUp.currentMicroamps(supplyMillivolts - d1.forwardDropMillivolts)
}

/**
 * **RTL NOT.** The input drives [baseResistor] (1 kOhm) into the base of [q1]; [collectorLoad]
 * (4 kOhm) runs from +5 V down to the collector; the emitter sits on ground; the output node Y is
 * the collector itself.
 *
 * Derivation, by cases on the switch bit:
 *  - Input at 0 V. V_BE = 0 V, below the 700 mV turn-on, so the transistor is cut off. No collector
 *    current flows, the 4 kOhm load therefore drops nothing, and Y is pulled all the way to the
 *    rail: **5.00 V, HIGH**.
 *  - Input at +5 V. The base clamps at V_BE(on) = 0.70 V, so the base resistor passes
 *    I_B = (5.00 - 0.70) / 1 kOhm = 4.30 mA. The most the collector can ever draw is
 *    I_C(sat) = (5.00 - 0.20) / 4 kOhm = 1.20 mA, so the required forced beta is 1.20 / 4.30 = 0.279.
 *    Any real small-signal NPN has beta of 30 or more, so the device is driven roughly a hundred
 *    times harder than saturation needs and collapses to **0.20 V, LOW**.
 *
 * This is the stage that undoes the diode gates' damage. It does not pass its input's level
 * through, degraded — it *regenerates* levels from the rails, which is why a real logic family is
 * built from transistors and why diode logic never got past a couple of levels deep.
 */
data class RtlInverter(
    val q1: NpnTransistor,
    val baseResistor: Resistor,
    val collectorLoad: Resistor,
    val supplyMillivolts: Int = SemiconductorConstants.SUPPLY_MILLIVOLTS
) {
    fun evaluate(inputMillivolts: Int): NodeLevel {
        // The base resistor limits current but cannot hold the base below V_BE(on): if the input
        // can reach turn-on at all, the junction gets there. So the switch bit alone decides.
        val saturated = q1.isConducting(inputMillivolts, SemiconductorConstants.GROUND_MILLIVOLTS)
        return NodeLevel(if (saturated) q1.vceSatMillivolts else supplyMillivolts)
    }

    fun evaluate(input: Boolean): NodeLevel = evaluate(NodeLevel.fromSwitch(input))

    /** Base drive the 1 kOhm resistor delivers once the junction has clamped at V_BE(on). */
    fun baseCurrentMicroamps(inputMillivolts: Int): Int {
        if (!q1.isConducting(inputMillivolts, SemiconductorConstants.GROUND_MILLIVOLTS)) return 0
        return baseResistor.currentMicroamps(inputMillivolts - q1.vbeOnMillivolts)
    }

    /** Collector current at saturation — set purely by the load resistor and the rails. */
    fun collectorSaturationCurrentMicroamps(): Int =
        collectorLoad.currentMicroamps(supplyMillivolts - q1.vceSatMillivolts)

    /**
     * I_C(sat) / I_B in hundredths, so the deep-saturation claim above is arithmetic rather than
     * assertion. Anything well under the device's real beta means saturation is guaranteed; this
     * circuit returns 27 (0.27), against a datasheet beta of 30 or more.
     */
    fun forcedBetaHundredths(inputMillivolts: Int): Int {
        val ib = baseCurrentMicroamps(inputMillivolts)
        if (ib == 0) return 0
        return collectorSaturationCurrentMicroamps() * 100 / ib
    }
}

/**
 * The exact bench inventory Experiment 01 issues: two diodes, one 1 kOhm resistor, one 4 kOhm
 * resistor, one NPN transistor.
 *
 * The same four passives are re-used across the three variations, which is how the experiment is
 * actually run at the bench — the student rebuilds, they are not handed three kits. So R1 is the
 * OR gate's pull-down *and* the inverter's base resistor, and R2 is the AND gate's pull-up *and*
 * the inverter's collector load. The two values were not chosen arbitrarily: 1 kOhm passes a few
 * milliamps at 5 V, enough to swamp a diode's leakage and to overdrive the transistor's base,
 * while 4 kOhm keeps the collector current down to about a milliamp so the stage stays saturated
 * on that base drive.
 */
object DiscreteApparatus {

    val d1: SignalDiode = SignalDiode("D1")
    val d2: SignalDiode = SignalDiode("D2")

    /** R1, 1 kOhm: pull-down for the diode OR, base resistor for the RTL inverter. */
    val r1: Resistor = Resistor("R1", 1_000)

    /** R2, 4 kOhm: pull-up for the diode AND, collector load for the RTL inverter. */
    val r2: Resistor = Resistor("R2", 4_000)

    val q1: NpnTransistor = NpnTransistor("Q1")

    /** Variation A: anodes to the inputs, commoned cathodes to Y, R1 from Y to ground. */
    val diodeOr: DiodeOrGate = DiodeOrGate(d1 = d1, d2 = d2, pullDown = r1)

    /** Variation B: cathodes to the inputs, commoned anodes to Y, R2 from Y up to +5 V. */
    val diodeAnd: DiodeAndGate = DiodeAndGate(d1 = d1, d2 = d2, pullUp = r2)

    /** Variation C: R1 into the base, R2 as collector load, emitter grounded, Y at the collector. */
    val rtlNot: RtlInverter = RtlInverter(q1 = q1, baseResistor = r1, collectorLoad = r2)
}
