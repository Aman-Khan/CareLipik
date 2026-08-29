package com.carelipik.app.domain.export

import com.carelipik.app.domain.model.ApprovedConsultation

data class ConsultationPdf(
    val localPath: String,
    val displayName: String,
    val sizeBytes: Long
)

sealed interface ConsultationPdfExportResult {
    data class Success(val pdf: ConsultationPdf) : ConsultationPdfExportResult
    data class Failure(val message: String) : ConsultationPdfExportResult
}

/** Generates a temporary PDF from an already approved consultation record. */
fun interface ConsultationPdfExporter {
    suspend fun export(consultation: ApprovedConsultation): ConsultationPdfExportResult
}
