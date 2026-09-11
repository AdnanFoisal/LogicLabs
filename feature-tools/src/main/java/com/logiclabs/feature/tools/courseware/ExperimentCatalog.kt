package com.logiclabs.feature.tools.courseware

/**
 * Every loadable experiment, and the grouping the catalog UI reads.
 *
 * The twelve sealed labs in [LabCurriculum.classicLabs] are not touched — their ids, order and
 * HMAC baselines are fixed, and `LabPresetIntegrityTest` asserts the list is exactly twelve long.
 * The extended experiments therefore live in their own lists and are joined here, which keeps the
 * seal gate meaningful: nothing added below can move a classic lab's digest.
 *
 * ### Why groups exist
 * One experiment can be built more than one way, and the printed course sheets say so — Experiment
 * 07's octal decoder is a 7411 build or a 7410 build, Experiment 10's clock is the trainer
 * generator or a hand-worked pulser. Those are the same experiment, so they share a [Group] and
 * are distinguished by `subtitle`. A group with a single variation is still a group; the UI just
 * has no picker to show for it.
 *
 * Grouping is on the `expNN_` id prefix rather than on `labNumber`, because `labNumber` collides:
 * the classic labs number 1..12 and the extended experiments number 1..18 again, over different
 * material. The prefix is unique per experiment and is what each contributing file documents
 * itself as writing.
 */
object ExperimentCatalog {

    /**
     * One experiment and the ways to build it.
     *
     * @param key `expNN` for an extended experiment, or the full lab id for a classic one — the
     *   classic labs have no variations, so each is its own single-entry group.
     * @param variations at least one, in the order the course sheet presents them. The first is
     *   what a plain tap loads.
     */
    data class Group(
        val key: String,
        val title: String,
        val variations: List<LabExperiment>
    ) {
        /** True when there is a real choice to offer, i.e. a variation picker is worth drawing. */
        val hasVariations: Boolean get() = variations.size > 1

        /** What a tap on the group loads when the user has not picked a variation. */
        val default: LabExperiment get() = variations.first()
    }

    /**
     * The extended experiments, flat, in course order.
     *
     * Deliberately a `by lazy` rather than a `val` of `listOf(...)`: each contributing list builds
     * its lambdas at class-init time, and forcing all of them during `LabCurriculum`'s own
     * initialisation would couple the two files' load order for no benefit.
     */
    val extendedLabs: List<LabExperiment> by lazy {
        discreteGateLabs + combinationalLabs + converterLabs + sequentialLabs + dacLabs + basicGateAdderLabs
    }

    /** Everything loadable, classic labs first. */
    val allLabs: List<LabExperiment> by lazy { LabCurriculum.classicLabs + extendedLabs }

    /**
     * [allLabs] folded into groups, preserving first-seen order.
     *
     * `groupBy` would do this in one line but returns a `Map`, and a map's iteration order is not
     * something a UI should rely on for a numbered course. The explicit fold makes the ordering
     * a property of the source lists, which are themselves in course order.
     */
    val groups: List<Group> by lazy {
        val order = mutableListOf<String>()
        val byKey = HashMap<String, MutableList<LabExperiment>>()
        for (lab in allLabs) {
            val key = groupKeyOf(lab)
            if (key !in byKey) {
                order += key
                byKey[key] = mutableListOf()
            }
            byKey.getValue(key) += lab
        }
        order.map { key ->
            val variations = byKey.getValue(key)
            Group(key = key, title = variations.first().title, variations = variations)
        }
    }

    /** Finds a lab by exact id across both classic and extended sets. */
    fun findById(id: String): LabExperiment? = allLabs.firstOrNull { it.id == id }

    /** The group a lab belongs to, or null if [id] is not a known lab. */
    fun groupOf(id: String): Group? = groups.firstOrNull { g -> g.variations.any { it.id == id } }

    /** Returns the human-readable display name for an experiment. */
    fun displayNameOf(lab: LabExperiment): String = lab.displayName

    /**
     * The key a lab groups under: its `expNN` prefix if it has one, else its whole id.
     *
     * A classic lab id like `lab7_half_adder` has no `expNN_` prefix and so becomes its own key —
     * which is correct, since the classic labs ship one build each.
     */
    private fun groupKeyOf(lab: LabExperiment): String {
        val id = lab.id
        if (!id.startsWith("exp")) return id
        val underscore = id.indexOf('_')
        return if (underscore > 3) id.substring(0, underscore) else id
    }
}

