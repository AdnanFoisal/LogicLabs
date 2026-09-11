package com.logiclabs.feature.instruments.ui.hardware

/**
 * View-layer mirror of the standard 7448 BCD-to-seven-segment font.
 *
 * This is deliberately a **copy** and not a reference to
 * `com.logiclabs.core.digital` `Chip7448.font7448`: the render layer must never
 * reach into the simulation engine for drawing data, so the engine stays free to
 * evolve its internal representation without breaking the panel. If the engine
 * font ever changes, update this table to match — it is a mirror, not a source
 * of truth for logic.
 *
 * Bit layout (active-high, one bit per segment):
 * ```
 *  bit0 = a (top)          bit4 = e (bottom-left)
 *  bit1 = b (upper-right)  bit5 = f (upper-left)
 *  bit2 = c (lower-right)  bit6 = g (middle)
 *  bit3 = d (bottom)
 * ```
 */
object SevenSegFont {

    /** Segment patterns for nibble values 0..15 (0-9 then A-F). */
    val PATTERNS: IntArray = intArrayOf(
        0x3F, // 0
        0x06, // 1
        0x5B, // 2
        0x4F, // 3
        0x66, // 4
        0x6D, // 5
        0x7D, // 6
        0x07, // 7
        0x7F, // 8
        0x6F, // 9
        0x5C, // A (7448 legacy glyph)
        0x4C, // b
        0x46, // C
        0x4D, // d
        0x78, // E
        0x00, // F (blank on a true 7448)
    )

    /** Segment bitmask for [value]; anything outside 0..15 is masked to a nibble. */
    fun segmentsFor(value: Int): Int = PATTERNS[value and 0x0F]

    // Segment bit indices, named so drawing code reads like a datasheet.
    const val SEG_A = 0x01
    const val SEG_B = 0x02
    const val SEG_C = 0x04
    const val SEG_D = 0x08
    const val SEG_E = 0x10
    const val SEG_F = 0x20
    const val SEG_G = 0x40
}
