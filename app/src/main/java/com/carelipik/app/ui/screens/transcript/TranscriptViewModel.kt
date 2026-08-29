package com.carelipik.app.ui.screens.transcript

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.carelipik.app.data.transcription.FakeAudioTranscriptionEngine
import com.carelipik.app.data.transcription.CareLipikTranscriptionEngineResolver
import com.carelipik.app.data.transcription.RuleBasedTranscriptReviewAnalyzer
import com.carelipik.app.domain.transcription.TranscriptReviewAnalyzer
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionEngineResolver
import com.carelipik.app.domain.transcription.TranscriptionResult
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TranscriptViewModel(
    private val engineResolver: TranscriptionEngineResolver = TranscriptionEngineResolver {
        FakeAudioTranscriptionEngine()
    },
    private val reviewAnalyzer: TranscriptReviewAnalyzer = RuleBasedTranscriptReviewAnalyzer(),
    private val processAsynchronously: Boolean = true
) : ViewModel() {
    private val _uiState = MutableStateFlow(TranscriptUiState())
    val uiState: StateFlow<TranscriptUiState> = _uiState.asStateFlow()
    private var sourceAudioPath: String? = null
    private var sourceLanguage: TranscriptionLanguage = TranscriptionLanguage.English
    private var sourceEngine: TranscriptionEngineOption = TranscriptionEngineOption.MedAsrEnglish

    fun transcribe(
        audioPath: String,
        language: TranscriptionLanguage = TranscriptionLanguage.English,
        engineOption: TranscriptionEngineOption = TranscriptionEngineOption.defaultFor(language)
    ) {
        require(engineOption.supports(language)) {
            "${engineOption.displayName} does not support ${language.displayName}."
        }
        sourceAudioPath = audioPath
        sourceLanguage = language
        sourceEngine = engineOption
        _uiState.value = TranscriptUiState(
            status = TranscriptStatus.Processing,
            language = language,
            engine = engineOption
        )
        val engine = engineResolver.resolve(engineOption)
        if (processAsynchronously) {
            viewModelScope.launch {
                val dispatcher = if (engine.option.isOffline) Dispatchers.Default else Dispatchers.IO
                val result = withContext(dispatcher) {
                    engine.transcribe(audioPath, language)
                }
                applyResult(result)
            }
        } else {
            applyResult(engine.transcribe(audioPath, language))
        }
    }

    fun retry() {
        sourceAudioPath?.let { transcribe(it, sourceLanguage, sourceEngine) }
    }

    fun setTranscript(transcript: String) {
        _uiState.update {
            val concerns = reviewAnalyzer.analyze(transcript, it.language)
            it.copy(
                status = TranscriptStatus.Ready,
                transcript = transcript,
                errorMessage = null,
                concerns = concerns,
                confirmedConcernIds = it.confirmedConcernIds.intersect(
                    concerns.mapTo(mutableSetOf()) { concern -> concern.id }
                )
            )
        }
    }

    fun confirmConcern(concernId: String) {
        _uiState.update { state ->
            if (state.concerns.none { it.id == concernId }) state else state.copy(
                confirmedConcernIds = state.confirmedConcernIds + concernId
            )
        }
    }

    fun applySuggestedReplacement(concernId: String) {
        _uiState.update { state ->
            val concern = state.concerns.firstOrNull { it.id == concernId } ?: return@update state
            val replacement = concern.suggestedReplacement ?: return@update state
            if (
                concern.startIndex !in 0..state.transcript.length ||
                concern.endIndexExclusive !in 0..state.transcript.length ||
                concern.startIndex >= concern.endIndexExclusive
            ) {
                return@update state
            }
            val updatedTranscript = state.transcript.replaceRange(
                concern.startIndex,
                concern.endIndexExclusive,
                replacement
            )
            val updatedConcerns = reviewAnalyzer.analyze(updatedTranscript, state.language)
            val replacementEnd = concern.startIndex + replacement.length
            val replacementConcern = updatedConcerns.firstOrNull {
                it.startIndex == concern.startIndex &&
                    it.endIndexExclusive == replacementEnd &&
                    it.text.equals(replacement, ignoreCase = true)
            }
            state.copy(
                transcript = updatedTranscript,
                concerns = updatedConcerns,
                confirmedConcernIds = buildSet {
                    addAll(
                        state.confirmedConcernIds.intersect(
                            updatedConcerns.mapTo(mutableSetOf()) { it.id }
                        )
                    )
                    replacementConcern?.id?.let(::add)
                },
                hasAttemptedContinue = false
            )
        }
    }

    fun validateForContinue(): Boolean {
        _uiState.update { it.copy(hasAttemptedContinue = true) }
        return _uiState.value.canContinue
    }

    fun transcriptText(): String = _uiState.value.transcript

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TranscriptViewModel::class.java))
            return TranscriptViewModel(
                CareLipikTranscriptionEngineResolver(applicationContext)
            ) as T
        }
    }

    private fun applyResult(result: TranscriptionResult) {
        _uiState.value = when (result) {
            is TranscriptionResult.Success -> TranscriptUiState(
                status = TranscriptStatus.Ready,
                transcript = result.transcript,
                language = sourceLanguage,
                engine = sourceEngine,
                concerns = reviewAnalyzer.analyze(result.transcript, sourceLanguage)
            )
            is TranscriptionResult.Failure -> TranscriptUiState(
                status = TranscriptStatus.Error,
                errorMessage = result.message,
                language = sourceLanguage,
                engine = sourceEngine
            )
        }
    }
}
