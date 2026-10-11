package com.carelipik.app.ui.screens.doctorprofile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.net.Uri
import com.carelipik.app.data.local.EncryptedDoctorProfileRepository
import com.carelipik.app.data.signature.SignatureImageProcessor
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
    private val signatureImageProcessor: SignatureImageProcessor? = null,
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
        updateEditable { it.copy(fullName = value) }
    }

    fun setSpecialty(value: String) {
        updateEditable { it.copy(specialty = value) }
    }

    fun setRegistrationNumber(value: String) {
        updateEditable { it.copy(registrationNumber = value) }
    }

    fun setClinicName(value: String) {
        updateEditable { it.copy(clinicName = value) }
    }

    fun togglePreferredLanguage(language: TranscriptionLanguage) {
        if (language == TranscriptionLanguage.Auto) return
        updateEditable { state ->
            val updatedLanguages = state.preferredLanguages.toMutableSet().apply {
                if (!add(language)) remove(language)
            }
            state.copy(preferredLanguages = updatedLanguages)
        }
    }

    fun setProcessingPreference(preference: ProcessingPreference) {
        updateEditable { it.copy(processingPreference = preference) }
    }

    fun setHandwrittenSignature(signature: String) {
        updateEditable { it.copy(handwrittenSignature = signature) }
    }

    fun importSignatureImage(
        uriString: String,
        cropLeft: Float,
        cropTop: Float,
        cropRight: Float,
        cropBottom: Float
    ) {
        val processor = signatureImageProcessor ?: return
        _uiState.update { it.copy(isLoading = true, saveError = null, saveMessage = null) }
        runOperation {
            processor.process(Uri.parse(uriString), cropLeft, cropTop, cropRight, cropBottom)
                .onSuccess { signature ->
                    updateEditable { it.copy(handwrittenSignature = signature, isLoading = false) }
                    _uiState.update {
                        it.copy(saveMessage = "Signature extracted. Review it, then save profile.")
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            saveError = error.message ?: "The signature image could not be processed."
                        )
                    }
                }
        }
    }

    fun saveProfile(onSaved: (DoctorProfile) -> Unit = {}): DoctorProfile? {
        _uiState.update { it.copy(hasAttemptedSave = true) }
        val profile = _uiState.value.takeIf(DoctorProfileUiState::canSave)?.toProfile()
            ?: return null
        val profileRepository = repository
        if (profileRepository == null) {
            _savedProfile.value = profile
            _uiState.update {
                it.copy(
                    hasUnsavedChanges = false,
                    saveError = null,
                    saveMessage = "Profile updated"
                )
            }
            onSaved(profile)
            return profile
        }
        runOperation {
            runCatching { profileRepository.save(profile) }
                .onSuccess {
                    _savedProfile.value = profile
                    _uiState.update {
                        it.copy(
                            hasUnsavedChanges = false,
                            saveError = null,
                            saveMessage = "Profile updated"
                        )
                    }
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

    private fun updateEditable(transform: (DoctorProfileUiState) -> DoctorProfileUiState) {
        _uiState.update { current ->
            val updated = transform(current).copy(
                hasAttemptedSave = false,
                saveError = null,
                saveMessage = null
            )
            updated.copy(hasUnsavedChanges = !updated.matches(_savedProfile.value))
        }
    }

    private fun DoctorProfileUiState.matches(profile: DoctorProfile?): Boolean {
        if (profile == null) {
            return fullName.isBlank() && specialty.isBlank() && registrationNumber.isBlank() &&
                clinicName.isBlank() &&
                preferredLanguages == setOf(TranscriptionLanguage.English) &&
                processingPreference == ProcessingPreference.SmartHybrid
        }
        return fullName.trim() == profile.fullName &&
            specialty.trim() == profile.specialty &&
            registrationNumber.trim() == profile.registrationNumber &&
            clinicName.trim() == profile.clinicName &&
            preferredLanguages == profile.preferredLanguages &&
            processingPreference == profile.processingPreference
            && handwrittenSignature == profile.handwrittenSignature
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
                repository = EncryptedDoctorProfileRepository(applicationContext),
                signatureImageProcessor = SignatureImageProcessor(applicationContext)
            ) as T
        }
    }
}
