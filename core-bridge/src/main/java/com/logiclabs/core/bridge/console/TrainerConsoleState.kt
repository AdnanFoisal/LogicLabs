package com.logiclabs.core.bridge.console

data class TrainerConsoleState(
    val masterPower: Boolean = true,
    val switches: BooleanArray = BooleanArray(8),
    val pulserA_P: Boolean = false,
    val pulserA_N: Boolean = true,
    val pulserB_P: Boolean = false,
    val pulserB_N: Boolean = true,
    val clockFrequencyHz: Double = 1.0,
    val clockRunning: Boolean = false,
    val clockState: Boolean = false,
    val leds: BooleanArray = BooleanArray(8),
    val segA_Value: Int = 0,
    val segB_Value: Int = 0,
    val segA_Segments: ByteArray = ByteArray(7), // a,b,c,d,e,f,g
    val segB_Segments: ByteArray = ByteArray(7)
) {
    fun getSwitch(index: Int): Boolean = if (index in 0..7) switches[index] else false

    fun setSwitch(index: Int, high: Boolean): TrainerConsoleState {
        val next = switches.clone()
        if (index in 0..7) next[index] = high
        return copy(switches = next)
    }
}
