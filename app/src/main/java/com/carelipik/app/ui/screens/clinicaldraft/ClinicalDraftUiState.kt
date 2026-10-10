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
    val isGeneratingOnDevice: Boolean = false,
    val onDeviceGenerationError: String? = null,
    val isGeneratingOnline: Boolean = false,
    val onlineGenerationError: String? = null,
    val hasOnlineGenerationConsent: Boolean = false
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
                "Generate the English note on device or with Gemini, or keep the consultation language"
            else -> null
        }

    val needsEnglishGeneration: Boolean
        get() = sourceLanguage != TranscriptionLanguage.English &&
            draft.noteLanguage == ClinicalNoteLanguage.English &&
            draft.generationSource !in setOf(
                ClinicalNoteGenerationSource.MedGemma,
                ClinicalNoteGenerationSource.Gemma4,
                ClinicalNoteGenerationSource.Gemini
            )

    val canContinue: Boolean
        get() = status == ClinicalDraftStatus.Ready &&
            draft.hasContent &&
            draft.medications.all { it.hasContent && it.isDoctorReviewed } &&
            !needsEnglishGeneration &&
            !isGeneratingOnDevice &&
            !isGeneratingOnline
}
