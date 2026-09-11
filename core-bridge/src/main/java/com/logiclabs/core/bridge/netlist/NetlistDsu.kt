package com.logiclabs.core.bridge.netlist

import com.logiclabs.core.bridge.topology.AD200Topology

/**
 * High-performance, allocation-free Disjoint-Set Union (DSU) engine for the AD-200 breadboard netlist.
 * Maps 1,896 physical sockets and 152 console terminal nodes to electrical nets in O(alpha(N)) time.
 */
class NetlistDsu(capacity: Int = AD200Topology.TOTAL_NETLIST_NODES) {

    private val parent = IntArray(capacity)
    private val baseContinuity = IntArray(capacity)
    val size: Int = capacity

    init {
        for (i in 0 until capacity) {
            baseContinuity[i] = -1
        }

        // Terminal column internal connections (5 rows per column)
        for (block in 0 until AD200Topology.TERMINAL_BLOCK_COUNT) {
            for (col in 0 until AD200Topology.COLUMNS_PER_BLOCK) {
                val s0 = AD200Topology.terminalSocket(block, col, 0)
                for (row in 1 until AD200Topology.ROWS_PER_BLOCK) {
                    val s = AD200Topology.terminalSocket(block, col, row)
                    unionInternal(baseContinuity, s0, s)
                }
            }
        }

        // Horizontal distribution rails (88 sockets per rail)
        for (rail in 0 until AD200Topology.RAIL_COUNT) {
            val r0 = AD200Topology.railSocket(rail, 0)
            for (pos in 1 until AD200Topology.SOCKETS_PER_RAIL) {
                val r = AD200Topology.railSocket(rail, pos)
                unionInternal(baseContinuity, r0, r)
            }
        }

        // Pre-union top and bottom LED test terminals (L0..L7)
        for (i in 0..7) {
            unionInternal(baseContinuity, AD200Topology.TERM_LED0 + i, AD200Topology.TERM_BOT_LED0 + i)
        }

        resetToBase()
    }

    fun resetToBase() {
        System.arraycopy(baseContinuity, 0, parent, 0, parent.size)
    }

    fun find(i: Int): Int {
        require(i in 0 until size) { "Socket index out of bounds: $i (max $size)" }
        var root = i
        while (parent[root] >= 0) {
            root = parent[root]
        }
        var curr = i
        while (curr != root) {
            val next = parent[curr]
            parent[curr] = root
            curr = next
        }
        return root
    }

    fun union(a: Int, b: Int): Int {
        val rootA = find(a)
        val rootB = find(b)
        if (rootA == rootB) return rootA

        val sizeA = -parent[rootA]
        val sizeB = -parent[rootB]

        return if (sizeA >= sizeB) {
            parent[rootA] = -(sizeA + sizeB)
            parent[rootB] = rootA
            rootA
        } else {
            parent[rootB] = -(sizeA + sizeB)
            parent[rootA] = rootB
            rootB
        }
    }

    fun areConnected(a: Int, b: Int): Boolean = find(a) == find(b)

    fun getNetSize(i: Int): Int = -parent[find(i)]

    private fun unionInternal(arr: IntArray, a: Int, b: Int): Int {
        var rootA = a
        while (arr[rootA] >= 0) rootA = arr[rootA]
        var rootB = b
        while (arr[rootB] >= 0) rootB = arr[rootB]
        if (rootA == rootB) return rootA

        val sizeA = -arr[rootA]
        val sizeB = -arr[rootB]
        return if (sizeA >= sizeB) {
            arr[rootA] = -(sizeA + sizeB)
            arr[rootB] = rootA
            rootA
        } else {
            arr[rootB] = -(sizeA + sizeB)
            arr[rootA] = rootB
            rootB
        }
    }
}
