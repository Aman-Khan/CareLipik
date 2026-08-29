package com.carelipik.app.ui.screens.home

import com.carelipik.app.domain.model.DoctorProfile
import com.carelipik.app.domain.model.ProcessingPreference
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun applyDoctorProfile_personalizesDoctorAndProcessingSummary() {
        val viewModel = HomeViewModel()
        val profile = DoctorProfile(
            fullName = "Asha Mehta",
            specialty = "General medicine",
            registrationNumber = "DMC-123",
            clinicName = "Care Clinic",
            preferredLanguages = setOf(TranscriptionLanguage.English),
            processingPreference = ProcessingPreference.PrivateOnDevice
        )

        viewModel.applyDoctorProfile(profile)

        val state = viewModel.uiState.value
        assertEquals("Asha Mehta", state.doctorName)
        assertEquals("General medicine", state.specialty)
        assertEquals("Private · On-device", state.processingLabel)
        assertEquals("On-device processing only", state.processingDetail)
    }
}
