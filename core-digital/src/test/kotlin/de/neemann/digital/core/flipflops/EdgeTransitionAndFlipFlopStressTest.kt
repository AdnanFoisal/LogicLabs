package de.neemann.digital.core.flipflops

import de.neemann.digital.core.Model
import de.neemann.digital.core.ObservableValue
import de.neemann.digital.core.ObservableValues
import de.neemann.digital.core.element.ElementAttributes
import de.neemann.digital.core.element.Keys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adversarial stress tests for edge-triggered flip-flops:
 * - Positive-edge clock transition trigger verification
 * - Level insensitivity (clock held high / clock held low)
 * - Setup and hold stability across cycles
 * - Asynchronous Set / Clear overrides (7474 and 7476 models)
 * - T flip-flop toggle and enable semantics
 */
class EdgeTransitionAndFlipFlopStressTest {

    @Test
    fun testFlipFlopD_RisingEdgeClockTriggerOnly() {
        val model = Model()
        val dIn = ObservableValue("D", 1)
        val clkIn = ObservableValue("CLK", 1)

        val attr = ElementAttributes()
            .set(Keys.BITS, 1)
            .set(Keys.DEFAULT, 0L)
        val dff = model.add(FlipflopD(attr))
        dff.setInputs(ObservableValues(dIn, clkIn))
        model.init(false)

        val q = dff.outputs[0]
        val qn = dff.outputs[1]

        // Initial state: Q=0, ~Q=1
        assertEquals(0L, q.value and 1L)
        assertEquals(1L, qn.value and 1L)

        // 1. D goes HIGH while CLK is LOW: Output MUST NOT change
        dIn.setValue(1)
        model.doStep()
        assertEquals("Q must remain 0 while clock is LOW", 0L, q.value and 1L)

        // 2. Rising clock edge (0 -> 1): Q MUST latch D (1)
        clkIn.setValue(1)
        model.doStep()
        assertEquals("Q must latch 1 on rising edge", 1L, q.value and 1L)
        assertEquals("~Q must be 0", 0L, qn.value and 1L)

        // 3. D goes LOW while CLK is held HIGH: Output MUST NOT change
        dIn.setValue(0)
        model.doStep()
        assertEquals("Q must remain 1 while clock is held HIGH", 1L, q.value and 1L)

        // 4. Falling clock edge (1 -> 0): Output MUST NOT change
        clkIn.setValue(0)
        model.doStep()
        assertEquals("Q must remain 1 on falling clock edge", 1L, q.value and 1L)

        // 5. Rising clock edge (0 -> 1) with D=0: Q MUST latch 0
        clkIn.setValue(1)
        model.doStep()
        assertEquals("Q must latch 0 on next rising edge", 0L, q.value and 1L)
        assertEquals("~Q must be 1", 1L, qn.value and 1L)
    }

    @Test
    fun testFlipFlopDAsync_AsynchronousSetClearOverrides() {
        val model = Model()
        val setIn = ObservableValue("SET", 1)
        val dIn = ObservableValue("D", 1)
        val clkIn = ObservableValue("CLK", 1)
        val clrIn = ObservableValue("CLR", 1)

        val attr = ElementAttributes()
            .set(Keys.BITS, 1)
            .set(Keys.DEFAULT, 0L)
        val dffAsync = model.add(FlipflopDAsync(attr))
        dffAsync.setInputs(ObservableValues(setIn, dIn, clkIn, clrIn))
        model.init(false)

        val q = dffAsync.outputs[0]
        val qn = dffAsync.outputs[1]

        // Initially 0
        assertEquals(0L, q.value and 1L)

        // 1. Asynchronous SET=1 while clock=0 and D=0 -> Q MUST immediately become 1
        setIn.setValue(1)
        model.doStep()
        assertEquals("Async SET must force Q=1 without clock edge", 1L, q.value and 1L)
        assertEquals(0L, qn.value and 1L)

        // 2. Clear SET, Q must stay 1
        setIn.setValue(0)
        model.doStep()
        assertEquals("Q must stay 1 after SET is de-asserted", 1L, q.value and 1L)

        // 3. Asynchronous CLR=1 while clock=0 and D=1 -> Q MUST immediately become 0
        dIn.setValue(1)
        clrIn.setValue(1)
        model.doStep()
        assertEquals("Async CLR must force Q=0 without clock edge", 0L, q.value and 1L)
        assertEquals(1L, qn.value and 1L)

        // 4. While CLR=1, pulsed clock edges with D=1 MUST NOT set Q
        clkIn.setValue(1)
        model.doStep()
        assertEquals("CLR must override rising clock edge", 0L, q.value and 1L)
        clkIn.setValue(0)
        model.doStep()

        // 5. Release CLR, clock edge should now work normally
        clrIn.setValue(0)
        model.doStep()
        assertEquals(0L, q.value and 1L)

        clkIn.setValue(1)
        model.doStep()
        assertEquals("Normal clock latching must resume after CLR release", 1L, q.value and 1L)
    }

