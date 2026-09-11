package com.logiclabs.core.bridge.model

/**
 * Simulation behavior mode.
 * - [IDEAL]: Standard academic mode. Tolerant of mistakes, circuit safeguards prevent burnout.
 * - [PRACTICAL]: Realistic laboratory mode. Reverse polarity, output shorts to GND/VCC, or bus contention
 *   destroy the IC with an explosion animation and disable output logic until restored.
 */
enum class SimulationMode {
    IDEAL,
    PRACTICAL
}
