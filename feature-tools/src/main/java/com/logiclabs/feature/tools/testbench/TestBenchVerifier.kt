package com.logiclabs.feature.tools.testbench

import com.logiclabs.core.bridge.circuit.BreadboardCircuit
import com.logiclabs.core.bridge.model.ElectricalLevel
import com.logiclabs.core.bridge.topology.AD200Topology
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class TruthTableRow(
    val inputValues: List<Boolean>,
    val expectedOutputs: List<Boolean>,
    val actualOutputs: List<Boolean>,
    val isPassed: Boolean
)

data class DiagnosticIssue(
    val isError: Boolean,
    val message: String
)

data class TestBenchReport(
    val rows: List<TruthTableRow>,
    val passedCount: Int,
    val totalCount: Int,
    val isAllPassed: Boolean,
    val hmacSha256Hash: String,
    val diagnostics: List<DiagnosticIssue> = emptyList(),
    val inputNames: List<String> = emptyList(),
    val outputNames: List<String> = emptyList(),
    val experimentTitle: String = "DIGITAL LOGIC VERIFICATION",
    /**
     * Wall-clock duration of the vector sweep itself — the 2^N step / read / compare
     * loop plus the switch-state restore — for the verifier's elapsed-style readout.
     *
     * Presentation metadata only, and deliberately **not** part of the HMAC digest:
     * the seal is pinned byte-for-byte by `LabPresetIntegrityTest`, and a timing
     * reading is not evidence about the circuit anyway.
     */
    val sweepDurationMs: Long = 0L
)

object TestBenchVerifier {

