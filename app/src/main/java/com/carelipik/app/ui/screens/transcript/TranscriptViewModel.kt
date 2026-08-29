package com.carelipik.app.ui.screens.transcript

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.carelipik.app.data.transcription.FakeAudioTranscriptionEngine
import com.carelipik.app.data.transcription.CareLipikTranscriptionEngineResolver
import com.carelipik.app.data.transcription.RuleBasedTranscriptReviewAnalyzer
import com.carelipik.app.data.transcription.LabelledTranscriptSegmentParser
import com.carelipik.app.data.transcription.HttpOnlineTranscriptReviewAnalyzer
import com.carelipik.app.domain.transcription.SpeakerRole
import com.carelipik.app.domain.transcription.OnlineTranscriptReviewAnalyzer
import com.carelipik.app.domain.transcription.OnlineTranscriptReviewResult
import com.carelipik.app.domain.transcription.TranscriptSegment
import com.carelipik.app.domain.transcription.TranscriptSegmentParser
import com.carelipik.app.domain.transcription.TranscriptReviewAnalyzer
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionEngineResolver
import com.carelipik.app.domain.transcription.TranscriptionResult
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.voice.DoctorVoiceRoleMatchResult
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
    private val onlineReviewAnalyzer: OnlineTranscriptReviewAnalyzer? = null,
    private val segmentParser: TranscriptSegmentParser = LabelledTranscriptSegmentParser(),
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
                val onlineReview = if (
                    result is TranscriptionResult.Success && !engine.option.isOffline
                ) {
                    withContext(Dispatchers.IO) {
                        onlineReviewAnalyzer?.analyze(result.transcript, language)
                    }
                } else {
                    null
                }
                applyResult(result, onlineReview)
            }
        } else {
            val result = engine.transcribe(audioPath, language)
            val onlineReview = if (
                result is TranscriptionResult.Success && !engine.option.isOffline
            ) {
                onlineReviewAnalyzer?.analyze(result.transcript, language)
            } else {
                null
            }
            applyResult(result, onlineReview)
        }
    }

    fun retry() {
        sourceAudioPath?.let { transcribe(it, sourceLanguage, sourceEngine) }
    }

    fun setTranscript(transcript: String) {
        _uiState.update {
            val concerns = reviewAnalyzer.analyze(transcript, it.language)
            val segments = segmentParser.parse(transcript)
            it.copy(
                status = TranscriptStatus.Ready,
                transcript = transcript,
                errorMessage = null,
                concerns = concerns,
                confirmedConcernIds = it.confirmedConcernIds.intersect(
                    concerns.mapTo(mutableSetOf()) { concern -> concern.id }
                ),
                segments = segments,
                speakerRoles = rolesFor(segments, it.speakerRoles),
                viewMode = if (segments.hasMultipleSpeakers()) {
                    it.viewMode
                } else {
                    TranscriptViewMode.FullTranscript
                },
                clinicalAnalysisSource = null,
                clinicalAnalysisWarning = if (!it.engine.isOffline) {
                    "Transcript changed. Run online medical term analysis again for updated suggestions."
                } else {
                    null
                }
            )
        }
    }

    fun analyzeTermsOnline() {
        val analyzer = onlineReviewAnalyzer ?: return
        val state = _uiState.value
        if (state.engine.isOffline || state.transcript.isBlank() || state.isAnalyzingTerms) return
        _uiState.update {
            it.copy(isAnalyzingTerms = true, clinicalAnalysisWarning = null)
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                analyzer.analyze(state.transcript, state.language)
            }
            applyOnlineReview(result)
        }
    }

    fun setViewMode(viewMode: TranscriptViewMode) {
        _uiState.update { state ->
            if (viewMode == TranscriptViewMode.Conversation && !state.canShowConversation) {
                state
            } else {
                state.copy(viewMode = viewMode)
            }
        }
    }

    fun assignSpeakerRole(speakerId: String, role: SpeakerRole) {
        if (role == SpeakerRole.Unassigned) return
        _uiState.update { state ->
            if (speakerId !in state.speakerIds) return@update state
            val updatedRoles = state.speakerRoles.toMutableMap()
            updatedRoles.entries
                .filter { it.key != speakerId && it.value == role }
                .forEach { updatedRoles[it.key] = SpeakerRole.Unassigned }
            updatedRoles[speakerId] = role
            if (state.speakerIds.size == 2) {
                val otherSpeaker = state.speakerIds.first { it != speakerId }
                updatedRoles[otherSpeaker] = when (role) {
                    SpeakerRole.Doctor -> SpeakerRole.Patient
                    SpeakerRole.Patient -> SpeakerRole.Doctor
                    SpeakerRole.Unassigned -> SpeakerRole.Unassigned
                }
            }
            state.copy(speakerRoles = updatedRoles, hasAttemptedContinue = false)
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
            val updatedSegments = segmentParser.parse(updatedTranscript)
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
                segments = updatedSegments,
                speakerRoles = rolesFor(updatedSegments, state.speakerRoles),
                viewMode = if (updatedSegments.hasMultipleSpeakers()) {
                    state.viewMode
                } else {
                    TranscriptViewMode.FullTranscript
                },
                hasAttemptedContinue = false,
                clinicalAnalysisSource = null,
                clinicalAnalysisWarning = if (!state.engine.isOffline) {
                    "Transcript changed. Run online medical term analysis again for updated suggestions."
                } else {
                    null
                }
            )
        }
    }

    fun validateForContinue(): Boolean {
        _uiState.update { it.copy(hasAttemptedContinue = true) }
        return _uiState.value.canContinue
    }

    fun transcriptText(): String {
        val state = _uiState.value
        if (state.segments.isEmpty() || state.pendingSpeakerIds.isNotEmpty()) {
            return state.transcript
        }
        return state.segments.joinToString(separator = "\n\n") { segment ->
            val role = state.speakerRoles[segment.speakerId] ?: SpeakerRole.Unassigned
            "${role.displayName}: ${segment.transcript}"
        }
    }

    fun resetForNewConsultation() {
        sourceAudioPath = null
        sourceLanguage = TranscriptionLanguage.English
        sourceEngine = TranscriptionEngineOption.MedAsrEnglish
        _uiState.value = TranscriptUiState()
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TranscriptViewModel::class.java))
            return TranscriptViewModel(
                engineResolver = CareLipikTranscriptionEngineResolver(applicationContext),
                onlineReviewAnalyzer = HttpOnlineTranscriptReviewAnalyzer(
                    backendBaseUrl = com.carelipik.app.BuildConfig.TRANSCRIPTION_BACKEND_URL,
                    allowInsecureLocalhost = com.carelipik.app.BuildConfig.DEBUG
                )
            ) as T
        }
    }

    private fun applyResult(
        result: TranscriptionResult,
        onlineReview: OnlineTranscriptReviewResult? = null
    ) {
        _uiState.value = when (result) {
            is TranscriptionResult.Success -> {
                val fallbackConcerns = reviewAnalyzer.analyze(result.transcript, sourceLanguage)
                val reviewSuccess = onlineReview as? OnlineTranscriptReviewResult.Success
                val reviewFailure = onlineReview as? OnlineTranscriptReviewResult.Failure
                TranscriptUiState(
                    status = TranscriptStatus.Ready,
                    transcript = result.transcript,
                    language = sourceLanguage,
                    engine = sourceEngine,
                    concerns = reviewSuccess?.concerns ?: fallbackConcerns,
                    segments = result.segments.ifEmpty { segmentParser.parse(result.transcript) },
                    speakerRoles = rolesFor(
                        segments = result.segments.ifEmpty { segmentParser.parse(result.transcript) },
                        doctorVoiceMatch = result.doctorVoiceMatch
                    ),
                    doctorVoiceMatch = result.doctorVoiceMatch,
                    speakerSeparationWarning = result.speakerSeparationWarning,
                    clinicalAnalysisSource = reviewSuccess?.sourceName,
                    clinicalAnalysisWarning = when {
                        reviewFailure != null -> reviewFailure.message +
                            " Limited offline review is shown instead."
                        reviewSuccess != null && !reviewSuccess.codesVerified ->
                            "AI found candidate terms, but medicine salts and terminology codes " +
                                "still require doctor verification."
                        else -> null
                    }
                )
            }
            is TranscriptionResult.Failure -> TranscriptUiState(
                status = TranscriptStatus.Error,
                errorMessage = result.message,
                language = sourceLanguage,
                engine = sourceEngine
            )
        }
    }

    private fun applyOnlineReview(result: OnlineTranscriptReviewResult) {
        _uiState.update { state ->
            when (result) {
                is OnlineTranscriptReviewResult.Success -> state.copy(
                    concerns = result.concerns,
                    confirmedConcernIds = emptySet(),
                    clinicalAnalysisSource = result.sourceName,
                    clinicalAnalysisWarning = if (result.codesVerified) {
                        null
                    } else {
                        "AI found candidate terms, but medicine salts and terminology codes " +
                            "still require doctor verification."
                    },
                    isAnalyzingTerms = false,
                    hasAttemptedContinue = false
                )
                is OnlineTranscriptReviewResult.Failure -> state.copy(
                    clinicalAnalysisWarning = result.message +
                        " Existing review suggestions were kept.",
                    isAnalyzingTerms = false
                )
            }
        }
    }

    private fun rolesFor(
        segments: List<TranscriptSegment>,
        existing: Map<String, SpeakerRole> = emptyMap(),
        doctorVoiceMatch: DoctorVoiceRoleMatchResult? = null
    ): Map<String, SpeakerRole> = segments
        .map { it.speakerId }
        .distinct()
        .associateWith { speakerId ->
            when {
                doctorVoiceMatch is DoctorVoiceRoleMatchResult.Matched &&
                    speakerId == doctorVoiceMatch.match.doctorSpeakerId -> SpeakerRole.Doctor
                doctorVoiceMatch is DoctorVoiceRoleMatchResult.Matched &&
                    segments.map { it.speakerId }.distinct().size == 2 -> SpeakerRole.Patient
                speakerId == "doctor" -> SpeakerRole.Doctor
                speakerId == "patient" -> SpeakerRole.Patient
                else -> existing[speakerId] ?: SpeakerRole.Unassigned
            }
        }

    private fun List<TranscriptSegment>.hasMultipleSpeakers(): Boolean =
        map { it.speakerId }.distinct().size >= 2
}
