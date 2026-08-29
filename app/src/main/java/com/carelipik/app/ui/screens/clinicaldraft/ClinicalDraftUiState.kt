package com.carelipik.app.ui.screens.clinicaldraft

import com.carelipik.app.domain.model.ClinicalDraft

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
    val hasAttemptedContinue: Boolean = false
) {
    val draftError: String?
        get() = if (hasAttemptedContinue && !draft.hasContent) {
            "Review and complete at least one section"
        } else {
            null
        }

    val canContinue: Boolean
        get() = status == ClinicalDraftStatus.Ready && draft.hasContent
}
