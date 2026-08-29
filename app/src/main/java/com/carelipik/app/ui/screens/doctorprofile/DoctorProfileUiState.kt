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
    val hasAttemptedSave: Boolean = false
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
}
