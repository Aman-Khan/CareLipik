package com.carelipik.app.data.export

import android.content.Context
import com.carelipik.app.domain.export.ConsultationExportContent
import com.carelipik.app.domain.export.ConsultationExportFormat
import com.carelipik.app.domain.export.ConsultationExportResult
import com.carelipik.app.domain.export.ConsultationStandardExporter
import com.carelipik.app.domain.export.ExportedConsultationFile
import com.carelipik.app.domain.export.ConsultationPdfExportResult
import com.carelipik.app.domain.model.ApprovedConsultation
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidConsultationStandardExporter(context: Context) : ConsultationStandardExporter {
    private val applicationContext = context.applicationContext
    private val pdfExporter = AndroidConsultationPdfExporter(applicationContext)
    private val exportDirectory = File(applicationContext.cacheDir, "consultation_exports")

    override suspend fun export(
        consultation: ApprovedConsultation,
        format: ConsultationExportFormat
    ): ConsultationExportResult = when (format) {
        ConsultationExportFormat.ClinicalPdf -> when (val result = pdfExporter.export(consultation)) {
            is ConsultationPdfExportResult.Success -> ConsultationExportResult.Success(
                ExportedConsultationFile(
                    localPath = result.pdf.localPath,
                    displayName = result.pdf.displayName,
                    sizeBytes = result.pdf.sizeBytes,
                    mimeType = format.mimeType,
                    format = format
                )
            )
            is ConsultationPdfExportResult.Failure ->
                ConsultationExportResult.Failure(result.message)
        }
        else -> writeTextExport(consultation, format)
    }

    private suspend fun writeTextExport(
        consultation: ApprovedConsultation,
        format: ConsultationExportFormat
    ): ConsultationExportResult = withContext(Dispatchers.IO) {
        runCatching {
            exportDirectory.mkdirs()
            cleanupStaleExports()
            val displayName = "carelipik-consultation-${consultation.id}.${format.extension}"
            val outputFile = File(exportDirectory, displayName)
            val pendingFile = File(exportDirectory, "$displayName.pending")
            val content = when (format) {
                ConsultationExportFormat.StructuredJson ->
                    ConsultationExportContent.structuredJson(consultation)
                ConsultationExportFormat.FhirR4Bundle ->
                    ConsultationExportContent.fhirR4Bundle(consultation)
                ConsultationExportFormat.PlainText ->
                    ConsultationExportContent.plainText(consultation)
                ConsultationExportFormat.ClinicalPdf -> error("PDF uses the PDF exporter.")
            }
            pendingFile.writeText(content, Charsets.UTF_8)
            if (!pendingFile.renameTo(outputFile)) {
                pendingFile.copyTo(outputFile, overwrite = true)
                check(pendingFile.delete()) { "Could not finish creating the export." }
            }
            ExportedConsultationFile(
                localPath = outputFile.absolutePath,
                displayName = displayName,
                sizeBytes = outputFile.length(),
                mimeType = format.mimeType,
                format = format
            )
        }.fold(
            onSuccess = ConsultationExportResult::Success,
            onFailure = { error ->
                ConsultationExportResult.Failure(
                    error.message ?: "The approved consultation export could not be created."
                )
            }
        )
    }

    private fun cleanupStaleExports() {
        val cutoff = System.currentTimeMillis() - MAX_EXPORT_AGE_MILLIS
        exportDirectory.listFiles().orEmpty()
            .filter { it.lastModified() < cutoff || it.name.endsWith(".pending") }
            .forEach(File::delete)
    }

    private companion object {
        const val MAX_EXPORT_AGE_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
