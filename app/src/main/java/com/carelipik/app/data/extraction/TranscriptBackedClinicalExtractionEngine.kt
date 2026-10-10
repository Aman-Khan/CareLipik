package com.carelipik.app.data.extraction

import com.carelipik.app.data.transcription.LabelledTranscriptSegmentParser
import com.carelipik.app.domain.extraction.ClinicalExtractionEngine
import com.carelipik.app.domain.extraction.ClinicalExtractionResult
import com.carelipik.app.domain.extraction.ClinicalVisitReasonFormatter
import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.transcription.TranscriptSegment
import com.carelipik.app.domain.transcription.TranscriptSegmentParser

/**
 * Produces a conservative offline draft without inventing a clinical summary.
 *
 * Patient statements and the complete reviewed transcript are retained verbatim so the doctor
 * can organize the note without losing source details. Assessment and plan remain doctor-authored.
 */
class TranscriptBackedClinicalExtractionEngine(
    private val segmentParser: TranscriptSegmentParser = LabelledTranscriptSegmentParser()
) : ClinicalExtractionEngine {
    override fun extract(transcript: String): ClinicalExtractionResult {
        val reviewedTranscript = transcript.trim()
        if (reviewedTranscript.isBlank()) {
            return ClinicalExtractionResult.Failure("A reviewed transcript is required.")
        }

        val segments = segmentParser.parse(reviewedTranscript)
        val patientStatements = segments.filter { it.speakerId == PATIENT_SPEAKER_ID }
        val sourceStatements = patientStatements.ifEmpty {
            listOf(TranscriptSegment(UNASSIGNED_SPEAKER_ID, reviewedTranscript))
        }
        val presentingComplaint = sourceStatements.firstNotNullOfOrNull { statement ->
            ClinicalVisitReasonFormatter.concise(statement.transcript).takeIf(String::isNotBlank)
        }.orEmpty()
        val historyStatements = sourceStatements.drop(1).ifEmpty { sourceStatements }

        return ClinicalExtractionResult.Success(
            ClinicalDraft(
                patientAge = patientStatements.firstNotNullOfOrNull(::ageFrom) ?: "",
                presentingComplaint = presentingComplaint,
                history = historyStatements.asBullets(),
                keyFindings = sourceStatements.asBullets(),
                assessmentNotes = "",
                planNotes = "",
                reviewedTranscript = reviewedTranscript
            )
        )
    }

    private fun ageFrom(segment: TranscriptSegment): String? = AGE_PATTERN
        .find(segment.transcript)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf { it in MIN_AGE..MAX_AGE }
        ?.toString()

    private fun List<TranscriptSegment>.asBullets(): String = joinToString("\n") { segment ->
        "• ${segment.transcript.trim()}"
    }

    private companion object {
        const val PATIENT_SPEAKER_ID = "patient"
        const val UNASSIGNED_SPEAKER_ID = "unassigned"
        const val MIN_AGE = 0
        const val MAX_AGE = 130
        val AGE_PATTERN = Regex(
            "(?<!\\d)(\\d{1,3})\\s*(?:years?|yrs?)\\s*old\\b",
            RegexOption.IGNORE_CASE
        )
    }
}
