package com.carelipik.app.domain.transcription

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptionEngineOptionTest {
    @Test
    fun medAsr_remainsAvailableForEnglishOnly() {
        val medAsr = TranscriptionEngineOption.MedAsrEnglish

        assertTrue(medAsr.supports(TranscriptionLanguage.English))
        assertFalse(medAsr.supports(TranscriptionLanguage.Hindi))
        assertFalse(medAsr.supports(TranscriptionLanguage.Hinglish))
    }
}
