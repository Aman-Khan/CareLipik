package com.carelipik.app.domain.transcription

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptionEngineOptionTest {
    @Test
    fun picker_showsOnlyThreeEnginesAndKeepsDefaultsVisible() {
        org.junit.Assert.assertEquals(
            listOf(TranscriptionEngineOption.MedAsrEnglish,
                TranscriptionEngineOption.AssemblyAiUniversal,
                TranscriptionEngineOption.SaarasHindiHinglish),
            TranscriptionEngineOption.visibleOptions
        )
        TranscriptionLanguage.entries.forEach {
            assertTrue(TranscriptionEngineOption.defaultFor(it) in TranscriptionEngineOption.visibleOptions)
        }
        org.junit.Assert.assertEquals("Multilingual (V3 Turbo)",
            TranscriptionEngineOption.AssemblyAiUniversal.displayName)
    }

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

    @Test
    fun fullAudioTurboTest_isOfflineAndKeepsDiarizedTurboAvailable() {
        val fullAudio = TranscriptionEngineOption.WhisperTurboFullAudioTest

        assertTrue(fullAudio.isOffline)
        assertTrue(fullAudio.supports(TranscriptionLanguage.Hinglish))
        assertTrue(
            TranscriptionEngineOption.entries.contains(
                TranscriptionEngineOption.WhisperTurboMultilingual
            )
        )
    }

    @Test
    fun assemblyAi_isOptionalOnlineMultilingualEngine() {
        val assemblyAi = TranscriptionEngineOption.AssemblyAiUniversal

        assertFalse(assemblyAi.isOffline)
        assertTrue(assemblyAi.supports(TranscriptionLanguage.Auto))
        assertTrue(assemblyAi.supports(TranscriptionLanguage.English))
        assertTrue(assemblyAi.supports(TranscriptionLanguage.Hindi))
        assertTrue(assemblyAi.supports(TranscriptionLanguage.Hinglish))
    }
}
