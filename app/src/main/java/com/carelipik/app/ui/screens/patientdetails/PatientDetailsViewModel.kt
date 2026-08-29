package com.carelipik.app.ui.screens.patientdetails

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PatientDetailsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PatientDetailsUiState())
    val uiState: StateFlow<PatientDetailsUiState> = _uiState.asStateFlow()

    fun setPatientName(patientName: String) {
        _uiState.update { it.copy(patientName = patientName) }
    }

    fun setAge(age: String) {
        if (age.all(Char::isDigit)) {
            _uiState.update { it.copy(age = age) }
        }
    }

    fun setVisitReason(visitReason: String) {
        _uiState.update { it.copy(visitReason = visitReason) }
    }

    fun validateForContinue(): Boolean {
        _uiState.update { it.copy(hasAttemptedContinue = true) }
        return _uiState.value.canContinue
    }

    fun currentDetails(): PatientDetailsUiState = _uiState.value

    fun resetForNewConsultation() {
        _uiState.value = PatientDetailsUiState()
    }
}
