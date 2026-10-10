package com.carelipik.app.ui.screens.recording

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.carelipik.app.data.audio.AndroidMicrophoneRecorder
import com.carelipik.app.data.audio.FakeConsultationRecorder
import com.carelipik.app.domain.recording.ConsultationRecorder
import com.carelipik.app.domain.recording.AudioImportResult
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.model.RestoredSavedRecording
import com.carelipik.app.domain.export.RecordingAudioExporter
import com.carelipik.app.domain.export.RecordingAudioExportResult
import com.carelipik.app.data.export.AndroidRecordingAudioExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RecordingViewModel(
    private val recorder: ConsultationRecorder = FakeConsultationRecorder(),
    private val useAutomaticTimer: Boolean = true,
    private val audioExporter: RecordingAudioExporter? = null,
    private val exportAsynchronously: Boolean = true
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()
    private var timerJob: Job? = null

    init {
        if (useAutomaticTimer) {
            viewModelScope.launch {
                recorder.amplitude.collect { amplitude ->
                    _uiState.update { it.copy(amplitude = amplitude) }
                }
            }
            viewModelScope.launch {
                recorder.recordedAudio.collect { audio ->
                    _uiState.update {
                        it.copy(
                            hasSavedAudio = audio != null,
                            audioSource = audio?.source,
                            audioDisplayName = audio?.displayName.orEmpty(),
                            audioSizeBytes = audio?.sizeBytes ?: 0
                        )
                    }
                }
            }
            viewModelScope.launch {
                recorder.isPlaying.collect { isPlaying ->
                    _uiState.update { it.copy(isPlaying = isPlaying) }
                }
            }
        }
    }

    fun startRecording() {
        if (_uiState.value.status != RecordingStatus.Ready) return
        recorder.start()
        _uiState.update {
            it.copy(
                status = RecordingStatus.Recording,
                importError = null,
                audioSource = null,
                audioDisplayName = "",
                audioSizeBytes = 0
            )
        }
        if (useAutomaticTimer) startTimer()
    }

    fun pauseRecording() {
        if (_uiState.value.status != RecordingStatus.Recording) return
        recorder.pause()
        timerJob?.cancel()
        _uiState.update { it.copy(status = RecordingStatus.Paused) }
    }

    fun resumeRecording() {
        if (_uiState.value.status != RecordingStatus.Paused) return
        recorder.resume()
        _uiState.update { it.copy(status = RecordingStatus.Recording) }
        if (useAutomaticTimer) startTimer()
    }

    fun stopRecording() {
        if (_uiState.value.status !in setOf(RecordingStatus.Recording, RecordingStatus.Paused)) return
        recorder.stop()
        timerJob?.cancel()
        _uiState.update {
            val audio = recorder.recordedAudio.value
            it.copy(
                status = RecordingStatus.Completed,
                hasSavedAudio = audio != null,
                audioSource = audio?.source,
                audioDisplayName = audio?.displayName.orEmpty(),
                audioSizeBytes = audio?.sizeBytes ?: 0
            )
        }
    }

    fun discardRecording() {
        if (_uiState.value.isDownloading) return
        if (_uiState.value.status == RecordingStatus.Ready) return
        recorder.discard()
        timerJob?.cancel()
        _uiState.value = RecordingUiState()
    }

    /** Clears temporary audio and all recording choices at a consultation boundary. */
    fun resetForNewConsultation() {
        recorder.discard()
        timerJob?.cancel()
        _uiState.value = RecordingUiState()
    }

    fun togglePlayback() {
        if (!_uiState.value.hasSavedAudio) return
        if (_uiState.value.isPlaying) recorder.stopPlayback() else recorder.play()
    }

    fun stopPlayback() {
        recorder.stopPlayback()
    }

    fun recordedAudioPath(): String? = recorder.recordedAudio.value?.localPath

    fun recordedAudio() = recorder.recordedAudio.value

    fun downloadAudio(destinationUri: String) {
        val state = _uiState.value
        if (destinationUri.isBlank() || state.isDownloading || state.isImporting) return
        val audio = recorder.recordedAudio.value
        if (state.status != RecordingStatus.Completed || audio == null) {
            _uiState.update { it.copy(downloadError = "Finish recording before downloading.") }
            return
        }
        val exporter = audioExporter ?: run {
            _uiState.update { it.copy(downloadError = "Recording download is unavailable.") }
            return
        }
        stopPlayback()
        _uiState.update { it.copy(isDownloading = true, downloadMessage = null, downloadError = null) }
        val operation: suspend () -> Unit = {
            try {
                when (val result = exporter.export(audio, destinationUri)) {
                    is RecordingAudioExportResult.Success -> _uiState.update {
                        it.copy(downloadMessage = "Recording downloaded as a WAV file.")
                    }
                    is RecordingAudioExportResult.Failure -> _uiState.update {
                        it.copy(downloadError = result.message)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(downloadError = error.message ?: "Recording download failed. Please try again.") }
            } finally {
                _uiState.update { it.copy(isDownloading = false) }
            }
        }
        if (exportAsynchronously) viewModelScope.launch { operation() } else runBlocking { operation() }
    }

    suspend fun restoreSavedRecording(saved: RestoredSavedRecording): Boolean {
        when (val result = recorder.restoreAudio(saved.audio)) {
            is AudioImportResult.Success -> {
                timerJob?.cancel()
                _uiState.value = RecordingUiState(
                    status = RecordingStatus.Completed,
                    elapsedSeconds = ((result.audio.durationMillis + 999L) / 1_000L).toInt(),
                    hasSavedAudio = true,
                    audioSource = result.audio.source,
                    audioDisplayName = result.audio.displayName,
                    audioSizeBytes = result.audio.sizeBytes,
                    transcriptionLanguage = saved.recording.language,
                    transcriptionEngine = saved.recording.engine,
                    hasOnlineProcessingConsent = false
                )
                return true
            }
            is AudioImportResult.Failure -> {
                _uiState.update { it.copy(importError = result.message) }
                return false
            }
        }
    }

    fun importAudio(sourceUri: String) {
        if (_uiState.value.isDownloading) return
        if (_uiState.value.status in setOf(RecordingStatus.Recording, RecordingStatus.Paused)) return
        if (sourceUri.isBlank()) return
        stopPlayback()
        val previousAudio = recorder.recordedAudio.value
        _uiState.update { it.copy(isImporting = true, importError = null) }
        viewModelScope.launch {
            when (val result = recorder.importAudio(sourceUri)) {
                is AudioImportResult.Success -> {
                    val audio = result.audio
                    _uiState.update {
                        it.copy(
                            status = RecordingStatus.Completed,
                            elapsedSeconds = ((audio.durationMillis + 999L) / 1_000L).toInt(),
                            amplitude = 0f,
                            hasSavedAudio = true,
                            audioSource = audio.source,
                            audioDisplayName = audio.displayName,
                            audioSizeBytes = audio.sizeBytes,
                            isPlaying = false,
                            isImporting = false,
                            importError = null,
                            downloadMessage = null,
                            downloadError = null
                        )
                    }
                }
                is AudioImportResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            status = if (previousAudio == null) {
                                RecordingStatus.Ready
                            } else {
                                RecordingStatus.Completed
                            },
                            elapsedSeconds = previousAudio?.durationMillis?.let { duration ->
                                ((duration + 999L) / 1_000L).toInt()
                            } ?: 0,
                            hasSavedAudio = previousAudio != null,
                            audioSource = previousAudio?.source,
                            audioDisplayName = previousAudio?.displayName.orEmpty(),
                            audioSizeBytes = previousAudio?.sizeBytes ?: 0,
                            isImporting = false,
                            importError = result.message
                        )
                    }
                }
            }
        }
    }

    fun setTranscriptionLanguage(language: TranscriptionLanguage) {
        _uiState.update { state ->
            state.copy(
                transcriptionLanguage = language,
                transcriptionEngine = if (state.transcriptionEngine.supports(language)) {
                    state.transcriptionEngine
                } else {
                    TranscriptionEngineOption.defaultFor(language)
                }
            )
        }
    }

    fun transcriptionLanguage(): TranscriptionLanguage = _uiState.value.transcriptionLanguage

    fun setTranscriptionEngine(engine: TranscriptionEngineOption) {
        if (!engine.supports(_uiState.value.transcriptionLanguage)) return
        _uiState.update { it.copy(transcriptionEngine = engine) }
    }

    fun transcriptionEngine(): TranscriptionEngineOption = _uiState.value.transcriptionEngine

    fun setOnlineProcessingConsent(hasConsent: Boolean) {
        _uiState.update { it.copy(hasOnlineProcessingConsent = hasConsent) }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _uiState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        if (recorder is AndroidMicrophoneRecorder) recorder.close()
        super.onCleared()
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RecordingViewModel::class.java))
            return RecordingViewModel(
                AndroidMicrophoneRecorder(applicationContext),
                audioExporter = AndroidRecordingAudioExporter(applicationContext)
            ) as T
        }
    }
}
