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
    WhisperMultilingual(
        displayName = "Multilingual (Whisper Small)",
        description = "Offline baseline for English, Hindi, and mixed speech",
        isOffline = true,
        supportedLanguages = TranscriptionLanguage.entries.toSet()
    );

    fun supports(language: TranscriptionLanguage): Boolean = language in supportedLanguages

    companion object {
        fun defaultFor(language: TranscriptionLanguage): TranscriptionEngineOption =
            if (language == TranscriptionLanguage.English) MedAsrEnglish else WhisperMultilingual
    }
}