    @Test
    fun testFlipFlopJK_TruthTableAndToggleMode() {
        val model = Model()
        val jIn = ObservableValue("J", 1)
        val clkIn = ObservableValue("CLK", 1)
        val kIn = ObservableValue("K", 1)

        val attr = ElementAttributes()
            .set(Keys.BITS, 1)
            .set(Keys.DEFAULT, 0L)
        val jk = model.add(FlipflopJK(attr))
        jk.setInputs(ObservableValues(jIn, clkIn, kIn))
        model.init(false)

        val q = jk.outputs[0]
        val qn = jk.outputs[1]

        fun pulseClock() {
            clkIn.setValue(0)
            model.doStep()
            clkIn.setValue(1)
            model.doStep()
        }

        // Initially Q=0
        assertEquals(0L, q.value and 1L)

        // J=0, K=0 -> No change (hold)
        jIn.setValue(0); kIn.setValue(0)
        pulseClock()
        assertEquals("J=0, K=0 must hold state (0)", 0L, q.value and 1L)

        // J=1, K=0 -> Set
        jIn.setValue(1); kIn.setValue(0)
        pulseClock()
        assertEquals("J=1, K=0 must set Q to 1", 1L, q.value and 1L)
        assertEquals(0L, qn.value and 1L)

        // J=0, K=0 -> Hold (1)
        jIn.setValue(0); kIn.setValue(0)
        pulseClock()
        assertEquals("J=0, K=0 must hold state (1)", 1L, q.value and 1L)

        // J=0, K=1 -> Reset
        jIn.setValue(0); kIn.setValue(1)
        pulseClock()
        assertEquals("J=0, K=1 must reset Q to 0", 0L, q.value and 1L)

        // J=1, K=1 -> Toggle mode across multiple clock cycles
        jIn.setValue(1); kIn.setValue(1)

        pulseClock()
        assertEquals("Toggle 1: Q should be 1", 1L, q.value and 1L)

        pulseClock()
        assertEquals("Toggle 2: Q should be 0", 0L, q.value and 1L)

        pulseClock()
        assertEquals("Toggle 3: Q should be 1", 1L, q.value and 1L)

        pulseClock()
        assertEquals("Toggle 4: Q should be 0", 0L, q.value and 1L)

        // Verify level insensitivity: holding clock HIGH should NOT toggle
        clkIn.setValue(1)
        for (step in 0 until 5) {
            model.doStep()
            assertEquals("Q must not toggle when clock is held steady", 0L, q.value and 1L)
        }
    }

    @Test
    fun testFlipFlopJKAsync_PresetClearOverrides() {
        val model = Model()
        val setIn = ObservableValue("SET", 1)
        val jIn = ObservableValue("J", 1)
        val clkIn = ObservableValue("CLK", 1)
        val kIn = ObservableValue("K", 1)
        val clrIn = ObservableValue("CLR", 1)

        val attr = ElementAttributes()
            .set(Keys.BITS, 1)
            .set(Keys.DEFAULT, 0L)
        val jkAsync = model.add(FlipflopJKAsync(attr))
        jkAsync.setInputs(ObservableValues(setIn, jIn, clkIn, kIn, clrIn))
        model.init(false)

        val q = jkAsync.outputs[0]

        // Async SET
        setIn.setValue(1)
        model.doStep()
        assertEquals("Async SET must set Q=1", 1L, q.value and 1L)

        setIn.setValue(0)
        model.doStep()
        assertEquals(1L, q.value and 1L)

        // Async CLR
        clrIn.setValue(1)
        model.doStep()
        assertEquals("Async CLR must reset Q=0", 0L, q.value and 1L)

        clrIn.setValue(0)
        model.doStep()
        assertEquals(0L, q.value and 1L)
    }

    @Test
    fun testFlipFlopRS_TruthTable() {
        val model = Model()
        val sIn = ObservableValue("S", 1)
        val clkIn = ObservableValue("CLK", 1)
        val rIn = ObservableValue("R", 1)

        val attr = ElementAttributes()
            .set(Keys.BITS, 1)
            .set(Keys.DEFAULT, 0L)
        val rs = model.add(FlipflopRS(attr))
        rs.setInputs(ObservableValues(sIn, clkIn, rIn))
        model.init(false)

        val q = rs.outputs[0]

        fun pulseClock() {
            clkIn.setValue(0)
            model.doStep()
            clkIn.setValue(1)
            model.doStep()
        }

        // S=1, R=0 -> Set
        sIn.setValue(1); rIn.setValue(0)
        pulseClock()
        assertEquals(1L, q.value and 1L)

        // S=0, R=0 -> Hold
        sIn.setValue(0); rIn.setValue(0)
        pulseClock()
        assertEquals(1L, q.value and 1L)

        // S=0, R=1 -> Reset
        sIn.setValue(0); rIn.setValue(1)
        pulseClock()
        assertEquals(0L, q.value and 1L)

        // S=0, R=0 -> Hold
        sIn.setValue(0); rIn.setValue(0)
        pulseClock()
        assertEquals(0L, q.value and 1L)
    }

    @Test
    fun testFlipFlopT_ToggleWithEnable() {
        val model = Model()
        val enIn = ObservableValue("EN", 1)
        val clkIn = ObservableValue("CLK", 1)

        val attr = ElementAttributes()
            .set(Keys.BITS, 1)
            .set(Keys.WITH_ENABLE, true)
            .set(Keys.DEFAULT, 0L)
        val tff = model.add(FlipflopT(attr))
        tff.setInputs(ObservableValues(enIn, clkIn))
        model.init(false)

        val q = tff.outputs[0]

        fun pulseClock() {
            clkIn.setValue(0)
            model.doStep()
            clkIn.setValue(1)
            model.doStep()
        }

        // Initially 0
        assertEquals(0L, q.value and 1L)

        // EN=0: pulses should NOT toggle
        enIn.setValue(0)
        pulseClock()
        pulseClock()
        assertEquals("Q must remain 0 when EN=0", 0L, q.value and 1L)

        // EN=1: pulses toggle
        enIn.setValue(1)
        pulseClock()
        assertEquals("Q toggles to 1", 1L, q.value and 1L)
        pulseClock()
        assertEquals("Q toggles to 0", 0L, q.value and 1L)
        pulseClock()
        assertEquals("Q toggles to 1", 1L, q.value and 1L)

        // EN=0: holds state at 1
        enIn.setValue(0)
        pulseClock()
        assertEquals("Q remains 1 when EN is turned off", 1L, q.value and 1L)
    }
}