    /**
     * Executes exhaustive 2^N input truth-table verification with diagnostics and HMAC-SHA256 cryptographic provenance.
     */
    fun verify(
        circuit: BreadboardCircuit,
        switchIndices: List<Int>,
        outputReader: () -> List<Boolean>,
        expectedFunction: (List<Boolean>) -> List<Boolean>,
        inputNames: List<String> = emptyList(),
        outputNames: List<String> = emptyList(),
        experimentTitle: String = "DIGITAL LOGIC VERIFICATION",
        secretKey: String = "LOGIC_LABS_VERIFICATION_KEY",
        ledIndices: List<Int> = (0 until (outputNames.size.coerceAtLeast(1))).toList()
    ): TestBenchReport {
        val diagnostics = mutableListOf<DiagnosticIssue>()

        // 1. Diagnostic Checks
        if (!circuit.masterPower) {
            diagnostics.add(DiagnosticIssue(isError = true, message = "Master DC Power Supply (+5V) is turned OFF! Turn on power."))
        }

        if (circuit.placedChips.isEmpty()) {
            diagnostics.add(DiagnosticIssue(isError = true, message = "No ICs placed on breadboard! Mount required chips."))
        }

        // Check power connections on placed chips
        for (chip in circuit.placedChips) {
            val vccSocket = chip.getPinSocket(chip.model.vccPin)
            val gndSocket = chip.getPinSocket(chip.model.gndPin)
            val vccLvl = circuit.getSocketLevel(vccSocket)
            val gndLvl = circuit.getSocketLevel(gndSocket)

            if (circuit.masterPower && vccLvl != ElectricalLevel.HIGH) {
                diagnostics.add(
                    DiagnosticIssue(
                        isError = true,
                        message = "IC ${chip.placedIc.partNumber} VCC (Pin ${chip.model.vccPin}) is not receiving +5V power."
                    )
                )
            }
            if (circuit.masterPower && gndLvl != ElectricalLevel.LOW) {
                diagnostics.add(
                    DiagnosticIssue(
                        isError = true,
                        message = "IC ${chip.placedIc.partNumber} GND (Pin ${chip.model.gndPin}) is not connected to 0V Ground."
                    )
                )
            }
        }

        if (circuit.contentionDetected) {
            diagnostics.add(DiagnosticIssue(isError = true, message = "Bus contention detected! Opposing totem-pole outputs connected together."))
        }
        if (circuit.isReversePolarityBurned) {
            diagnostics.add(DiagnosticIssue(isError = true, message = "Reverse polarity detected on chip substrate! Check VCC/GND polarity."))
        }

        // Check input switch connectivity
        if (switchIndices.isEmpty()) {
            diagnostics.add(DiagnosticIssue(isError = true, message = "No input switches specified for verification."))
            return TestBenchReport(
                totalCount = 0,
                passedCount = 0,
                rows = emptyList(),
                isAllPassed = false,
                hmacSha256Hash = "",
                diagnostics = diagnostics,
                inputNames = inputNames,
                outputNames = outputNames,
                experimentTitle = experimentTitle,
                sweepDurationMs = 0L
            )
        }

        for (swIdx in switchIndices) {
            if (swIdx in 0..7) {
                val swSocket = AD200Topology.TERM_SW0 + swIdx
                if (circuit.dsu.getNetSize(swSocket) <= 1) {
                    diagnostics.add(
                        DiagnosticIssue(
                            isError = true,
                            message = "Input Switch SW$swIdx is not connected to any component on the breadboard."
                        )
                    )
                }
            } else {
                diagnostics.add(
                    DiagnosticIssue(
                        isError = true,
                        message = "Switch index SW$swIdx is out of range 0..7."
                    )
                )
            }
        }

        // Check output LED connectivity
        for (ledIdx in ledIndices) {
            if (ledIdx in 0..7) {
                val ledSocket = AD200Topology.TERM_LED0 + ledIdx
                if (circuit.dsu.getNetSize(ledSocket) <= 2) {
                    diagnostics.add(
                        DiagnosticIssue(
                            isError = true,
                            message = "Output LED$ledIdx is not connected to any logic output on the breadboard (floating)."
                        )
                    )
                }
            } else {
                diagnostics.add(
                    DiagnosticIssue(
                        isError = true,
                        message = "LED index LED$ledIdx is out of range 0..7."
                    )
                )
            }
        }

        if (switchIndices.any { it !in 0..7 } || ledIndices.any { it !in 0..7 }) {
            return TestBenchReport(
                totalCount = 0,
                passedCount = 0,
                rows = emptyList(),
                isAllPassed = false,
                hmacSha256Hash = "",
                diagnostics = diagnostics,
                inputNames = inputNames,
                outputNames = outputNames,
                experimentTitle = experimentTitle,
                sweepDurationMs = 0L
            )
        }

        // 2. Truth Table Verification
        val n = switchIndices.size.coerceIn(1, 8)
        val vectorCount = 1 shl n
        val rows = mutableListOf<TruthTableRow>()
        var passedCount = 0

        // Save current switch state
        val originalSwitches = circuit.switches.copyOf()

        // Isolate inputs: ensure switches not under test are grounded (LOW) so they cannot
        // inject spurious logic levels into the breadboard during the vector sweep.
        for (i in circuit.switches.indices) {
            if (i !in switchIndices) {
                circuit.switches[i] = false
            }
        }

        val digestPayload = StringBuilder()
        val sweepStartNanos = System.nanoTime()

        for (v in 0 until vectorCount) {
            val inputs = mutableListOf<Boolean>()
            for (i in 0 until n) {
                val bit = ((v shr (n - 1 - i)) and 1) == 1
                inputs.add(bit)
                circuit.switches[switchIndices[i]] = bit
            }

            circuit.step()
            val actual = outputReader()
            val expected = expectedFunction(inputs)
            val matched = (actual == expected) && diagnostics.none { it.isError }
            if (matched) passedCount++

            rows.add(TruthTableRow(inputs, expected, actual, matched))

            digestPayload.append("V=").append(v)
                .append(":IN=").append(inputs.joinToString("") { if (it) "1" else "0" })
                .append(":OUT=").append(actual.joinToString("") { if (it) "1" else "0" })
                .append(";")
        }

        // Restore original switch state
        System.arraycopy(originalSwitches, 0, circuit.switches, 0, originalSwitches.size)
        circuit.step()

        // Elapsed readout for the report header. Measured outside [digestPayload], so
        // the seal stays byte-identical to the recorded baseline.
        val sweepDurationMs = (System.nanoTime() - sweepStartNanos) / 1_000_000L

        // 3. Compute HMAC-SHA256
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(secretKey.toByteArray(), "HmacSHA256"))
        val hashBytes = hmac.doFinal(digestPayload.toString().toByteArray())
        val hashHex = hashBytes.joinToString("") { "%02x".format(it) }

        return TestBenchReport(
            rows = rows,
            passedCount = passedCount,
            totalCount = vectorCount,
            isAllPassed = (passedCount == vectorCount && diagnostics.none { it.isError }),
            hmacSha256Hash = hashHex,
            diagnostics = diagnostics,
            inputNames = if (inputNames.isNotEmpty()) inputNames else switchIndices.map { "SW$it" },
            outputNames = if (outputNames.isNotEmpty()) outputNames else ledIndices.map { "LED$it" },
            experimentTitle = experimentTitle,
            sweepDurationMs = sweepDurationMs
        )
    }
}
