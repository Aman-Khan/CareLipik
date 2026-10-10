package com.carelipik.app.ui.screens.clinicaldraft

import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.model.ClinicalNoteGenerationSource
import com.carelipik.app.domain.model.ClinicalNoteLanguage
import com.carelipik.app.domain.transcription.TranscriptionLanguage

enum class ClinicalDraftStatus {
    Idle,
    Processing,
    Ready,
    Error
}

data class ClinicalDraftUiState(
    val status: ClinicalDraftStatus = ClinicalDraftStatus.Idle,
    val draft: ClinicalDraft = ClinicalDraft(),
    val errorMessage: String? = null,
    val hasAttemptedContinue: Boolean = false,
    val sourceLanguage: TranscriptionLanguage = TranscriptionLanguage.English,
    val isGeneratingOnline: Boolean = false,
    val onlineGenerationError: String? = null,
    val hasOnlineGenerationConsent: Boolean = false,
    val isGeneratingLocal: Boolean = false,
    val localGenerationDetail: String? = null,
    val localGenerationError: String? = null
) {
    val draftError: String?
        get() = when {
            !hasAttemptedContinue -> null
            !draft.hasContent -> "Review and complete at least one section"
            draft.medications.any { !it.hasContent } ->
                "Complete or remove each medicine entry"
            draft.medications.any { !it.isDoctorReviewed } ->
                "Doctor review is required for every medicine and dosage"
            needsEnglishGeneration ->
                "Generate the English note with Gemini or keep the consultation language"
            else -> null
        }

    val needsEnglishGeneration: Boolean
        get() = sourceLanguage != TranscriptionLanguage.English &&
            draft.noteLanguage == ClinicalNoteLanguage.English &&
            draft.generationSource != ClinicalNoteGenerationSource.Gemini

    val canContinue: Boolean
        get() = status == ClinicalDraftStatus.Ready &&
            draft.hasContent &&
            draft.medications.all { it.hasContent && it.isDoctorReviewed } &&
            !needsEnglishGeneration &&
            !isGeneratingOnline && !isGeneratingLocal
}
