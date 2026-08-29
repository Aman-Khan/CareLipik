package com.carelipik.app.ui.screens.transcript

import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptConcern
import com.carelipik.app.domain.transcription.TranscriptSegment
import com.carelipik.app.domain.transcription.SpeakerRole

enum class TranscriptStatus {
    Idle,
    Processing,
    Ready,
    Error
}

enum class TranscriptViewMode {
    FullTranscript,
    Conversation
}

data class TranscriptUiState(
    val status: TranscriptStatus = TranscriptStatus.Idle,
    val transcript: String = "",
    val errorMessage: String? = null,
    val hasAttemptedContinue: Boolean = false,
    val language: TranscriptionLanguage = TranscriptionLanguage.English,
    val engine: TranscriptionEngineOption = TranscriptionEngineOption.MedAsrEnglish,
    val concerns: List<TranscriptConcern> = emptyList(),
    val confirmedConcernIds: Set<String> = emptySet(),
    val segments: List<TranscriptSegment> = emptyList(),
    val speakerRoles: Map<String, SpeakerRole> = emptyMap(),
    val viewMode: TranscriptViewMode = TranscriptViewMode.FullTranscript
) {
    val pendingConcerns: List<TranscriptConcern>
        get() = concerns.filterNot { it.id in confirmedConcernIds }

    val confirmedConcernCount: Int
        get() = concerns.size - pendingConcerns.size

    val speakerIds: List<String>
        get() = segments.map { it.speakerId }.distinct()

    val canShowConversation: Boolean
        get() = speakerIds.size >= 2

    val pendingSpeakerIds: List<String>
        get() = if (speakerIds.size < 2) {
            emptyList()
        } else {
            speakerIds.filter { speakerRoles[it] in setOf(null, SpeakerRole.Unassigned) }
        }

    val transcriptError: String?
        get() = when {
            !hasAttemptedContinue -> null
            transcript.isBlank() -> "Add or enter a transcript before continuing"
            pendingSpeakerIds.isNotEmpty() ->
                "Confirm which detected speaker is the doctor and patient"
            pendingConcerns.isNotEmpty() ->
                "Confirm or correct every highlighted term before continuing"
            else -> null
        }

    val canContinue: Boolean
        get() = status == TranscriptStatus.Ready &&
            transcript.isNotBlank() &&
            pendingSpeakerIds.isEmpty() &&
            pendingConcerns.isEmpty()
}
