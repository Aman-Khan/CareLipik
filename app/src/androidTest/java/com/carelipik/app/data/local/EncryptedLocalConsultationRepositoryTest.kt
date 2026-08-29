package com.carelipik.app.data.local

import androidx.test.core.app.ApplicationProvider
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.model.ClinicalDraft
import java.io.File
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
            draft = ClinicalDraft(history = "Synthetic history")
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
}
