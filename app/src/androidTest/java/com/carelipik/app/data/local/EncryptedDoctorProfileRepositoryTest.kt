package com.carelipik.app.data.local

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.carelipik.app.domain.model.DoctorProfile
import com.carelipik.app.domain.model.ProcessingPreference
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedDoctorProfileRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val profileFile by lazy { File(context.filesDir, "synthetic_doctor_profile_test.clp") }

    @Before
    fun clearProfile() {
        profileFile.delete()
        File(context.filesDir, "synthetic_doctor_profile_test.clp.pending").delete()
    }

    @Test
    fun profileRoundTripsWithoutPlaintextAtRest() = runBlocking {
        val profile = DoctorProfile(
            fullName = "Synthetic Doctor",
            specialty = "Synthetic specialty",
            registrationNumber = "TEST-123",
            clinicName = "Synthetic Clinic",
            preferredLanguages = setOf(
                TranscriptionLanguage.English,
                TranscriptionLanguage.Hindi
            ),
            processingPreference = ProcessingPreference.SmartHybrid
        )
        val repository = EncryptedDoctorProfileRepository(context, profileFile)

        repository.save(profile)

        assertEquals(profile, EncryptedDoctorProfileRepository(context, profileFile).load())
        assertFalse(profileFile.readText(Charsets.ISO_8859_1).contains("Synthetic Doctor"))
        assertFalse(profileFile.readText(Charsets.ISO_8859_1).contains("TEST-123"))
    }
}
