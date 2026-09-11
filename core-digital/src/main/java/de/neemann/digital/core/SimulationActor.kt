/*
 * Copyright (c) 2026 Logic Labs
 * Headless Digital Engine Coroutines Simulation Actor
 */
package de.neemann.digital.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.util.concurrent.atomic.AtomicReference

/**
 * Commands sent to the [SimulationActor] via its command channel.
 */
sealed interface SimulationCommand {
    data object Step : SimulationCommand
    data class Run(val freqHz: Double = 1000.0) : SimulationCommand
    data object Pause : SimulationCommand
    data class SetInput(val netId: Int, val value: Long, val highZ: Long = 0L) : SimulationCommand
    data class SetInputByName(val name: String, val value: Long, val highZ: Long = 0L) : SimulationCommand
    data class SetInputBool(val netId: Int, val high: Boolean) : SimulationCommand
    data object Stop : SimulationCommand
}

/**
 * Atomic double-buffered simulation state snapshot.
 *
 * Pre-allocated primitive arrays guarantee ZERO HEAP ALLOCATIONS during reads
 * and writes, making it suitable for locked 60/120 FPS UI render loops.
 */
class SimulationStateSnapshot(
    val maxNets: Int,
    val maxSignals: Int = 32
) {
    // Primitive arrays for zero-allocation performance
    val netValues = LongArray(maxNets)
    val netHighZ = LongArray(maxNets)

    // Pre-allocated signal values
    val signalValues = LongArray(maxSignals)
    val signalHighZ = LongArray(maxSignals)

    @Volatile var isOscillating: Boolean = false
    @Volatile var isClamped: Boolean = false
    @Volatile var hasThermalBurnout: Boolean = false
    @Volatile var burnedNetIndex: Int = -1
    @Volatile var sequenceNumber: Long = 0L
    @Volatile var timestampNanos: Long = 0L
    @Volatile var eventsProcessed: Int = 0

    fun getValue(netIndex: Int): Long {
        return if (netIndex in 0 until maxNets) netValues[netIndex] else 0L
    }

    fun isHighZ(netIndex: Int): Boolean {
        return if (netIndex in 0 until maxNets) netHighZ[netIndex] != 0L else true
    }

    fun getBool(netIndex: Int): Boolean {
        return if (netIndex in 0 until maxNets) (netValues[netIndex] and 1L) != 0L else false
    }

    /**
     * Copies data into this snapshot from the model and tracked nets WITHOUT any heap allocations.
     */
    fun updateFrom(
        trackedNets: Array<ObservableValue>?,
        trackedSignals: List<Signal>?,
        isClamped: Boolean,
        hasBurnout: Boolean,
        burnedNet: Int,
        events: Int
    ) {
        if (trackedNets != null) {
            val count = minOf(trackedNets.size, maxNets)
            for (i in 0 until count) {
                val net = trackedNets[i]
                netValues[i] = net.valueHighZIsZero
                netHighZ[i] = net.highZ
            }
        }

        if (trackedSignals != null) {
            val count = minOf(trackedSignals.size, maxSignals)
            for (i in 0 until count) {
                val sigVal = trackedSignals[i].value
                signalValues[i] = sigVal.valueHighZIsZero
                signalHighZ[i] = sigVal.highZ
            }
        }

        this.isClamped = isClamped
        this.isOscillating = isClamped
        this.hasThermalBurnout = hasBurnout
        this.burnedNetIndex = burnedNet
        this.eventsProcessed = events
        this.sequenceNumber++
        this.timestampNanos = System.nanoTime()
    }
}

/**
 * Coroutines-based Simulation Actor executing headless digital logic models.
 *
 * Implements:
 * - Channel-based asynchronous command dispatch.
 * - Atomic double-buffered snapshot publishing via [AtomicReference].
 * - 10,000 events/tick ring-oscillator safeguard with cooperative [yield].
 * - Zero allocations on snapshot read.
 */
