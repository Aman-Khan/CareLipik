package com.carelipik.app.data.local

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.content.FileProvider
import com.carelipik.app.domain.export.ConsultationExportFormat
import com.carelipik.app.domain.export.ExportedConsultationFile
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedConsultationReportRepositoryTest {
    @Test
    fun reportRoundTrip_isEncryptedLinkedMaterializedAndDeleted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = EncryptedConsultationReportRepository(context)
        val consultationId = UUID.randomUUID().toString()
        val marker = "Synthetic approved report marker ${UUID.randomUUID()}"
        val source = File(context.cacheDir, "synthetic-report-${UUID.randomUUID()}.txt")
            .apply { writeText(marker) }

        try {
            val artifact = repository.save(
                consultationId = consultationId,
                exportedFile = ExportedConsultationFile(
                    localPath = source.absolutePath,
                    displayName = "synthetic-approved-note.txt",
                    sizeBytes = source.length(),
                    mimeType = ConsultationExportFormat.PlainText.mimeType,
                    format = ConsultationExportFormat.PlainText
                )
            )

            assertEquals(listOf(artifact), repository.list(consultationId))
            val encryptedFile = File(
                context.filesDir,
                "${EncryptedConsultationReportRepository.REPORT_DIRECTORY}/" +
                    "$consultationId/PlainText.clr"
            )
            assertTrue(encryptedFile.isFile)
            assertFalse(encryptedFile.readBytes().decodeToString().contains(marker))

            val materialized = repository.materialize(artifact)
            assertEquals(marker, File(materialized.localPath).readText())
            assertTrue(
                File(materialized.localPath).canonicalPath.startsWith(
                    File(
                        context.cacheDir,
                        EncryptedConsultationReportRepository.ACCESS_DIRECTORY
                    ).canonicalPath
                )
            )
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(materialized.localPath)
            )
            assertEquals("content", uri.scheme)

            repository.deleteForConsultation(consultationId)
            assertTrue(repository.list(consultationId).isEmpty())
            assertFalse(File(materialized.localPath).exists())
        } finally {
            repository.deleteForConsultation(consultationId)
            source.delete()
        }
    }
}
