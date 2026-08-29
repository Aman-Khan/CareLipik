package com.carelipik.app.ui.screens.history

import com.carelipik.app.data.local.FakeLocalConsultationRepository
import com.carelipik.app.domain.export.ConsultationExportFormat
import com.carelipik.app.domain.export.ExportedConsultationFile
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.repository.ConsultationReportArtifact
import com.carelipik.app.domain.repository.ConsultationReportRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsultationHistoryViewModelTest {
    @Test
    fun select_notifiesOnlyAfterDetailStateIsReady() {
        val consultation = approvedConsultation()
        val viewModel = ConsultationHistoryViewModel(
            repository = FakeLocalConsultationRepository(listOf(consultation)),
            reportRepository = FakeReportRepository(artifact(consultation.id)),
            processAsynchronously = false
        )
        var selectedWasReadyInsideCallback = false

        viewModel.select(consultation.id) { wasSelected ->
            selectedWasReadyInsideCallback =
                wasSelected && viewModel.uiState.value.selected?.id == consultation.id
        }

        assertTrue(selectedWasReadyInsideCallback)
    }

    @Test
    fun linkedReport_canBeOpenedDeletedAndCascadeDeletedWithConsultation() = runBlocking {
        val consultation = approvedConsultation()
        val consultationRepository = FakeLocalConsultationRepository(listOf(consultation))
        val reportRepository = FakeReportRepository(artifact(consultation.id))
        val viewModel = ConsultationHistoryViewModel(
            repository = consultationRepository,
            reportRepository = reportRepository,
            processAsynchronously = false
        )

        viewModel.select(consultation.id)

        assertEquals(consultation, viewModel.uiState.value.selected)
        assertEquals(1, viewModel.uiState.value.reports.size)
        var openedFile: ExportedConsultationFile? = null
        viewModel.prepareReport(viewModel.uiState.value.reports.single()) { openedFile = it }
        assertEquals("synthetic-report.pdf", openedFile?.displayName)

        viewModel.deleteReport(viewModel.uiState.value.reports.single())
        assertTrue(viewModel.uiState.value.reports.isEmpty())

        reportRepository.save(consultation.id, openedFile!!)
        viewModel.refreshSelectedReports()
        assertEquals(1, viewModel.uiState.value.reports.size)
        viewModel.delete(consultation.id)

        assertNull(consultationRepository.get(consultation.id))
        assertFalse(reportRepository.hasReports)
        assertNull(viewModel.uiState.value.selected)
    }

    private fun approvedConsultation() = ApprovedConsultation(
        id = "00000000-0000-0000-0000-000000000081",
        approvedAtMillis = 123L,
        patientName = "Synthetic reference",
        patientAge = "45",
        visitReason = "Synthetic visit",
        draft = ClinicalDraft(history = "Synthetic history")
    )

    private fun artifact(consultationId: String) = ConsultationReportArtifact(
        consultationId = consultationId,
        format = ConsultationExportFormat.ClinicalPdf,
        generatedAtMillis = 456L,
        displayName = "synthetic-report.pdf",
        sizeBytes = 1_024L
    )

    private class FakeReportRepository(
        initial: ConsultationReportArtifact
    ) : ConsultationReportRepository {
        private val reports = mutableListOf(initial)
        val hasReports: Boolean get() = reports.isNotEmpty()

        override suspend fun list(consultationId: String): List<ConsultationReportArtifact> =
            reports.filter { it.consultationId == consultationId }

        override suspend fun save(
            consultationId: String,
            exportedFile: ExportedConsultationFile
        ): ConsultationReportArtifact = ConsultationReportArtifact(
            consultationId = consultationId,
            format = exportedFile.format,
            generatedAtMillis = 789L,
            displayName = exportedFile.displayName,
            sizeBytes = exportedFile.sizeBytes
        ).also {
            reports.removeAll { existing -> existing.format == it.format }
            reports += it
        }

        override suspend fun materialize(
            artifact: ConsultationReportArtifact
        ): ExportedConsultationFile = ExportedConsultationFile(
            localPath = "/private/cache/${artifact.displayName}",
            displayName = artifact.displayName,
            sizeBytes = artifact.sizeBytes,
            mimeType = artifact.format.mimeType,
            format = artifact.format
        )

        override suspend fun delete(artifact: ConsultationReportArtifact) {
            reports.remove(artifact)
        }

        override suspend fun deleteForConsultation(consultationId: String) {
            reports.removeAll { it.consultationId == consultationId }
        }
    }
}
