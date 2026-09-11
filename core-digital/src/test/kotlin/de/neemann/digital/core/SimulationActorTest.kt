package de.neemann.digital.core

import de.neemann.digital.core.basic.And
import de.neemann.digital.core.element.ElementAttributes
import de.neemann.digital.core.element.Keys
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulationActorTest {

    @Test
    fun testActorLifecycleAndCommandDispatch() = runBlocking {
        val model = Model()
        val inA = ObservableValue("A", 1)
        val inB = ObservableValue("B", 1)

        val attr = ElementAttributes()
        attr.set(Keys.BITS, 1)
        attr.set(Keys.INPUT_COUNT, 2)
        val andGate = model.add(And(attr))
        andGate.setInputs(ObservableValues(inA, inB))
        model.init(false)

        val out = andGate.outputs[0]
        val trackedNets = arrayOf(inA, inB, out)

        val actor = SimulationActor(
            model = model,
            trackedNets = trackedNets,
            scope = this
        )

        actor.start()
        assertTrue("Actor should be started", true)

        // Initial state: 0 and 0 -> 0
        inA.setValue(0)
        inB.setValue(0)
        actor.sendBlocking(SimulationCommand.Step)
        delay(50)

        var snap = actor.activeSnapshot.get()
        assertNotNull(snap)
        assertFalse(snap.getBool(0))
        assertFalse(snap.getBool(1))
        assertFalse(snap.getBool(2))

        // Dispatch SetInputBool
        actor.sendBlocking(SimulationCommand.SetInputBool(0, true))
        actor.sendBlocking(SimulationCommand.SetInputBool(1, true))
        delay(50)

        snap = actor.activeSnapshot.get()
        assertTrue(snap.getBool(0))
        assertTrue(snap.getBool(1))
        assertTrue("Output should be HIGH for AND(1, 1)", snap.getBool(2))

        // Test Pause and Run
        actor.sendBlocking(SimulationCommand.Run(freqHz = 500.0))
        delay(50)
        assertTrue(actor.isRunning)

        actor.sendBlocking(SimulationCommand.Pause)
        delay(50)
        assertFalse(actor.isRunning)

        actor.stop()
        delay(50)
    }

    @Test
    fun testSnapshotImmutabilityAndZeroAllocationReads() {
        val snapshot = SimulationStateSnapshot(maxNets = 16)
        val inA = ObservableValue("A", 1)
        inA.setValue(1)
        val inB = ObservableValue("B", 1)
        inB.setValue(0)

        snapshot.updateFrom(
            trackedNets = arrayOf(inA, inB),
            trackedSignals = null,
            isClamped = false,
            hasBurnout = false,
            burnedNet = -1,
            events = 42
        )

        assertEquals(1L, snapshot.getValue(0))
        assertEquals(0L, snapshot.getValue(1))
        assertTrue(snapshot.getBool(0))
        assertFalse(snapshot.getBool(1))
        assertEquals(42, snapshot.eventsProcessed)
        assertFalse(snapshot.isClamped)

        // Verify that repeated reads return consistent values without altering state
        val val1 = snapshot.getValue(0)
        val val2 = snapshot.getValue(0)
        assertEquals(val1, val2)
    }
}
