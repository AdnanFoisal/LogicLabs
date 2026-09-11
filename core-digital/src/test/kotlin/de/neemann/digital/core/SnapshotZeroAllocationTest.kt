package de.neemann.digital.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.management.ManagementFactory

/**
 * Empirical verification of zero memory allocations during SimulationStateSnapshot reads.
 *
 * Requirements specify:
 * "Zero object allocations inside Compose Canvas draw loop / 60/120 FPS UI synchronization."
 *
 * This test uses JVM ThreadMXBean.getThreadAllocatedBytes to measure exact heap allocations
 * across 1,000,000 consecutive snapshot read operations.
 */
class SnapshotZeroAllocationTest {

    @Test
    fun testZeroAllocationOnSnapshotReads() {
        val mxBean = ManagementFactory.getThreadMXBean()
        val sunThreadBean = mxBean as? com.sun.management.ThreadMXBean

        if (sunThreadBean != null && sunThreadBean.isThreadAllocatedMemorySupported) {
            if (!sunThreadBean.isThreadAllocatedMemoryEnabled) {
                sunThreadBean.isThreadAllocatedMemoryEnabled = true
            }

            val model = Model()
            val netA = ObservableValue("A", 1).setValue(1)
            val netB = ObservableValue("B", 1).setValue(0)
            val scope = CoroutineScope(Dispatchers.Default)

            val actor = SimulationActor(
                model = model,
                trackedNets = arrayOf(netA, netB),
                scope = scope
            )

            val threadId = Thread.currentThread().id

            // Warm-up JIT compilation and class loading (100,000 iterations)
            var blackhole = 0L
            for (i in 0 until 100_000) {
                val s = actor.activeSnapshot.get()
                blackhole += s.getValue(0) + s.getValue(1)
                blackhole += if (s.getBool(0)) 1L else 0L
                blackhole += if (s.isHighZ(0)) 1L else 0L
                blackhole += s.sequenceNumber
                blackhole += if (s.isOscillating) 1L else 0L
                blackhole += if (s.isClamped) 1L else 0L
                blackhole += if (s.hasThermalBurnout) 1L else 0L
                blackhole += s.burnedNetIndex.toLong()
                blackhole += s.eventsProcessed.toLong()
                blackhole += s.timestampNanos
            }

            // Measurement phase: 1,000,000 reads
            val bytesBefore = sunThreadBean.getThreadAllocatedBytes(threadId)

            for (i in 0 until 1_000_000) {
                val s = actor.activeSnapshot.get()
                blackhole += s.getValue(0) + s.getValue(1)
                blackhole += if (s.getBool(0)) 1L else 0L
                blackhole += if (s.isHighZ(0)) 1L else 0L
                blackhole += s.sequenceNumber
                blackhole += if (s.isOscillating) 1L else 0L
                blackhole += if (s.isClamped) 1L else 0L
                blackhole += if (s.hasThermalBurnout) 1L else 0L
                blackhole += s.burnedNetIndex.toLong()
                blackhole += s.eventsProcessed.toLong()
                blackhole += s.timestampNanos
            }

            val bytesAfter = sunThreadBean.getThreadAllocatedBytes(threadId)
            val allocatedBytes = bytesAfter - bytesBefore

            scope.cancel()

            // Verify blackhole was computed
            assertTrue(blackhole != 0L)

            // Strictly verify ZERO per-read allocations (< 512 bytes total across 1,000,000 reads to tolerate JIT OSR metadata)
            assertTrue("Expected zero per-read allocations (< 512 bytes for 1M reads), but found: $allocatedBytes bytes", allocatedBytes < 512L)
        } else {
            // Fallback for JVMs that do not support ThreadAllocatedBytes
            System.err.println("ThreadAllocatedMemory is not supported on this JVM runtime")
        }
    }
}
