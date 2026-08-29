package com.carelipik.app.domain.repository

import com.carelipik.app.domain.export.ConsultationExportFormat
import com.carelipik.app.domain.export.ExportedConsultationFile

data class ConsultationReportArtifact(
    val consultationId: String,
    val format: ConsultationExportFormat,
    val generatedAtMillis: Long,
    val displayName: String,
    val sizeBytes: Long
)

interface ConsultationReportRepository {
    suspend fun list(consultationId: String): List<ConsultationReportArtifact>

    suspend fun save(
        consultationId: String,
        exportedFile: ExportedConsultationFile
    ): ConsultationReportArtifact

    suspend fun materialize(
        artifact: ConsultationReportArtifact
    ): ExportedConsultationFile

    suspend fun delete(artifact: ConsultationReportArtifact)

    suspend fun deleteForConsultation(consultationId: String)
}
