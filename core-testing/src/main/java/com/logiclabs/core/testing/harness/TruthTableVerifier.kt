package com.logiclabs.core.testing.harness

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class TruthTableRow(
    val inputValues: List<Boolean>,
    val expectedOutputs: List<Boolean>,
    val actualOutputs: List<Boolean>,
    val isPassed: Boolean
)

data class TruthTableResult(
    val rows: List<TruthTableRow>,
    val totalVectors: Int,
    val passedVectors: Int,
    val isAllPassed: Boolean,
    val hmacSha256Hash: String
) {
    fun formatTable(inputNames: List<String>, outputNames: List<String>): String {
        val sb = StringBuilder()
        sb.append(inputNames.joinToString(" | ") { it.padEnd(4) })
        sb.append(" || ")
        sb.append(outputNames.joinToString(" | ") { it.padEnd(5) })
        sb.append(" | Result\n")
        sb.append("-".repeat(inputNames.size * 7 + outputNames.size * 8 + 12)).append("\n")

        for (row in rows) {
            sb.append(row.inputValues.joinToString(" | ") { (if (it) "1" else "0").padEnd(4) })
            sb.append(" || ")
            sb.append(row.actualOutputs.joinToString(" | ") { (if (it) "1" else "0").padEnd(5) })
            sb.append(" | ")
            sb.append(if (row.isPassed) "[PASS]" else "[FAIL]")
            sb.append("\n")
        }
        sb.append("HMAC-SHA256 Provenance Seal: $hmacSha256Hash\n")
        return sb.toString()
    }
}

object TruthTableVerifier {

    /**
     * Executes an exhaustive 2^N test vector sweep across the given input switch indices.
     */
    fun verify(
        circuit: SimulationCircuit,
        switchIndices: List<Int>,
        outputReader: () -> List<Boolean>,
        expectedFunction: (List<Boolean>) -> List<Boolean>,
        secretKey: String = "LOGIC_LABS_LAB_AUDIT_KEY"
    ): TruthTableResult {
        val n = switchIndices.size
        require(n in 1..8) { "Input switches count must be 1..8: $n" }
        val vectorCount = 1 shl n
        val rows = mutableListOf<TruthTableRow>()
        var passedCount = 0

        val digestPayload = StringBuilder()

        for (v in 0 until vectorCount) {
            val inputVector = mutableListOf<Boolean>()
            for (i in 0 until n) {
                val bit = ((v shr (n - 1 - i)) and 1) == 1
                inputVector.add(bit)
                circuit.switches[switchIndices[i]] = bit
            }

            circuit.step()
            val actual = outputReader()
            val expected = expectedFunction(inputVector)

            val match = (actual == expected)
            if (match) passedCount++

            rows.add(TruthTableRow(inputVector, expected, actual, match))

            // Append to HMAC payload
            digestPayload.append("IN=").append(inputVector.joinToString("") { if (it) "1" else "0" })
            digestPayload.append(":EXP=").append(expected.joinToString("") { if (it) "1" else "0" })
            digestPayload.append(":ACT=").append(actual.joinToString("") { if (it) "1" else "0" })
            digestPayload.append(":MATCH=").append(match)
            digestPayload.append(";")
        }

        val hmac = computeHmacSha256(secretKey, digestPayload.toString())

        return TruthTableResult(
            rows = rows,
            totalVectors = vectorCount,
            passedVectors = passedCount,
            isAllPassed = (passedCount == vectorCount),
            hmacSha256Hash = hmac
        )
    }

    private fun computeHmacSha256(key: String, data: String): String {
        val algorithm = "HmacSHA256"
        val mac = Mac.getInstance(algorithm)
        val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), algorithm)
        mac.init(secretKeySpec)
        val hash = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
