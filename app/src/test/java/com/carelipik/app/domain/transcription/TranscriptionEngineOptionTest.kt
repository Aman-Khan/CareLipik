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

    @Test
    fun whisperTurbo_isAnAdditionalOfflineMultilingualOption() {
        val turbo = TranscriptionEngineOption.WhisperTurboMultilingual

        assertTrue(turbo.isOffline)
        assertTrue(turbo.supports(TranscriptionLanguage.English))
        assertTrue(turbo.supports(TranscriptionLanguage.Hindi))
        assertTrue(turbo.supports(TranscriptionLanguage.Hinglish))
        assertTrue(TranscriptionEngineOption.entries.contains(TranscriptionEngineOption.WhisperMultilingual))
    }
}
