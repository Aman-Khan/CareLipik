package com.carelipik.app.ui.screens.clinicaldraft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.carelipik.app.data.extraction.HttpGeminiClinicalNoteGenerationEngine
import com.carelipik.app.data.extraction.LiteRtMedGemmaClinicalNoteGenerationEngine
import com.carelipik.app.data.extraction.DirectGeminiClinicalNoteGenerationEngine
import com.carelipik.app.data.extraction.PreferDeviceKeyClinicalNoteGenerationEngine
import com.carelipik.app.data.local.DeviceApiKeyProvider
import com.carelipik.app.domain.repository.ApiProvider
import com.carelipik.app.data.extraction.TranscriptBackedClinicalExtractionEngine
import com.carelipik.app.domain.extraction.ClinicalExtractionEngine
import com.carelipik.app.domain.extraction.ClinicalExtractionResult
import com.carelipik.app.domain.extraction.ClinicalNoteGenerationEngine
import com.carelipik.app.domain.extraction.ClinicalNoteGenerationRequest
import com.carelipik.app.domain.extraction.ClinicalNoteGenerationResult
import com.carelipik.app.domain.extraction.ClinicalNoteTemplates
import com.carelipik.app.domain.extraction.ConservativePrescriptionExtractor
import com.carelipik.app.domain.model.ClinicalNoteFormat
import com.carelipik.app.domain.model.ClinicalNoteLanguage
import com.carelipik.app.domain.model.MedicationDraft
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClinicalDraftViewModel(
    private val engine: ClinicalExtractionEngine = TranscriptBackedClinicalExtractionEngine(),
    private val deviceGenerationEngine: ClinicalNoteGenerationEngine? = null,
    private val onlineEngine: ClinicalNoteGenerationEngine? = null,
    private val isOnline: () -> Boolean = { false },
    private val processAsynchronously: Boolean = true
) : ViewModel() {
    private val _uiState = MutableStateFlow(ClinicalDraftUiState())
    val uiState: StateFlow<ClinicalDraftUiState> = _uiState.asStateFlow()
    private var sourceTranscript: String? = null
    private var sourceLanguage = TranscriptionLanguage.English
    private var sourceSpecialtyName = ""
    private var sourcePatientAge = ""
    private var sourceVisitReason = ""

    fun generate(
        transcript: String,
        language: TranscriptionLanguage = TranscriptionLanguage.English,
        specialtyName: String = "",
        patientAge: String = "",
        visitReason: String = ""
    ) {
        sourceTranscript = transcript
        sourceLanguage = language
        sourceSpecialtyName = specialtyName.trim()
        sourcePatientAge = patientAge.trim()
        sourceVisitReason = visitReason.trim()
        _uiState.value = ClinicalDraftUiState(
            status = ClinicalDraftStatus.Processing,
            sourceLanguage = language
        )
        if (processAsynchronously) {
            viewModelScope.launch {
                val result = withContext(Dispatchers.Default) { engine.extract(transcript) }
                applyResultAsync(result)
            }
        } else {
            applyResult(engine.extract(transcript))
        }
    }

    fun retry() {
        sourceTranscript?.let { transcript ->
            generate(
                transcript,
                sourceLanguage,
                sourceSpecialtyName,
                sourcePatientAge,
                sourceVisitReason
            )
        }
    }

    fun setPatientAge(value: String) {
        if (value.all(Char::isDigit)) updateDraft { copy(patientAge = value) }
    }
    fun setPresentingComplaint(value: String) = updateDraft { copy(presentingComplaint = value) }
    fun setHistory(value: String) = updateDraft { copy(history = value) }
    fun setKeyFindings(value: String) = updateDraft { copy(keyFindings = value) }
    fun setAssessmentNotes(value: String) = updateDraft { copy(assessmentNotes = value) }
    fun setPlanNotes(value: String) = updateDraft { copy(planNotes = value) }

    fun selectNoteFormat(format: ClinicalNoteFormat) {
        val currentDraft = _uiState.value.draft
        if (currentDraft.noteFormat == format) return
        val formattedDraft = ClinicalNoteTemplates.apply(
            currentDraft,
            format,
            currentDraft.noteLanguage,
            sourceSpecialtyName
        )
        regenerateSelectedTemplate(formattedDraft)
    }

    fun selectNoteLanguage(language: ClinicalNoteLanguage) {
        val currentDraft = _uiState.value.draft
        if (currentDraft.noteLanguage == language) return
        val formattedDraft = ClinicalNoteTemplates.apply(
            currentDraft,
            currentDraft.noteFormat,
            language,
            sourceSpecialtyName
        )
        regenerateSelectedTemplate(formattedDraft)
    }

    fun setSpecialtyName(value: String) {
        sourceSpecialtyName = value.take(120)
        updateDraft { copy(specialtyName = sourceSpecialtyName) }
    }

    fun setOnlineGenerationConsent(hasConsent: Boolean) {
        _uiState.update {
            it.copy(
                hasOnlineGenerationConsent = hasConsent,
                onlineGenerationError = if (hasConsent) null else it.onlineGenerationError
            )
        }
        if (hasConsent && isOnline() && onlineEngine != null && sourceTranscript != null) {
            regenerateSelectedTemplate(_uiState.value.draft)
        }
    }

    fun setSectionContent(index: Int, content: String) = updateDraft {
        if (index !in structuredSections.indices) this else copy(
            structuredSections = structuredSections.toMutableList().also { sections ->
                sections[index] = sections[index].copy(content = content)
            }
        )
    }

    fun generateWithGemini() {
        val generator = onlineEngine
        val transcript = sourceTranscript
        if (generator == null || transcript.isNullOrBlank()) {
            _uiState.update {
                it.copy(onlineGenerationError = "Online clinical note generation is unavailable.")
            }
            return
        }
        if (!isOnline()) {
            _uiState.update {
                it.copy(
                    onlineGenerationError =
                        "No validated internet connection is available. MedGemma remains active."
                )
            }
            return
        }
        if (_uiState.value.isGeneratingOnline) return
        val currentDraft = _uiState.value.draft
        val request = ClinicalNoteGenerationRequest(
            reviewedTranscript = transcript,
            sourceLanguage = sourceLanguage,
            noteFormat = currentDraft.noteFormat,
            outputLanguage = currentDraft.noteLanguage,
            specialtyName = sourceSpecialtyName,
            patientAge = sourcePatientAge.ifBlank { currentDraft.patientAge },
            visitReason = sourceVisitReason.ifBlank { currentDraft.presentingComplaint }
        )
        _uiState.update {
            it.copy(isGeneratingOnline = true, onlineGenerationError = null)
        }
        val applyGeneration: (ClinicalNoteGenerationResult) -> Unit = { result ->
            _uiState.update { state ->
                when (result) {
                    is ClinicalNoteGenerationResult.Success -> state.copy(
                        status = ClinicalDraftStatus.Ready,
                        draft = result.draft.copy(
                            patientAge = result.draft.patientAge.ifBlank {
                                currentDraft.patientAge
                            },
                            presentingComplaint = result.draft.presentingComplaint.ifBlank {
                                currentDraft.presentingComplaint
                            },
                            history = result.draft.history.ifBlank { currentDraft.history },
                            keyFindings = result.draft.keyFindings.ifBlank {
                                currentDraft.keyFindings
                            },
                            assessmentNotes = result.draft.assessmentNotes.ifBlank {
                                currentDraft.assessmentNotes
                            },
                            planNotes = result.draft.planNotes.ifBlank {
                                currentDraft.planNotes
                            },
                            medications = result.draft.medications.ifEmpty {
                                ConservativePrescriptionExtractor.extract(transcript)
                            },
                            reviewedTranscript = transcript
                        ),
                        isGeneratingOnline = false,
                        onlineGenerationError = null,
                        hasAttemptedContinue = false
                    )
                    is ClinicalNoteGenerationResult.Failure -> state.copy(
                        isGeneratingOnline = false,
                        onlineGenerationError = result.message
                    )
                }
            }
        }
        if (processAsynchronously) {
            viewModelScope.launch {
                applyGeneration(withContext(Dispatchers.IO) { generator.generate(request) })
            }
        } else {
            applyGeneration(generator.generate(request))
        }
    }

    fun generateWithMedGemma() {
        val generator = deviceGenerationEngine
        val transcript = sourceTranscript
        if (generator == null || transcript.isNullOrBlank()) {
            _uiState.update {
                it.copy(onDeviceGenerationError = "On-device MedGemma generation is unavailable.")
            }
            return
        }
        if (_uiState.value.isGeneratingOnDevice) return
        val currentDraft = _uiState.value.draft
        val request = ClinicalNoteGenerationRequest(
            reviewedTranscript = transcript,
            sourceLanguage = sourceLanguage,
            noteFormat = currentDraft.noteFormat,
            outputLanguage = currentDraft.noteLanguage,
            specialtyName = sourceSpecialtyName,
            patientAge = sourcePatientAge.ifBlank { currentDraft.patientAge },
            visitReason = sourceVisitReason.ifBlank { currentDraft.presentingComplaint }
        )
        _uiState.update {
            it.copy(isGeneratingOnDevice = true, onDeviceGenerationError = null)
        }
        val applyGeneration: (ClinicalNoteGenerationResult) -> Unit = { result ->
            _uiState.update { state ->
                when (result) {
                    is ClinicalNoteGenerationResult.Success -> state.copy(
                        status = ClinicalDraftStatus.Ready,
                        draft = result.draft.copy(
                            patientAge = result.draft.patientAge.ifBlank {
                                currentDraft.patientAge
                            },
                            presentingComplaint = result.draft.presentingComplaint.ifBlank {
                                currentDraft.presentingComplaint
                            },
                            history = result.draft.history.ifBlank { currentDraft.history },
                            keyFindings = result.draft.keyFindings.ifBlank {
                                currentDraft.keyFindings
                            },
                            assessmentNotes = result.draft.assessmentNotes.ifBlank {
                                currentDraft.assessmentNotes
                            },
                            planNotes = result.draft.planNotes.ifBlank {
                                currentDraft.planNotes
                            },
                            medications = result.draft.medications.ifEmpty {
                                ConservativePrescriptionExtractor.extract(transcript)
                            },
                            reviewedTranscript = transcript
                        ),
                        isGeneratingOnDevice = false,
                        onDeviceGenerationError = null,
                        hasAttemptedContinue = false
                    )
                    is ClinicalNoteGenerationResult.Failure -> state.copy(
                        isGeneratingOnDevice = false,
                        onDeviceGenerationError = result.message
                    )
                }
            }
        }
        if (processAsynchronously) {
            viewModelScope.launch {
                applyGeneration(withContext(Dispatchers.IO) { generator.generate(request) })
            }
        } else {
            applyGeneration(generator.generate(request))
        }
    }

    fun addMedication() = updateDraft {
        copy(medications = medications + MedicationDraft())
    }

    fun updateMedication(index: Int, medication: MedicationDraft) = updateDraft {
        if (index !in medications.indices) this else copy(
            medications = medications.toMutableList().also { it[index] = medication }
        )
    }

    fun removeMedication(index: Int) = updateDraft {
        if (index !in medications.indices) this else copy(
            medications = medications.filterIndexed { itemIndex, _ -> itemIndex != index }
        )
    }

    fun validateForContinue(): Boolean {
        _uiState.update { it.copy(hasAttemptedContinue = true) }
        return _uiState.value.canContinue
    }

    fun currentDraft(): com.carelipik.app.domain.model.ClinicalDraft = _uiState.value.draft

    fun resetForNewConsultation() {
        viewModelScope.coroutineContext.cancelChildren()
        sourceTranscript = null
        sourceLanguage = TranscriptionLanguage.English
        sourceSpecialtyName = ""
        sourcePatientAge = ""
        sourceVisitReason = ""
        _uiState.value = ClinicalDraftUiState()
    }

    private fun updateDraft(transform: com.carelipik.app.domain.model.ClinicalDraft.() -> com.carelipik.app.domain.model.ClinicalDraft) {
        _uiState.update {
            it.copy(status = ClinicalDraftStatus.Ready, draft = it.draft.transform(), errorMessage = null)
        }
    }

    private fun applyResult(result: ClinicalExtractionResult) {
        _uiState.value = when (result) {
            is ClinicalExtractionResult.Success -> {
                val baseDraft = preparedDraft(result)
                automaticallyGenerateStructuredDraft(
                    baseDraft = baseDraft,
                generationResult = generatePreferred(generationRequest(baseDraft))
                )
            }
            is ClinicalExtractionResult.Failure -> ClinicalDraftUiState(
                status = ClinicalDraftStatus.Error,
                errorMessage = result.message,
                sourceLanguage = sourceLanguage
            )
        }
    }

    private suspend fun applyResultAsync(result: ClinicalExtractionResult) {
        _uiState.value = when (result) {
            is ClinicalExtractionResult.Success -> {
                val baseDraft = preparedDraft(result)
                val generationResult = withContext(Dispatchers.IO) {
                    generatePreferred(generationRequest(baseDraft))
                }
                automaticallyGenerateStructuredDraft(baseDraft, generationResult)
            }
            is ClinicalExtractionResult.Failure -> ClinicalDraftUiState(
                status = ClinicalDraftStatus.Error,
                errorMessage = result.message,
                sourceLanguage = sourceLanguage
            )
        }
    }

    private fun preparedDraft(result: ClinicalExtractionResult.Success) =
        ClinicalNoteTemplates.apply(
            draft = result.draft.copy(
                patientAge = sourcePatientAge.ifBlank { result.draft.patientAge },
                reviewedTranscript = sourceTranscript.orEmpty()
            ),
            format = ClinicalNoteFormat.Soap,
            language = ClinicalNoteLanguage.Original,
            specialtyName = sourceSpecialtyName
        )

    private fun automaticallyGenerateStructuredDraft(
        baseDraft: com.carelipik.app.domain.model.ClinicalDraft,
        generationResult: ClinicalNoteGenerationResult?
    ): ClinicalDraftUiState {
        val transcript = sourceTranscript.orEmpty()
        return when (val result = generationResult) {
            is ClinicalNoteGenerationResult.Success -> readyState(
                result.draft.copy(
                    patientAge = result.draft.patientAge.ifBlank { baseDraft.patientAge },
                    presentingComplaint = result.draft.presentingComplaint.ifBlank {
                        baseDraft.presentingComplaint
                    },
                    history = result.draft.history.ifBlank { baseDraft.history },
                    keyFindings = result.draft.keyFindings.ifBlank { baseDraft.keyFindings },
                    assessmentNotes = result.draft.assessmentNotes.ifBlank {
                        baseDraft.assessmentNotes
                    },
                    planNotes = result.draft.planNotes.ifBlank { baseDraft.planNotes },
                    medications = result.draft.medications.ifEmpty {
                        ConservativePrescriptionExtractor.extract(transcript)
                    },
                    reviewedTranscript = transcript
                )
            )
            is ClinicalNoteGenerationResult.Failure -> readyState(
                baseDraft,
                onDeviceError = "Automatic MedGemma structuring was unavailable. " +
                    "You can review the transcript-backed draft or use online drafting."
            )
            null -> readyState(baseDraft)
        }
    }

    private fun generationRequest(
        baseDraft: com.carelipik.app.domain.model.ClinicalDraft
    ) = ClinicalNoteGenerationRequest(
        reviewedTranscript = sourceTranscript.orEmpty(),
        sourceLanguage = sourceLanguage,
        noteFormat = baseDraft.noteFormat,
        outputLanguage = baseDraft.noteLanguage,
        specialtyName = sourceSpecialtyName,
        patientAge = sourcePatientAge.ifBlank { baseDraft.patientAge },
        visitReason = sourceVisitReason.ifBlank { baseDraft.presentingComplaint }
    )

    private fun readyState(
        draft: com.carelipik.app.domain.model.ClinicalDraft,
        onDeviceError: String? = null
    ) = _uiState.value.copy(
        status = ClinicalDraftStatus.Ready,
        draft = draft,
        sourceLanguage = sourceLanguage,
        errorMessage = null,
        isGeneratingOnDevice = false,
        isGeneratingOnline = false,
        onDeviceGenerationError = onDeviceError
    )

    private fun regenerateSelectedTemplate(
        formattedDraft: com.carelipik.app.domain.model.ClinicalDraft
    ) {
        _uiState.update {
            it.copy(
                status = ClinicalDraftStatus.Processing,
                draft = formattedDraft,
                onDeviceGenerationError = null,
                onlineGenerationError = null,
                hasAttemptedContinue = false
            )
        }
        if (processAsynchronously) {
            viewModelScope.launch {
                val result = withContext(Dispatchers.IO) {
                    generatePreferred(generationRequest(formattedDraft))
                }
                _uiState.value = automaticallyGenerateStructuredDraft(formattedDraft, result)
            }
        } else {
            _uiState.value = automaticallyGenerateStructuredDraft(
                formattedDraft,
                generatePreferred(generationRequest(formattedDraft))
            )
        }
    }

    private fun generatePreferred(
        request: ClinicalNoteGenerationRequest
    ): ClinicalNoteGenerationResult? {
        if (isOnline()) {
            val onlineResult = onlineEngine?.generate(request)
            if (onlineResult is ClinicalNoteGenerationResult.Success) return onlineResult
        }
        return deviceGenerationEngine?.generate(request)
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ClinicalDraftViewModel::class.java))
            val keyProvider = DeviceApiKeyProvider(applicationContext)
            val connectivityManager = applicationContext.getSystemService(
                ConnectivityManager::class.java
            )
            return ClinicalDraftViewModel(
                deviceGenerationEngine = LiteRtMedGemmaClinicalNoteGenerationEngine(
                    applicationContext
                ),
                onlineEngine = PreferDeviceKeyClinicalNoteGenerationEngine(
                    hasDeviceKey = { keyProvider.get(ApiProvider.Gemini) != null },
                    direct = DirectGeminiClinicalNoteGenerationEngine {
                        keyProvider.get(ApiProvider.Gemini)
                    },
                    fallback = HttpGeminiClinicalNoteGenerationEngine(
                        backendBaseUrl = com.carelipik.app.BuildConfig.TRANSCRIPTION_BACKEND_URL,
                        allowInsecureLocalhost = com.carelipik.app.BuildConfig.DEBUG
                    )
                ),
                isOnline = {
                    connectivityManager.activeNetwork?.let { network ->
                        connectivityManager.getNetworkCapabilities(network)?.let { capabilities ->
                            capabilities.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_INTERNET
                            ) && capabilities.hasCapability(
                                NetworkCapabilities.NET_CAPABILITY_VALIDATED
                            )
                        }
                    } ?: false
                }
            ) as T
        }
    }
}
