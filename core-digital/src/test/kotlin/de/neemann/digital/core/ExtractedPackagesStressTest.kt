package de.neemann.digital.core

import de.neemann.digital.core.arithmetic.Add
import de.neemann.digital.core.arithmetic.Comparator
import de.neemann.digital.core.arithmetic.Sub
import de.neemann.digital.core.basic.And
import de.neemann.digital.core.basic.NAnd
import de.neemann.digital.core.basic.Not
import de.neemann.digital.core.basic.Or
import de.neemann.digital.core.basic.XOr
import de.neemann.digital.core.element.ElementAttributes
import de.neemann.digital.core.element.Keys
import de.neemann.digital.core.wiring.Decoder
import de.neemann.digital.core.wiring.Demultiplexer
import de.neemann.digital.core.wiring.Driver
import de.neemann.digital.core.wiring.Multiplexer
import de.neemann.digital.core.wiring.Splitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adversarial stress tests across all extracted core simulation packages:
 * - Multi-input & multi-bit gates (3-in NAND 7410, 4-in NAND 7420, 8-bit bus logic)
 * - Arithmetic components (Add 4-bit 7483 exhaustive 512-vector, Sub, Comparator signed/unsigned)
 * - Wiring and switching components (Multiplexer, Demultiplexer, Decoder, Splitter, Driver tri-state)
 */
class ExtractedPackagesStressTest {

    @Test
    fun test3InputNAnd_7410ExhaustiveTruthTable() {
        val model = Model()
        val inA = ObservableValue("A", 1)
        val inB = ObservableValue("B", 1)
        val inC = ObservableValue("C", 1)

        val attr = ElementAttributes()
            .set(Keys.BITS, 1)
            .set(Keys.INPUT_COUNT, 3)
        val nand3 = model.add(NAnd(attr))
        nand3.setInputs(ObservableValues(inA, inB, inC))
        model.init(false)

        val out = nand3.outputs[0]

        // 2^3 = 8 combinations
        for (a in 0L..1L) {
            for (b in 0L..1L) {
                for (c in 0L..1L) {
                    inA.setValue(a)
                    inB.setValue(b)
                    inC.setValue(c)
                    model.doStep()
                    val expected = if (a == 1L && b == 1L && c == 1L) 0L else 1L
                    assertEquals("3-in NAND mismatch for ($a, $b, $c)", expected, out.value and 1L)
                }
            }
        }
    }

    @Test
    fun test4InputNAnd_7420ExhaustiveTruthTable() {
        val model = Model()
        val inputs = Array(4) { i -> ObservableValue("IN_$i", 1) }

        val attr = ElementAttributes()
            .set(Keys.BITS, 1)
            .set(Keys.INPUT_COUNT, 4)
        val nand4 = model.add(NAnd(attr))
        nand4.setInputs(ObservableValues(*inputs))
        model.init(false)

        val out = nand4.outputs[0]

        // 2^4 = 16 combinations
        for (i in 0 until 16) {
            for (bit in 0 until 4) {
                val bitVal = ((i shr bit) and 1).toLong()
                inputs[bit].setValue(bitVal)
            }
            model.doStep()
            val expected = if (i == 15) 0L else 1L
            assertEquals("4-in NAND mismatch for index $i", expected, out.value and 1L)
        }
    }

