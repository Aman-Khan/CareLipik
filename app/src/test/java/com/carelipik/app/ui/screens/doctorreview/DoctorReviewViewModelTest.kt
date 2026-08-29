package com.carelipik.app.ui.screens.doctorreview

import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.data.local.FakeLocalConsultationRepository
import com.carelipik.app.ui.screens.patientdetails.PatientDetailsUiState
import kotlinx.coroutines.runBlocking
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

    @Test
    fun unconfirmedDraft_isNeverPersisted() = runBlocking {
        val repository = FakeLocalConsultationRepository()
        val viewModel = DoctorReviewViewModel(
            repository = repository,
            processAsynchronously = false
        )
        viewModel.loadDraft(ClinicalDraft(history = "Synthetic history"))

        assertFalse(
            viewModel.approve(PatientDetailsUiState(patientName = "Test reference")) {}
        )
        assertTrue(repository.list().isEmpty())
    }

    @Test
    fun finalDoctorApproval_persistsDocumentationWithoutAudio() = runBlocking {
        val repository = FakeLocalConsultationRepository()
        val viewModel = DoctorReviewViewModel(
            repository = repository,
            currentTimeMillis = { 123L },
            newId = { "00000000-0000-0000-0000-000000000001" },
            processAsynchronously = false
        )
        viewModel.loadDraft(ClinicalDraft(history = "Synthetic history"))
        viewModel.setConfirmedReview(true)

        assertTrue(
            viewModel.approve(
                PatientDetailsUiState(
                    patientName = "Test reference",
                    age = "40",
                    visitReason = "Synthetic visit"
                )
            ) {}
        )

        val saved = repository.list().single()
        assertEquals("Test reference", saved.patientName)
        assertEquals("Synthetic history", saved.draft.history)
    }

    @Test
    fun approvedDraft_usesDoctorReviewedTranscriptAgeAndComplaintWhenFormFieldsAreBlank() =
        runBlocking {
            val repository = FakeLocalConsultationRepository()
            val viewModel = DoctorReviewViewModel(
                repository = repository,
                processAsynchronously = false
            )
            viewModel.loadDraft(
                ClinicalDraft(
                    patientAge = "47",
                    presentingComplaint = "Cough and fever for four days",
                    reviewedTranscript = "Patient: I am 47 years old."
                )
            )
            viewModel.setConfirmedReview(true)

            assertTrue(
                viewModel.approve(PatientDetailsUiState(patientName = "Test reference")) {}
            )

            val saved = repository.list().single()
            assertEquals("47", saved.patientAge)
            assertEquals("Cough and fever for four days", saved.visitReason)
        }
}
