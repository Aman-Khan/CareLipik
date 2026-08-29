package com.carelipik.app.domain.extraction

import com.carelipik.app.domain.model.ClinicalDraft

sealed interface ClinicalExtractionResult {
    data class Success(val draft: ClinicalDraft) : ClinicalExtractionResult
    data class Failure(val message: String) : ClinicalExtractionResult
}

/** Boundary for converting a reviewed transcript into editable clinical documentation. */
interface ClinicalExtractionEngine {
    fun extract(transcript: String): ClinicalExtractionResult
}
