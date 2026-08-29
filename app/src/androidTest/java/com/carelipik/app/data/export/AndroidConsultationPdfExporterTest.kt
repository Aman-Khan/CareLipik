package com.carelipik.app.data.export

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import com.carelipik.app.domain.export.ConsultationPdfExportResult
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.model.ClinicalDraft
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidConsultationPdfExporterTest {
    @Test
    fun approvedClinicalNoteCreatesReadableMultiPagePdfWithoutAudio() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val result = AndroidConsultationPdfExporter(context).export(
            ApprovedConsultation(
                id = SAMPLE_ID,
                approvedAtMillis = 1_788_000_000_000L,
                patientName = "Synthetic patient reference",
                patientAge = "42",
                visitReason = "Synthetic follow-up",
                draft = ClinicalDraft(
                    presentingComplaint = "Synthetic cough and fever for three days.",
                    history = longSyntheticText("History"),
                    keyFindings = longSyntheticText("Key finding"),
                    assessmentNotes = "Synthetic assessment for layout verification only.",
                    planNotes = "Synthetic plan. No diagnosis or prescription is represented."
                )
            )
        )

        assertTrue(result is ConsultationPdfExportResult.Success)
        val file = File((result as ConsultationPdfExportResult.Success).pdf.localPath)
        assertTrue(file.isFile && file.length() > 1_000L)
        assertFalse(file.name.contains("patient", ignoreCase = true))
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertTrue(renderer.pageCount >= 2)
                repeat(renderer.pageCount) { pageIndex ->
                    renderer.openPage(pageIndex).use { page ->
                        assertTrue(page.width > 0 && page.height > 0)
                    }
                }
            }
        }
    }

    private fun longSyntheticText(prefix: String): String =
        (1..35).joinToString(" ") { index ->
            "$prefix $index contains synthetic clinical documentation for PDF wrapping and pagination."
        }

    private companion object {
        const val SAMPLE_ID = "00000000-0000-0000-0000-000000000099"
    }
}
