package com.carelipik.app.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.carelipik.app.domain.export.ConsultationPdf
import com.carelipik.app.domain.export.ConsultationPdfExportResult
import com.carelipik.app.domain.export.ConsultationPdfExporter
import com.carelipik.app.domain.model.ApprovedConsultation
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Creates a polished clinical-note PDF locally in private cache. */
class AndroidConsultationPdfExporter(context: Context) : ConsultationPdfExporter {
    private val exportDirectory = File(context.cacheDir, EXPORT_DIRECTORY)

    override suspend fun export(
        consultation: ApprovedConsultation
    ): ConsultationPdfExportResult = withContext(Dispatchers.IO) {
        runCatching {
            exportDirectory.mkdirs()
            cleanupStaleExports()
            val displayName = "carelipik-consultation-${consultation.id}.pdf"
            val outputFile = File(exportDirectory, displayName)
            val pendingFile = File(exportDirectory, "$displayName.pending")
            PdfDocument().useDocument { document ->
                ConsultationPdfLayout(document).render(consultation)
                pendingFile.outputStream().use(document::writeTo)
            }
            if (!pendingFile.renameTo(outputFile)) {
                pendingFile.copyTo(outputFile, overwrite = true)
                check(pendingFile.delete()) { "Could not finish creating the PDF." }
            }
            require(outputFile.isFile && outputFile.length() > 0L) {
                "The generated PDF is empty."
            }
            ConsultationPdf(
                localPath = outputFile.absolutePath,
                displayName = displayName,
                sizeBytes = outputFile.length()
            )
        }.fold(
            onSuccess = ConsultationPdfExportResult::Success,
            onFailure = { error ->
                ConsultationPdfExportResult.Failure(
                    error.message ?: "The approved consultation PDF could not be created."
                )
            }
        )
    }

    private fun cleanupStaleExports() {
        val cutoff = System.currentTimeMillis() - MAX_EXPORT_AGE_MILLIS
        exportDirectory.listFiles().orEmpty()
            .filter { it.lastModified() < cutoff || it.name.endsWith(".pending") }
            .forEach(File::delete)
    }

    private inline fun <T> PdfDocument.useDocument(block: (PdfDocument) -> T): T = try {
        block(this)
    } finally {
        close()
    }

    private companion object {
        const val EXPORT_DIRECTORY = "consultation_exports"
        const val MAX_EXPORT_AGE_MILLIS = 24L * 60L * 60L * 1_000L
    }
}

private class ConsultationPdfLayout(private val document: PdfDocument) {
    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK
        textSize = 10.5f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val labelPaint = TextPaint(bodyPaint).apply {
        color = MUTED
        textSize = 8.5f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val sectionPaint = TextPaint(bodyPaint).apply {
        color = TEAL
        textSize = 12f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val titlePaint = TextPaint(bodyPaint).apply {
        color = INK
        textSize = 21f
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private var pageNumber = 0
    private var page: PdfDocument.Page? = null
    private lateinit var canvas: Canvas
    private var y = TOP_MARGIN
    private lateinit var consultation: ApprovedConsultation

    fun render(consultation: ApprovedConsultation) {
        this.consultation = consultation
        startPage(consultation)
        patientSummary(consultation)
        section("Presenting complaint", consultation.draft.presentingComplaint)
        section("History", consultation.draft.history)
        section("Key findings", consultation.draft.keyFindings)
        section("Assessment notes", consultation.draft.assessmentNotes)
        section("Plan notes", consultation.draft.planNotes)
        finishPage()
    }

    private fun startPage(consultation: ApprovedConsultation) {
        pageNumber += 1
        page = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        )
        canvas = checkNotNull(page).canvas
        canvas.drawColor(Color.WHITE)
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 10f, Paint().apply { color = TEAL })
        canvas.drawText("CareLipik", LEFT_MARGIN, 42f, TextPaint(titlePaint).apply {
            textSize = 15f
            color = TEAL
        })
        if (pageNumber == 1) {
            canvas.drawText("Doctor-approved clinical note", LEFT_MARGIN, 78f, titlePaint)
            canvas.drawText(
                "Approved ${formatDate(consultation.approvedAtMillis)}",
                LEFT_MARGIN,
                98f,
                labelPaint
            )
            y = 122f
        } else {
            canvas.drawText("Clinical note - continued", LEFT_MARGIN, 70f, sectionPaint)
            y = 92f
        }
    }

    private fun patientSummary(consultation: ApprovedConsultation) {
        ensureSpace(96f, consultation)
        val top = y
        canvas.drawRoundRect(
            LEFT_MARGIN,
            top,
            PAGE_WIDTH - RIGHT_MARGIN,
            top + 76f,
            10f,
            10f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PALE_TEAL }
        )
        drawLabelAndValue("PATIENT / REFERENCE", consultation.patientName, top + 18f)
        drawLabelAndValue("AGE", consultation.patientAge.ifBlank { "Not recorded" }, top + 46f)
        drawLabelAndValue("VISIT REASON", consultation.visitReason.ifBlank { "Not recorded" }, top + 64f)
        y = top + 96f
    }

