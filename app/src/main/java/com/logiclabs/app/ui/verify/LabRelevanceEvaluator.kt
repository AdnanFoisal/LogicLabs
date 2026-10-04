package com.logiclabs.app.ui.verify

import com.logiclabs.app.ui.screens.bench.discoverBestIoMapping
import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.topology.AD200Topology
import com.logiclabs.feature.tools.courseware.LabExperiment
import kotlin.math.roundToInt

/**
 * Result of relevance evaluation for a candidate lab against the current breadboard circuit.
 */
data class LabRelevanceResult(
    val lab: LabExperiment,
    val totalScorePercent: Int,
    val functionalScorePercent: Int,
    val icScorePercent: Int,
    val matchingVectors: Int,
    val totalVectors: Int,
    val matchedIcs: List<String>,
    val missingIcs: List<String>,
    val effectiveSwitches: List<Int>,
    val effectiveLeds: List<Int>
)

/**
 * Evaluates and ranks coursework labs based on circuit output behavior and mounted ICs.
 *
 * Employs the user-approved hybrid score:
 * - 75% weight: Functional truth-table output concordance (% of 2^N vectors matching).
 * - 25% weight: Hardware IC component overlap (Jaccard similarity between placed chips and expected chips).
 *
 * Filters to candidates with total score >= 35% and returns up to the top 3 matches.
 */
object LabRelevanceEvaluator {

    const val MIN_RELEVANCE_THRESHOLD: Int = 35
    const val MAX_RECOMMENDATIONS: Int = 3

