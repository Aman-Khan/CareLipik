package com.carelipik.app.ui.screens.savedrecordings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carelipik.app.data.local.EncryptedSavedRecordingRepository
import com.carelipik.app.domain.model.RecordedAudio
import com.carelipik.app.domain.model.RestoredSavedRecording
import com.carelipik.app.domain.model.SavedRecording
import com.carelipik.app.domain.repository.SavedRecordingRepository
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class SavedRecordingsUiState(
    val recordings: List<SavedRecording> = emptyList(),
    val isBusy: Boolean = false,
    val error: String? = null
)

class SavedRecordingsViewModel(
    private val repository: SavedRecordingRepository,
    private val processAsynchronously: Boolean = true,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val currentTimeMillis: () -> Long = System::currentTimeMillis
) : ViewModel() {
    private val _uiState = MutableStateFlow(SavedRecordingsUiState())
    val uiState = _uiState.asStateFlow()
    private var activeRecordingId: String? = null

    fun startNewConsultation() {
        activeRecordingId = null
        _uiState.update { it.copy(error = null) }
    }

    fun refresh() = runOperation {
        _uiState.update { it.copy(recordings = repository.list()) }
    }

    fun save(
        audio: RecordedAudio?,
        patientName: String,
        patientAge: String,
        visitReason: String,
        language: TranscriptionLanguage,
        engine: TranscriptionEngineOption,
        hasRecordingConsent: Boolean,
        onSaved: () -> Unit
    ) = runOperation {
        require(audio != null) { "Finish recording before saving for later." }
        require(patientName.isNotBlank()) { "Enter a patient name or reference before saving." }
        require(hasRecordingConsent) { "Recording consent is required before saving." }
        require(engine.supports(language)) { "Choose an engine that supports the conversation language." }
        val recording = SavedRecording(
            id = activeRecordingId ?: newId(),
            savedAtMillis = currentTimeMillis(),
            patientName = patientName.trim(),
            patientAge = patientAge.trim(),
            visitReason = visitReason.trim(),
            language = language,
            engine = engine,
            audioDisplayName = audio.displayName,
            durationMillis = audio.durationMillis,
            audioSource = audio.source,
            hasRecordingConsent = hasRecordingConsent
        )
        repository.save(recording, audio)
        activeRecordingId = recording.id
        onSaved()
    }

    fun resume(id: String, onResume: suspend (RestoredSavedRecording) -> Boolean) = runOperation {
        val restored = repository.restore(id)
        check(onResume(restored)) { "The saved recording could not be loaded. Please try again." }
        activeRecordingId = id
    }

    fun delete(id: String) = runOperation {
        repository.delete(id)
        if (activeRecordingId == id) activeRecordingId = null
        _uiState.update { it.copy(recordings = repository.list()) }
    }

    /** Called only after approved documentation has been successfully persisted. */
    fun completeConsultation() {
        val id = activeRecordingId ?: return
        runOperation {
            repository.delete(id)
            activeRecordingId = null
        }
    }

    private fun runOperation(operation: suspend () -> Unit) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, error = null) }
        val guarded: suspend () -> Unit = {
            try {
                operation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(error = error.message ?: "The recording could not be saved or opened.") }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
        if (processAsynchronously) viewModelScope.launch { guarded() } else runBlocking { guarded() }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SavedRecordingsViewModel::class.java))
            return SavedRecordingsViewModel(EncryptedSavedRecordingRepository(applicationContext)) as T
        }
    }
}