class SimulationActor(
    val model: Model,
    val trackedNets: Array<ObservableValue> = emptyArray(),
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    maxNets: Int = maxOf(trackedNets.size, 64)
) {
    companion object {
        const val MAX_EVENTS_PER_TICK = 10_000
    }

    private val commandChannel = Channel<SimulationCommand>(capacity = Channel.BUFFERED)

    // Double buffer pair pre-allocated for zero-allocation updates
    private val bufferA = SimulationStateSnapshot(maxNets)
    private val bufferB = SimulationStateSnapshot(maxNets)
    private var currentBackBuffer = 0

    // Atomic publication reference for Compose / UI consumption
    val activeSnapshot = AtomicReference<SimulationStateSnapshot>()

    private var actorJob: Job? = null
    @Volatile var isRunning: Boolean = false
        private set
    @Volatile var currentFrequencyHz: Double = 1000.0
        private set

    init {
        // Publish initial snapshot
        publishSnapshot(isClamped = false, hasBurnout = false, burnedNet = -1, events = 0)
    }

    fun send(command: SimulationCommand) {
        commandChannel.trySend(command)
    }

    suspend fun sendBlocking(command: SimulationCommand) {
        commandChannel.send(command)
    }

    fun start(): Job {
        if (actorJob?.isActive == true) return actorJob!!
        val job = scope.launch(dispatcher) {
            runActorLoop()
        }
        actorJob = job
        return job
    }

    fun stop() {
        send(SimulationCommand.Stop)
    }

    private suspend fun runActorLoop() {
        var lastSnapshotTime = System.nanoTime()
        val frameIntervalNanos = 8_333_333L // ~120 FPS target throttle

        while (scope.isActive) {
            // 1. Process all pending commands non-blockingly
            drainCommands@ while (true) {
                val cmd = commandChannel.tryReceive().getOrNull() ?: break@drainCommands
                when (cmd) {
                    is SimulationCommand.Step -> {
                        val result = executeStepSafeguarded()
                        publishSnapshot(result.isClamped, result.hasThermalBurnout, -1, result.eventsProcessed)
                    }
                    is SimulationCommand.Run -> {
                        isRunning = true
                        currentFrequencyHz = if (cmd.freqHz > 0.0) cmd.freqHz else 1000.0
                    }
                    is SimulationCommand.Pause -> {
                        isRunning = false
                    }
                    is SimulationCommand.SetInput -> {
                        if (cmd.netId in trackedNets.indices) {
                            val net = trackedNets[cmd.netId]
                            net.set(cmd.value, cmd.highZ)
                            val result = executeStepSafeguarded()
                            publishSnapshot(result.isClamped, result.hasThermalBurnout, -1, result.eventsProcessed)
                        }
                    }
                    is SimulationCommand.SetInputBool -> {
                        if (cmd.netId in trackedNets.indices) {
                            val net = trackedNets[cmd.netId]
                            net.set(if (cmd.high) 1L else 0L, 0L)
                            val result = executeStepSafeguarded()
                            publishSnapshot(result.isClamped, result.hasThermalBurnout, -1, result.eventsProcessed)
                        }
                    }
                    is SimulationCommand.SetInputByName -> {
                        val inputNet = model.getInput(cmd.name)
                        if (inputNet != null) {
                            inputNet.set(cmd.value, cmd.highZ)
                            val result = executeStepSafeguarded()
                            publishSnapshot(result.isClamped, result.hasThermalBurnout, -1, result.eventsProcessed)
                        }
                    }
                    is SimulationCommand.Stop -> {
                        isRunning = false
                        return
                    }
                }
            }

            // 2. Continuous execution if running
            if (isRunning) {
                val result = executeStepSafeguarded()

                val now = System.nanoTime()
                if (now - lastSnapshotTime >= frameIntervalNanos) {
                    publishSnapshot(result.isClamped, result.hasThermalBurnout, -1, result.eventsProcessed)
                    lastSnapshotTime = now
                }

                if (result.isClamped) {
                    // Cooperatively invoke yield() so UI and other coroutines run smoothly
                    yield()
                } else {
                    // Throttle according to frequency
                    val stepDelayMs = (1000.0 / currentFrequencyHz).toLong()
                    if (stepDelayMs > 0) {
                        delay(stepDelayMs)
                    } else {
                        yield()
                    }
                }
            } else {
                // Idle wait when paused
                delay(10)
            }
        }
    }

    /**
     * Executes micro-steps capped at MAX_EVENTS_PER_TICK (10,000).
     * Cooperatively yields if clamped.
     */
    suspend fun executeStepSafeguarded(maxEvents: Int = MAX_EVENTS_PER_TICK): Model.StepResult {
        val result = model.doStepSafeguarded(maxEvents)
        if (result.isClamped) {
            yield()
        }
        return result
    }

    private fun publishSnapshot(
        isClamped: Boolean,
        hasBurnout: Boolean,
        burnedNet: Int,
        events: Int
    ) {
        val back = if (currentBackBuffer == 0) bufferA else bufferB
        val signals = model.signals
        back.updateFrom(trackedNets, signals, isClamped, hasBurnout, burnedNet, events)
        activeSnapshot.set(back)
        currentBackBuffer = 1 - currentBackBuffer
    }
}
