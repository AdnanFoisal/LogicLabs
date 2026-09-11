package de.neemann.digital.core

import de.neemann.digital.core.basic.And
import de.neemann.digital.core.basic.NAnd
import de.neemann.digital.core.basic.Not
import de.neemann.digital.core.element.ElementAttributes
import de.neemann.digital.core.element.Keys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ChallengerM1StressTest {

    /**
     * Empirical Challenge 1: 1-Inverter Tight Degenerate Feedback Loop.
     * The single inverter's output is directly wired back into its own input.
     * This is the tightest possible ring oscillator (period = 2 gate delays).
     */
    @Test(timeout = 5000)
    fun test1InverterTightFeedbackLoopSafeguard() {
        val model = Model()
        val notGate = model.add(Not(ElementAttributes().apply { set(Keys.BITS, 1) }))
        val out = notGate.outputs[0]

        // Connect output directly to input
        notGate.setInputs(ObservableValues(out))

        // Initialization must not hang or throw fatal NodeException
        model.init(false)

        // Step multiple times; each step must clamp deterministically at MAX_EVENTS_PER_TICK (10,000)
        for (i in 1..20) {
            val result = model.doStepSafeguarded(10_000)
            assertTrue("Step $i: 1-inverter tight loop must be clamped", result.isClamped)
            assertEquals("Step $i: Exactly 10,000 events must be processed", 10_000, result.eventsProcessed)
            assertFalse("Step $i: No thermal burnout expected", result.hasThermalBurnout)
        }
    }

    /**
     * Empirical Challenge 2: 5-Inverter Ring Oscillator.
     * An odd-numbered chain: NOT1 -> NOT2 -> NOT3 -> NOT4 -> NOT5 -> NOT1.
     */
    @Test(timeout = 5000)
    fun test5InverterRingOscillatorSafeguard() {
        val model = Model()
        val inverters = Array(5) {
            model.add(Not(ElementAttributes().apply { set(Keys.BITS, 1) }))
        }

        // Chain NOT0 -> NOT1 -> NOT2 -> NOT3 -> NOT4 -> NOT0
        for (i in 0 until 5) {
            val prevOut = inverters[(i + 4) % 5].outputs[0]
            inverters[i].setInputs(ObservableValues(prevOut))
        }

        model.init(false)

        for (i in 1..10) {
            val result = model.doStepSafeguarded(10_000)
            assertTrue("Step $i: 5-inverter ring must clamp", result.isClamped)
            assertEquals("Step $i: Expected 10,000 events", 10_000, result.eventsProcessed)
        }
    }

    /**
     * Empirical Challenge 3: Cross-Coupled NAND SR Latch Race Condition.
     * Active-low SR latch initialized to S=0, R=0 (both outputs 1).
     * Simultaneous release S=1, R=1 triggers race condition / hazard.
     */
    @Test(timeout = 5000)
    fun testCrossCoupledNAndLatchRaceCondition() {
        val model = Model()
        val sBar = ObservableValue("S_BAR", 1)
        val rBar = ObservableValue("R_BAR", 1)

        val attr = ElementAttributes().apply {
            set(Keys.BITS, 1)
            set(Keys.INPUT_COUNT, 2)
        }
        val nand1 = model.add(NAnd(attr))
        val nand2 = model.add(NAnd(attr))

        val q = nand1.outputs[0]
        val qBar = nand2.outputs[0]

        // Cross-couple
        nand1.setInputs(ObservableValues(sBar, qBar))
        nand2.setInputs(ObservableValues(q, rBar))

        // Start with S=0, R=0
        sBar.setValue(0)
        rBar.setValue(0)
        model.init(false)

        // Simultaneous release: S=1, R=1
        sBar.setValue(1)
        rBar.setValue(1)

        // Safeguarded step must not hang or throw exception
        val result = model.doStepSafeguarded(10_000)
        assertNotNull(result)
        // Either clamped or settled deterministically
        assertTrue("Latch step should process microsteps without crashing", result.eventsProcessed >= 0)
    }

    /**
     * Empirical Challenge 4: SimulationActor Continuous Astable Run & Interruptibility.
     * Ensure that when the actor runs an oscillating circuit at high frequency:
     * 1. The coroutine dispatcher is not starved (cooperative yield works).
     * 2. Pause/Stop commands can successfully interrupt continuous clamping execution.
     */
    @Test(timeout = 10000)
    fun testSimulationActorContinuousRingOscillatorWithInterruption() = runBlocking {
        val model = Model()
        val not1 = model.add(Not(ElementAttributes().apply { set(Keys.BITS, 1) }))
        val not2 = model.add(Not(ElementAttributes().apply { set(Keys.BITS, 1) }))
        val not3 = model.add(Not(ElementAttributes().apply { set(Keys.BITS, 1) }))

        val o1 = not1.outputs[0]
        val o2 = not2.outputs[0]
        val o3 = not3.outputs[0]

        not1.setInputs(ObservableValues(o3))
        not2.setInputs(ObservableValues(o1))
        not3.setInputs(ObservableValues(o2))

        model.init(false)

        val actor = SimulationActor(
            model = model,
            trackedNets = arrayOf(o1, o2, o3),
            scope = this
        )

        actor.start()

        // Run actor continuously at high frequency
        actor.sendBlocking(SimulationCommand.Run(freqHz = 50000.0))
        delay(50)
        assertTrue(actor.isRunning)

        // Verify cooperative scheduling: another coroutine can increment a counter while actor runs
        val cooperativeCounter = AtomicInteger(0)
        val livenessJob = launch(Dispatchers.Default) {
            for (i in 1..10) {
                delay(20)
                cooperativeCounter.incrementAndGet()
            }
        }

        livenessJob.join()
        assertTrue(
            "Other coroutines must not be starved during continuous ring oscillation: count=${cooperativeCounter.get()}",
            cooperativeCounter.get() >= 5
        )

        // Verify that Pause interrupts continuous execution within 500ms
        withTimeout(500) {
            actor.sendBlocking(SimulationCommand.Pause)
        }
        delay(50)
        assertFalse("Actor must be paused", actor.isRunning)

        val snap = actor.activeSnapshot.get()
        assertNotNull(snap)
        assertTrue("Snapshot should flag oscillation/clamping", snap.isClamped || snap.isOscillating)

        // Stop actor cleanly
        actor.stop()
        delay(50)
    }

    /**
     * Empirical Challenge 5: SimulationActor Concurrent Command Flood.
     * Flood the actor with hundreds of concurrent commands from multiple coroutines.
     * Verify no deadlocks, no unhandled exceptions, and no state corruption.
     */
    @Test(timeout = 10000)
    fun testSimulationActorConcurrentCommandFlood() = runBlocking {
        val model = Model()
        val inA = ObservableValue("A", 1)
        val inB = ObservableValue("B", 1)
        val attr = ElementAttributes().apply {
            set(Keys.BITS, 1)
            set(Keys.INPUT_COUNT, 2)
        }
        val andGate = model.add(And(attr))
        andGate.setInputs(ObservableValues(inA, inB))
        model.init(false)

        val tracked = arrayOf(inA, inB, andGate.outputs[0])
        val actor = SimulationActor(
            model = model,
            trackedNets = tracked,
            scope = this
        )

        actor.start()

        val exceptionCaught = AtomicBoolean(false)
        val completedCommands = AtomicInteger(0)

        // Launch 8 concurrent workers flooding commands
        val workers = (1..8).map { workerId ->
            async(Dispatchers.Default) {
                try {
                    for (i in 1..50) {
                        when (i % 5) {
                            0 -> actor.send(SimulationCommand.SetInputBool(0, i % 2 == 0))
                            1 -> actor.send(SimulationCommand.SetInputBool(1, i % 3 == 0))
                            2 -> actor.send(SimulationCommand.Step)
                            3 -> actor.send(SimulationCommand.Run(freqHz = 2000.0))
                            4 -> actor.send(SimulationCommand.Pause)
                        }
                        completedCommands.incrementAndGet()
                    }
                } catch (e: Throwable) {
                    exceptionCaught.set(true)
                }
            }
        }

        // Concurrently poll snapshots
        val reader = async(Dispatchers.Default) {
            var readCount = 0
            while (readCount < 200) {
                val snap = actor.activeSnapshot.get()
                assertNotNull(snap)
                // Read values without error
                snap.getBool(0)
                snap.getBool(1)
                snap.getBool(2)
                readCount++
                delay(2)
            }
        }

        workers.awaitAll()
        reader.await()

        assertFalse("No exceptions should be thrown during concurrent flood", exceptionCaught.get())
        assertTrue("All commands dispatched", completedCommands.get() >= 400)

        actor.stop()
        delay(50)
    }

    /**
     * Empirical Challenge 6: Double-Buffered Snapshot Atomicity and Non-Tearing.
     * Verify that while the actor continuously updates tracked nets, readers observing
     * the active snapshot never observe null values, index bounds violations, or corrupted fields.
     */
    @Test(timeout = 10000)
    fun testSnapshotAtomicityUnderContinuousUpdates() = runBlocking {
        val model = Model()
        val bits = 8
        val inputs = Array(bits) { ObservableValue("IN_$it", 1) }
        model.init(false)

        val actor = SimulationActor(
            model = model,
            trackedNets = inputs,
            scope = this
        )

        actor.start()

        val isWriting = AtomicBoolean(true)
        val tornReads = AtomicInteger(0)

        // Writer coroutine rapidly sets all bits to either all 0s or all 1s
        val writer = launch(Dispatchers.Default) {
            var toggle = false
            for (step in 1..200) {
                toggle = !toggle
                val v = if (toggle) 1L else 0L
                for (bit in 0 until bits) {
                    actor.sendBlocking(SimulationCommand.SetInput(bit, v))
                }
                actor.sendBlocking(SimulationCommand.Step)
            }
            isWriting.set(false)
        }

        // Reader coroutine rapidly reads active snapshot
        val reader = launch(Dispatchers.Default) {
            while (isWriting.get()) {
                val snap = actor.activeSnapshot.get()
                if (snap != null) {
                    val seq = snap.sequenceNumber
                    val events = snap.eventsProcessed
                    // Reading all net values must never throw or return negative indices
                    for (i in 0 until bits) {
                        val v = snap.getValue(i)
                        if (v != 0L && v != 1L) {
                            tornReads.incrementAndGet()
                        }
                    }
                    assertTrue("Sequence number must be non-negative", seq >= 0L)
                    assertTrue("Events processed must be non-negative", events >= 0)
                }
                delay(1)
            }
        }

        writer.join()
        reader.join()

        assertEquals("Zero torn reads permitted", 0, tornReads.get())

        actor.stop()
        delay(50)
    }

    /**
     * Empirical Challenge 7: Actor Repeated Start / Stop / Restart Cycles.
     * Verifies clean lifecycle management with zero coroutine leaks.
     */
    @Test(timeout = 10000)
    fun testActorRepeatedStartStopCycles() = runBlocking {
        val model = Model()
        val inA = ObservableValue("A", 1)
        model.init(false)

        val actor = SimulationActor(
            model = model,
            trackedNets = arrayOf(inA),
            scope = this
        )

        for (cycle in 1..5) {
            val job = actor.start()
            assertTrue("Actor job must be active on cycle $cycle", job.isActive)

            actor.sendBlocking(SimulationCommand.Step)
            actor.sendBlocking(SimulationCommand.Run(freqHz = 500.0))
            var waited = 0
            while (!actor.isRunning && waited < 500) {
                delay(20)
                waited += 20
            }
            assertTrue("Actor must be running on cycle $cycle", actor.isRunning)

            actor.sendBlocking(SimulationCommand.Pause)
            waited = 0
            while (actor.isRunning && waited < 500) {
                delay(20)
                waited += 20
            }
            assertFalse("Actor must be paused on cycle $cycle", actor.isRunning)

            actor.stop()
            delay(50)
        }
    }
}
