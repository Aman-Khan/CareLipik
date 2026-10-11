package com.carelipik.app

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.carelipik.app.data.prescription.AndroidPrescriptionPdfRenderer
import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.prescription.PrescriptionDetail
import com.carelipik.app.domain.prescription.PrescriptionTemplates
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrescriptionPdfRendererTest {
    @Test fun renderer_createsReadablePdfAndPaginatesLongInstructions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("synthetic-rx-", ".pdf", context.cacheDir)
        try {
            val source = PrescriptionTemplates.create(null, ClinicalDraft(), "Synthetic patient", "Example doctor")
            file.writeBytes(AndroidPrescriptionPdfRenderer.render(source))
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer -> assertEquals(1, renderer.pageCount) }
            }
            val longSource = PrescriptionTemplates.updateDetails(source, mapOf(
                PrescriptionDetail.Notes to List(180) { "Synthetic instruction $it" }.joinToString("\n")
            ))
            file.writeBytes(AndroidPrescriptionPdfRenderer.render(longSource))
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer -> assertTrue(renderer.pageCount > 1) }
            }
        } finally { file.delete() }
    }
}
