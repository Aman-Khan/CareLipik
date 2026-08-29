package com.carelipik.app.ui.screens.doctorreview

import androidx.lifecycle.ViewModel
import com.carelipik.app.domain.model.ClinicalDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DoctorReviewViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DoctorReviewUiState())
    val uiState: StateFlow<DoctorReviewUiState> = _uiState.asStateFlow()

    fun loadDraft(draft: ClinicalDraft) {
        _uiState.value = DoctorReviewUiState(draft = draft)
    }

    fun setConfirmedReview(isConfirmed: Boolean) {
        _uiState.update { it.copy(hasConfirmedReview = isConfirmed) }
    }

    fun validateApproval(): Boolean {
        _uiState.update { it.copy(hasAttemptedApproval = true) }
        return _uiState.value.canApprove
    }
}
