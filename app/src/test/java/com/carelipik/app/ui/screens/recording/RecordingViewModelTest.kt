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
import com.carelipik.app.domain.export.RecordingAudioExporter
import com.carelipik.app.domain.export.RecordingAudioExportResult

class RecordingViewModelTest {
    @Test
    fun speakerCount_defaultsToTwoAndResetsForNewConsultation() {
        val viewModel = RecordingViewModel(TrackingRecorder(), useAutomaticTimer = false)
        assertEquals(2, viewModel.speakerCount())
        viewModel.setSpeakerCount(4)
        assertEquals(4, viewModel.uiState.value.speakerCount)
        viewModel.resetForNewConsultation()
        assertEquals(2, viewModel.speakerCount())
    }

    @Test
    fun download_copiesCurrentRecordingWithoutDiscardingOrTranscribingIt() {
        val recorder = TrackingRecorder()
        var exportedPath: String? = null
        var exportedDestination: String? = null
        val viewModel = RecordingViewModel(
            recorder, useAutomaticTimer = false, exportAsynchronously = false,
            audioExporter = RecordingAudioExporter { audio, destination ->
                exportedPath = audio.localPath
                exportedDestination = destination
                RecordingAudioExportResult.Success(audio.sizeBytes)
            }
        )
        viewModel.startRecording()
        viewModel.stopRecording()
        viewModel.downloadAudio("content://synthetic/new.wav")

        assertEquals("/private/test.wav", exportedPath)
        assertEquals("content://synthetic/new.wav", exportedDestination)
        assertEquals(RecordingStatus.Completed, viewModel.uiState.value.status)
        assertTrue(viewModel.uiState.value.downloadMessage!!.contains("downloaded"))
        assertFalse(viewModel.uiState.value.isDownloading)
        assertTrue(viewModel.uiState.value.canContinue)
        assertFalse(recorder.calls.contains("discard"))
    }

    @Test
    fun failedDownload_keepsOriginalRecordingAvailableForRetry() {
        val viewModel = RecordingViewModel(
            TrackingRecorder(), useAutomaticTimer = false, exportAsynchronously = false,
            audioExporter = RecordingAudioExporter { _, _ -> RecordingAudioExportResult.Failure("Synthetic write failure") }
        )
        viewModel.startRecording()
        viewModel.stopRecording()
        viewModel.downloadAudio("content://synthetic/new.wav")

        assertEquals("Synthetic write failure", viewModel.uiState.value.downloadError)
        assertFalse(viewModel.uiState.value.isDownloading)
        assertTrue(viewModel.uiState.value.hasSavedAudio)
        assertTrue(viewModel.uiState.value.canContinue)
    }

    @Test
    fun unfinishedRecording_cannotBeDownloaded() {
        var exported = false
        val viewModel = RecordingViewModel(
            TrackingRecorder(), useAutomaticTimer = false, exportAsynchronously = false,
            audioExporter = RecordingAudioExporter { _, _ ->
                exported = true
                RecordingAudioExportResult.Success(1)
            }
        )
        viewModel.downloadAudio("content://synthetic/new.wav")

        assertFalse(exported)
        assertTrue(viewModel.uiState.value.downloadError!!.contains("Finish recording"))
    }

    @Test
    fun download_blocksDiscardAndContinueUntilCopyFinishes() {
        val recorder = TrackingRecorder()
        lateinit var viewModel: RecordingViewModel
        viewModel = RecordingViewModel(
            recorder, useAutomaticTimer = false, exportAsynchronously = false,
            audioExporter = RecordingAudioExporter { _, _ ->
                assertTrue(viewModel.uiState.value.isDownloading)
                assertFalse(viewModel.uiState.value.canContinue)
                viewModel.discardRecording()
                assertFalse(recorder.calls.contains("discard"))
                RecordingAudioExportResult.Success(128)
            }
        )
        viewModel.startRecording()
        viewModel.stopRecording()
        viewModel.downloadAudio("content://synthetic/new.wav")

        assertFalse(viewModel.uiState.value.isDownloading)
        assertTrue(viewModel.uiState.value.canContinue)
    }
    @Test
    fun restoredRecording_preservesSelectionsAndRequiresNewOnlineConsent() = kotlinx.coroutines.runBlocking {
        val recorder = TrackingRecorder()
        val viewModel = RecordingViewModel(recorder, useAutomaticTimer = false)
        val audio = RecordedAudio("synthetic.wav", 32_044L, durationMillis = 1_000L)
        val saved = com.carelipik.app.domain.model.SavedRecording(
            "00000000-0000-0000-0000-000000000001", 1_000L, "Synthetic patient", "30", "Synthetic visit",
            TranscriptionLanguage.Hinglish, TranscriptionEngineOption.SaarasHindiHinglish,
            "Synthetic WAV", 1_000L, com.carelipik.app.domain.model.RecordedAudioSource.Microphone, true,
            speakerCount = 3
        )
        assertTrue(viewModel.restoreSavedRecording(com.carelipik.app.domain.model.RestoredSavedRecording(saved, audio)))
        assertEquals(3, viewModel.speakerCount())
        assertEquals(RecordingStatus.Completed, viewModel.uiState.value.status)
        assertEquals(TranscriptionLanguage.Hinglish, viewModel.transcriptionLanguage())
        assertEquals(TranscriptionEngineOption.SaarasHindiHinglish, viewModel.transcriptionEngine())
        assertFalse(viewModel.uiState.value.hasOnlineProcessingConsent)
        assertFalse(viewModel.uiState.value.canContinue)
        viewModel.setOnlineProcessingConsent(true)
        assertTrue(viewModel.uiState.value.canContinue)
    }
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
            TranscriptionEngineOption.AssemblyAiUniversal,
            viewModel.transcriptionEngine()
        )
    }

    @Test
    fun onlineEngine_canContinueWithoutSeparateUploadCheckbox() {
        val viewModel = RecordingViewModel(TrackingRecorder(), useAutomaticTimer = false)

        viewModel.startRecording()
        viewModel.stopRecording()
        viewModel.setTranscriptionLanguage(TranscriptionLanguage.Hindi)

        assertFalse(viewModel.uiState.value.hasOnlineProcessingConsent)
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
    fun switchingFromHinglishToEnglish_keepsCompatibleAdaptiveEngine() {
        val viewModel = RecordingViewModel(TrackingRecorder(), useAutomaticTimer = false)

        viewModel.setTranscriptionLanguage(TranscriptionLanguage.Hinglish)
        viewModel.setTranscriptionLanguage(TranscriptionLanguage.English)

        assertEquals(
            TranscriptionEngineOption.AssemblyAiUniversal,
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
        override suspend fun restoreAudio(audio: RecordedAudio): AudioImportResult {
            recordedAudio.value = audio
            return AudioImportResult.Success(audio)
        }
    }
}
