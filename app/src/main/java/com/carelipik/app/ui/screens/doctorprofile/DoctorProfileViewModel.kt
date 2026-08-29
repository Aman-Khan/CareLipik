package com.carelipik.app.ui.screens.doctorprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.carelipik.app.data.local.EncryptedDoctorProfileRepository
import com.carelipik.app.domain.model.DoctorProfile
import com.carelipik.app.domain.model.ProcessingPreference
import com.carelipik.app.domain.repository.DoctorProfileRepository
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class DoctorProfileViewModel(
    initialState: DoctorProfileUiState = DoctorProfileUiState(),
    private val repository: DoctorProfileRepository? = null,
    private val processAsynchronously: Boolean = true
) : ViewModel() {
    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<DoctorProfileUiState> = _uiState.asStateFlow()
    private val _savedProfile = MutableStateFlow<DoctorProfile?>(null)
    val savedProfile: StateFlow<DoctorProfile?> = _savedProfile.asStateFlow()

    init {
        loadSavedProfile()
    }

    private fun loadSavedProfile() {
        val profileRepository = repository ?: return
        _uiState.update { it.copy(isLoading = true) }
        runOperation {
            val profile = profileRepository.load()
            if (profile != null) {
                _uiState.value = DoctorProfileUiState.fromProfile(profile)
                _savedProfile.value = profile
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

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

    fun saveProfile(onSaved: (DoctorProfile) -> Unit = {}): DoctorProfile? {
        _uiState.update { it.copy(hasAttemptedSave = true) }
        val profile = _uiState.value.takeIf(DoctorProfileUiState::canSave)?.toProfile()
            ?: return null
        val profileRepository = repository
        if (profileRepository == null) {
            _savedProfile.value = profile
            onSaved(profile)
            return profile
        }
        runOperation {
            runCatching { profileRepository.save(profile) }
                .onSuccess {
                    _savedProfile.value = profile
                    _uiState.update { it.copy(saveError = null) }
                    onSaved(profile)
                }
                .onFailure {
                    _uiState.update {
                        it.copy(saveError = "The doctor profile could not be saved on this device.")
                    }
                }
        }
        return profile
    }

    private fun runOperation(operation: suspend () -> Unit) {
        if (processAsynchronously) viewModelScope.launch { operation() } else runBlocking { operation() }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DoctorProfileViewModel::class.java))
            return DoctorProfileViewModel(
                repository = EncryptedDoctorProfileRepository(applicationContext)
            ) as T
        }
    }
}
