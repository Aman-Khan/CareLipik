package com.carelipik.app.ui.screens.recording

import com.carelipik.app.domain.recording.ConsultationRecorder
import com.carelipik.app.domain.recording.AudioImportResult
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.carelipik.app.domain.model.RecordedAudio

class RecordingViewModelTest {
    @Test
    fun recording_canBePausedResumedAndCompleted() {
        val recorder = TrackingRecorder()
        val viewModel = RecordingViewModel(recorder, useAutomaticTimer = false)

        viewModel.startRecording()
        assertEquals(RecordingStatus.Recording, viewModel.uiState.value.status)

        viewModel.pauseRecording()
        assertEquals(RecordingStatus.Paused, viewModel.uiState.value.status)

        viewModel.resumeRecording()
        viewModel.stopRecording()

        assertEquals(RecordingStatus.Completed, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.canContinue)
        assertEquals(listOf("start", "pause", "resume", "stop"), recorder.calls)
    }

    @Test
    fun discard_resetsCompletedRecording() {
        val recorder = TrackingRecorder()
        val viewModel = RecordingViewModel(recorder, useAutomaticTimer = false)

        viewModel.startRecording()
        viewModel.stopRecording()
        viewModel.discardRecording()

        assertEquals(RecordingUiState(), viewModel.uiState.value)
        assertFalse(viewModel.uiState.value.canContinue)
        assertEquals("discard", recorder.calls.last())
    }

    @Test
    fun newConsultation_discardsPreviousAudioAndSelections() {
        val recorder = TrackingRecorder()
        val viewModel = RecordingViewModel(recorder, useAutomaticTimer = false)
        viewModel.startRecording()
        viewModel.stopRecording()
        viewModel.setTranscriptionLanguage(TranscriptionLanguage.Hinglish)
        viewModel.setOnlineProcessingConsent(true)

        viewModel.resetForNewConsultation()

        assertEquals(RecordingUiState(), viewModel.uiState.value)
        assertEquals("discard", recorder.calls.last())
    }

    @Test
    fun invalidActions_areIgnored() {
        val recorder = TrackingRecorder()
        val viewModel = RecordingViewModel(recorder, useAutomaticTimer = false)

        viewModel.pauseRecording()
        viewModel.stopRecording()

        assertEquals(RecordingStatus.Ready, viewModel.uiState.value.status)
        assertTrue(recorder.calls.isEmpty())
    }

    @Test
    fun duration_isFormattedForDisplay() {
        val uiState = RecordingUiState(elapsedSeconds = 125)

        assertEquals("02:05", uiState.formattedDuration)
    }

    @Test
    fun languageSelection_isKeptForTranscription() {
        val viewModel = RecordingViewModel(TrackingRecorder(), useAutomaticTimer = false)

        viewModel.setTranscriptionLanguage(TranscriptionLanguage.Hinglish)

        assertEquals(
            TranscriptionLanguage.Hinglish,
            viewModel.transcriptionLanguage()
        )
        assertEquals(
            TranscriptionEngineOption.SaarasHindiHinglish,
            viewModel.transcriptionEngine()
        )
    }

    @Test
    fun onlineEngine_requiresPatientConsentBeforeContinue() {
        val viewModel = RecordingViewModel(TrackingRecorder(), useAutomaticTimer = false)

        viewModel.startRecording()
        viewModel.stopRecording()
        viewModel.setTranscriptionLanguage(TranscriptionLanguage.Hindi)

        assertFalse(viewModel.uiState.value.canContinue)

        viewModel.setOnlineProcessingConsent(true)

        assertTrue(viewModel.uiState.value.canContinue)
    }

    @Test
    fun offlineFallback_doesNotRequireOnlineConsent() {
        val viewModel = RecordingViewModel(TrackingRecorder(), useAutomaticTimer = false)

        viewModel.startRecording()
        viewModel.stopRecording()
        viewModel.setTranscriptionLanguage(TranscriptionLanguage.Hindi)
        viewModel.setTranscriptionEngine(TranscriptionEngineOption.WhisperMultilingual)

        assertTrue(viewModel.uiState.value.canContinue)
    }

    @Test
    fun englishEngine_canBeSelectedForComparison() {
        val viewModel = RecordingViewModel(TrackingRecorder(), useAutomaticTimer = false)

        viewModel.setTranscriptionEngine(TranscriptionEngineOption.WhisperMultilingual)

        assertEquals(
            TranscriptionEngineOption.WhisperMultilingual,
            viewModel.transcriptionEngine()
        )
    }

    @Test
    fun switchingFromHinglishToEnglish_keepsCompatibleOnlineEngine() {
        val viewModel = RecordingViewModel(TrackingRecorder(), useAutomaticTimer = false)

        viewModel.setTranscriptionLanguage(TranscriptionLanguage.Hinglish)
        viewModel.setTranscriptionLanguage(TranscriptionLanguage.English)

        assertEquals(
            TranscriptionEngineOption.SaarasHindiHinglish,
            viewModel.transcriptionEngine()
        )
    }

    @Test
    fun stopPlayback_stopsAudioBeforeLeavingRecordingScreen() {
        val recorder = TrackingRecorder()
        val viewModel = RecordingViewModel(recorder, useAutomaticTimer = false)

        viewModel.stopPlayback()

        assertEquals("stopPlayback", recorder.calls.last())
    }

    private class TrackingRecorder : ConsultationRecorder {
        override val amplitude: StateFlow<Float> = MutableStateFlow(0f)
        override val recordedAudio = MutableStateFlow<RecordedAudio?>(null)
        override val isPlaying = MutableStateFlow(false)
        val calls = mutableListOf<String>()

        override fun start() { calls += "start" }
        override fun pause() { calls += "pause" }
        override fun resume() { calls += "resume" }
        override fun stop() {
            calls += "stop"
            recordedAudio.value = RecordedAudio("/private/test.wav", 128)
        }
        override fun discard() { calls += "discard" }
        override fun play() { calls += "play" }
        override fun stopPlayback() { calls += "stopPlayback" }
        override suspend fun importAudio(sourceUri: String): AudioImportResult =
            AudioImportResult.Failure("Test import is unavailable")
    }
}
