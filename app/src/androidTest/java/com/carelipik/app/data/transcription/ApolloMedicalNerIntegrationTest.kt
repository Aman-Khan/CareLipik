package com.carelipik.app.data.transcription

import androidx.test.platform.app.InstrumentationRegistry
import com.carelipik.app.domain.transcription.MedicalEntityType
import org.junit.Assert.assertTrue
import org.junit.Assert.assertArrayEquals
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import java.io.File
import org.junit.Test

/** Run explicitly against a device with the Apollo bundle installed; uses synthetic text only. */
class ApolloMedicalNerIntegrationTest {
    @Test
    fun installedModel_recognizesMedicationAndDiseaseAcrossLongTranscript() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ApolloOnnxMedicalNamedEntityRecognizer(context).use { recognizer ->
            assertTrue("Install the Apollo bundle before running this test", recognizer.isAvailable())
            val text = "Patient takes metformin 500 mg twice daily and has tuberculosis."
            val tokenizer = ApolloSentencePieceTokenizer(File(context.filesDir,
                "models/apollo-medical-ner/tokenizer.json").readText())
            assertArrayEquals("Android tokens differ from Hugging Face reference",
                longArrayOf(1, 14064, 1046, 36805, 2486, 4056, 2736, 1323, 263, 303, 25239, 260, 2),
                tokenizer.encode(text, 256).ids)
            val entities = recognizer.recognize(text)
            assertTrue("Medication not detected", entities.any {
                it.text == "metformin" && it.type == MedicalEntityType.Medication
            })
            assertTrue("Disease not detected", entities.any {
                it.text == "tuberculosis" && it.type == MedicalEntityType.Disease
            })
            val concerns = ApolloHybridTranscriptReviewAnalyzer(recognizer).analyze(text, TranscriptionLanguage.English)
            assertTrue("NER medication was not highlighted", concerns.any {
                it.text == "metformin" && it.id.startsWith("apollo-medical-ner:")
            })
            val longText = "The patient is speaking about the consultation. ".repeat(70) + text
            assertTrue("End of long transcript was missed", recognizer.recognize(longText).any {
                it.text == "metformin" && it.startIndex > 2000
            })
        }
    }
}
