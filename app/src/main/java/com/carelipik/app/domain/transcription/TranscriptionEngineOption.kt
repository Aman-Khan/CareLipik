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
    NameMaskedSaaras(
        displayName = "Online model",
        description = "English transcription with the patient's name masked on device before audio upload",
        isOffline = false,
        supportedLanguages = setOf(TranscriptionLanguage.English)
    ),
    AssemblyAiUniversal(
        displayName = "Multilingual V3 Turbo",
        description = "Automatic multilingual transcription with on-device fallback when offline",
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
        displayName = "Multilingual Whisper (V3 Turbo)",
        description = "On-device multilingual Whisper V3 Turbo using local model assets",
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
        // Other engines remain available internally and to tests, but are hidden in the demo.
        val visibleOptions = listOf(
            AssemblyAiUniversal,
            NameMaskedSaaras
        )

        fun defaultFor(language: TranscriptionLanguage): TranscriptionEngineOption = when (language) {
            TranscriptionLanguage.English,
            TranscriptionLanguage.Hindi,
            TranscriptionLanguage.Hinglish,
            TranscriptionLanguage.Auto -> AssemblyAiUniversal
        }
    }
}
