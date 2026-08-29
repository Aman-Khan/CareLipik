package com.carelipik.app.data.local

import androidx.test.core.app.ApplicationProvider
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.model.ClinicalNoteFormat
import com.carelipik.app.domain.model.ClinicalNoteGenerationSource
import com.carelipik.app.domain.model.ClinicalNoteLanguage
import com.carelipik.app.domain.model.ClinicalNoteSection
import com.carelipik.app.domain.model.MedicationDraft
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedLocalConsultationRepositoryTest {
    @Test
    fun approvedConsultation_roundTripsEncryptedAndCanBeDeleted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = EncryptedLocalConsultationRepository(context)
        val id = UUID.randomUUID().toString()
        val item = ApprovedConsultation(
            id = id,
            approvedAtMillis = 123L,
            patientName = "Synthetic reference",
            patientAge = "35",
            visitReason = "Synthetic visit",
            draft = ClinicalDraft(
                patientAge = "35",
                history = "Synthetic history",
                reviewedTranscript = "Doctor: Synthetic question\n\nPatient: Synthetic answer",
                noteFormat = ClinicalNoteFormat.Soap,
                noteLanguage = ClinicalNoteLanguage.English,
                structuredSections = listOf(
                    ClinicalNoteSection(
                        id = "subjective",
                        title = "Subjective",
                        content = "Synthetic answer",
                        sourceTurnIds = listOf("T2")
                    )
                ),
                medications = listOf(
                    MedicationDraft(
                        name = "Synthetic medicine",
                        dose = "One tablet",
                        frequency = "Once daily",
                        sourceEvidence = "Doctor: Synthetic prescription",
                        isDoctorReviewed = true
                    )
                ),
                coverageWarnings = listOf("Synthetic coverage warning"),
                generationSource = ClinicalNoteGenerationSource.Gemini
            )
        )
        try {
            repository.save(item)

            assertEquals(item, repository.get(id))
            val storedFile = File(
                context.filesDir,
                "${EncryptedLocalConsultationRepository.DIRECTORY_NAME}/$id.clh"
            )
            assertFalse(storedFile.readText().contains("Synthetic reference"))

            repository.delete(id)
            assertNull(repository.get(id))
        } finally {
            repository.delete(id)
        }
    }

    @Test
    fun longReviewedTranscript_roundTripsBeyondLegacyWriteUtfLimit() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = EncryptedLocalConsultationRepository(context)
        val id = UUID.randomUUID().toString()
        val transcript = "Patient: Synthetic statement.\n".repeat(3_000)
        val item = ApprovedConsultation(
            id = id,
            approvedAtMillis = 456L,
            patientName = "Synthetic long record",
            patientAge = "47",
            visitReason = "Synthetic visit",
            draft = ClinicalDraft(reviewedTranscript = transcript)
        )
        try {
            repository.save(item)

            assertEquals(transcript, repository.get(id)?.draft?.reviewedTranscript)
        } finally {
            repository.delete(id)
        }
    }

    @Test
    fun versionOneRecord_decodesWithEmptyNewFields() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = EncryptedLocalConsultationRepository(context)
        val bytes = ByteArrayOutputStream().use { outputBytes ->
            DataOutputStream(outputBytes).use { output ->
                output.writeInt(1)
                output.writeUTF("00000000-0000-0000-0000-000000000001")
                output.writeLong(123L)
                output.writeUTF("Legacy synthetic reference")
                output.writeUTF("40")
                output.writeUTF("Legacy synthetic visit")
                output.writeUTF("Legacy complaint")
                output.writeUTF("Legacy history")
                output.writeUTF("Legacy findings")
                output.writeUTF("Legacy assessment")
                output.writeUTF("Legacy plan")
            }
            outputBytes.toByteArray()
        }

        val decoded = repository.decodeFromStorage(bytes)

        assertEquals("Legacy history", decoded.draft.history)
        assertEquals("", decoded.draft.patientAge)
        assertEquals("", decoded.draft.reviewedTranscript)
    }

    @Test
    fun versionTwoRecord_decodesWithDefaultTemplateAndMedicationFields() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = EncryptedLocalConsultationRepository(context)
        val bytes = ByteArrayOutputStream().use { outputBytes ->
            DataOutputStream(outputBytes).use { output ->
                output.writeInt(2)
                output.writeLengthPrefixed("00000000-0000-0000-0000-000000000002")
                output.writeLong(456L)
                listOf(
                    "Legacy v2 synthetic reference",
                    "47",
                    "Legacy v2 visit",
                    "47",
                    "Legacy v2 complaint",
                    "Legacy v2 history",
                    "Legacy v2 findings",
                    "Legacy v2 assessment",
                    "Legacy v2 plan",
                    "Patient: Legacy v2 transcript"
                ).forEach { output.writeLengthPrefixed(it) }
            }
            outputBytes.toByteArray()
        }

        val decoded = repository.decodeFromStorage(bytes)

        assertEquals("Legacy v2 history", decoded.draft.history)
        assertEquals(ClinicalNoteFormat.Soap, decoded.draft.noteFormat)
        assertTrue(decoded.draft.medications.isEmpty())
    }

    private fun DataOutputStream.writeLengthPrefixed(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }
}
