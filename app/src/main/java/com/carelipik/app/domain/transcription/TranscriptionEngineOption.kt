package com.carelipik.app.domain.transcription

enum class TranscriptionEngineOption(
    val displayName: String,
    val description: String,
    val isOffline: Boolean,
    private val supportedLanguages: Set<TranscriptionLanguage>
) {
    MedAsrEnglish(
        displayName = "Medical English (MedASR)",
        description = "English-only model trained for medical speech",
        isOffline = true,
        supportedLanguages = setOf(TranscriptionLanguage.English)
    ),
    SaarasHindiHinglish(
        displayName = "Online multilingual (Saaras)",
        description = "Cloud ASR with batch two-speaker separation for Indian conversations",
        isOffline = false,
        supportedLanguages = setOf(
            TranscriptionLanguage.English,
            TranscriptionLanguage.Hindi,
            TranscriptionLanguage.Hinglish
        )
    ),
    WhisperMultilingual(
        displayName = "Multilingual (Whisper Small)",
        description = "Offline baseline for English, Hindi, and mixed speech",
        isOffline = true,
        supportedLanguages = TranscriptionLanguage.entries.toSet()
    );

    fun supports(language: TranscriptionLanguage): Boolean = language in supportedLanguages

    companion object {
        fun defaultFor(language: TranscriptionLanguage): TranscriptionEngineOption = when (language) {
            TranscriptionLanguage.English -> MedAsrEnglish
            TranscriptionLanguage.Hindi,
            TranscriptionLanguage.Hinglish -> SaarasHindiHinglish
            TranscriptionLanguage.Auto -> WhisperMultilingual
        }
    }
}
