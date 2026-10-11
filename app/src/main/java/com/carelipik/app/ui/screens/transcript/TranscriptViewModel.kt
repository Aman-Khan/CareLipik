package com.carelipik.app.ui.screens.transcript

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.carelipik.app.data.transcription.FakeAudioTranscriptionEngine
import com.carelipik.app.data.transcription.CareLipikTranscriptionEngineResolver
import com.carelipik.app.data.transcription.RuleBasedTranscriptReviewAnalyzer
import com.carelipik.app.data.transcription.ApolloHybridTranscriptReviewAnalyzer
import com.carelipik.app.data.transcription.ApolloOnnxMedicalNamedEntityRecognizer
import com.carelipik.app.data.transcription.LabelledTranscriptSegmentParser
import com.carelipik.app.data.transcription.HttpOnlineTranscriptReviewAnalyzer
import com.carelipik.app.data.transcription.DirectGeminiTranscriptReviewAnalyzer
import com.carelipik.app.data.transcription.PreferDeviceKeyTranscriptReviewAnalyzer
import com.carelipik.app.data.local.DeviceApiKeyProvider
import com.carelipik.app.data.local.EncryptedTranscriptCorrectionRepository
import com.carelipik.app.domain.repository.ApiProvider
import com.carelipik.app.domain.repository.TranscriptCorrectionRepository
import com.carelipik.app.domain.training.TranscriptCorrectionLabel
import com.carelipik.app.domain.transcription.SpeakerRole
import com.carelipik.app.domain.transcription.OnlineTranscriptReviewAnalyzer
import com.carelipik.app.domain.transcription.OnlineTranscriptReviewResult
import com.carelipik.app.domain.transcription.TranscriptConcern
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
import java.util.UUID

