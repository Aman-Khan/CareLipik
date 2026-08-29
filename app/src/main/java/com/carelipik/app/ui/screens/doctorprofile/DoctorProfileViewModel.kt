package com.carelipik.app.ui.screens.doctorprofile

import androidx.lifecycle.ViewModel
import com.carelipik.app.domain.model.DoctorProfile
import com.carelipik.app.domain.model.ProcessingPreference
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DoctorProfileViewModel(
    initialState: DoctorProfileUiState = DoctorProfileUiState()
) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<DoctorProfileUiState> = _uiState.asStateFlow()

    fun setFullName(value: String) {
        _uiState.update { it.copy(fullName = value) }
    }

    fun setSpecialty(value: String) {
        _uiState.update { it.copy(specialty = value) }
    }

    fun setRegistrationNumber(value: String) {
        _uiState.update { it.copy(registrationNumber = value) }
    }

    fun setClinicName(value: String) {
        _uiState.update { it.copy(clinicName = value) }
    }

    fun togglePreferredLanguage(language: TranscriptionLanguage) {
        if (language == TranscriptionLanguage.Auto) return
        _uiState.update { state ->
            val updatedLanguages = state.preferredLanguages.toMutableSet().apply {
                if (!add(language)) remove(language)
            }
            state.copy(preferredLanguages = updatedLanguages)
        }
    }

    fun setProcessingPreference(preference: ProcessingPreference) {
        _uiState.update { it.copy(processingPreference = preference) }
    }

    fun saveProfile(): DoctorProfile? {
        _uiState.update { it.copy(hasAttemptedSave = true) }
        return _uiState.value.takeIf(DoctorProfileUiState::canSave)?.toProfile()
    }
}
