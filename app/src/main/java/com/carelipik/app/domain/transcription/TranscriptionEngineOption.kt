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
    AssemblyAiUniversal(
        displayName = "Online multilingual (AssemblyAI)",
        description = "Experimental cloud ASR with automatic language detection and multi-speaker diarization",
        isOffline = false,
        supportedLanguages = TranscriptionLanguage.entries.toSet()
    ),
    WhisperMultilingual(
        displayName = "Multilingual (Whisper Small)",
        description = "Offline baseline for English, Hindi, and mixed speech",
        isOffline = true,
        supportedLanguages = TranscriptionLanguage.entries.toSet()
    ),
    WhisperTurboMultilingual(
        displayName = "Multilingual (Whisper Turbo)",
        description = "Higher-capacity offline model; requires about 1.04 GB of local assets",
        isOffline = true,
        supportedLanguages = TranscriptionLanguage.entries.toSet()
    ),
    WhisperTurboFullAudioTest(
        displayName = "Whisper Turbo (full audio test)",
        description = "Experimental 30-second context without speaker diarization or role matching",
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
