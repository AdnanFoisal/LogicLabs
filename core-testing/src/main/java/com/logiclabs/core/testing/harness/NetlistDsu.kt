package com.logiclabs.core.testing.harness

/**
 * High-performance, allocation-free Disjoint-Set Union (DSU) engine for the AD-200 breadboard netlist.
 * Maps 1,896 physical sockets and 152 console terminal nodes to electrical nets in O(alpha(N)) time.
 *
 * Invariants:
 * - Flat [IntArray] representation with size 2048.
 * - If parent[i] >= 0, parent[i] is the parent index.
 * - If parent[i] < 0, i is a root, and -parent[i] is the set size.
 * - find(i) is strictly iterative (two-pass path compression) without recursion or heap allocation.
 * - union(a, b) uses union-by-size.
 * - Pre-computed immutable baseContinuity array restores AD-200 column and rail continuity via System.arraycopy.
 */
class NetlistDsu(capacity: Int = BreadboardGeometry.TOTAL_NETLIST_NODES) {

    private val parent = IntArray(capacity)
    private val baseContinuity = IntArray(capacity)
    val size: Int = capacity

    init {
        // Step 1: Initialize all nodes as singletons with size 1 (-1)
        for (i in 0 until capacity) {
            baseContinuity[i] = -1
        }

        // Step 2: Form 256 terminal column nets (each block has 64 columns of 5 interconnected sockets)
        for (block in 0 until BreadboardGeometry.TERMINAL_BLOCK_COUNT) {
            for (col in 0 until BreadboardGeometry.COLUMNS_PER_BLOCK) {
                val s0 = BreadboardGeometry.terminalSocket(block, col, 0)
                for (row in 1 until BreadboardGeometry.ROWS_PER_BLOCK) {
                    val s = BreadboardGeometry.terminalSocket(block, col, row)
                    unionInternal(baseContinuity, s0, s)
                }
            }
        }

        // Step 3: Form 7 horizontal distribution rail nets (each rail has 88 continuous sockets)
        for (rail in 0 until BreadboardGeometry.RAIL_COUNT) {
            val r0 = BreadboardGeometry.railSocket(rail, 0)
            for (pos in 1 until BreadboardGeometry.SOCKETS_PER_RAIL) {
                val r = BreadboardGeometry.railSocket(rail, pos)
                unionInternal(baseContinuity, r0, r)
            }
        }

        // Reset runtime parent array to base continuity
        resetToBase()
    }

    /**
     * Resets runtime connections to the baseline AD-200 continuity in < 2 microseconds with zero heap allocations.
     */
    fun resetToBase() {
        System.arraycopy(baseContinuity, 0, parent, 0, parent.size)
    }

    /**
     * Finds the canonical representative net root for the given socket index.
     * Uses iterative two-pass path compression for zero stack/heap allocations.
     */
    fun find(i: Int): Int {
        require(i in 0 until size) { "Socket index out of bounds: $i (max $size)" }
        var root = i
        while (parent[root] >= 0) {
            root = parent[root]
        }
        // Path compression pass
        var curr = i
        while (curr != root) {
            val next = parent[curr]
            parent[curr] = root
            curr = next
        }
        return root
    }

    /**
     * Merges the electrical nets containing socket [a] and socket [b] using union-by-size.
     * Returns the canonical root of the merged net. Zero heap allocations.
     */
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

    /**
     * Returns true if two sockets belong to the same electrical net.
     */
    fun areConnected(a: Int, b: Int): Boolean {
        return find(a) == find(b)
    }

    /**
     * Returns the number of physical sockets connected to this electrical net.
     */
    fun getNetSize(i: Int): Int {
        val root = find(i)
        return -parent[root]
    }

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