    private fun drawLabelAndValue(label: String, value: String, baseline: Float) {
        canvas.drawText(label, LEFT_MARGIN + 14f, baseline, labelPaint)
        canvas.drawText(
            value.take(MAX_SUMMARY_CHARACTERS),
            SUMMARY_VALUE_X,
            baseline,
            bodyPaint
        )
    }

    private fun section(title: String, content: String) {
        val displayedContent = content.trim().ifBlank { "Not documented" }
        val layout = textLayout(displayedContent, bodyPaint, CONTENT_WIDTH)
        val requiredHeight = 28f + layout.height
        if (requiredHeight <= availableHeight()) {
            drawSection(title, layout)
            return
        }
        if (availableHeight() < MIN_SECTION_START_HEIGHT) {
            newPage()
        }
        drawLongSection(title, displayedContent)
    }

    private fun drawSection(title: String, layout: StaticLayout) {
        canvas.drawText(title, LEFT_MARGIN, y, sectionPaint)
        y += 10f
        canvas.drawRect(LEFT_MARGIN, y, PAGE_WIDTH - RIGHT_MARGIN, y + 1f, Paint().apply {
            color = DIVIDER
        })
        y += 10f
        canvas.save()
        canvas.translate(LEFT_MARGIN, y)
        layout.draw(canvas)
        canvas.restore()
        y += layout.height + SECTION_GAP
    }

    private fun drawLongSection(title: String, content: String) {
        var remaining = content
        var continued = false
        while (remaining.isNotBlank()) {
            val heading = if (continued) "$title (continued)" else title
            canvas.drawText(heading, LEFT_MARGIN, y, sectionPaint)
            y += 20f
            val fitting = fittingPrefix(remaining, availableHeight().toInt())
            val pageText = remaining.take(fitting).trim()
            val layout = textLayout(pageText, bodyPaint, CONTENT_WIDTH)
            canvas.save()
            canvas.translate(LEFT_MARGIN, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + SECTION_GAP
            remaining = remaining.drop(fitting).trimStart()
            if (remaining.isNotBlank()) {
                newPage()
                continued = true
            }
        }
    }

    private fun fittingPrefix(text: String, maxHeight: Int): Int {
        var low = 1
        var high = text.length
        var best = 1
        while (low <= high) {
            val middle = (low + high) / 2
            val candidate = text.take(middle)
            if (textLayout(candidate, bodyPaint, CONTENT_WIDTH).height <= maxHeight) {
                best = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        if (best < text.length) {
            val wordBoundary = text.lastIndexOf(' ', startIndex = best)
            if (wordBoundary > 0) best = wordBoundary
        }
        return best.coerceAtLeast(1)
    }

    private fun ensureSpace(requiredHeight: Float, consultation: ApprovedConsultation) {
        if (requiredHeight > availableHeight()) {
            finishPage()
            startPage(consultation)
        }
    }

    private fun newPage() {
        finishPage()
        startPage(consultation)
    }

    private fun finishPage() {
        page?.let { activePage ->
            canvas.drawText(
                "CareLipik assists with documentation. Clinical accuracy remains the doctor's responsibility.",
                LEFT_MARGIN,
                PAGE_HEIGHT - 30f,
                TextPaint(labelPaint).apply { textSize = 7.5f }
            )
            canvas.drawText(
                "Page $pageNumber",
                PAGE_WIDTH - RIGHT_MARGIN - 32f,
                PAGE_HEIGHT - 16f,
                labelPaint
            )
            document.finishPage(activePage)
        }
        page = null
    }

    private fun availableHeight(): Float = PAGE_HEIGHT - BOTTOM_MARGIN - y

    private fun textLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setLineSpacing(2f, 1f)
            .build()

    private fun formatDate(timestamp: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val LEFT_MARGIN = 44f
        const val RIGHT_MARGIN = 44f
        const val TOP_MARGIN = 54f
        const val BOTTOM_MARGIN = 58f
        const val SECTION_GAP = 22f
        const val MIN_SECTION_START_HEIGHT = 120f
        const val CONTENT_WIDTH = PAGE_WIDTH - LEFT_MARGIN.toInt() - RIGHT_MARGIN.toInt()
        const val MAX_SUMMARY_CHARACTERS = 58
        const val SUMMARY_VALUE_X = 180f
        val INK = Color.rgb(30, 43, 45)
        val MUTED = Color.rgb(84, 103, 105)
        val TEAL = Color.rgb(0, 107, 105)
        val PALE_TEAL = Color.rgb(231, 246, 245)
        val DIVIDER = Color.rgb(201, 218, 217)
    }
}