/**
 * Clean, human-readable display name for truth-table verification, catalog search, and dialog chips.
 * Maps every experiment across the 12 classic labs and 37 extended curriculum experiments to a
 * concise, descriptive title.
 */
val LabExperiment.displayName: String
    get() = when (id) {
        // Classic Labs (12)
        "lab1_inverter" -> "Inverter / NOT Gate"
        "lab2_and_gate" -> "AND Gate"
        "lab3_or_gate" -> "OR Gate"
        "lab4_nand_gate" -> "NAND Gate"
        "lab5_nor_gate" -> "NOR Gate"
        "lab6_xor_gate" -> "XOR Gate"
        "lab7_half_adder" -> "Half Adder Sum & Carry"
        "lab8_full_adder" -> "Full Adder (7483)"
        "lab9_d_flip_flop" -> "D Flip-Flop (7474)"
        "lab10_jk_flip_flop" -> "JK Flip-Flop (7476)"
        "lab11_bcd_decoder" -> "BCD to 7-Segment (7448)"
        "lab12_quad_nand_latch" -> "SR Latch from NAND"

        // Discrete Gate Labs (3)
        "exp01_diode_or" -> "Diode OR Gate"
        "exp01_diode_and" -> "Diode AND Gate"
        "exp01_rtl_not" -> "RTL NOT Gate"

        // Combinational Logic Labs (8)
        "exp02_nand_universal" -> "Universal NAND Logic (7400)"
        "exp02_nor_universal" -> "Universal NOR Logic (7402)"
        "exp03_unsimplified" -> "Boolean Simplification (Unsimplified)"
        "exp03_simplified" -> "Boolean Simplification (Simplified)"
        "exp04_half_adder" -> "Half Adder (7486, 7408)"
        "exp04_full_adder" -> "Full Adder (7486, 7408, 7432)"
        "exp05_half_subtractor" -> "Half Subtractor"
        "exp05_full_subtractor" -> "Full Subtractor"

        // Code Converters & Decoders (10)
        "exp06_binary_to_gray" -> "Binary-to-Gray Converter"
        "exp06_gray_to_binary" -> "Gray-to-Binary Converter"
        "exp07_octal_decoder_7411" -> "3-to-8 Line Decoder (7411)"
        "exp07_octal_decoder_7410" -> "3-to-8 Line Decoder (7410)"
        "exp08_comparator_74266" -> "2-Bit Magnitude Comparator (74266)"
        "exp08_comparator_7486" -> "2-Bit Magnitude Comparator (7486)"
        "exp09_mux_4to1" -> "4:1 Multiplexer"
        "exp09_demux_1to4" -> "1:4 Demultiplexer"
        "exp18_bcd_seven_segment_7448" -> "BCD to 7-Segment Decoder"
        "exp18_bcd_seven_segment_controls" -> "BCD to 7-Segment Controls"

        // Sequential Logic Labs (4)
        "exp10_clocked_sr_trainer_clock" -> "Clocked S-R Flip-Flop (Clock)"
        "exp10_clocked_sr_pulser" -> "Clocked S-R Flip-Flop (Pulser)"
        "exp11_ripple_counter_trainer_clock" -> "2-Bit Ripple Counter (Clock)"
        "exp11_ripple_counter_pulser" -> "2-Bit Ripple Counter (Pulser)"

        // D/A Converters (2)
        "exp12_passive_ladder" -> "Passive Weighted Ladder DAC"
        "exp12_opamp_summing" -> "Op-Amp Summing DAC"

        // Basic Gate Adder & Logic Synthesis (10)
        "exp13_half_adder_basic_sum" -> "Half Adder Sum (Basic Gates)"
        "exp13_half_adder_basic_carry" -> "Half Adder Carry (7408)"
        "exp14_full_adder_basic_sum" -> "Full Adder Sum"
        "exp14_full_adder_basic_carry" -> "Full Adder Carry-Out"
        "exp15_xnor_74266" -> "XNOR Equality Gate (74266)"
        "exp15_xnor_7486_7404" -> "XNOR from XOR & Inverter"
        "exp16_buffer_7404" -> "Non-Inverting Buffer (7404)"
        "exp16_buffer_7432" -> "Non-Inverting Buffer (7432)"
        "exp17_quad_nand_7420" -> "4-Input NAND Gate (7420)"
        "exp17_quad_and_7420_7404" -> "4-Input AND from NAND (7420)"

        else -> title
    }

