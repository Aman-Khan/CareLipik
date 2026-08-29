package com.carelipik.app.ui.screens.transcript

import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionEngineOption

enum class TranscriptStatus {
    Idle,
    Processing,
    Ready,
    Error
}

data class TranscriptUiState(
    val status: TranscriptStatus = TranscriptStatus.Idle,
    val transcript: String = "",
    val errorMessage: String? = null,
    val hasAttemptedContinue: Boolean = false,
    val language: TranscriptionLanguage = TranscriptionLanguage.English,
    val engine: TranscriptionEngineOption = TranscriptionEngineOption.MedAsrEnglish
) {
    val transcriptError: String?
        get() = if (hasAttemptedContinue && transcript.isBlank()) {
            "Add or enter a transcript before continuing"
        } else {
            null
        }

    val canContinue: Boolean
        get() = status == TranscriptStatus.Ready && transcript.isNotBlank()
}
