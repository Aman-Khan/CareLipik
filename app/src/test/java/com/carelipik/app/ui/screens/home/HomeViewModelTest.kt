package com.carelipik.app.ui.screens.home

import com.carelipik.app.data.local.FakeLocalConsultationRepository
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.model.ClinicalNoteFormat
import com.carelipik.app.domain.model.DoctorProfile
import com.carelipik.app.domain.model.ProcessingPreference
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {
    @Test
    fun repositoryHistory_populatesThreeMostRecentConsultations() {
        val consultations = (1L..4L).map { index ->
            ApprovedConsultation(
                id = "consultation-$index",
                approvedAtMillis = index,
                patientName = "Synthetic reference $index",
                patientAge = "",
                visitReason = "Synthetic reason $index",
                draft = ClinicalDraft(noteFormat = ClinicalNoteFormat.Soap)
            )
        }

        val viewModel = HomeViewModel(
            consultationRepository = FakeLocalConsultationRepository(consultations),
            processAsynchronously = false
        )

        assertEquals(3, viewModel.uiState.value.recentConsultations.size)
        assertEquals(
            listOf("consultation-4", "consultation-3", "consultation-2"),
            viewModel.uiState.value.recentConsultations.map { it.id }
        )
        assertEquals(
            "Synthetic reason 4",
            viewModel.uiState.value.recentConsultations.first().visitReason
        )
    }

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
