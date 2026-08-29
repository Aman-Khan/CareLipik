package com.carelipik.app.ui.screens.doctorreview

import com.carelipik.app.domain.model.ClinicalDraft

data class DoctorReviewUiState(
    val draft: ClinicalDraft = ClinicalDraft(),
    val hasConfirmedReview: Boolean = false,
    val hasAttemptedApproval: Boolean = false
) {
    val missingSections: List<String>
        get() = buildList {
            if (draft.presentingComplaint.isBlank()) add("Presenting complaint")
            if (draft.history.isBlank()) add("History")
            if (draft.keyFindings.isBlank()) add("Key findings")
            if (draft.assessmentNotes.isBlank()) add("Assessment notes")
            if (draft.planNotes.isBlank()) add("Plan notes")
        }

    val confirmationError: String?
        get() = if (hasAttemptedApproval && !hasConfirmedReview) {
            "Confirm that you reviewed and corrected the draft"
        } else {
            null
        }

    val canApprove: Boolean
        get() = draft.hasContent && hasConfirmedReview
}
