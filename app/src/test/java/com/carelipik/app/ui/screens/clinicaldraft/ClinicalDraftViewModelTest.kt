package com.carelipik.app.ui.screens.clinicaldraft

import com.carelipik.app.domain.extraction.ClinicalExtractionEngine
import com.carelipik.app.domain.extraction.ClinicalExtractionResult
import com.carelipik.app.domain.model.ClinicalDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClinicalDraftViewModelTest {
    @Test
    fun successfulExtraction_createsEditableDraft() {
        val draft = ClinicalDraft(presentingComplaint = "Synthetic complaint")
        val viewModel = ClinicalDraftViewModel(
            engine = StubEngine(ClinicalExtractionResult.Success(draft)),
            processAsynchronously = false
        )

        viewModel.generate("Synthetic transcript")
        viewModel.setHistory("Doctor-corrected history")

        assertEquals(ClinicalDraftStatus.Ready, viewModel.uiState.value.status)
        assertEquals("Doctor-corrected history", viewModel.uiState.value.draft.history)
        assertTrue(viewModel.validateForContinue())
    }

    @Test
    fun failedExtraction_displaysErrorAndPreventsContinue() {
        val viewModel = ClinicalDraftViewModel(
            engine = StubEngine(ClinicalExtractionResult.Failure("Engine unavailable")),
            processAsynchronously = false
        )

        viewModel.generate("Synthetic transcript")

        assertEquals(ClinicalDraftStatus.Error, viewModel.uiState.value.status)
        assertEquals("Engine unavailable", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.canContinue)
    }

    @Test
    fun emptyDraft_requiresContentBeforeDoctorReview() {
        val viewModel = ClinicalDraftViewModel(
            engine = StubEngine(ClinicalExtractionResult.Success(ClinicalDraft())),
            processAsynchronously = false
        )

        viewModel.generate("Synthetic transcript")

        assertFalse(viewModel.validateForContinue())
        assertEquals("Review and complete at least one section", viewModel.uiState.value.draftError)
    }

    private class StubEngine(
        private val result: ClinicalExtractionResult
    ) : ClinicalExtractionEngine {
        override fun extract(transcript: String): ClinicalExtractionResult = result
    }
}
