package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.TranscriptConcernType
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleBasedTranscriptReviewAnalyzerTest {
    private val analyzer = RuleBasedTranscriptReviewAnalyzer()

    @Test
    fun englishConfusion_suggestsCoughForCup() {
        val concerns = analyzer.analyze(
            "I have had cup for three days and mild fever.",
            TranscriptionLanguage.English
        )

        val concern = concerns.first { it.text.equals("cup", ignoreCase = true) }
        assertEquals(TranscriptConcernType.PossibleRecognitionError, concern.type)
        assertEquals("cough", concern.suggestedReplacement)
        assertTrue(concerns.any { it.text.equals("fever", ignoreCase = true) })
    }

    @Test
    fun hindiTranscript_marksClinicalPhrasesWithoutOverlappingWords() {
        val concerns = analyzer.analyze(
            "मुझे सूखी खांसी और बुखार है।",
            TranscriptionLanguage.Hindi
        )

        assertEquals(listOf("सूखी खांसी", "बुखार"), concerns.map { it.text })
    }

    @Test
    fun hinglishTranscript_detectsRomanizedConfusionAndClinicalTerm() {
        val concerns = analyzer.analyze(
            "Mujhe khasi aur bukhar hai.",
            TranscriptionLanguage.Hinglish
        )

        assertEquals("khansi", concerns.first { it.text == "khasi" }.suggestedReplacement)
        assertTrue(concerns.any { it.text == "bukhar" })
    }
}
