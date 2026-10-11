package com.carelipik.app.ui.screens.export

import com.carelipik.app.domain.export.ConsultationExportFormat
import com.carelipik.app.domain.export.ConsultationExportResult
import com.carelipik.app.domain.export.ConsultationStandardExporter
import com.carelipik.app.domain.export.ExportedConsultationFile
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.repository.ConsultationReportArtifact
import com.carelipik.app.domain.repository.ConsultationReportRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsultationExportViewModelTest {
    @Test
    fun prescriptionAttachmentRequiresElectronicSignature() {
        val unsigned = ConsultationExportUiState(
            status = ConsultationExportStatus.ReadyToGenerate,
            consultation = approvedConsultation(),
            prescriptionImagePaths = listOf("/synthetic/prescription.jpg")
        )
        val signed = unsigned.copy(
            electronicallySign = true,
            signerName = "Dr Synthetic",
            handwrittenSignature = "0.1,0.2;0.8,0.7"
        )

        assertFalse(unsigned.canGenerate)
        assertTrue(signed.canGenerate)
    }

    @Test
    fun generatedReportLocksExportConfiguration() {
        val viewModel = ConsultationExportViewModel(
            CapturingExporter(successResult()),
            processAsynchronously = false
        )
        viewModel.load(approvedConsultation(), "Dr Synthetic", "0.1,0.2;0.8,0.7")
        viewModel.generate()

        viewModel.selectFormat(ConsultationExportFormat.PlainText)
        viewModel.setSignerName("Changed")
        viewModel.setHandwrittenSignature("")

        assertFalse(viewModel.uiState.value.isConfigurationEditable)
        assertEquals(ConsultationExportFormat.ClinicalPdf, viewModel.uiState.value.selectedFormat)
        assertEquals("Dr Synthetic", viewModel.uiState.value.signerName)
        assertTrue(viewModel.uiState.value.handwrittenSignature.isNotBlank())
    }

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
        assertEquals("approved.pdf", viewModel.uiState.value.exportedFile?.displayName)
    }

    @Test
    fun generationFailureCanBeRetried() {
        val exporter = CapturingExporter(
            ConsultationExportResult.Failure("Synthetic generation failure")
        )
        val viewModel = ConsultationExportViewModel(exporter, processAsynchronously = false)
        viewModel.load(approvedConsultation())

        viewModel.generate()

        assertEquals(ConsultationExportStatus.Error, viewModel.uiState.value.status)
        assertEquals("Synthetic generation failure", viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.canGenerate)
    }

    @Test
    fun selectingFormatClearsPreviousFileAndExportsSelectedFormat() {
        val exporter = CapturingExporter(successResult(ConsultationExportFormat.PlainText))
        val viewModel = ConsultationExportViewModel(exporter, processAsynchronously = false)
        viewModel.load(approvedConsultation())

        viewModel.selectFormat(ConsultationExportFormat.PlainText)
        viewModel.generate()

        assertEquals(ConsultationExportFormat.PlainText, exporter.receivedFormat)
        assertEquals(ConsultationExportFormat.PlainText, viewModel.uiState.value.exportedFile?.format)
    }

    @Test
    fun electronicSignatureRequiresNameAndIsAttachedToExport() {
        val exporter = CapturingExporter(successResult())
        val viewModel = ConsultationExportViewModel(exporter, processAsynchronously = false)
        viewModel.load(approvedConsultation())
        viewModel.setElectronicallySign(true)

        assertTrue(!viewModel.uiState.value.canGenerate)

        viewModel.setSignerName("Dr Synthetic")
        viewModel.setHandwrittenSignature("0.1,0.2;0.8,0.7")
        viewModel.generate()

        assertEquals("Dr Synthetic", exporter.received?.electronicSignerName)
        assertTrue(exporter.received?.electronicallySignedAtMillis != null)
        assertTrue(exporter.received?.handwrittenSignature?.isNotBlank() == true)
    }

    @Test
    fun generatedReport_isPersistedAndLinkedToApprovedConsultation() {
        val reportRepository = CapturingReportRepository()
        val viewModel = ConsultationExportViewModel(
            exporter = CapturingExporter(successResult(ConsultationExportFormat.StructuredJson)),
            reportRepository = reportRepository,
            processAsynchronously = false
        )
        val consultation = approvedConsultation()
        viewModel.load(consultation)
        viewModel.selectFormat(ConsultationExportFormat.StructuredJson)

        viewModel.generate()

        assertEquals(consultation.id, reportRepository.savedConsultationId)
        assertEquals(
            ConsultationExportFormat.StructuredJson,
            viewModel.uiState.value.savedArtifact?.format
        )
        assertNull(viewModel.uiState.value.persistenceWarning)
    }

    private class CapturingExporter(
        private val result: ConsultationExportResult
    ) : ConsultationStandardExporter {
        var received: ApprovedConsultation? = null
        var receivedFormat: ConsultationExportFormat? = null

        override suspend fun export(
            consultation: ApprovedConsultation,
            format: ConsultationExportFormat
        ): ConsultationExportResult {
            received = consultation
            receivedFormat = format
            return result
        }
    }

    private class CapturingReportRepository : ConsultationReportRepository {
        var savedConsultationId: String? = null

        override suspend fun list(consultationId: String): List<ConsultationReportArtifact> =
            emptyList()

        override suspend fun save(
            consultationId: String,
            exportedFile: ExportedConsultationFile
        ): ConsultationReportArtifact {
            savedConsultationId = consultationId
            return ConsultationReportArtifact(
                consultationId = consultationId,
                format = exportedFile.format,
                generatedAtMillis = 456L,
                displayName = exportedFile.displayName,
                sizeBytes = exportedFile.sizeBytes
            )
        }

        override suspend fun materialize(
            artifact: ConsultationReportArtifact
        ): ExportedConsultationFile = error("Not used")

        override suspend fun delete(artifact: ConsultationReportArtifact) = Unit

        override suspend fun deleteForConsultation(consultationId: String) = Unit
    }

    private fun approvedConsultation() = ApprovedConsultation(
        id = "00000000-0000-0000-0000-000000000001",
        approvedAtMillis = 123L,
        patientName = "Synthetic reference",
        patientAge = "40",
        visitReason = "Synthetic visit",
        draft = ClinicalDraft(history = "Synthetic history")
    )

    private fun successResult(
        format: ConsultationExportFormat = ConsultationExportFormat.ClinicalPdf
    ) = ConsultationExportResult.Success(
        ExportedConsultationFile(
            localPath = "/private/cache/approved.pdf",
            displayName = "approved.pdf",
            sizeBytes = 1_024L,
            mimeType = format.mimeType,
            format = format
        )
    )
}
