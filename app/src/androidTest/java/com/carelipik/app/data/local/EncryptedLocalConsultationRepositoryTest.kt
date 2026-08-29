package com.carelipik.app.data.local

import androidx.test.core.app.ApplicationProvider
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.model.ClinicalDraft
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
                reviewedTranscript = "Doctor: Synthetic question\n\nPatient: Synthetic answer"
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
}
