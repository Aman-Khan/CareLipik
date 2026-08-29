package com.carelipik.app.domain.export

enum class ConsultationExportFormat(
    val displayName: String,
    val description: String,
    val extension: String,
    val mimeType: String
) {
    ClinicalPdf(
        displayName = "Clinical note PDF",
        description = "Human-readable A4 document for review, saving, or printing.",
        extension = "pdf",
        mimeType = "application/pdf"
    ),
    StructuredJson(
        displayName = "Structured JSON",
        description = "CareLipik schema for application-to-application interchange.",
        extension = "json",
        mimeType = "application/json"
    ),
    FhirR4Bundle(
        displayName = "HL7 FHIR R4 bundle",
        description = "Base R4 document Bundle with Composition and DocumentReference.",
        extension = "fhir.json",
        mimeType = "application/fhir+json"
    ),
    PlainText(
        displayName = "Plain-text EHR note",
        description = "Simple labelled note for copying into an EHR.",
        extension = "txt",
        mimeType = "text/plain"
    )
}

data class ExportedConsultationFile(
    val localPath: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val format: ConsultationExportFormat
)

sealed interface ConsultationExportResult {
    data class Success(val file: ExportedConsultationFile) : ConsultationExportResult
    data class Failure(val message: String) : ConsultationExportResult
}

fun interface ConsultationStandardExporter {
    suspend fun export(
        consultation: com.carelipik.app.domain.model.ApprovedConsultation,
        format: ConsultationExportFormat
    ): ConsultationExportResult
}