    @Test
    fun testMultiBitLogicGates_8BitBus() {
        val model = Model()
        val inA = ObservableValue("A", 8)
        val inB = ObservableValue("B", 8)

        val attr = ElementAttributes().set(Keys.BITS, 8).set(Keys.INPUT_COUNT, 2)
        val andGate = model.add(And(attr))
        andGate.setInputs(ObservableValues(inA, inB))

        val orGate = model.add(Or(attr))
        orGate.setInputs(ObservableValues(inA, inB))

        val xorGate = model.add(XOr(attr))
        xorGate.setInputs(ObservableValues(inA, inB))

        val notAttr = ElementAttributes().set(Keys.BITS, 8)
        val notGate = model.add(Not(notAttr))
        notGate.setInputs(ObservableValues(inA))

        model.init(false)

        val testPairs = listOf(
            Pair(0x00L, 0x00L),
            Pair(0xFFL, 0x00L),
            Pair(0x55L, 0xAAL),
            Pair(0x0FL, 0xF0L),
            Pair(0x3CL, 0xC3L),
            Pair(0xFFL, 0xFFL)
        )

        for ((a, b) in testPairs) {
            inA.setValue(a)
            inB.setValue(b)
            model.doStep()

            val expectedAnd = (a and b) and 0xFFL
            val expectedOr = (a or b) and 0xFFL
            val expectedXor = (a xor b) and 0xFFL
            val expectedNot = (a.inv()) and 0xFFL

            assertEquals("8-bit AND mismatch for ($a, $b)", expectedAnd, andGate.outputs[0].value and 0xFFL)
            assertEquals("8-bit OR mismatch for ($a, $b)", expectedOr, orGate.outputs[0].value and 0xFFL)
            assertEquals("8-bit XOR mismatch for ($a, $b)", expectedXor, xorGate.outputs[0].value and 0xFFL)
            assertEquals("8-bit NOT mismatch for $a", expectedNot, notGate.outputs[0].value and 0xFFL)
        }
    }

    @Test
    fun test4BitAdder_7483Exhaustive512Vectors() {
        val model = Model()
        val inA = ObservableValue("A", 4)
        val inB = ObservableValue("B", 4)
        val cIn = ObservableValue("Cin", 1)

        val attr = ElementAttributes().set(Keys.BITS, 4)
        val adder = model.add(Add(attr))
        adder.setInputs(ObservableValues(inA, inB, cIn))
        model.init(false)

        val sum = adder.outputs[0]
        val cOut = adder.outputs[1]

        // Test all 16 * 16 * 2 = 512 combinations
        for (a in 0L..15L) {
            for (b in 0L..15L) {
                for (cin in 0L..1L) {
                    inA.setValue(a)
                    inB.setValue(b)
                    cIn.setValue(cin)
                    model.doStep()

                    val expectedTotal = a + b + cin
                    val expectedSum = expectedTotal and 0x0FL
                    val expectedCout = if (expectedTotal >= 16L) 1L else 0L

                    assertEquals("Sum mismatch for $a + $b + $cin", expectedSum, sum.value and 0x0FL)
                    assertEquals("Cout mismatch for $a + $b + $cin", expectedCout, cOut.value and 1L)
                }
            }
        }
    }

    @Test
    fun test64BitAdder_ExtremeCarryBoundary() {
        val model = Model()
        val inA = ObservableValue("A", 64)
        val inB = ObservableValue("B", 64)
        val cIn = ObservableValue("Cin", 1)

        val attr = ElementAttributes().set(Keys.BITS, 64)
        val adder = model.add(Add(attr))
        adder.setInputs(ObservableValues(inA, inB, cIn))
        model.init(false)

        val sum = adder.outputs[0]
        val cOut = adder.outputs[1]

        // 0xFFFFFFFFFFFFFFFF + 1 with cin=0 -> sum=0, cout=1
        inA.setValue(-1L) // all 1s
        inB.setValue(1L)
        cIn.setValue(0L)
        model.doStep()

        assertEquals("64-bit overflow sum", 0L, sum.value)
        assertEquals("64-bit overflow cout", 1L, cOut.value and 1L)

        // 0x7FFFFFFFFFFFFFFF + 0x7FFFFFFFFFFFFFFF + 1 -> sum = 0xFFFFFFFFFFFFFFFF, cout = 0
        inA.setValue(Long.MAX_VALUE)
        inB.setValue(Long.MAX_VALUE)
        cIn.setValue(1L)
        model.doStep()

        assertEquals("64-bit max sum without carry", -1L, sum.value)
        assertEquals("64-bit cout without carry", 0L, cOut.value and 1L)
    }

