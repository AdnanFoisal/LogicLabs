package com.logiclabs.core.testing.model

/**
 * Represents the discrete electrical logic level of a net, pin, or terminal.
 * Follows IEEE standard 4-state digital logic modeling plus unpowered state:
 * - [LOW]: 0V nominal, driven logic zero.
 * - [HIGH]: +5V nominal, driven logic one.
 * - [HIGH_Z]: High impedance, floating or disconnected.
 * - [CONFLICT]: Bus contention / driver fighting (multiple active drivers opposing each other).
 * - [UNPOWERED]: Component lacks VCC or GND connections; inactive.
 */
enum class ElectricalLevel(val symbol: Char, val voltageApprox: Double) {
    LOW('0', 0.0),
    HIGH('1', 5.0),
    HIGH_Z('Z', 1.5),
    CONFLICT('X', 2.5),
    UNPOWERED('U', 0.0);

    val isDriven: Boolean get() = this == LOW || this == HIGH
    val isConflict: Boolean get() = this == CONFLICT
    val isHighZ: Boolean get() = this == HIGH_Z
    val isUnpowered: Boolean get() = this == UNPOWERED

    fun toBoolean(): Boolean = when (this) {
        HIGH -> true
        LOW -> false
        HIGH_Z -> false // Floating inputs in standard testing default to false unless pulled up
        CONFLICT -> false
        UNPOWERED -> false
    }

    companion object {
        fun fromBoolean(value: Boolean): ElectricalLevel = if (value) HIGH else LOW

        fun fromChar(char: Char): ElectricalLevel = when (char) {
            '0' -> LOW
            '1' -> HIGH
            'Z', 'z' -> HIGH_Z
            'X', 'x' -> CONFLICT
            'U', 'u' -> UNPOWERED
            else -> throw IllegalArgumentException("Unknown logic level character: '$char'")
        }

        fun resolveBus(driversHigh: Int, driversLow: Int, hasPullUp: Boolean = false, hasPullDown: Boolean = false): ElectricalLevel {
            return when {
                driversHigh > 0 && driversLow > 0 -> CONFLICT
                driversHigh > 0 -> HIGH
                driversLow > 0 -> LOW
                hasPullUp && !hasPullDown -> HIGH
                hasPullDown && !hasPullUp -> LOW
                hasPullUp && hasPullDown -> CONFLICT
                else -> HIGH_Z
            }
        }
    }
}
