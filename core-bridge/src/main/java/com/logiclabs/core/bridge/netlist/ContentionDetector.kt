package com.logiclabs.core.bridge.netlist

import com.logiclabs.core.bridge.model.ElectricalLevel

data class NetContentionReport(
    val hasContention: Boolean,
    val hasThermalWarning: Boolean,
    val hasShortCircuit: Boolean,
    val highDriverCount: Int,
    val lowDriverCount: Int,
    val affectedNets: Set<Int> = emptySet()
)

object ContentionDetector {

    /**
     * Inspects active driver counts on each canonical electrical net.
     * Flag thermal warning / contention if opposing totem-pole drivers exist on the same net.
     */
    fun analyze(
        driverLevels: Map<Int, ElectricalLevel>,
        dsu: NetlistDsu
    ): NetContentionReport {
        val netHighDrivers = mutableMapOf<Int, Int>()
        val netLowDrivers = mutableMapOf<Int, Int>()
        val contentiousNets = mutableSetOf<Int>()

        for ((socket, level) in driverLevels) {
            val root = dsu.find(socket)
            when (level) {
                ElectricalLevel.HIGH -> {
                    netHighDrivers[root] = (netHighDrivers[root] ?: 0) + 1
                }
                ElectricalLevel.LOW -> {
                    netLowDrivers[root] = (netLowDrivers[root] ?: 0) + 1
                }
                else -> {}
            }
        }

        var totalHighLowConflicts = 0
        for ((root, highs) in netHighDrivers) {
            val lows = netLowDrivers[root] ?: 0
            if (highs > 0 && lows > 0) {
                contentiousNets.add(root)
                totalHighLowConflicts++
            }
        }

        return NetContentionReport(
            hasContention = contentiousNets.isNotEmpty(),
            hasThermalWarning = totalHighLowConflicts > 0,
            hasShortCircuit = totalHighLowConflicts > 0,
            highDriverCount = netHighDrivers.values.sum(),
            lowDriverCount = netLowDrivers.values.sum(),
            affectedNets = contentiousNets
        )
    }
}
