package com.carelipik.app.ui.screens.transcript

import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptConcern
import com.carelipik.app.domain.transcription.TranscriptSegment
import com.carelipik.app.domain.transcription.SpeakerRole
import com.carelipik.app.domain.voice.DoctorVoiceRoleMatchResult
import com.carelipik.app.domain.transcription.HybridTranscriptionReview
import com.carelipik.app.domain.transcription.CorrectionStatus
import com.carelipik.app.domain.transcription.TranscriptionStage
import com.carelipik.app.domain.transcription.TranscriptLabelProjection

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
    val speakerNames: Map<String, String> = emptyMap(),
    val doctorVoiceMatch: DoctorVoiceRoleMatchResult? = null,
    val speakerSeparationWarning: String? = null,
    val clinicalAnalysisSource: String? = null,
    val clinicalAnalysisWarning: String? = null,
    val hasOnlineAnalysisConsent: Boolean = false,
    val isAnalyzingTerms: Boolean = false,
    val hybridReview: HybridTranscriptionReview? = null,
    val transcriptionStage: TranscriptionStage? = null,
    val sourceAudioPath: String? = null,
    val viewMode: TranscriptViewMode = TranscriptViewMode.FullTranscript
) {
    val pendingHybridCorrections: Boolean
        get() = hybridReview?.corrections?.any {
            it.status == CorrectionStatus.Suggested || it.status == CorrectionStatus.Unresolved
        } == true
    val pendingConcerns: List<TranscriptConcern>
        get() = concerns.filterNot { it.id in confirmedConcernIds }

    val confirmedConcernCount: Int
        get() = concerns.size - pendingConcerns.size

    val speakerIds: List<String>
        get() = segments.map { it.speakerId }.distinct()

    val canShowConversation: Boolean
        get() = speakerIds.size >= 2

    fun personLabel(speakerId: String): String = "Person ${speakerIds.indexOf(speakerId) + 1}"

    fun speakerLabel(speakerId: String): String {
        val name = speakerNames[speakerId]?.trim().orEmpty()
        val role = speakerRoles[speakerId] ?: SpeakerRole.Unassigned
        return when {
            name.isNotEmpty() && role != SpeakerRole.Unassigned ->
                "$name (${role.displayName})"
            name.isNotEmpty() -> name
            role in setOf(SpeakerRole.Doctor, SpeakerRole.Patient) -> role.displayName
            role == SpeakerRole.Other -> "Other (${personLabel(speakerId)})"
            else -> personLabel(speakerId)
        }
    }

    val labelProjection: TranscriptLabelProjection
        get() {
            val labels = speakerIds.associateWith(::speakerLabel)
            val duplicateLabels = labels.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            return TranscriptLabelProjection(transcript, labels.mapValues { (id, label) ->
                if (label in duplicateLabels) "$label (${personLabel(id)})" else label
            })
        }

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
            pendingHybridCorrections -> "Review the Whisper/MedASR disagreements above"
            pendingSpeakerIds.isNotEmpty() ->
                "Confirm each person's role: Doctor, Patient, or Other"
            pendingConcerns.isNotEmpty() ->
                "Confirm or correct every highlighted term before continuing"
            else -> null
        }

    val canContinue: Boolean
        get() = status == TranscriptStatus.Ready &&
            !isAnalyzingTerms &&
            !pendingHybridCorrections &&
            transcript.isNotBlank() &&
            pendingSpeakerIds.isEmpty() &&
            pendingConcerns.isEmpty()
}
