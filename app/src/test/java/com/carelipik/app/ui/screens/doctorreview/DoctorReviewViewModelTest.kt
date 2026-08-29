package com.carelipik.app.ui.screens.doctorreview

import com.carelipik.app.domain.model.ClinicalDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoctorReviewViewModelTest {
    @Test
    fun approval_requiresExplicitConfirmation() {
        val viewModel = DoctorReviewViewModel()
        viewModel.loadDraft(ClinicalDraft(presentingComplaint = "Synthetic complaint"))

        assertFalse(viewModel.validateApproval())
        assertEquals(
            "Confirm that you reviewed and corrected the draft",
            viewModel.uiState.value.confirmationError
        )
    }

    @Test
    fun confirmedNonEmptyDraft_canBeApproved() {
        val viewModel = DoctorReviewViewModel()
        viewModel.loadDraft(ClinicalDraft(presentingComplaint = "Synthetic complaint"))

        viewModel.setConfirmedReview(true)

        assertTrue(viewModel.validateApproval())
        assertNull(viewModel.uiState.value.confirmationError)
    }

    @Test
    fun missingSections_areListedForReview() {
        val viewModel = DoctorReviewViewModel()
        viewModel.loadDraft(ClinicalDraft(presentingComplaint = "Synthetic complaint"))

        assertEquals(
            listOf("History", "Key findings", "Assessment notes", "Plan notes"),
            viewModel.uiState.value.missingSections
        )
    }

    @Test
    fun loadingUpdatedDraft_clearsPreviousConfirmation() {
        val viewModel = DoctorReviewViewModel()
        viewModel.loadDraft(ClinicalDraft(history = "First"))
        viewModel.setConfirmedReview(true)

        viewModel.loadDraft(ClinicalDraft(history = "Updated"))

        assertFalse(viewModel.uiState.value.hasConfirmedReview)
    }
}
