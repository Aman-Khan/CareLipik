package com.carelipik.app.ui.screens.doctorreview

import com.carelipik.app.domain.model.ClinicalDraft

data class DoctorReviewUiState(
    val draft: ClinicalDraft = ClinicalDraft(),
    val hasConfirmedReview: Boolean = false,
    val includeReviewedTranscriptInExport: Boolean = true,
    val hasAttemptedApproval: Boolean = false
) {
    val missingSections: List<String>
        get() = draft.effectiveSections
            .filter { it.content.isBlank() }
            .map { it.title }

    val confirmationError: String?
        get() = if (hasAttemptedApproval && !hasConfirmedReview) {
            "Confirm that you reviewed and corrected the draft"
        } else {
            null
        }

    val medicationError: String?
        get() = when {
            draft.medications.any { !it.hasContent } ->
                "Complete or remove every medicine entry before approval"
            draft.medications.any { !it.isDoctorReviewed } ->
                "Every prescribed medicine and dosage requires doctor verification"
            else -> null
        }

    val canApprove: Boolean
        get() = draft.hasContent && hasConfirmedReview && medicationError == null
}
