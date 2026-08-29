package com.carelipik.app.data.export

import androidx.test.core.app.ApplicationProvider
import com.carelipik.app.domain.export.ConsultationExportFormat
import com.carelipik.app.domain.export.ConsultationExportResult
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.model.ClinicalDraft
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidConsultationStandardExporterTest {
    @Test
    fun allFormatsCreatePrivateNonAudioFilesAndJsonFormatsAreParseable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val exporter = AndroidConsultationStandardExporter(context)

        ConsultationExportFormat.entries.forEach { format ->
            val result = exporter.export(consultation(), format)

            assertTrue(result is ConsultationExportResult.Success)
            val exported = (result as ConsultationExportResult.Success).file
            val file = File(exported.localPath)
            assertTrue(file.isFile && file.length() > 0L)
            assertTrue(file.canonicalPath.startsWith(context.cacheDir.canonicalPath))
            assertEquals(format, exported.format)
            assertEquals(format.mimeType, exported.mimeType)
            assertFalse(file.name.contains("patient", ignoreCase = true))
            assertFalse(file.readBytes().decodeToString().contains(".wav"))

            when (format) {
                ConsultationExportFormat.StructuredJson -> {
                    val root = JSONObject(file.readText())
                    assertTrue(root.getBoolean("doctorApproved"))
                    assertFalse(root.getBoolean("includesConsultationAudio"))
                }
                ConsultationExportFormat.FhirR4Bundle -> {
                    val root = JSONObject(file.readText())
                    assertEquals("Bundle", root.getString("resourceType"))
                    assertEquals("document", root.getString("type"))
                    assertTrue(root.has("identifier"))
                    assertTrue(root.has("timestamp"))
                    val entries = root.getJSONArray("entry")
                    assertEquals(
                        "Composition",
                        entries.getJSONObject(0).getJSONObject("resource").getString("resourceType")
                    )
                    assertTrue((0 until entries.length()).any { index ->
                        entries.getJSONObject(index).getJSONObject("resource")
                            .getString("resourceType") == "DocumentReference"
                    })
                }
                ConsultationExportFormat.PlainText ->
                    assertTrue(file.readText().contains("DOCTOR-APPROVED CLINICAL NOTE"))
                ConsultationExportFormat.ClinicalPdf ->
                    assertTrue(file.readBytes().take(4).toByteArray().decodeToString() == "%PDF")
            }
        }
    }

    private fun consultation() = ApprovedConsultation(
        id = "00000000-0000-0000-0000-000000000077",
        approvedAtMillis = 1_788_000_000_000L,
        patientName = "Synthetic reference",
        patientAge = "51",
        visitReason = "Synthetic follow-up",
        draft = ClinicalDraft(
            presentingComplaint = "Synthetic complaint",
            history = "Synthetic history",
            keyFindings = "Synthetic findings",
            assessmentNotes = "Synthetic assessment",
            planNotes = "Synthetic plan"
        )
    )
}
