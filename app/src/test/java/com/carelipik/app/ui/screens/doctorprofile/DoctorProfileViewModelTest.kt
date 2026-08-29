package com.carelipik.app.ui.screens.doctorprofile

import com.carelipik.app.domain.model.DoctorProfile
import com.carelipik.app.domain.model.ProcessingPreference
import com.carelipik.app.domain.repository.DoctorProfileRepository
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoctorProfileViewModelTest {
    @Test
    fun savedProfile_isRestoredAndSubsequentEditsArePersisted() {
        val original = DoctorProfile(
            fullName = "Asha Mehta",
            specialty = "General medicine",
            registrationNumber = "DMC-123",
            clinicName = "Care Clinic",
            preferredLanguages = setOf(TranscriptionLanguage.English),
            processingPreference = ProcessingPreference.PrivateOnDevice
        )
        val repository = FakeDoctorProfileRepository(original)
        val viewModel = DoctorProfileViewModel(
            repository = repository,
            processAsynchronously = false
        )

        assertEquals("Asha Mehta", viewModel.uiState.value.fullName)
        assertEquals(original, viewModel.savedProfile.value)

        viewModel.setClinicName("Updated Care Clinic")
        viewModel.saveProfile()

        assertEquals("Updated Care Clinic", repository.profile?.clinicName)
        assertEquals("Updated Care Clinic", viewModel.savedProfile.value?.clinicName)
    }

    @Test
    fun saveProfile_requiresDoctorName() {
        val viewModel = DoctorProfileViewModel()

        val profile = viewModel.saveProfile()

        assertNull(profile)
        assertEquals("Enter the doctor's name", viewModel.uiState.value.fullNameError)
    }

    @Test
    fun saveProfile_requiresAtLeastOneLanguage() {
        val viewModel = DoctorProfileViewModel()
        viewModel.setFullName("Asha Mehta")
        viewModel.togglePreferredLanguage(TranscriptionLanguage.English)

        val profile = viewModel.saveProfile()

        assertNull(profile)
        assertEquals(
            "Select at least one consultation language",
            viewModel.uiState.value.preferredLanguagesError
        )
    }

    @Test
    fun saveProfile_returnsTrimmedProfileAndSelectedPreferences() {
        val viewModel = DoctorProfileViewModel()
        viewModel.setFullName("  Asha Mehta  ")
        viewModel.setSpecialty(" General medicine ")
        viewModel.setRegistrationNumber(" DMC-123 ")
        viewModel.setClinicName(" Care Clinic ")
        viewModel.togglePreferredLanguage(TranscriptionLanguage.Hindi)
        viewModel.setProcessingPreference(ProcessingPreference.PrivateOnDevice)

        val profile = viewModel.saveProfile()

        assertEquals("Asha Mehta", profile?.fullName)
        assertEquals("General medicine", profile?.specialty)
        assertEquals("DMC-123", profile?.registrationNumber)
        assertEquals("Care Clinic", profile?.clinicName)
        assertEquals(
            setOf(TranscriptionLanguage.English, TranscriptionLanguage.Hindi),
            profile?.preferredLanguages
        )
        assertEquals(ProcessingPreference.PrivateOnDevice, profile?.processingPreference)
    }

    @Test
    fun autoDetect_isNotStoredAsPreferredConsultationLanguage() {
        val viewModel = DoctorProfileViewModel()

        viewModel.togglePreferredLanguage(TranscriptionLanguage.Auto)

        assertTrue(TranscriptionLanguage.Auto !in viewModel.uiState.value.preferredLanguages)
    }

    private class FakeDoctorProfileRepository(
        var profile: DoctorProfile? = null
    ) : DoctorProfileRepository {
        override suspend fun load(): DoctorProfile? = profile

        override suspend fun save(profile: DoctorProfile) {
            this.profile = profile
        }
    }
}