class TranscriptViewModel(
    private val engineResolver: TranscriptionEngineResolver = TranscriptionEngineResolver {
        FakeAudioTranscriptionEngine()
    },
    private val reviewAnalyzer: TranscriptReviewAnalyzer = RuleBasedTranscriptReviewAnalyzer(),
    private val onlineReviewAnalyzer: OnlineTranscriptReviewAnalyzer? = null,
    private val segmentParser: TranscriptSegmentParser = LabelledTranscriptSegmentParser(),
    private val correctionRepository: TranscriptCorrectionRepository? = null,
    private val processAsynchronously: Boolean = true
) : ViewModel() {
    private val _uiState = MutableStateFlow(TranscriptUiState())
    val uiState: StateFlow<TranscriptUiState> = _uiState.asStateFlow()
    private var sourceAudioPath: String? = null
    private var sourceAudioReferenceId: String? = null
    private var sourceLanguage: TranscriptionLanguage = TranscriptionLanguage.English
    private var sourceEngine: TranscriptionEngineOption = TranscriptionEngineOption.AssemblyAiUniversal

    fun transcribe(
        audioPath: String,
        language: TranscriptionLanguage = TranscriptionLanguage.English,
        engineOption: TranscriptionEngineOption = TranscriptionEngineOption.defaultFor(language)
    ) {
        require(engineOption.supports(language)) {
            "${engineOption.displayName} does not support ${language.displayName}."
        }
        if (sourceAudioPath != audioPath) {
            sourceAudioReferenceId = UUID.randomUUID().toString()
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
            val result = engine.transcribe(audioPath, language)
            applyResult(result)
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
                clinicalAnalysisWarning = if (it.clinicalAnalysisSource != null) {
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
        if (state.transcript.isBlank() || state.isAnalyzingTerms) return
        if (!state.hasOnlineAnalysisConsent) {
            _uiState.update {
                it.copy(
                    clinicalAnalysisWarning =
                        "Confirm consent before sending the reviewed transcript for online analysis."
                )
            }
            return
        }
        _uiState.update {
            it.copy(isAnalyzingTerms = true, clinicalAnalysisWarning = null)
        }
        if (processAsynchronously) {
            viewModelScope.launch {
                val result = withContext(Dispatchers.IO) {
                    analyzer.analyze(state.transcript, state.language)
                }
                applyOnlineReview(result, state.transcript)
            }
        } else {
            applyOnlineReview(
                result = analyzer.analyze(state.transcript, state.language),
                analyzedTranscript = state.transcript
            )
        }
    }

    fun setOnlineAnalysisConsent(hasConsent: Boolean) {
        _uiState.update { state ->
            if (state.isAnalyzingTerms) state else state.copy(
                hasOnlineAnalysisConsent = hasConsent,
                clinicalAnalysisWarning = if (
                    hasConsent && state.clinicalAnalysisWarning?.startsWith("Confirm consent") == true
                ) {
                    null
                } else {
                    state.clinicalAnalysisWarning
                }
            )
        }
    }

    fun setTrainingDataConsent(hasConsent: Boolean) {
        _uiState.update {
            it.copy(
                hasTrainingDataConsent = hasConsent,
                trainingDataMessage = if (hasConsent) {
                    "Confirmed corrections will be encrypted and kept only on this device."
                } else {
                    null
                }
            )
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
            if (role in setOf(SpeakerRole.Doctor, SpeakerRole.Patient)) {
                updatedRoles.entries
                    .filter { it.key != speakerId && it.value == role }
                    .forEach { updatedRoles[it.key] = SpeakerRole.Unassigned }
            }
            updatedRoles[speakerId] = role
            if (state.speakerIds.size == 2) {
                val otherSpeaker = state.speakerIds.first { it != speakerId }
                updatedRoles[otherSpeaker] = when (role) {
                    SpeakerRole.Doctor -> SpeakerRole.Patient
                    SpeakerRole.Patient -> SpeakerRole.Doctor
                    SpeakerRole.Unassigned,
                    SpeakerRole.OtherParticipant,
                    SpeakerRole.Noise -> updatedRoles[otherSpeaker] ?: SpeakerRole.Unassigned
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

    fun selectConcern(concernId: String) {
        _uiState.update { state ->
            if (state.concerns.none { it.id == concernId }) state else state.copy(
                selectedConcernId = concernId
            )
        }
    }

    fun updateConcern(concernId: String, replacement: String) {
        replaceConcern(concernId, replacement.trim())
    }

    fun applySuggestedReplacement(concernId: String) {
        val concern = _uiState.value.concerns.firstOrNull { it.id == concernId } ?: return
        replaceConcern(concernId, concern.suggestedReplacement.orEmpty())
    }

    private fun replaceConcern(concernId: String, replacement: String) {
        if (replacement.isBlank()) return
        var correctionToSave: TranscriptCorrectionLabel? = null
        var correctionAudioPath: String? = null
        _uiState.update { state ->
            val concern = state.concerns.firstOrNull { it.id == concernId } ?: return@update state
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
            if (
                state.hasTrainingDataConsent &&
                !concern.text.equals(replacement, ignoreCase = false)
            ) {
                val contextRange = correctionContextRange(state.transcript, concern)
                val asrContext = state.transcript.substring(contextRange).trim()
                val relativeStart = concern.startIndex - contextRange.first
                val relativeEnd = relativeStart + (concern.endIndexExclusive - concern.startIndex)
                val correctedContext = state.transcript.substring(contextRange)
                    .replaceRange(relativeStart, relativeEnd, replacement)
                    .trim()
                val id = UUID.randomUUID().toString()
                val audioReferenceId = sourceAudioReferenceId ?: UUID.randomUUID().toString()
                correctionToSave = TranscriptCorrectionLabel(
                    id = id,
                    createdAtMillis = System.currentTimeMillis(),
                    audioClipReference = "encrypted-local-reference:$audioReferenceId",
                    asrText = asrContext,
                    correctedText = correctedContext,
                    originalTerm = concern.text,
                    correctedTerm = replacement,
                    language = state.language.trainingLanguageTag(),
                    transcriptionEngine = state.engine.name
                )
                correctionAudioPath = sourceAudioPath
            }
            val updatedConcerns = reviewAnalyzer.analyze(updatedTranscript, state.language)
            val updatedSegments = segmentParser.parse(updatedTranscript)
            val replacementEnd = concern.startIndex + replacement.length
            val replacementConcern = updatedConcerns.firstOrNull {
                it.startIndex == concern.startIndex &&
                    it.endIndexExclusive == replacementEnd &&
                    it.text.equals(replacement, ignoreCase = true)
            }
            val replacementConcernIds = updatedConcerns
                .filter {
                    it.startIndex >= concern.startIndex &&
                        it.endIndexExclusive <= replacementEnd
                }
                .mapTo(mutableSetOf()) { it.id }
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
                    addAll(replacementConcernIds)
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
                selectedConcernId = replacementConcern?.id,
                clinicalAnalysisWarning = if (state.clinicalAnalysisSource != null) {
                    "Transcript changed. Run online medical term analysis again for updated suggestions."
                } else {
                    null
                }
            )
        }
        correctionToSave?.let { label ->
            val audioPath = correctionAudioPath
            val repository = correctionRepository
            if (audioPath == null || repository == null) {
                _uiState.update {
                    it.copy(
                        trainingDataMessage =
                            "Correction applied, but training data could not be saved."
                    )
                }
            } else {
                val saveCorrection = {
                    runCatching { repository.save(label, audioPath) }
                        .onSuccess {
                            _uiState.update { state ->
                                state.copy(
                                    trainingDataMessage =
                                        "Doctor-confirmed correction saved securely on this device."
                                )
                            }
                        }
                        .onFailure {
                            _uiState.update { state ->
                                state.copy(
                                    trainingDataMessage =
                                        "Correction applied, but training data could not be saved."
                                )
                            }
                        }
                }
                if (processAsynchronously) {
                    viewModelScope.launch(Dispatchers.IO) { saveCorrection() }
                } else {
                    saveCorrection()
                }
            }
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
        return state.segments.filterNot { segment ->
            state.speakerRoles[segment.speakerId] == SpeakerRole.Noise
        }.joinToString(separator = "\n\n") { segment ->
            val role = state.speakerRoles[segment.speakerId] ?: SpeakerRole.Unassigned
            "${role.displayName}: ${segment.transcript}"
        }
    }

    fun resetForNewConsultation() {
        sourceAudioPath = null
        sourceAudioReferenceId = null
        sourceLanguage = TranscriptionLanguage.English
        sourceEngine = TranscriptionEngineOption.AssemblyAiUniversal
        _uiState.value = TranscriptUiState()
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TranscriptViewModel::class.java))
            val keyProvider = DeviceApiKeyProvider(applicationContext)
            return TranscriptViewModel(
                engineResolver = CareLipikTranscriptionEngineResolver(applicationContext),
                reviewAnalyzer = ApolloHybridTranscriptReviewAnalyzer(
                    recognizer = ApolloOnnxMedicalNamedEntityRecognizer(applicationContext)
                ),
                onlineReviewAnalyzer = PreferDeviceKeyTranscriptReviewAnalyzer(
                    hasDeviceKey = { keyProvider.get(ApiProvider.Gemini) != null },
                    direct = DirectGeminiTranscriptReviewAnalyzer {
                        keyProvider.get(ApiProvider.Gemini)
                    },
                    fallback = HttpOnlineTranscriptReviewAnalyzer(
                        backendBaseUrl = com.carelipik.app.BuildConfig.TRANSCRIPTION_BACKEND_URL,
                        allowInsecureLocalhost = com.carelipik.app.BuildConfig.DEBUG
                    )
                ),
                correctionRepository = EncryptedTranscriptCorrectionRepository(applicationContext)
            ) as T
        }
    }

    private fun applyResult(result: TranscriptionResult) {
        _uiState.value = when (result) {
            is TranscriptionResult.Success -> {
                val fallbackConcerns = reviewAnalyzer.analyze(result.transcript, sourceLanguage)
                TranscriptUiState(
                    status = TranscriptStatus.Ready,
                    transcript = result.transcript,
                    language = sourceLanguage,
                    engine = sourceEngine,
                    concerns = fallbackConcerns,
                    segments = result.segments.ifEmpty { segmentParser.parse(result.transcript) },
                    speakerRoles = rolesFor(
                        segments = result.segments.ifEmpty { segmentParser.parse(result.transcript) },
                        doctorVoiceMatch = result.doctorVoiceMatch
                    ),
                    doctorVoiceMatch = result.doctorVoiceMatch,
                    speakerSeparationWarning = result.speakerSeparationWarning,
                    clinicalAnalysisSource = null,
                    clinicalAnalysisWarning = null
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

    private fun applyOnlineReview(
        result: OnlineTranscriptReviewResult,
        analyzedTranscript: String
    ) {
        _uiState.update { state ->
            if (state.transcript != analyzedTranscript) {
                return@update state.copy(
                    clinicalAnalysisSource = null,
                    clinicalAnalysisWarning =
                        "Transcript changed while online analysis was running. Analyze again.",
                    isAnalyzingTerms = false
                )
            }
            when (result) {
                is OnlineTranscriptReviewResult.Success -> {
                    val mergedConcerns = mergeConcerns(
                        transcript = state.transcript,
                        language = state.language,
                        onlineConcerns = result.concerns
                    )
                    state.copy(
                        concerns = mergedConcerns,
                        confirmedConcernIds = state.confirmedConcernIds.intersect(
                            mergedConcerns.mapTo(mutableSetOf()) { it.id }
                        ),
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
                }
                is OnlineTranscriptReviewResult.Failure -> state.copy(
                    clinicalAnalysisWarning = result.message +
                        " Existing review suggestions were kept.",
                    isAnalyzingTerms = false
                )
            }
        }
    }

    private fun mergeConcerns(
        transcript: String,
        language: TranscriptionLanguage,
        onlineConcerns: List<TranscriptConcern>
    ): List<TranscriptConcern> {
        val offlineConcerns = reviewAnalyzer.analyze(transcript, language)
        val prioritized = buildList {
            addAll(offlineConcerns.filter { it.suggestedReplacement != null })
            addAll(onlineConcerns)
            addAll(offlineConcerns.filter { it.suggestedReplacement == null })
        }
        return prioritized.fold(mutableListOf<TranscriptConcern>()) { accepted, candidate ->
            val overlaps = accepted.any {
                it.startIndex < candidate.endIndexExclusive &&
                    candidate.startIndex < it.endIndexExclusive
            }
            if (!overlaps) accepted += candidate
            accepted
        }.sortedWith(
            compareBy<TranscriptConcern> { it.startIndex }
                .thenByDescending { it.endIndexExclusive - it.startIndex }
        )
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

    private fun correctionContextRange(
        transcript: String,
        concern: TranscriptConcern
    ): IntRange {
        val start = (concern.startIndex - 1 downTo 0).firstOrNull {
            transcript[it] in CONTEXT_BOUNDARIES
        }?.plus(1) ?: 0
        val endExclusive = (concern.endIndexExclusive until transcript.length).firstOrNull {
            transcript[it] in CONTEXT_BOUNDARIES
        }?.plus(1) ?: transcript.length
        return start until endExclusive
    }

    private fun TranscriptionLanguage.trainingLanguageTag(): String = when (this) {
        TranscriptionLanguage.Auto -> "und-IN"
        TranscriptionLanguage.English -> "en-IN"
        TranscriptionLanguage.Hindi -> "hi-IN"
        TranscriptionLanguage.Hinglish -> "hi-Latn-IN"
    }

    private companion object {
        val CONTEXT_BOUNDARIES = setOf('.', '?', '!', '\n')
    }
}
