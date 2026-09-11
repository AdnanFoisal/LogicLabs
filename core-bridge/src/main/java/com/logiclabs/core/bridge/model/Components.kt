package com.logiclabs.core.bridge.model

enum class WireColor(val hexArgb: Long, val displayName: String) {
    RED(0xFFFF2222L, "Red (+5V)"),
    BLACK(0xFF222222L, "Black (GND)"),
    BLUE(0xFF2266FFL, "Blue"),
    GREEN(0xFF22BB33L, "Green"),
    YELLOW(0xFFFFDD00L, "Yellow"),
    ORANGE(0xFFFF8800L, "Orange (Clock)"),
    WHITE(0xFFEEEEEEL, "White"),
    GRAY(0xFF888888L, "Gray"),
    PURPLE(0xFF9933CCL, "Purple"),
    BROWN(0xFF884422L, "Brown")
}

data class JumperWire(
    val id: String,
    val startSocket: Int,
    val endSocket: Int,
    val color: WireColor,
    val elevationLevel: Int = 0,
    val isManhattan: Boolean = false
)

data class PlacedIC(
    val id: String,
    val partNumber: String,
    val trench: Int,       // 1 (between block 0 and 1) or 2 (between block 2 and 3)
    val startColumn: Int,  // 0..63
    val isRotated180: Boolean = false
)

sealed class PassiveComponent(
    open val id: String,
    open val socketA: Int,
    open val socketB: Int
) {
    data class Resistor(
        override val id: String,
        override val socketA: Int,
        override val socketB: Int,
        val resistanceOhms: Long = 1000L
    ) : PassiveComponent(id, socketA, socketB)

    data class Capacitor(
        override val id: String,
        override val socketA: Int,
        override val socketB: Int,
        val capacitancePicoFarads: Long = 100_000L // 100nF
    ) : PassiveComponent(id, socketA, socketB)
}
