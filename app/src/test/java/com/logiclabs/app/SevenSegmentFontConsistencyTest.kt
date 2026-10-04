package com.logiclabs.app

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.feature.instruments.ui.hardware.SevenSegFont
import com.logiclabs.feature.tools.courseware.ExperimentCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Keeps every copy of the 7448 segment ROM honest against the data sheet, and against each other.
 *
 * The part is modelled four times over, for four different reasons:
 *
 *  1. `Chip7448` in `:core-bridge` — the simulation engine, the source of truth for logic.
 *  2. `Chip7448` in `:core-testing` — a deliberately decoupled harness model (that module has no
 *     dependency on `:core-bridge`), so it is a second hand-written copy of the same ROM.
 *  3. `FONT_7448` in `:feature-tools` — the copy `exp18_bcd_seven_segment_7448` grades against.
 *  4. `SevenSegFont.PATTERNS` in `:feature-instruments` — the view-layer glyph mirror, which
 *     draws what the student sees on the faceplate.
 *
 * Copies are fine; silent divergence is not. It has already happened once: codes 10, 12 and 13
 * lit segment `c` where the data sheet lights `f` (and code 10 lit an extra `c`), so the lab
 * graded a font no 7448 produces — and because code 3 and code 4 above were wrong *together*,
 * the truth-table sweep passed anyway. The engine-and-render pair is the dangerous one: it can
 * disagree without any test noticing, which is what this file exists to prevent.
 *
 * `:app` is the only module that can see all four, which is why the cross-check lives here.
 */
class SevenSegmentFontConsistencyTest {

    /**
     * The 7448's decode for all sixteen 4-bit codes, transcribed from the TI SDLS111 data sheet
     * truth table (`SN5446A, '47A, '48, SN54LS47, 'LS48, 'LS49`, sheet 5-61).
     *
     * Bit 0 = segment a (top) through bit 6 = segment g (middle). Codes 0-9 are the decimal
     * font; codes 10-15 are inputs the part was never meant to see but does decode, and the
     * data sheet prints those rows: 10 = d,e,g (0x58), 11 = c,d,g (0x4C), 12 = b,f,g (0x62),
     * 13 = a,d,f,g (0x69), 14 = d,e,f,g (0x78), and 15 blanks the digit (0x00).
     */
    private val datasheetFont = intArrayOf(
        0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07,
        0x7F, 0x6F, 0x58, 0x4C, 0x62, 0x69, 0x78, 0x00
    )

    private val hex = { v: Int -> "%02X".format(v and 0xFF) }

    /** The lab that exposes all four BCD bits on SW0..SW3 and all seven segments on LED0..LED6. */
    private fun decoderLab() =
        ExperimentCatalog.findById("exp18_bcd_seven_segment_7448")
            ?: error("exp18_bcd_seven_segment_7448 is missing from the catalog")

    /** Segment mask the engine's own 7448 drives for one code, read off the LEDs. */
    private fun engineFont(lab: com.logiclabs.feature.tools.courseware.LabExperiment): IntArray {
        val circuit = BreadboardCircuit().also { lab.buildCircuit(it) }
        return IntArray(16) { code ->
            for (bit in 0..3) circuit.switches[bit] = ((code shr bit) and 1) == 1
            circuit.step()
            (0..6).fold(0) { mask, seg -> if (circuit.ledValues[seg]) mask or (1 shl seg) else mask }
        }
    }

    /** Segment mask the lab's declared truth table promises for one code. */
    private fun declaredFont(lab: com.logiclabs.feature.tools.courseware.LabExperiment): IntArray =
        IntArray(16) { code ->
            val inputs = (0..3).map { bit -> ((code shr bit) and 1) == 1 }
            lab.expectedFunction(inputs)
                .foldIndexed(0) { seg, mask, lit -> if (lit) mask or (1 shl seg) else mask }
        }

    @Test
    fun engine7448DecodesAllSixteenCodesAsTheDataSheetSays() {
        val actual = engineFont(decoderLab())

        for (code in 0..15) {
            assertEquals(
                "engine 7448 output for code $code",
                hex(datasheetFont[code]),
                hex(actual[code])
            )
        }
    }

    @Test
    fun renderMirrorDrawsTheSameGlyphsAsTheEngine() {
        // A divergence here is invisible to every logic test in the project: the engine would
        // grade one glyph and the faceplate would draw another.
        for (code in 0..15) {
            assertEquals(
                "SevenSegFont mirror for code $code",
                hex(datasheetFont[code]),
                hex(SevenSegFont.segmentsFor(code))
            )
        }
    }

    @Test
    fun theLabGradesTheGlyphsThePartActuallyProduces() {
        val lab = decoderLab()

        assertEquals(
            "the lab's declared truth table disagrees with the engine's own 7448",
            engineFont(lab).toList(),
            declaredFont(lab).toList()
        )
    }

    @Test
    fun lampTestStillForcesEverySegmentOn() {
        // Guards the control pins around the corrected ROM: a font change must not disturb the
        // ~LT override the second variation of the same experiment grades.
        val lab = ExperimentCatalog.findById("exp18_bcd_seven_segment_controls")
            ?: error("exp18_bcd_seven_segment_controls is missing from the catalog")
        val circuit = BreadboardCircuit().also { lab.buildCircuit(it) }

        // ~LT is SW0 and is active LOW, so code 00000 asserts it and every one of the 32 rows
        // must show all seven segments lit regardless of the BCD word underneath.
        for (word in 0..15) {
            circuit.switches[0] = false
            for (bit in 0..3) circuit.switches[bit + 1] = ((word shr bit) and 1) == 1
            circuit.step()

            val mask = (0..6).fold(0) { acc, seg -> if (circuit.ledValues[seg]) acc or (1 shl seg) else acc }
            assertEquals("lamp test must light all segments for word $word", 0x7F, mask)
        }
    }
}