    fun evaluateRelevance(
        circuit: BreadboardCircuit,
        candidateLabs: List<LabExperiment>,
        limit: Int = MAX_RECOMMENDATIONS,
        isCancelled: () -> Boolean = { false }
    ): List<LabRelevanceResult> {
        if (isCancelled()) return emptyList()
        val placedPartNumbers = circuit.placedChips.map { it.placedIc.partNumber.trim() }.toSet()
        val connectedSwitches = (0..7).filter { circuit.dsu.getNetSize(AD200Topology.TERM_SW0 + it) > 1 }
        val connectedLeds = (0..7).filter { circuit.dsu.getNetSize(AD200Topology.TERM_LED0 + it) > 2 }

        // If circuit is completely empty (no chips and no wires), or has no I/O connected,
        // no functional experiment can be identified
        if (circuit.placedChips.isEmpty() && connectedSwitches.isEmpty() && connectedLeds.isEmpty()) {
            return emptyList()
        }

        val results = mutableListOf<LabRelevanceResult>()

        for (lab in candidateLabs) {
            if (isCancelled()) return emptyList()
            val expectedIcs = lab.targetChips.map { it.trim() }.toSet()
            val matchedIcs = expectedIcs.filter { it in placedPartNumbers }
            val missingIcs = expectedIcs.filter { it !in placedPartNumbers }

            // Pruning 1: If chips are placed on board, skip labs with zero chip overlap
            if (placedPartNumbers.isNotEmpty() && expectedIcs.isNotEmpty() && matchedIcs.isEmpty()) {
                continue
            }

            // Pruning 2: If switches are wired, skip labs requiring far more switches than wired
            if (connectedSwitches.isNotEmpty() && lab.switchIndices.size > connectedSwitches.size + 1) {
                continue
            }

            // 1. Hardware IC Overlap Score (Jaccard similarity between placed chips and expected chips)
            val icScoreFloat: Float = when {
                expectedIcs.isEmpty() && placedPartNumbers.isEmpty() -> 100f
                expectedIcs.isEmpty() || placedPartNumbers.isEmpty() -> 0f
                else -> {
                    val unionSize = (expectedIcs + placedPartNumbers).size
                    (matchedIcs.size.toFloat() / unionSize.toFloat()) * 100f
                }
            }

            // 2. Functional Output Concordance Score
            val n = lab.switchIndices.size.coerceIn(1, 8)
            val vectorCount = 1 shl n

            // Require both switches and LEDs to be connected for functional verification
            val functionalScoreFloat: Float = if (connectedSwitches.isEmpty() || connectedLeds.isEmpty()) {
                0f
            } else {
                val fallbackSw = if (connectedSwitches.size >= n) {
                    connectedSwitches.take(n)
                } else {
                    lab.switchIndices
                }
                val fallbackLed = if (connectedLeds.size >= lab.ledIndices.size) {
                    connectedLeds.take(lab.ledIndices.size)
                } else {
                    lab.ledIndices
                }

                val (bestSw, bestLed, matchingRows) = discoverBestIoMapping(
                    circuit = circuit,
                    connectedSwitches = connectedSwitches,
                    connectedLeds = connectedLeds,
                    requiredSwitchCount = lab.switchIndices.size,
                    requiredLedCount = lab.ledIndices.size,
                    expectedFunction = lab.expectedFunction,
                    fallbackSwitches = fallbackSw,
                    fallbackLeds = fallbackLed
                )

                val vectorRatio = matchingRows.toFloat() / vectorCount.toFloat()

                // Terminal explanation penalty: proportional coverage of user's active terminals
                val swExplanation = minOf(1.0f, lab.switchIndices.size.toFloat() / maxOf(1, connectedSwitches.size).toFloat())
                val ledExplanation = minOf(1.0f, lab.ledIndices.size.toFloat() / maxOf(1, connectedLeds.size).toFloat())
                val terminalFactor = 0.7f + 0.3f * (swExplanation * 0.5f + ledExplanation * 0.5f)

                (vectorRatio * terminalFactor * 100f).coerceIn(0f, 100f)
            }

            // 3. Composite Hybrid Score (75% functional + 25% IC overlap)
            val totalScorePercent = (functionalScoreFloat * 0.75f + icScoreFloat * 0.25f)
                .roundToInt()
                .coerceIn(0, 100)

            if (totalScorePercent >= MIN_RELEVANCE_THRESHOLD) {
                val fallbackSw = if (connectedSwitches.size >= n) connectedSwitches.take(n) else lab.switchIndices
                val fallbackLed = if (connectedLeds.size >= lab.ledIndices.size) connectedLeds.take(lab.ledIndices.size) else lab.ledIndices
                val (bestSw, bestLed, matchingRows) = if (connectedSwitches.isNotEmpty() && connectedLeds.isNotEmpty()) {
                    discoverBestIoMapping(
                        circuit = circuit,
                        connectedSwitches = connectedSwitches,
                        connectedLeds = connectedLeds,
                        requiredSwitchCount = lab.switchIndices.size,
                        requiredLedCount = lab.ledIndices.size,
                        expectedFunction = lab.expectedFunction,
                        fallbackSwitches = fallbackSw,
                        fallbackLeds = fallbackLed
                    )
                } else {
                    Triple(fallbackSw, fallbackLed, 0)
                }

                val rawFunctionalPercent = if (vectorCount > 0) ((matchingRows.toFloat() / vectorCount.toFloat()) * 100f).roundToInt() else 0

                results.add(
                    LabRelevanceResult(
                        lab = lab,
                        totalScorePercent = totalScorePercent,
                        functionalScorePercent = rawFunctionalPercent,
                        icScorePercent = icScoreFloat.roundToInt(),
                        matchingVectors = matchingRows,
                        totalVectors = vectorCount,
                        matchedIcs = matchedIcs,
                        missingIcs = missingIcs,
                        effectiveSwitches = bestSw,
                        effectiveLeds = bestLed
                    )
                )
            }
        }

        // Sort descending by total score, then by matching vector count, then by functional score
        results.sortWith(
            compareByDescending<LabRelevanceResult> { it.totalScorePercent }
                .thenByDescending { it.matchingVectors }
                .thenByDescending { it.functionalScorePercent }
        )

        return results.take(limit)
    }
}
