package com.carelipik.app.ui.screens.recording

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
    val isPlaying: Boolean = false,
    val transcriptionLanguage: TranscriptionLanguage = TranscriptionLanguage.English,
    val transcriptionEngine: TranscriptionEngineOption = TranscriptionEngineOption.MedAsrEnglish,
    val hasOnlineProcessingConsent: Boolean = false
) {
    val formattedDuration: String
        get() = "%02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)

    val canContinue: Boolean
        get() = status == RecordingStatus.Completed &&
            hasSavedAudio &&
            (transcriptionEngine.isOffline || hasOnlineProcessingConsent)
}
