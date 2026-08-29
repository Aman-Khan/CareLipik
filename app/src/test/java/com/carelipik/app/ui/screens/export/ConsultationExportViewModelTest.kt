package com.carelipik.app.ui.screens.export

import com.carelipik.app.domain.export.ConsultationPdf
import com.carelipik.app.domain.export.ConsultationPdfExportResult
import com.carelipik.app.domain.export.ConsultationPdfExporter
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.model.ClinicalDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsultationExportViewModelTest {
    @Test
    fun exportCannotRunBeforeApprovedConsultationIsLoaded() {
        val exporter = CapturingExporter(successResult())
        val viewModel = ConsultationExportViewModel(exporter, processAsynchronously = false)

        viewModel.generate()

        assertEquals(ConsultationExportStatus.Empty, viewModel.uiState.value.status)
        assertNull(exporter.received)
    }

    @Test
    fun approvedConsultationGeneratesTemporaryPdf() {
        val exporter = CapturingExporter(successResult())
        val viewModel = ConsultationExportViewModel(exporter, processAsynchronously = false)
        val consultation = approvedConsultation()
        viewModel.load(consultation)

        viewModel.generate()

        assertEquals(consultation, exporter.received)
        assertEquals(ConsultationExportStatus.Generated, viewModel.uiState.value.status)
        assertEquals("approved.pdf", viewModel.uiState.value.pdf?.displayName)
    }

    @Test
    fun generationFailureCanBeRetried() {
        val exporter = CapturingExporter(
            ConsultationPdfExportResult.Failure("Synthetic generation failure")
        )
        val viewModel = ConsultationExportViewModel(exporter, processAsynchronously = false)
        viewModel.load(approvedConsultation())

        viewModel.generate()

        assertEquals(ConsultationExportStatus.Error, viewModel.uiState.value.status)
        assertEquals("Synthetic generation failure", viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.canGenerate)
    }

    private class CapturingExporter(
        private val result: ConsultationPdfExportResult
    ) : ConsultationPdfExporter {
        var received: ApprovedConsultation? = null

        override suspend fun export(
            consultation: ApprovedConsultation
        ): ConsultationPdfExportResult {
            received = consultation
            return result
        }
    }

    private fun approvedConsultation() = ApprovedConsultation(
        id = "00000000-0000-0000-0000-000000000001",
        approvedAtMillis = 123L,
        patientName = "Synthetic reference",
        patientAge = "40",
        visitReason = "Synthetic visit",
        draft = ClinicalDraft(history = "Synthetic history")
    )

    private fun successResult() = ConsultationPdfExportResult.Success(
        ConsultationPdf(
            localPath = "/private/cache/approved.pdf",
            displayName = "approved.pdf",
            sizeBytes = 1_024L
        )
    )
}
