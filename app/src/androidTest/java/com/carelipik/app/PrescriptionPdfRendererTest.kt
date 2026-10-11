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
    @Test fun convertedLetterhead_acceptsSavedProfileAndSelectedPrescriptionInputs() {
        val template = PrescriptionTemplates.common.replace(PrescriptionTemplates.BODY,
            "${PrescriptionTemplates.DOCTOR}\n${PrescriptionTemplates.PATIENT}\n${PrescriptionTemplates.BODY}")
        val engine = com.carelipik.app.data.prescription.GeminiLetterheadConversionEngine(generate = { prompt, _, _, mime ->
            assertEquals("application/pdf", mime)
            assertTrue(prompt.contains("EXPLICITLY ADDED"))
            org.json.JSONObject().put("latex", template)
        })
        var saved: String? = null
        val vm = com.carelipik.app.ui.screens.prescription.PrescriptionViewModel(
            repository = object : com.carelipik.app.domain.prescription.LetterheadRepository {
                override suspend fun load(): String? = saved
                override suspend fun save(template: String) { saved = template }
            }, engine = engine, readPdf = { "%PDF-synthetic fixture".toByteArray() },
            writeDocument = { _, _ -> }, processAsynchronously = false)
        vm.setConsent(true)
        vm.convert(android.net.Uri.parse("content://synthetic-letterhead"))
        vm.saveTemplate()
        assertEquals(template, saved)
        vm.create(ClinicalDraft(patientAge = "30"), "Synthetic patient", "Example doctor",
            "Example specialty", "REG-123", "Example clinic")
        assertTrue(vm.uiState.value.medicines.isEmpty())
        vm.addMedicine()
        vm.updateMedicine(0, com.carelipik.app.domain.model.MedicationDraft(name = "ExampleMed",
            dose = "1 tablet", frequency = "morning", duration = "5 days"))
        val source = vm.uiState.value.source
        assertTrue(source.contains("REG-123"))
        assertTrue(source.contains("ExampleMed"))
        assertTrue(source.contains("Synthetic patient"))
        assertTrue(!source.contains(PrescriptionTemplates.DOCTOR) && !source.contains(PrescriptionTemplates.PATIENT))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("synthetic-letterhead-", ".pdf", context.cacheDir)
        try {
            file.writeBytes(AndroidPrescriptionPdfRenderer.render(source))
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer -> assertEquals(1, renderer.pageCount) }
            }
        } finally { file.delete() }
    }

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
