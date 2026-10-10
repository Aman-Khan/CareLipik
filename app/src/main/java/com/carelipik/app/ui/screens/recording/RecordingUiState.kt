package com.carelipik.app.ui.screens.recording

import com.carelipik.app.domain.model.RecordedAudioSource
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionEngineOption

enum class RecordingStatus {
    Ready,
    Recording,
    Paused,
    Completed
}

data class RecordingUiState(
    val status: RecordingStatus = RecordingStatus.Ready,
    val elapsedSeconds: Int = 0,
    val amplitude: Float = 0f,
    val hasSavedAudio: Boolean = false,
    val audioSource: RecordedAudioSource? = null,
    val audioDisplayName: String = "",
    val audioSizeBytes: Long = 0,
    val isPlaying: Boolean = false,
    val isImporting: Boolean = false,
    val importError: String? = null,
    val isDownloading: Boolean = false,
    val downloadMessage: String? = null,
    val downloadError: String? = null,
    val transcriptionLanguage: TranscriptionLanguage = TranscriptionLanguage.English,
    val transcriptionEngine: TranscriptionEngineOption = TranscriptionEngineOption.MedAsrEnglish,
    val hasOnlineProcessingConsent: Boolean = false
) {
    val formattedDuration: String
        get() = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)

    val canContinue: Boolean
        get() = status == RecordingStatus.Completed &&
            hasSavedAudio &&
            !isImporting &&
            !isDownloading &&
            (transcriptionEngine.isOffline || hasOnlineProcessingConsent)

    val isImportedAudio: Boolean
        get() = audioSource == RecordedAudioSource.Imported
}
