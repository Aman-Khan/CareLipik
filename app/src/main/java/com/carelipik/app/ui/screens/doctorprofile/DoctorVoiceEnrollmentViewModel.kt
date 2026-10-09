package com.carelipik.app.ui.screens.doctorprofile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carelipik.app.data.audio.AndroidMicrophoneRecorder
import com.carelipik.app.data.voice.LocalDoctorVoiceSampleStore
import com.carelipik.app.domain.recording.ConsultationRecorder
import com.carelipik.app.domain.recording.AudioImportResult
import com.carelipik.app.domain.voice.DoctorVoiceEnrollmentRules
import com.carelipik.app.domain.voice.DoctorVoiceSampleSaveResult
import com.carelipik.app.domain.voice.DoctorVoiceSampleStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DoctorVoiceEnrollmentViewModel(
    private val recorder: ConsultationRecorder,
    private val sampleStore: DoctorVoiceSampleStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(DoctorVoiceEnrollmentUiState())
    val uiState: StateFlow<DoctorVoiceEnrollmentUiState> = _uiState.asStateFlow()
    private var timerJob: Job? = null
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            sampleStore.load()?.let { sample ->
                _uiState.update {
                    it.copy(
                        status = DoctorVoiceEnrollmentStatus.Enrolled,
                        sampleDurationMillis = sample.durationMillis,
                        hasExistingSample = true,
                        enrolledSampleCount = sample.enrolledSampleCount
                    )
                }
            }
        }
        viewModelScope.launch {
            recorder.amplitude.collect { amplitude ->
                _uiState.update { it.copy(amplitude = amplitude) }
            }
        }
        viewModelScope.launch {
            recorder.recordedAudio.collect { audio ->
                if (audio != null && _uiState.value.status == DoctorVoiceEnrollmentStatus.Saving) {
                    persist(audio)
                }
            }
        }
    }

    fun startRecording() {
        if (_uiState.value.status in setOf(
                DoctorVoiceEnrollmentStatus.Recording,
                DoctorVoiceEnrollmentStatus.Saving
            )
        ) return
        recorder.start()
        _uiState.update {
            it.copy(
                status = DoctorVoiceEnrollmentStatus.Recording,
                amplitude = 0f,
                elapsedSeconds = 0,
                message = null
            )
        }
        startTimer()
    }

    fun stopAndSave() {
        if (_uiState.value.status != DoctorVoiceEnrollmentStatus.Recording) return
        timerJob?.cancel()
        recorder.stop()
        _uiState.update {
            it.copy(
                status = DoctorVoiceEnrollmentStatus.Saving,
                amplitude = 0f,
                message = null
            )
        }
        recorder.recordedAudio.value?.let(::persist)
    }

    fun importSample(sourceUri: String) {
        if (
            sourceUri.isBlank() || _uiState.value.status in setOf(
                DoctorVoiceEnrollmentStatus.Recording,
                DoctorVoiceEnrollmentStatus.Saving
            )
        ) return
        _uiState.update {
            it.copy(
                status = DoctorVoiceEnrollmentStatus.Saving,
                amplitude = 0f,
                message = null
            )
        }
        viewModelScope.launch {
            when (val result = recorder.importAudio(sourceUri)) {
                is AudioImportResult.Success -> persist(result.audio)
                is AudioImportResult.Failure -> {
                    val existingSample = sampleStore.load()
                    _uiState.update {
                        it.copy(
                            status = DoctorVoiceEnrollmentStatus.Error,
                            hasExistingSample = existingSample != null,
                            enrolledSampleCount = existingSample?.enrolledSampleCount ?: 0,
                            sampleDurationMillis = existingSample?.durationMillis
                                ?: it.sampleDurationMillis,
                            message = result.message
                        )
                    }
                    recorder.discard()
                }
            }
        }
    }

    fun deleteSample() {
        if (_uiState.value.status in setOf(
                DoctorVoiceEnrollmentStatus.Recording,
                DoctorVoiceEnrollmentStatus.Saving
            )
        ) return
        viewModelScope.launch {
            sampleStore.delete()
            _uiState.value = DoctorVoiceEnrollmentUiState()
        }
    }

    private fun persist(audio: com.carelipik.app.domain.model.RecordedAudio) {
        if (saveJob?.isActive == true) return
        saveJob = viewModelScope.launch {
            when (val result = sampleStore.save(audio)) {
                is DoctorVoiceSampleSaveResult.Success -> {
                    _uiState.update {
                        it.copy(
                            status = DoctorVoiceEnrollmentStatus.Enrolled,
                            sampleDurationMillis = result.sample.durationMillis,
                            hasExistingSample = true,
                            enrolledSampleCount = result.sample.enrolledSampleCount,
                            message = "Voice sample saved privately on this device."
                        )
                    }
                }
                is DoctorVoiceSampleSaveResult.Failure -> {
                    val existingSample = sampleStore.load()
                    _uiState.update {
                        it.copy(
                            status = DoctorVoiceEnrollmentStatus.Error,
                            hasExistingSample = existingSample != null,
                            enrolledSampleCount = existingSample?.enrolledSampleCount ?: 0,
                            sampleDurationMillis = existingSample?.durationMillis
                                ?: it.sampleDurationMillis,
                            message = result.message
                        )
                    }
                }
            }
            recorder.discard()
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _uiState.update { state ->
                    state.copy(elapsedSeconds = state.elapsedSeconds + 1)
                }
                if (
                    _uiState.value.elapsedSeconds >=
                    DoctorVoiceEnrollmentRules.maximumDurationMillis / 1_000L
                ) {
                    stopAndSave()
                    return@launch
                }
            }
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        saveJob?.cancel()
        if (recorder is AndroidMicrophoneRecorder) recorder.close() else recorder.discard()
        super.onCleared()
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DoctorVoiceEnrollmentViewModel::class.java))
            return DoctorVoiceEnrollmentViewModel(
                recorder = AndroidMicrophoneRecorder(applicationContext),
                sampleStore = LocalDoctorVoiceSampleStore(applicationContext)
            ) as T
        }
    }
}
