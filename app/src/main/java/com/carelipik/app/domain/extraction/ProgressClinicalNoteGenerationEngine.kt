package com.carelipik.app.domain.extraction

interface ProgressClinicalNoteGenerationEngine : ClinicalNoteGenerationEngine {
    fun generateWithProgress(
        request: ClinicalNoteGenerationRequest,
        checkCancelled: () -> Unit,
        onDetail: (String) -> Unit
    ): ClinicalNoteGenerationResult
}
