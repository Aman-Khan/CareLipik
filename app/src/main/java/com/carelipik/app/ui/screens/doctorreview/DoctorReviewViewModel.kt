package com.carelipik.app.ui.screens.doctorreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.carelipik.app.data.local.EncryptedLocalConsultationRepository
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.repository.ConsultationRepository
import com.carelipik.app.ui.screens.patientdetails.PatientDetailsUiState
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DoctorReviewViewModel(
    private val repository: ConsultationRepository? = null,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val processAsynchronously: Boolean = true
) : ViewModel() {
    private val _uiState = MutableStateFlow(DoctorReviewUiState())
    val uiState: StateFlow<DoctorReviewUiState> = _uiState.asStateFlow()

    fun loadDraft(draft: ClinicalDraft) {
        _uiState.value = DoctorReviewUiState(draft = draft)
    }

    fun setConfirmedReview(isConfirmed: Boolean) {
        _uiState.update { it.copy(hasConfirmedReview = isConfirmed) }
    }

    fun setIncludeReviewedTranscriptInExport(include: Boolean) {
        _uiState.update { it.copy(includeReviewedTranscriptInExport = include) }
    }

    fun resetForNewConsultation() {
        _uiState.value = DoctorReviewUiState()
    }

    fun validateApproval(): Boolean {
        _uiState.update { it.copy(hasAttemptedApproval = true) }
        return _uiState.value.canApprove
    }

    fun approve(
        patient: PatientDetailsUiState,
        onSaved: (ApprovedConsultation) -> Unit
    ): Boolean {
        if (!validateApproval()) return false
        val store = repository ?: return false
        val consultation = ApprovedConsultation(
            id = newId(),
            approvedAtMillis = currentTimeMillis(),
            patientName = patient.patientName.trim(),
            patientAge = patient.age.trim().ifBlank { _uiState.value.draft.patientAge.trim() },
            visitReason = patient.visitReason.trim().ifBlank {
                _uiState.value.draft.presentingComplaint.trim()
            },
            draft = _uiState.value.draft,
            includeReviewedTranscriptInExport =
                _uiState.value.includeReviewedTranscriptInExport
        )
        if (processAsynchronously) {
            viewModelScope.launch {
                store.save(consultation)
                onSaved(consultation)
            }
        } else {
            runBlocking { store.save(consultation) }
            onSaved(consultation)
        }
        return true
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DoctorReviewViewModel::class.java))
            return DoctorReviewViewModel(
                repository = EncryptedLocalConsultationRepository(applicationContext)
            ) as T
        }
    }
}