    @Test
    fun testSubtractor_4Bit() {
        val model = Model()
        val inA = ObservableValue("A", 4)
        val inB = ObservableValue("B", 4)
        val bIn = ObservableValue("Bin", 1)

        val attr = ElementAttributes().set(Keys.BITS, 4)
        val sub = model.add(Sub(attr))
        sub.setInputs(ObservableValues(inA, inB, bIn))
        model.init(false)

        val diff = sub.outputs[0]
        val bOut = sub.outputs[1]

        // 7 - 3 - 0 = 4, borrow=0
        inA.setValue(7); inB.setValue(3); bIn.setValue(0)
        model.doStep()
        assertEquals(4L, diff.value and 0x0FL)
        assertEquals(0L, bOut.value and 1L)

        // 3 - 7 - 0 = -4 (12 in 4-bit two's complement), borrow=1
        inA.setValue(3); inB.setValue(7); bIn.setValue(0)
        model.doStep()
        assertEquals(12L, diff.value and 0x0FL)
        assertEquals(1L, bOut.value and 1L)

        // 0 - 0 - 1 = -1 (15 in 4-bit), borrow=1
        inA.setValue(0); inB.setValue(0); bIn.setValue(1)
        model.doStep()
        assertEquals(15L, diff.value and 0x0FL)
        assertEquals(1L, bOut.value and 1L)
    }

    @Test
    fun testComparator_SignedAndUnsigned() {
        val model = Model()
        val inA = ObservableValue("A", 8)
        val inB = ObservableValue("B", 8)

        // Unsigned comparator
        val attrUnsigned = ElementAttributes().set(Keys.BITS, 8).set(Keys.SIGNED, false)
        val compUnsigned = model.add(Comparator(attrUnsigned))
        compUnsigned.setInputs(ObservableValues(inA, inB))

        // Signed comparator
        val attrSigned = ElementAttributes().set(Keys.BITS, 8).set(Keys.SIGNED, true)
        val compSigned = model.add(Comparator(attrSigned))
        compSigned.setInputs(ObservableValues(inA, inB))

        model.init(false)

        val gtU = compUnsigned.outputs[0]
        val eqU = compUnsigned.outputs[1]
        val ltU = compUnsigned.outputs[2]

        val gtS = compSigned.outputs[0]
        val eqS = compSigned.outputs[1]
        val ltS = compSigned.outputs[2]

        // 1. Equal: A=42, B=42
        inA.setValue(42); inB.setValue(42)
        model.doStep()
        assertEquals(1L, eqU.value and 1L); assertEquals(0L, gtU.value and 1L); assertEquals(0L, ltU.value and 1L)
        assertEquals(1L, eqS.value and 1L); assertEquals(0L, gtS.value and 1L); assertEquals(0L, ltS.value and 1L)

        // 2. 0xFF vs 0x01:
        // Unsigned: 255 > 1 (gt=1)
        // Signed (8-bit): -1 < +1 (lt=1)
        inA.setValue(0xFF); inB.setValue(0x01)
        model.doStep()
        assertEquals("Unsigned: 255 > 1", 1L, gtU.value and 1L)
        assertEquals(0L, eqU.value and 1L)
        assertEquals(0L, ltU.value and 1L)

        assertEquals("Signed: -1 < 1", 1L, ltS.value and 1L)
        assertEquals(0L, eqS.value and 1L)
        assertEquals(0L, gtS.value and 1L)
    }

    @Test
    fun testMultiplexer_4to1() {
        val model = Model()
        val sel = ObservableValue("SEL", 2)
        val in0 = ObservableValue("IN0", 8)
        val in1 = ObservableValue("IN1", 8)
        val in2 = ObservableValue("IN2", 8)
        val in3 = ObservableValue("IN3", 8)

        val attr = ElementAttributes().set(Keys.BITS, 8).set(Keys.SELECTOR_BITS, 2)
        val mux = model.add(Multiplexer(attr))
        mux.setInputs(ObservableValues(sel, in0, in1, in2, in3))
        model.init(false)

        val out = mux.outputs[0]

        in0.setValue(0x11)
        in1.setValue(0x22)
        in2.setValue(0x33)
        in3.setValue(0x44)

        sel.setValue(0); model.doStep(); assertEquals(0x11L, out.value)
        sel.setValue(1); model.doStep(); assertEquals(0x22L, out.value)
        sel.setValue(2); model.doStep(); assertEquals(0x33L, out.value)
        sel.setValue(3); model.doStep(); assertEquals(0x44L, out.value)
    }

