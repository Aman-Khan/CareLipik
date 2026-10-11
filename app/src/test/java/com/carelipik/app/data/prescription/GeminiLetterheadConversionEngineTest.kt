package com.carelipik.app.data.prescription

import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.prescription.PrescriptionTemplates
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GeminiLetterheadConversionEngineTest {
    private val template = """
        \documentclass[12pt]{article}
        \begin{document}
        Synthetic contact address
        ${PrescriptionTemplates.DOCTOR}
        ${PrescriptionTemplates.PATIENT}
        ${PrescriptionTemplates.BODY}
        Synthetic footer
        \end{document}
    """.trimIndent()

    @Test fun pdf_isSentWithReusableTemplateContract_andResponseAcceptsLaterInputs() {
        val bytes = "%PDF-synthetic fixture".toByteArray()
        val engine = GeminiLetterheadConversionEngine(generate = { prompt, schema, sent, mime ->
            assertSame(bytes, sent)
            assertEquals("application/pdf", mime)
            assertTrue(prompt.contains("EXPLICITLY ADDED"))
            assertTrue(prompt.contains(PrescriptionTemplates.DOCTOR))
            assertTrue(prompt.contains(PrescriptionTemplates.PATIENT))
            assertTrue(prompt.contains(PrescriptionTemplates.BODY))
            assertTrue(schema.getJSONObject("properties").has("latex"))
            JSONObject().put("latex", template)
        })
        val result = engine.convert(bytes)
        val prescription = PrescriptionTemplates.create(result, ClinicalDraft(patientAge = "30"),
            "Synthetic patient", "Example doctor", "Example specialty", "REG-123", "Example clinic")
        assertTrue(prescription.contains("Synthetic contact address"))
        assertTrue(prescription.contains("Example doctor"))
        assertTrue(prescription.contains("REG-123"))
        assertTrue(prescription.contains("Synthetic patient"))
        assertFalse(prescription.contains(PrescriptionTemplates.DOCTOR))
        assertFalse(prescription.contains(PrescriptionTemplates.PATIENT))
        assertFalse(prescription.contains(PrescriptionTemplates.BODY))
    }

    @Test fun image_isSentWithDetectedMimeTypeWithoutChangingBytes() {
        val bytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 1)
        val engine = GeminiLetterheadConversionEngine(generate = { _, _, sent, mime ->
            assertSame(bytes, sent)
            assertEquals("image/jpeg", mime)
            JSONObject().put("latex", template)
        })
        assertEquals(template, engine.convert(bytes))
    }

    @Test fun missingDynamicPlaceholders_isRejectedInsteadOfSavingFilledSample() {
        val engine = GeminiLetterheadConversionEngine(generate = { _, _, _, _ ->
            JSONObject().put("latex", PrescriptionTemplates.common)
        })
        assertThrows(IllegalArgumentException::class.java) { engine.convert("%PDF-synthetic fixture".toByteArray()) }
    }

    @Test fun unsupportedAttachment_isRejectedBeforeCallingGemini() {
        val engine = GeminiLetterheadConversionEngine(generate = { _, _, _, _ -> error("Must not send") })
        assertThrows(IllegalStateException::class.java) { engine.convert("unsupported".toByteArray()) }
    }
}
