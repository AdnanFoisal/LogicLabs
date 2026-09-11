package com.logiclabs.core.bridge.model

enum class ElectricalLevel {
    LOW,
    HIGH,
    HIGH_Z,
    CONTENTION,
    UNPOWERED,
    BURNOUT;

    val isHigh: Boolean get() = this == HIGH
    val isLow: Boolean get() = this == LOW
    val isFloating: Boolean get() = this == HIGH_Z

    companion object {
        fun fromBoolean(value: Boolean): ElectricalLevel = if (value) HIGH else LOW
    }
}

enum class PinRole {
    INPUT,
    OUTPUT,
    BIDIRECTIONAL,
    CLOCK,
    POWER_VCC,
    POWER_GND,
    PASSIVE,
    NO_CONNECT
}

data class PinDefinition(
    val pinNumber: Int,
    val name: String,
    val role: PinRole,
    val isInverted: Boolean = false,
    val isClock: Boolean = false
)
