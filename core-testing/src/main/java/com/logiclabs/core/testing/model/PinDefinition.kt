package com.logiclabs.core.testing.model

/**
 * Direction and role of a physical IC pin.
 */
enum class PinRole {
    INPUT,
    OUTPUT,
    POWER_VCC,
    POWER_GND,
    NO_CONNECT
}

/**
 * Encapsulates the pin metadata of an IC.
 */
data class PinDefinition(
    val pinNumber: Int,
    val name: String,
    val role: PinRole,
    val isInverted: Boolean = false,
    val isClock: Boolean = false
)
