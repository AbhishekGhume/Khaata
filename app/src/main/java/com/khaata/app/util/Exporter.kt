package com.khaata.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.khaata.app.data.model.Expense
import com.khaata.app.data.model.MonthSummary
import com.khaata.app.data.model.monthLabel
import java.io.File
import java.util.Locale

/**
 * On-device CSV/PDF export of the whole ledger. Everything here is platform/IO
 * work that needs a [Context] (file writing, the share sheet, PDF rendering), so
 * it lives outside the ViewModel. Callers should invoke the build/write helpers
 * off the main thread (Dispatchers.IO) and only launch the share intent on the
 * main thread.
 *
 * Generated files land in cacheDir/exports and are handed to other apps through
 * the FileProvider declared in the manifest (authority "${packageName}.fileprovider").
 */
object Exporter {

    private fun categoryLabel(key: String, categories: List<CategoryMeta>): String =
        categoryMeta(key, categories).label

    /**
     * Escapes a value for RFC-4180 CSV (quote it and double any inner quotes), and
     * neutralizes spreadsheet formula injection: a user-entered note like `=1+1` or
     * `@SUM(...)` would otherwise be evaluated as a formula when the CSV is opened in
     * Excel/Sheets. Prefixing a leading `'` forces such cells to be read as text.
     */
    private fun csvCell(value: String): String {
        val guarded = if (value.isNotEmpty() && value.first() in charArrayOf('=', '+', '-', '@', '\t', '\r')) {
            "'$value"
        } else value
        val needsQuote = guarded.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = guarded.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }

    /**
     * A spreadsheet-friendly dump of every expense, newest first (as passed in).
     * Columns: Date, Category, Note, Amount (plain number, no ₹ so it stays numeric).
     */
    fun buildExpensesCsv(expenses: List<Expense>, categories: List<CategoryMeta>): String {
        val sb = StringBuilder()
        sb.append("Date,Category,Note,Amount\n")
        expenses.forEach { e ->
            sb.append(csvCell(e.date)).append(',')
                .append(csvCell(categoryLabel(e.category, categories))).append(',')
                .append(csvCell(e.note)).append(',')
                // Exact value with 2 decimals (Locale.ROOT so the separator is always
                // '.'). This is a backup — rounding to whole rupees here would silently
                // lose paise and stop sums reconciling with the in-app totals.
                .append(String.format(Locale.ROOT, "%.2f", e.amount))
                .append('\n')
        }
        return sb.toString()
    }

    /**
     * A printable PDF report: a monthly summary table (income / expenses / net)
     * followed by the full all-time expense list, paginated onto A4-ish pages.
     */
    fun buildLedgerPdf(
        context: Context,
        months: List<MonthSummary>,
        expenses: List<Expense>,
        categories: List<CategoryMeta>,
    ): File {
        val pageWidth = 595   // A4 @ 72dpi
        val pageHeight = 842
        val margin = 40f
        val doc = PdfDocument()

        val title = Paint().apply { textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = 0xFF1B2A38.toInt() }
        val heading = Paint().apply { textSize = 13f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = 0xFF1B2A38.toInt() }
        val body = Paint().apply { textSize = 11f; color = 0xFF1B2A38.toInt() }
        val muted = Paint().apply { textSize = 10f; color = 0xFF6B6357.toInt() }
        val rightBody = Paint(body).apply { textAlign = Paint.Align.RIGHT }
        val rightHeading = Paint(heading).apply { textAlign = Paint.Align.RIGHT }

        val lineHeight = 18f
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = margin

        fun newPage() {
            doc.finishPage(page)
            pageNumber += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > pageHeight - margin) newPage()
        }

        // ── Title ──
        canvas.drawText("Khaata — Ledger Export", margin, y, title)
        y += lineHeight + 6f
        canvas.drawText("All-time report · ${expenses.size} entries", margin, y, muted)
        y += lineHeight + 8f

        // ── Monthly summary ──
        val orderedMonths = months.sortedByDescending { it.monthKey }
        if (orderedMonths.isNotEmpty()) {
            ensureSpace(lineHeight * 2)
            canvas.drawText("Monthly summary", margin, y, heading)
            y += lineHeight + 2f
            val colIncome = pageWidth - margin - 240f
            val colExpense = pageWidth - margin - 120f
            val colNet = pageWidth - margin
            canvas.drawText("MONTH", margin, y, muted)
            canvas.drawText("INCOME", colIncome, y, Paint(muted).apply { textAlign = Paint.Align.RIGHT })
            canvas.drawText("EXPENSES", colExpense, y, Paint(muted).apply { textAlign = Paint.Align.RIGHT })
            canvas.drawText("NET", colNet, y, Paint(muted).apply { textAlign = Paint.Align.RIGHT })
            y += lineHeight
            orderedMonths.forEach { m ->
                ensureSpace(lineHeight)
                canvas.drawText(monthLabel(m.monthKey), margin, y, body)
                canvas.drawText(formatINR(m.income), colIncome, y, rightBody)
                canvas.drawText(formatINR(m.totalExpenses), colExpense, y, rightBody)
                canvas.drawText(formatINR(m.netSavings), colNet, y, rightBody)
                y += lineHeight
            }
            y += lineHeight
        }

        // ── Expense list ──
        ensureSpace(lineHeight * 2)
        canvas.drawText("All expenses", margin, y, heading)
        y += lineHeight + 2f
        val colAmount = pageWidth - margin
        val dateX = margin
        val catX = margin + 90f
        val noteX = margin + 230f
        val rightMuted = Paint(muted).apply { textAlign = Paint.Align.RIGHT }

        // The column header must be re-emitted on every page, or every page after the
        // first shows an unlabeled table.
        fun drawExpenseHeader() {
            canvas.drawText("DATE", dateX, y, muted)
            canvas.drawText("CATEGORY", catX, y, muted)
            canvas.drawText("NOTE", noteX, y, muted)
            canvas.drawText("AMOUNT", colAmount, y, rightMuted)
            y += lineHeight
        }
        drawExpenseHeader()

        expenses.forEach { e ->
            if (y + lineHeight > pageHeight - margin) {
                newPage()
                drawExpenseHeader()
            }
            canvas.drawText(e.date, dateX, y, body)
            canvas.drawText(categoryLabel(e.category, categories).take(20), catX, y, body)
            canvas.drawText(e.note.take(24), noteX, y, body)
            canvas.drawText(formatINR(e.amount), colAmount, y, rightBody)
            y += lineHeight
        }

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "khaata-ledger.pdf")
        try {
            doc.finishPage(page)
            file.outputStream().use { doc.writeTo(it) }
        } finally {
            // Always release the PdfDocument's native pages, even if writeTo throws
            // (e.g. disk full) — otherwise the native allocation leaks.
            doc.close()
        }
        return file
    }

    /** Writes text to cacheDir/exports/[fileName] and returns a shareable content Uri. */
    fun writeTextToCache(context: Context, fileName: String, content: String): Uri {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content)
        return uriFor(context, file)
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Opens the system share sheet for a generated file. */
    fun shareFile(context: Context, uri: Uri, mimeType: String, subject: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share export").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
