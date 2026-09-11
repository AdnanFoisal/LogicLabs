package de.neemann.digital.core.basic

import de.neemann.digital.core.Model
import de.neemann.digital.core.ObservableValue
import de.neemann.digital.core.ObservableValues
import de.neemann.digital.core.element.ElementAttributes
import de.neemann.digital.core.element.Keys
import org.junit.Assert.assertEquals
import org.junit.Test

class BasicGatesTest {

    private fun testGate2Inputs(
        gateFactory: (ElementAttributes) -> Function,
        expectedOutputs: List<Long> // for (0,0), (0,1), (1,0), (1,1)
    ) {
        val model = Model()
        val inA = ObservableValue("A", 1)
        val inB = ObservableValue("B", 1)

        val attr = ElementAttributes()
        attr.set(Keys.BITS, 1)
        attr.set(Keys.INPUT_COUNT, 2)
        val gate = model.add(gateFactory(attr))
        gate.setInputs(ObservableValues(inA, inB))
        model.init(false)

        val out = gate.outputs[0]

        val inputs = listOf(
            Pair(0L, 0L),
            Pair(0L, 1L),
            Pair(1L, 0L),
            Pair(1L, 1L)
        )

        for (i in inputs.indices) {
            val (a, b) = inputs[i]
            inA.setValue(a)
            inB.setValue(b)
            model.doStep()
            assertEquals("Mismatch for input ($a, $b)", expectedOutputs[i], out.value and 1L)
        }
    }

    @Test
    fun testAndGate() {
        testGate2Inputs(::And, listOf(0L, 0L, 0L, 1L))
    }

    @Test
    fun testNAndGate() {
        testGate2Inputs(::NAnd, listOf(1L, 1L, 1L, 0L))
    }

    @Test
    fun testOrGate() {
        testGate2Inputs(::Or, listOf(0L, 1L, 1L, 1L))
    }

    @Test
    fun testNOrGate() {
        testGate2Inputs(::NOr, listOf(1L, 0L, 0L, 0L))
    }

    @Test
    fun testXOrGate() {
        testGate2Inputs(::XOr, listOf(0L, 1L, 1L, 0L))
    }

    @Test
    fun testXNOrGate() {
        testGate2Inputs(::XNOr, listOf(1L, 0L, 0L, 1L))
    }

    @Test
    fun testNotGate() {
        val model = Model()
        val inA = ObservableValue("A", 1)

        val attr = ElementAttributes()
        attr.set(Keys.BITS, 1)
        val notGate = model.add(Not(attr))
        notGate.setInputs(ObservableValues(inA))
        model.init(false)

        val out = notGate.outputs[0]

        // 0 -> 1
        inA.setValue(0)
        model.doStep()
        assertEquals(1L, out.value and 1L)

        // 1 -> 0
        inA.setValue(1)
        model.doStep()
        assertEquals(0L, out.value and 1L)

        // 0 -> 1 again
        inA.setValue(0)
        model.doStep()
        assertEquals(1L, out.value and 1L)
    }
}
