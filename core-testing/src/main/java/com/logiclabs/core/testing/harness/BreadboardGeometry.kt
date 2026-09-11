package com.logiclabs.core.testing.harness

/**
 * Encapsulates the physical geometry and coordinate system of the K&H IDL-800A trainer's AD-200 breadboard.
 * Exactly 1,896 physical sockets:
 * - 4 terminal blocks x 64 columns x 5 rows = 1,280 sockets (0..1279)
 * - 7 horizontal distribution rails x 88 sockets = 616 sockets (1280..1895)
 * - 152 peripheral terminal sockets (1896..2047)
 */
object BreadboardGeometry {
    const val TERMINAL_BLOCK_COUNT = 4
    const val COLUMNS_PER_BLOCK = 64
    const val ROWS_PER_BLOCK = 5
    const val SOCKETS_PER_BLOCK = COLUMNS_PER_BLOCK * ROWS_PER_BLOCK // 320
    const val TOTAL_TERMINAL_SOCKETS = TERMINAL_BLOCK_COUNT * SOCKETS_PER_BLOCK // 1280

    const val RAIL_COUNT = 7
    const val SOCKETS_PER_RAIL = 88
    const val TOTAL_RAIL_SOCKETS = RAIL_COUNT * SOCKETS_PER_RAIL // 616

    const val TOTAL_BREADBOARD_SOCKETS = TOTAL_TERMINAL_SOCKETS + TOTAL_RAIL_SOCKETS // 1896
    const val TOTAL_NETLIST_NODES = 2048

    // Rail identities: Dual Power Rails (Top VCC/GND, Bottom VCC/GND)
    const val RAIL_TOP_VCC_5V = 0
    const val RAIL_TOP_GND = 1
    const val RAIL_BOT_VCC_5V = 2
    const val RAIL_BOT_GND = 3
    const val RAIL_MID_NEG_5V = 2
    const val RAIL_MID_GND = 3
    const val RAIL_MID_POS_12V = 4
    const val RAIL_BOT_NEG_12V = 6

    // Peripheral terminal offsets (1896..2047)
    const val TERM_SW0 = 1896
    const val TERM_SW1 = 1897
    const val TERM_SW2 = 1898
    const val TERM_SW3 = 1899
    const val TERM_SW4 = 1900
    const val TERM_SW5 = 1901
    const val TERM_SW6 = 1902
    const val TERM_SW7 = 1903
    const val TERM_PULSER_A_P = 1904
    const val TERM_PULSER_A_N = 1905
    const val TERM_PULSER_B_P = 1906
    const val TERM_PULSER_B_N = 1907
    const val TERM_LED0 = 1908
    const val TERM_LED1 = 1909
    const val TERM_LED2 = 1910
    const val TERM_LED3 = 1911
    const val TERM_LED4 = 1912
    const val TERM_LED5 = 1913
    const val TERM_LED6 = 1914
    const val TERM_LED7 = 1915
    const val TERM_CLK = 1916
    const val TERM_CLK_INV = 1917
    const val TERM_SEG_A_BCD_A = 1918
    const val TERM_SEG_A_BCD_B = 1919
    const val TERM_SEG_A_BCD_C = 1920
    const val TERM_SEG_A_BCD_D = 1921
    const val TERM_SEG_A_LT = 1922
    const val TERM_SEG_A_RBI = 1923
    const val TERM_SEG_A_BI = 1924
    const val TERM_SEG_B_BCD_A = 1926
    const val TERM_POWER_VCC = 1934
    const val TERM_POWER_GND = 1935

    /**
     * Maps terminal block, column (0..63), and row (0..4) to flat socket index [0..1279].
     */
    fun terminalSocket(block: Int, col: Int, row: Int): Int {
        require(block in 0 until TERMINAL_BLOCK_COUNT) { "Block must be 0..3: $block" }
        require(col in 0 until COLUMNS_PER_BLOCK) { "Column must be 0..63: $col" }
        require(row in 0 until ROWS_PER_BLOCK) { "Row must be 0..4: $row" }
        return (block * SOCKETS_PER_BLOCK) + (col * ROWS_PER_BLOCK) + row
    }

    /**
     * Maps rail index (0..6) and position (0..87) to flat socket index [1280..1895].
     */
    fun railSocket(rail: Int, pos: Int): Int {
        require(rail in 0 until RAIL_COUNT) { "Rail must be 0..6: $rail" }
        require(pos in 0 until SOCKETS_PER_RAIL) { "Position must be 0..87: $pos" }
        return TOTAL_TERMINAL_SOCKETS + (rail * SOCKETS_PER_RAIL) + pos
    }

    /**
     * Calculates the socket index where an IC pin sits.
     * @param trench 1 (between block 0 and 1) or 2 (between block 2 and 3)
     * @param startCol Starting column (0..63) where Pin 1 is placed
     * @param pinNumber Pin number (1..pinCount)
     * @param pinCount Total pins (14 or 16)
     */
    fun icPinSocket(trench: Int, startCol: Int, pinNumber: Int, pinCount: Int): Int {
        require(trench in 1..2) { "Trench must be 1 or 2: $trench" }
        val half = pinCount / 2
        require(pinNumber in 1..pinCount) { "Pin number must be 1..$pinCount: $pinNumber" }

        val (upperBlock, lowerBlock) = if (trench == 1) Pair(0, 1) else Pair(2, 3)

        return if (pinNumber <= half) {
            // Lower row of pins (Pin 1 to half) sits in lower block row 0 (closest to trench)
            // Standard convention: Pin 1 (bottom-left) to Pin half (bottom-right = GND)
            val col = startCol + (pinNumber - 1)
            require(col in 0 until COLUMNS_PER_BLOCK) { "IC extends beyond column 63" }
            terminalSocket(lowerBlock, col, 0)
        } else {
            // Upper row of pins (half+1 to pinCount) sits in upper block row 4, running right-to-left
            // Pin half+1 (top-right) to Pin pinCount (top-left = VCC)
            val col = startCol + (pinCount - pinNumber)
            require(col in 0 until COLUMNS_PER_BLOCK) { "IC extends beyond column 63" }
            terminalSocket(upperBlock, col, 4)
        }
    }
}
