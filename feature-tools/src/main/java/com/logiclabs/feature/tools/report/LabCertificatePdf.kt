package com.logiclabs.feature.tools.report

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.logiclabs.feature.tools.testbench.TestBenchReport
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a [TestBenchReport] to a one-page A4 PDF and exposes it as a shareable
 * content [Uri].
 *
 * A4 at 72 dpi is 595 x 842 pt, which is what `PdfDocument` wants in points; the
 * platform renderer scales it up at print time so there is no need to compose at
 * 300 dpi. `PdfDocument` is API 19+, so this is safe on minSdk 26.
 */
object LabCertificatePdf {

    /** Must match the `<provider android:authorities>` value declared in `:app`. */
    const val FILE_PROVIDER_AUTHORITY = "com.logiclabs.app.fileprovider"

    /** Subdirectory of `cacheDir` the `<cache-path>` in `res/xml/file_paths.xml` exposes. */
    private const val CACHE_SUBDIR = "reports"

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f

    /**
     * Writes the certificate PDF into `context.cacheDir/reports/` and returns a
     * `content://` [Uri] for it.
     *
     * Returns **null** on any failure, including the case where the `:app` manifest has
     * no matching `<provider>` registered for [FILE_PROVIDER_AUTHORITY] — `FileProvider`
     * throws `IllegalArgumentException` in that case. Export is a convenience, never a
     * critical path, so nothing here is allowed to propagate.
     *
     * Grant read access on the receiving intent with
     * `Intent.FLAG_GRANT_READ_URI_PERMISSION`.
     */
    fun export(context: Context, report: TestBenchReport): Uri? {
        return try {
            val dir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(dir, "logic-labs-report-$stamp.pdf")

            val document = PdfDocument()
            try {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
                val page = document.startPage(pageInfo)
                drawPage(page.canvas, report)
                document.finishPage(page)
                FileOutputStream(file).use { document.writeTo(it) }
            } finally {
                document.close()
            }

            FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
        } catch (_: Throwable) {
            // Missing <provider>, no cache space, or a renderer failure: all non-fatal.
            null
        }
    }