    @Test
    fun testDemultiplexer_1to4() {
        val model = Model()
        val sel = ObservableValue("SEL", 2)
        val dataIn = ObservableValue("DATA", 8)

        val attr = ElementAttributes().set(Keys.BITS, 8).set(Keys.SELECTOR_BITS, 2).set(Keys.DEFAULT, 0L)
        val demux = model.add(Demultiplexer(attr))
        demux.setInputs(ObservableValues(sel, dataIn))
        model.init(false)

        dataIn.setValue(0xAB)

        for (target in 0 until 4) {
            sel.setValue(target.toLong())
            model.doStep()

            for (i in 0 until 4) {
                val expected = if (i == target) 0xABL else 0L
                assertEquals("Demux channel $i for sel=$target", expected, demux.outputs[i].value)
            }
        }
    }

    @Test
    fun testDecoder_2to4() {
        val model = Model()
        val sel = ObservableValue("SEL", 2)

        val attr = ElementAttributes().set(Keys.SELECTOR_BITS, 2)
        val decoder = model.add(Decoder(attr))
        decoder.setInputs(ObservableValues(sel))
        model.init(false)

        for (target in 0 until 4) {
            sel.setValue(target.toLong())
            model.doStep()

            for (i in 0 until 4) {
                val expected = if (i == target) 1L else 0L
                assertEquals("Decoder output $i for sel=$target", expected, decoder.outputs[i].value and 1L)
            }
        }
    }

    @Test
    fun testDriver_TriStateBehavior() {
        val model = Model()
        val inVal = ObservableValue("IN", 8)
        val selVal = ObservableValue("SEL", 1)

        val attr = ElementAttributes().set(Keys.BITS, 8).set(Keys.INVERT_DRIVER_OUTPUT, false)
        val driver = model.add(Driver(attr))
        driver.setInputs(ObservableValues(inVal, selVal))
        model.init(false)

        val out = driver.outputs[0]

        // When SEL=0, output must be High-Z
        inVal.setValue(0x7F)
        selVal.setValue(0)
        model.doStep()
        assertTrue("Output must be High-Z when SEL=0", out.isHighZ)

        // When SEL=1, output must be driven with input value
        selVal.setValue(1)
        model.doStep()
        assertFalse("Output must NOT be High-Z when SEL=1", out.isHighZ)
        assertEquals(0x7FL, out.value)

        // Change input while enabled
        inVal.setValue(0x33)
        model.doStep()
        assertEquals(0x33L, out.value)

        // Disable again -> back to High-Z
        selVal.setValue(0)
        model.doStep()
        assertTrue("Output must return to High-Z when SEL=0", out.isHighZ)
    }

    @Test
    fun testSplitter_8BitToNibblesAndMerge() {
        val model = Model()
        val byteIn = ObservableValue("BYTE_IN", 8)

        // Split 8 bits into two 4-bit nibbles: "4,4"
        val splitAttr = ElementAttributes()
            .set(Keys.INPUT_SPLIT, "8")
            .set(Keys.OUTPUT_SPLIT, "4,4")
        val splitter = Splitter(splitAttr)
        splitter.setInputs(ObservableValues(byteIn))
        splitter.init(model)

        val lowNibble = splitter.outputs[0]
        val highNibble = splitter.outputs[1]

        // Merge two 4-bit nibbles back into 8 bits: "4,4" -> "8"
        val mergeAttr = ElementAttributes()
            .set(Keys.INPUT_SPLIT, "4,4")
            .set(Keys.OUTPUT_SPLIT, "8")
        val merger = Splitter(mergeAttr)
        merger.setInputs(ObservableValues(lowNibble, highNibble))
        merger.init(model)

        val byteOut = merger.outputs[0]

        byteIn.setValue(0xA5)
        assertEquals(0x05L, lowNibble.value and 0x0FL)
        assertEquals(0x0AL, highNibble.value and 0x0FL)
        assertEquals(0xA5L, byteOut.value and 0xFFL)

        byteIn.setValue(0xF0)
        assertEquals(0x00L, lowNibble.value and 0x0FL)
        assertEquals(0x0FL, highNibble.value and 0x0FL)
        assertEquals(0xF0L, byteOut.value and 0xFFL)
    }
}
