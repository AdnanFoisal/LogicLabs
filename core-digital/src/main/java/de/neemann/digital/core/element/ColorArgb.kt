package de.neemann.digital.core.element

/**
 * Packed 32-bit ARGB Int color value class for zero-allocation representation.
 */
@JvmInline
value class ColorArgb(val value: Int) {
    inline val alpha: Int get() = (value ushr 24) and 0xFF
    inline val red: Int get() = (value ushr 16) and 0xFF
    inline val green: Int get() = (value ushr 8) and 0xFF
    inline val blue: Int get() = value and 0xFF

    companion object {
        val RED = ColorArgb(0xFFFF0000.toInt())
        val GREEN = ColorArgb(0xFF00FF00.toInt())
        val BLUE = ColorArgb(0xFF0000FF.toInt())
        val YELLOW = ColorArgb(0xFFFFFF00.toInt())
        val TRANSPARENT = ColorArgb(0x00000000)

        fun argb(a: Int, r: Int, g: Int, b: Int): ColorArgb =
            ColorArgb(((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF))

        fun rgb(r: Int, g: Int, b: Int): ColorArgb = argb(0xFF, r, g, b)
    }
}