    /** Copies [hash] onto the clipboard. Silently does nothing if the service is absent. */
    fun copyHashToClipboard(context: Context, hash: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Logic Labs verification hash", hash))
        } catch (_: Throwable) {
        }
    }

    private fun drawPage(canvas: android.graphics.Canvas, report: TestBenchReport) {
        val title = Paint().apply {
            color = AndroidColor.parseColor("#101216")
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val heading = Paint().apply {
            color = AndroidColor.parseColor("#101216")
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val body = Paint().apply {
            color = AndroidColor.parseColor("#2A2E36")
            textSize = 10f
            typeface = Typeface.SANS_SERIF
            isAntiAlias = true
        }
        val mono = Paint().apply {
            color = AndroidColor.parseColor("#2A2E36")
            textSize = 9f
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
        }
        val monoBold = Paint().apply {
            color = AndroidColor.parseColor("#101216")
            textSize = 9f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        val rule = Paint().apply {
            color = AndroidColor.parseColor("#B8BCC4")
            strokeWidth = 0.8f
        }
        val frame = Paint().apply {
            color = AndroidColor.parseColor("#3A3E47")
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
        }

        canvas.drawRect(MARGIN * 0.5f, MARGIN * 0.5f, PAGE_WIDTH - MARGIN * 0.5f, PAGE_HEIGHT - MARGIN * 0.5f, frame)

        // Every text call below is bounded by this. 595 - 2 x 48 = 499pt.
        val contentWidth = PAGE_WIDTH - MARGIN * 2f

        var y = MARGIN + 18f
        // The wordmark is the one string on the page that must never shrink, so it is
        // ellipsised rather than wrapped — if it ever did not fit, that is a page-setup
        // bug to see immediately, not something to hide by reflowing.
        canvas.drawText(
            ellipsize("LOGIC LABS — VERIFICATION CERTIFICATE", title, contentWidth),
            MARGIN, y, title
        )
        y += lineHeight(title)
        y = drawWrapped(
            canvas, "K&H IDL-800A Digital Logic Trainer · AD-200 Breadboard",
            MARGIN, y, contentWidth, body, maxLines = 2
        )
        y += 4f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rule)

        y += 20f
        canvas.drawText("EXPERIMENT", MARGIN, y, heading)
        y += lineHeight(heading)
        // Caller-supplied and the least predictable string on the page. The classic lab
        // titles fit on one line at 10pt, but the new experiment names carry a variation
        // suffix ("Fundamental Gates from Discrete Components · Variation A · Diode OR
        // gate"), which does not. Two lines, then ellipsis.
        y = drawWrapped(canvas, report.experimentTitle, MARGIN, y, contentWidth, body, maxLines = 2)
        val issued = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())
        canvas.drawText("Issued: $issued", MARGIN, y, body)
        y += lineHeight(body)
        canvas.drawText(
            "Result: ${if (report.isAllPassed) "PASS" else "FAIL"}   " +
                "Vectors: ${report.passedCount} / ${report.totalCount}",
            MARGIN, y, body
        )
        y += lineHeight(body)

        y += 14f
        canvas.drawText("TRUTH TABLE", MARGIN, y, heading)
        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rule)
        y += 14f

        val inputHeader = if (report.inputNames.isEmpty()) "IN" else report.inputNames.joinToString(" ")
        val outputHeader = if (report.outputNames.isEmpty()) "OUT" else report.outputNames.joinToString(" ")
        // Column origins and, crucially, the width each one owns. The old code had only
        // origins, so a wide header simply drew over its neighbour: "EXPECTED [O0 O1 O2
        // O3 O4 O5 O6 O7]" is ~184pt of 9pt mono in a 130pt column, which is exactly the
        // overlapping text seen on the 8-output decoder labs. Every cell is now clipped
        // to its own column, with a GUTTER of clear space so adjacent text never touches.
        val colVector = MARGIN
        val colInputs = MARGIN + 44f
        val colExpected = MARGIN + 190f
        val colActual = MARGIN + 320f
        val colResult = MARGIN + 440f
        val rightEdge = PAGE_WIDTH - MARGIN
        val gutter = 6f
        val wVector = colInputs - colVector - gutter
        val wInputs = colExpected - colInputs - gutter
        val wExpected = colActual - colExpected - gutter
        val wActual = colResult - colActual - gutter
        val wResult = rightEdge - colResult

        canvas.drawText(ellipsize("VECTOR", monoBold, wVector), colVector, y, monoBold)
        canvas.drawText(ellipsize("IN [$inputHeader]", monoBold, wInputs), colInputs, y, monoBold)
        canvas.drawText(
            ellipsize("EXPECTED [$outputHeader]", monoBold, wExpected), colExpected, y, monoBold
        )
        canvas.drawText(ellipsize("ACTUAL", monoBold, wActual), colActual, y, monoBold)
        canvas.drawText(ellipsize("RESULT", monoBold, wResult), colResult, y, monoBold)
        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rule)
        y += 14f

        val rowStep = lineHeight(mono)
        val rowLimit = PAGE_HEIGHT - MARGIN - 190f
        for ((index, row) in report.rows.withIndex()) {
            if (y > rowLimit) {
                canvas.drawText(
                    ellipsize(
                        "… ${report.rows.size - index} further vectors omitted",
                        mono, contentWidth
                    ),
                    colVector, y, mono
                )
                y += rowStep
                break
            }
            canvas.drawText("${index + 1}", colVector, y, mono)
            canvas.drawText(ellipsize(bitString(row.inputValues), mono, wInputs), colInputs, y, mono)
            canvas.drawText(
                ellipsize(bitString(row.expectedOutputs), mono, wExpected), colExpected, y, mono
            )
            canvas.drawText(
                ellipsize(bitString(row.actualOutputs), mono, wActual), colActual, y, mono
            )
            canvas.drawText(if (row.isPassed) "PASS" else "FAIL", colResult, y, monoBold)
            y += rowStep
        }

        y += 12f
        canvas.drawText("CIRCUIT SUMMARY", MARGIN, y, heading)
        y += lineHeight(heading)
        y = drawWrapped(
            canvas,
            "Inputs: ${report.inputNames.size}   Outputs: ${report.outputNames.size}   " +
                "Vectors swept: ${report.totalCount}",
            MARGIN, y, contentWidth, body, maxLines = 2
        )
        val errors = report.diagnostics.count { it.isError }
        canvas.drawText("Diagnostics: ${report.diagnostics.size} (errors: $errors)", MARGIN, y, body)
        y += lineHeight(body)

        // Diagnostic messages are free-form sentences and the longest strings on the page.
        // They are bulleted and indented, so their column is 8pt narrower, and each is
        // held to two lines so a verbose one cannot displace the seal below.
        val bulletX = MARGIN + 8f
        val bulletWidth = contentWidth - 8f
        for (issue in report.diagnostics.take(6)) {
            y = drawWrapped(
                canvas,
                "• ${if (issue.isError) "ERROR" else "NOTE"}: ${issue.message}",
                bulletX, y, bulletWidth, mono, maxLines = 2
            )
        }

        y += 18f
        canvas.drawText("HMAC-SHA256 PROVENANCE SEAL", MARGIN, y, heading)
        y += 6f
        canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, rule)
        y += 14f
        val hashStep = lineHeight(mono)
        for (group in hashRows(report.hmacSha256Hash)) {
            canvas.drawText(group, MARGIN, y, mono)
            y += hashStep
        }

        // Footer is anchored to the page bottom, not to `y`, and grows upward: measuring
        // it first means a two-line footer starts one line higher instead of having its
        // second line fall off the page.
        val footerLines = wrap(
            "This document is generated from a signed simulation run. The seal binds the input " +
                "vectors to the observed outputs.",
            mono, contentWidth
        )
        val footerStep = lineHeight(mono)
        var footerY = PAGE_HEIGHT - MARGIN - footerStep * (footerLines.size - 1)
        for (line in footerLines) {
            canvas.drawText(line, MARGIN, footerY, mono)
            footerY += footerStep
        }
    }

    /** Splits the 64-hex-character digest into four grouped 16-character rows. */
    fun hashRows(hash: String): List<String> {
        if (hash.isEmpty()) return listOf("(unsealed)")
        return hash.chunked(16).map { row -> row.chunked(4).joinToString(" ") }
    }

    // --- Text layout ---------------------------------------------------------
    //
    // `Canvas.drawText` neither wraps nor clips: it draws one line from the baseline
    // given and runs straight off the page edge if the string is too long, with no
    // error and nothing in the output to say text was lost. Every truncated and
    // overlapping field in the exported report came from that — a long experiment
    // title, a diagnostic sentence, the footer, and a truth-table header whose
    // bracketed signal list grew past the next column's x. The four helpers below
    // are the whole fix: measure first, then place.

    /**
     * Baseline-to-baseline advance for [paint].
     *
     * Derived from the font's own metrics rather than a hardcoded step. The old code
     * advanced 12–14pt after every line regardless of paint, which is wider than 9pt
     * mono needs and *narrower* than the 20pt title, so the title's descenders ran
     * into the line beneath it.
     */
    private fun lineHeight(paint: Paint): Float =
        paint.fontMetrics.let { it.descent - it.ascent + it.leading }

    /**
     * [text] shortened to fit [maxWidth], with a trailing ellipsis when it had to be cut.
     *
     * For fields that must stay on one line — a table cell, a column header. Anything
     * that may legitimately need two lines goes through [wrap] instead.
     */
    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth <= 0f) return ""
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        val room = maxWidth - paint.measureText(ellipsis)
        if (room <= 0f) return ellipsis
        val kept = paint.breakText(text, true, room, null)
        return text.substring(0, kept).trimEnd() + ellipsis
    }

    /**
     * [text] broken into lines that each fit [maxWidth], on word boundaries where possible.
     *
     * `Paint.breakText` gives the character count that fits, which is a mid-word cut; this
     * walks back to the last space so the wrap reads as prose. A single word longer than
     * the column is cut hard rather than overflowing, since overflow is the defect being
     * fixed.
     */
    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (maxWidth <= 0f || text.isEmpty()) return listOf(text)
        if (paint.measureText(text) <= maxWidth) return listOf(text)

        val lines = mutableListOf<String>()
        var rest = text
        while (rest.isNotEmpty()) {
            if (paint.measureText(rest) <= maxWidth) {
                lines += rest
                break
            }
            val fits = paint.breakText(rest, true, maxWidth, null).coerceAtLeast(1)
            // Back up to a space, but not so far that we emit an almost-empty line.
            val space = rest.lastIndexOf(' ', fits.coerceAtMost(rest.length - 1))
            val cut = if (space > fits / 3) space else fits
            lines += rest.substring(0, cut).trimEnd()
            rest = rest.substring(cut).trimStart()
        }
        return lines
    }

    /**
     * Draws [text] wrapped inside [maxWidth] and returns the baseline for the line *after* it.
     *
     * [maxLines] caps the block; the last line drawn is ellipsised if there is more text, so
     * a runaway string costs a fixed amount of page instead of pushing the seal off the
     * bottom.
     */
    private fun drawWrapped(
        canvas: android.graphics.Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        paint: Paint,
        maxLines: Int = 3,
    ): Float {
        val lh = lineHeight(paint)
        val lines = wrap(text, paint, maxWidth)
        var baseline = y
        for ((i, line) in lines.withIndex()) {
            if (i == maxLines - 1 && lines.size > maxLines) {
                // Fold everything still unwritten into one ellipsised line.
                val tail = lines.drop(i).joinToString(" ")
                canvas.drawText(ellipsize(tail, paint, maxWidth), x, baseline, paint)
                return baseline + lh
            }
            if (i >= maxLines) break
            canvas.drawText(line, x, baseline, paint)
            baseline += lh
        }
        return baseline
    }

    private fun bitString(values: List<Boolean>): String {
        if (values.isEmpty()) return "-"
        val sb = StringBuilder(values.size)
        for (v in values) sb.append(if (v) '1' else '0')
        return sb.toString()
    }
}
