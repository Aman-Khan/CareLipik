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
import com.carelipik.app.data.transcription.DirectGeminiTranscriptReviewAnalyzer
import com.carelipik.app.data.transcription.PreferDeviceKeyTranscriptReviewAnalyzer
import com.carelipik.app.data.local.DeviceApiKeyProvider
import com.carelipik.app.domain.repository.ApiProvider
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
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.carelipik.app.domain.transcription.ProgressAwareTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionStage
import com.carelipik.app.domain.transcription.CorrectionStatus
import com.carelipik.app.domain.transcription.HybridTranscriptionReview
import com.carelipik.app.domain.transcription.ConsultationAwareTranscriptionEngine
import com.carelipik.app.domain.transcription.TranscriptionConsultationContext

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
    private var sourceSpeakerCount = com.carelipik.app.domain.transcription.SpeakerCount.DEFAULT
    private var sourceConsultationContext = TranscriptionConsultationContext()
    private var transcriptionJob: Job? = null
    @Volatile
    private var transcriptionRequest = 0L
    private val inferenceMutex = Mutex()

    fun transcribe(
        audioPath: String,
        language: TranscriptionLanguage = TranscriptionLanguage.English,
        engineOption: TranscriptionEngineOption = TranscriptionEngineOption.defaultFor(language),
        speakerCount: Int = com.carelipik.app.domain.transcription.SpeakerCount.DEFAULT,
        consultationContext: TranscriptionConsultationContext = TranscriptionConsultationContext()
    ) {
        com.carelipik.app.domain.transcription.SpeakerCount.validate(speakerCount)
        require(engineOption.supports(language)) {
            "${engineOption.displayName} does not support ${language.displayName}."
        }
        transcriptionJob?.cancel()
        val request = ++transcriptionRequest
        sourceAudioPath = audioPath
        sourceLanguage = language
        sourceEngine = engineOption
        sourceSpeakerCount = speakerCount
        sourceConsultationContext = consultationContext.copy(expectedSpeakerCount = speakerCount)
        val requestConsultation = sourceConsultationContext
        _uiState.value = TranscriptUiState(
            status = TranscriptStatus.Processing,
            language = language,
            engine = engineOption,
            transcriptionStage = if (engineOption in setOf(TranscriptionEngineOption.WhisperMedAsrHybrid,
                    TranscriptionEngineOption.LlmGuidedHybrid)) TranscriptionStage.Whisper else null
        )
        if (processAsynchronously) {
            transcriptionJob = viewModelScope.launch {
                val requestContext = coroutineContext
                val dispatcher = if (engineOption.isOffline) Dispatchers.Default else Dispatchers.IO
                val result = inferenceMutex.withLock {
                    withContext(dispatcher) {
                        runTranscription(audioPath, language, engineOption, speakerCount, request, requestConsultation) { requestContext.ensureActive() }
                    }
                }
                if (request == transcriptionRequest) applyResult(result)
            }
        } else {
            applyResult(runTranscription(audioPath, language, engineOption, speakerCount, request, requestConsultation) {})
        }
    }

    private fun runTranscription(
        audioPath: String,
        language: TranscriptionLanguage,
        engineOption: TranscriptionEngineOption,
        speakerCount: Int,
        request: Long,
        consultationContext: TranscriptionConsultationContext,
        checkCancelled: () -> Unit
    ): TranscriptionResult = try {
        checkCancelled()
        val engine = engineResolver.resolve(engineOption, speakerCount)
        val result = if (engine is ConsultationAwareTranscriptionEngine) {
            engine.transcribeWithContext(audioPath, language, consultationContext, { stage ->
                _uiState.update { if (request == transcriptionRequest) it.copy(transcriptionStage = stage) else it }
            }, checkCancelled) { detail ->
                _uiState.update { if (request == transcriptionRequest) it.copy(transcriptionDetail = detail) else it }
            }
        } else if (engine is ProgressAwareTranscriptionEngine) {
            engine.transcribeWithDetails(audioPath, language, { stage ->
                _uiState.update { if (request == transcriptionRequest) it.copy(transcriptionStage = stage) else it }
            }, checkCancelled) { detail ->
                _uiState.update { if (request == transcriptionRequest) it.copy(transcriptionDetail = detail) else it }
            }
        } else engine.transcribe(audioPath, language)
        checkCancelled()
        result
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        TranscriptionResult.Failure(error.message ?: "Transcription could not be completed.")
    }

    fun cancelTranscription() {
        transcriptionJob?.cancel()
        transcriptionRequest++
        _uiState.update {
            it.copy(status = TranscriptStatus.Error, transcriptionStage = null,
                errorMessage = "Transcription cancelled. The recording is retained; retry when ready.")
        }
    }

    fun retry() {
        sourceAudioPath?.let { transcribe(it, sourceLanguage, sourceEngine, sourceSpeakerCount, sourceConsultationContext) }
    }

    fun setTranscript(transcript: String) {
        _uiState.update {
            val concerns = reviewAnalyzer.analyze(transcript, it.language)
            val segments = segmentParser.parse(transcript)
            it.copy(
                status = TranscriptStatus.Ready,
                transcript = transcript,
                hybridReview = if (transcript == it.transcript) it.hybridReview else invalidateHybridReview(it.hybridReview),
                errorMessage = null,
                concerns = concerns,
                confirmedConcernIds = it.confirmedConcernIds.intersect(
                    concerns.mapTo(mutableSetOf()) { concern -> concern.id }
                ),
                segments = segments,
                speakerRoles = rolesFor(segments, it.speakerRoles),
                speakerNames = it.speakerNames.filterKeys { id -> segments.any { it.speakerId == id } },
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

    fun setDisplayedTranscript(transcript: String) {
        if (_uiState.value.isAnalyzingTerms) return
        setTranscript(_uiState.value.labelProjection.restore(transcript))
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
        val request = transcriptionRequest
        if (processAsynchronously) {
            viewModelScope.launch {
                val result = withContext(Dispatchers.IO) {
                    runOnlineReview(analyzer, state)
                }
                if (request == transcriptionRequest) applyOnlineReview(result, state.transcript)
            }
        } else {
            applyOnlineReview(
                result = runOnlineReview(analyzer, state),
                analyzedTranscript = state.transcript
            )
        }
    }

    private fun runOnlineReview(
        analyzer: OnlineTranscriptReviewAnalyzer,
        state: TranscriptUiState
    ): OnlineTranscriptReviewResult = try {
        analyzer.analyze(state.transcript, state.language)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        OnlineTranscriptReviewResult.Failure(error.message ?: "Medical term enhancement could not be completed.")
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
            if (state.isAnalyzingTerms) return@update state
            val updatedRoles = state.speakerRoles.toMutableMap()
            updatedRoles.entries
                .filter { role != SpeakerRole.Other && it.key != speakerId && it.value == role }
                .forEach { updatedRoles[it.key] = SpeakerRole.Unassigned }
            updatedRoles[speakerId] = role
            if (state.speakerIds.size == 2 && role != SpeakerRole.Other) {
                val otherSpeaker = state.speakerIds.first { it != speakerId }
                if (updatedRoles[otherSpeaker] != SpeakerRole.Other) {
                    updatedRoles[otherSpeaker] = when (role) {
                        SpeakerRole.Doctor -> SpeakerRole.Patient
                        SpeakerRole.Patient -> SpeakerRole.Doctor
                        SpeakerRole.Unassigned -> SpeakerRole.Unassigned
                        SpeakerRole.Other -> SpeakerRole.Other
                    }
                }
            }
            state.copy(speakerRoles = updatedRoles, hasAttemptedContinue = false)
        }
    }

    fun setSpeakerName(speakerId: String, name: String) {
        _uiState.update { state ->
            if (speakerId !in state.speakerIds || state.isAnalyzingTerms) state else state.copy(
                speakerNames = state.speakerNames + (speakerId to name.replace('\n', ' ').replace('\r', ' ').replace(':', ' '))
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
            val updatedSegments = segmentParser.parse(updatedTranscript)
            val replacementEnd = concern.startIndex + replacement.length
            val replacementConcern = updatedConcerns.firstOrNull {
                it.startIndex == concern.startIndex &&
                    it.endIndexExclusive == replacementEnd &&
                    it.text.equals(replacement, ignoreCase = true)
            }
            state.copy(
                transcript = updatedTranscript,
                hybridReview = invalidateHybridReview(state.hybridReview),
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
                speakerNames = state.speakerNames.filterKeys { id ->
                    updatedSegments.any { it.speakerId == id }
                },
                viewMode = if (updatedSegments.hasMultipleSpeakers()) {
                    state.viewMode
                } else {
                    TranscriptViewMode.FullTranscript
                },
                hasAttemptedContinue = false,
                clinicalAnalysisSource = null,
                clinicalAnalysisWarning = if (state.clinicalAnalysisSource != null) {
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

    /** Applies a prepared phrase after checking its offsets. Never invokes a transcription model. */
    fun acceptHybridCorrection(id: String) {
        val state = _uiState.value
        val review = state.hybridReview ?: return
        val correction = review.corrections.firstOrNull { it.id == id } ?: return
        if (correction.status != CorrectionStatus.Suggested) return
        val start = correction.transcriptStartIndex ?: return
        val end = correction.transcriptEndIndex ?: return
        if (start !in 0..state.transcript.length || end !in start..state.transcript.length ||
            state.transcript.substring(start, end) != correction.originalText
        ) {
            _uiState.update { it.copy(hybridReview = invalidateHybridReview(review)) }
            return
        }
        val updated = state.transcript.replaceRange(start, end, correction.suggestedText)
        val delta = correction.suggestedText.length - (end - start)
        val corrections = review.corrections.map { other ->
            when {
                other.id == id -> other.copy(status = CorrectionStatus.Accepted)
                other.status !in setOf(CorrectionStatus.Suggested, CorrectionStatus.Unresolved) -> other
                other.transcriptStartIndex == null || other.transcriptEndIndex == null -> other
                other.transcriptStartIndex >= end -> other.copy(
                    transcriptStartIndex = other.transcriptStartIndex + delta,
                    transcriptEndIndex = other.transcriptEndIndex + delta
                )
                other.transcriptEndIndex > start -> other.copy(
                    status = CorrectionStatus.Unresolved, transcriptStartIndex = null, transcriptEndIndex = null
                )
                else -> other
            }
        }
        setTranscript(updated)
        _uiState.update { it.copy(hybridReview = review.copy(corrections = corrections), hasAttemptedContinue = false,
            confirmedConcernIds = it.confirmedConcernIds + it.concerns.filter { concern ->
                concern.startIndex < start + correction.suggestedText.length && concern.endIndexExclusive > start
            }.map { concern -> concern.id }) }
    }

    fun acceptMedAsrCorrection(id: String) {
        val correction = _uiState.value.hybridReview?.corrections?.firstOrNull { it.id == id } ?: return
        val alternative = correction.medAsrSuggestedText ?: return
        if (correction.status != CorrectionStatus.Suggested) return
        _uiState.update { state ->
            state.copy(hybridReview = state.hybridReview?.let { review ->
                review.copy(corrections = review.corrections.map {
                    if (it.id == id) it.copy(suggestedText = alternative) else it
                })
            })
        }
        acceptHybridCorrection(id)
    }

    fun applyManualHybridCorrection(id: String, replacement: String) {
        val state = _uiState.value
        val correction = state.hybridReview?.corrections?.firstOrNull { it.id == id } ?: return
        if (correction.status !in setOf(CorrectionStatus.Suggested, CorrectionStatus.Unresolved) || replacement.length > 2_000) return
        val original = correction.originalText.ifBlank { correction.whisperContext }
        if (original.isBlank()) return
        val existingStart = correction.transcriptStartIndex
        val existingEnd = correction.transcriptEndIndex
        val validOffsets = existingStart != null && existingEnd != null && existingStart >= 0 &&
            existingEnd in existingStart..state.transcript.length && state.transcript.substring(existingStart, existingEnd) == original
        val start = if (validOffsets) existingStart!! else state.transcript.indexOf(original)
        if (start < 0 || (!validOffsets && state.transcript.lastIndexOf(original) != start)) return
        val end = start + original.length
        _uiState.update { current -> current.copy(hybridReview = current.hybridReview?.let { review ->
            review.copy(corrections = review.corrections.map {
                if (it.id == id) it.copy(suggestedText = replacement, originalText = original,
                    transcriptStartIndex = start, transcriptEndIndex = end, status = CorrectionStatus.Suggested) else it
            })
        }) }
        acceptHybridCorrection(id)
    }

    fun rejectHybridCorrection(id: String) {
        _uiState.update { state ->
            val correction = state.hybridReview?.corrections?.firstOrNull { it.id == id }
            val confirmed = state.concerns.filter { concern ->
                val start = correction?.transcriptStartIndex
                val end = correction?.transcriptEndIndex
                start != null && end != null && concern.startIndex < end && concern.endIndexExclusive > start
            }.map { it.id }
            state.copy(confirmedConcernIds = state.confirmedConcernIds + confirmed,
                hybridReview = state.hybridReview?.let { review ->
                review.copy(corrections = review.corrections.map {
                    if (it.id == id && it.status in setOf(CorrectionStatus.Suggested, CorrectionStatus.Unresolved)) {
                        it.copy(status = CorrectionStatus.Rejected)
                    } else it
                })
            })
        }
    }

    private fun invalidateHybridReview(review: HybridTranscriptionReview?): HybridTranscriptionReview? =
        review?.copy(corrections = review.corrections.map {
            if (it.status in setOf(CorrectionStatus.Suggested, CorrectionStatus.Unresolved)) {
                it.copy(status = CorrectionStatus.Unresolved, transcriptStartIndex = null, transcriptEndIndex = null,
                    reasons = (it.reasons + "Transcript changed; review this alternative manually").distinct())
            } else it
        })

    fun transcriptText(): String {
        val state = _uiState.value
        if (state.segments.isEmpty() || state.pendingSpeakerIds.isNotEmpty()) {
            return state.transcript
        }
        return state.segments.joinToString(separator = "\n\n") { segment ->
            "${state.speakerLabel(segment.speakerId)}: ${segment.transcript}"
        }
    }

    fun resetForNewConsultation() {
        transcriptionRequest++
        viewModelScope.coroutineContext.cancelChildren()
        sourceAudioPath = null
        sourceConsultationContext = TranscriptionConsultationContext()
        sourceLanguage = TranscriptionLanguage.English
        sourceEngine = TranscriptionEngineOption.MedAsrEnglish
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
                onlineReviewAnalyzer = PreferDeviceKeyTranscriptReviewAnalyzer(
                    hasDeviceKey = { keyProvider.get(ApiProvider.Gemini) != null },
                    direct = DirectGeminiTranscriptReviewAnalyzer {
                        keyProvider.get(ApiProvider.Gemini)
                    },
                    fallback = HttpOnlineTranscriptReviewAnalyzer(
                        backendBaseUrl = com.carelipik.app.BuildConfig.TRANSCRIPTION_BACKEND_URL,
                        allowInsecureLocalhost = com.carelipik.app.BuildConfig.DEBUG
                    )
                )
            ) as T
        }
    }

    private fun applyResult(result: TranscriptionResult) {
        _uiState.value = when (result) {
            is TranscriptionResult.Success -> {
                val fallbackConcerns = reviewAnalyzer.analyze(result.transcript, sourceLanguage)
                val segments = result.segments.ifEmpty { segmentParser.parse(result.transcript) }
                val actualSpeakerCount = segments.map { it.speakerId }
                    .filterNot { it == "speaker-unknown" }.distinct().size
                val countWarning = if (actualSpeakerCount != sourceSpeakerCount) {
                    "You selected $sourceSpeakerCount speakers, but the transcript contains " +
                        "$actualSpeakerCount speaker groups. Speaker separation is incomplete; " +
                        "review the recording and speaker assignments before continuing."
                } else null
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
                    speakerSeparationWarning = listOfNotNull(result.speakerSeparationWarning, countWarning)
                        .joinToString(" ").ifBlank { null },
                    clinicalAnalysisSource = null,
                    clinicalAnalysisWarning = null,
                    hybridReview = result.hybridReview,
                    sourceAudioPath = sourceAudioPath
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
        .filterNot { it == "speaker-unknown" }
        .distinct()
        .associateWith { speakerId ->
            when {
                doctorVoiceMatch is DoctorVoiceRoleMatchResult.Matched &&
                    speakerId == doctorVoiceMatch.match.doctorSpeakerId -> SpeakerRole.Doctor
                doctorVoiceMatch is DoctorVoiceRoleMatchResult.Matched &&
                    segments.map { it.speakerId }.filterNot { it == "speaker-unknown" }
                        .distinct().size == 2 -> SpeakerRole.Patient
                speakerId == "doctor" -> SpeakerRole.Doctor
                speakerId == "patient" -> SpeakerRole.Patient
                else -> existing[speakerId] ?: SpeakerRole.Unassigned
            }
        }

    private fun List<TranscriptSegment>.hasMultipleSpeakers(): Boolean =
        map { it.speakerId }.distinct().size >= 2
}
