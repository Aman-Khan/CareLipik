package com.carelipik.app.ui.screens.transcript

import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptConcern

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
    val engine: TranscriptionEngineOption = TranscriptionEngineOption.MedAsrEnglish,
    val concerns: List<TranscriptConcern> = emptyList(),
    val confirmedConcernIds: Set<String> = emptySet()
) {
    val pendingConcerns: List<TranscriptConcern>
        get() = concerns.filterNot { it.id in confirmedConcernIds }

    val confirmedConcernCount: Int
        get() = concerns.size - pendingConcerns.size

    val transcriptError: String?
        get() = when {
            !hasAttemptedContinue -> null
            transcript.isBlank() -> "Add or enter a transcript before continuing"
            pendingConcerns.isNotEmpty() ->
                "Confirm or correct every highlighted term before continuing"
            else -> null
        }

    val canContinue: Boolean
        get() = status == TranscriptStatus.Ready &&
            transcript.isNotBlank() &&
            pendingConcerns.isEmpty()
}
