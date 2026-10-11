package com.carelipik.app.data.prescription

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.carelipik.app.domain.prescription.PrescriptionPreviewContent
import java.io.ByteArrayOutputStream

/** The preview and PDF export share these exact page bytes. */
object AndroidPrescriptionPdfRenderer {
    fun render(source: String): ByteArray {
        val document = PdfDocument()
        var page: PdfDocument.Page? = null
        try {
            var pageNumber = 0
            var y = 48f
            fun newPage() {
                page?.let(document::finishPage)
                page = null
                require(++pageNumber <= 40) { "Prescription exceeds 40 pages." }
                page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                page!!.canvas.drawColor(Color.WHITE)
                y = 48f
            }
            newPage()
            PrescriptionPreviewContent.blocks(source).forEach { block ->
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(30, 41, 59)
                    textSize = if (block.heading) 14f else 11f
                    typeface = Typeface.create("sans-serif", if (block.heading) Typeface.BOLD else Typeface.NORMAL)
                }
                val layout = StaticLayout.Builder.obtain(block.text, 0, block.text.length, paint, 499)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build()
                for (line in 0 until layout.lineCount) {
                    val height = (layout.getLineBottom(line) - layout.getLineTop(line)).toFloat() + 4f
                    if (y + height > 794f) newPage()
                    val text = block.text.substring(layout.getLineStart(line), layout.getLineEnd(line)).trimEnd()
                    page!!.canvas.drawText(text, 48f, y - paint.ascent(), paint)
                    y += height
                }
                y += if (block.heading) 6f else 14f
            }
            page?.let(document::finishPage)
            page = null
            return ByteArrayOutputStream().also(document::writeTo).toByteArray()
        } finally {
            page?.let(document::finishPage)
            document.close()
        }
    }
}
