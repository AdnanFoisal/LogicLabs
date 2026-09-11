package de.neemann.digital.core

import de.neemann.digital.core.basic.Not
import de.neemann.digital.core.element.ElementAttributes
import de.neemann.digital.core.element.Keys
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RingOscillatorSafeguardTest {

    private fun create3InverterRing(): Triple<Model, Array<Not>, Array<ObservableValue>> {
        val model = Model()

        val not1 = model.add(Not(ElementAttributes().apply { set(Keys.BITS, 1) }))
        val not2 = model.add(Not(ElementAttributes().apply { set(Keys.BITS, 1) }))
        val not3 = model.add(Not(ElementAttributes().apply { set(Keys.BITS, 1) }))

        val out1 = not1.outputs[0]
        val out2 = not2.outputs[0]
        val out3 = not3.outputs[0]

        // Wire in a 3-inverter closed loop:
        // NOT1 receives out3
        // NOT2 receives out1
        // NOT3 receives out2
        not1.setInputs(ObservableValues(out3))
        not2.setInputs(ObservableValues(out1))
        not3.setInputs(ObservableValues(out2))

        // Initialize model; ring-oscillator safeguard allows init to complete safely
        model.init(false)

        return Triple(model, arrayOf(not1, not2, not3), arrayOf(out1, out2, out3))
    }

    @Test(timeout = 5000)
    fun testRingOscillatorSafeguardDirectModel() {
        val (model, _, _) = create3InverterRing()

        // Calling doStepSafeguarded should cap at exactly 10,000 events without hanging or throwing fatal crash
        val result = model.doStepSafeguarded(10_000)

        assertTrue("Ring oscillator must be clamped at max events", result.isClamped)
        assertEquals("Should have processed exactly 10,000 events", 10_000, result.eventsProcessed)
        assertFalse("Should not report thermal burnout for simple inverters", result.hasThermalBurnout)
    }

    @Test(timeout = 5000)
    fun testRingOscillatorSafeguardViaActor() = runBlocking {
        val (model, _, outs) = create3InverterRing()

        val actor = SimulationActor(
            model = model,
            trackedNets = outs,
            scope = this
        )

        actor.start()

        // Step the actor on the ring oscillator
        actor.sendBlocking(SimulationCommand.Step)
        delay(100)

        val snapshot = actor.activeSnapshot.get()
        assertNotNull("Snapshot must be published", snapshot)
        assertTrue("Snapshot must report oscillation/clamping", snapshot.isClamped || snapshot.isOscillating)
        assertEquals(10_000, snapshot.eventsProcessed)

        actor.stop()
        delay(50)
    }
}
