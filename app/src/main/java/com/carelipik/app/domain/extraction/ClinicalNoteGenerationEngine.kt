package com.carelipik.app.domain.extraction

import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.model.ClinicalNoteFormat
import com.carelipik.app.domain.model.ClinicalNoteLanguage
import com.carelipik.app.domain.transcription.TranscriptionLanguage

data class ClinicalNoteGenerationRequest(
    val reviewedTranscript: String,
    val sourceLanguage: TranscriptionLanguage,
    val noteFormat: ClinicalNoteFormat,
    val outputLanguage: ClinicalNoteLanguage,
    val specialtyName: String,
    val patientAge: String,
    val visitReason: String
)

sealed interface ClinicalNoteGenerationResult {
    data class Success(val draft: ClinicalDraft) : ClinicalNoteGenerationResult
    data class Failure(val message: String) : ClinicalNoteGenerationResult
}

fun interface ClinicalNoteGenerationEngine {
    fun generate(request: ClinicalNoteGenerationRequest): ClinicalNoteGenerationResult
}
