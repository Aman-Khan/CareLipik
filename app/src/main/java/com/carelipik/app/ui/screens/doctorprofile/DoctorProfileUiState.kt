package com.carelipik.app.ui.screens.doctorprofile

import com.carelipik.app.domain.model.DoctorProfile
import com.carelipik.app.domain.model.ProcessingPreference
import com.carelipik.app.domain.transcription.TranscriptionLanguage

data class DoctorProfileUiState(
    val fullName: String = "",
    val specialty: String = "",
    val registrationNumber: String = "",
    val clinicName: String = "",
    val preferredLanguages: Set<TranscriptionLanguage> = setOf(TranscriptionLanguage.English),
    val processingPreference: ProcessingPreference = ProcessingPreference.SmartHybrid,
    val hasAttemptedSave: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val isLoading: Boolean = false,
    val saveError: String? = null,
    val saveMessage: String? = null
) {
    val fullNameError: String?
        get() = if (hasAttemptedSave && fullName.isBlank()) {
            "Enter the doctor's name"
        } else {
            null
        }

    val preferredLanguagesError: String?
        get() = if (hasAttemptedSave && preferredLanguages.isEmpty()) {
            "Select at least one consultation language"
        } else {
            null
        }

    val canSave: Boolean
        get() = fullName.isNotBlank() && preferredLanguages.isNotEmpty()

    fun toProfile(): DoctorProfile = DoctorProfile(
        fullName = fullName.trim(),
        specialty = specialty.trim(),
        registrationNumber = registrationNumber.trim(),
        clinicName = clinicName.trim(),
        preferredLanguages = preferredLanguages,
        processingPreference = processingPreference
    )

    companion object {
        fun fromProfile(profile: DoctorProfile) = DoctorProfileUiState(
            fullName = profile.fullName,
            specialty = profile.specialty,
            registrationNumber = profile.registrationNumber,
            clinicName = profile.clinicName,
            preferredLanguages = profile.preferredLanguages,
            processingPreference = profile.processingPreference,
            hasUnsavedChanges = false,
            isLoading = false
        )
    }
}
