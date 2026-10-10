package com.carelipik.app.ui.screens.clinicaldraft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.carelipik.app.data.extraction.HttpGeminiClinicalNoteGenerationEngine
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import com.carelipik.app.domain.extraction.ProgressClinicalNoteGenerationEngine
import com.carelipik.app.data.extraction.QwenClinicalNoteGenerationEngine

class ClinicalDraftViewModel(
    private val engine: ClinicalExtractionEngine = TranscriptBackedClinicalExtractionEngine(),
    private val onlineEngine: ClinicalNoteGenerationEngine? = null,
    private val processAsynchronously: Boolean = true,
    private val localEngine: ProgressClinicalNoteGenerationEngine? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(ClinicalDraftUiState())
    val uiState: StateFlow<ClinicalDraftUiState> = _uiState.asStateFlow()
    private var sourceTranscript: String? = null
    private var sourceLanguage = TranscriptionLanguage.English
    private var sourceSpecialtyName = ""
    private var sourcePatientAge = ""
    private var sourceVisitReason = ""
    private var localGenerationJob: Job? = null

    fun generate(
        transcript: String,
        language: TranscriptionLanguage = TranscriptionLanguage.English,
        specialtyName: String = "",
        patientAge: String = "",
        visitReason: String = ""
    ) {
        cancelLocalGeneration()
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
                applyResult(result)
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

    fun selectNoteFormat(format: ClinicalNoteFormat) = updateDraft {
        if (noteFormat == format) this else {
            ClinicalNoteTemplates.apply(this, format, noteLanguage, sourceSpecialtyName)
        }
    }

    fun selectNoteLanguage(language: ClinicalNoteLanguage) = updateDraft {
        if (noteLanguage == language) this else {
            ClinicalNoteTemplates.apply(this, noteFormat, language, sourceSpecialtyName)
        }
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
    }

    fun setSectionContent(index: Int, content: String) = updateDraft {
        if (index !in structuredSections.indices) this else copy(
            structuredSections = structuredSections.toMutableList().also { sections ->
                sections[index] = sections[index].copy(content = content)
            }
        )
    }

    fun generateWithGemini() {
        if (_uiState.value.isGeneratingLocal) return
        val generator = onlineEngine
        val transcript = sourceTranscript
        if (generator == null || transcript.isNullOrBlank()) {
            _uiState.update {
                it.copy(onlineGenerationError = "Online Gemini note generation is unavailable.")
            }
            return
        }
        if (!_uiState.value.hasOnlineGenerationConsent) {
            _uiState.update {
                it.copy(
                    onlineGenerationError =
                        "Confirm consent before sending the reviewed transcript to Gemini."
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
                            presentingComplaint = currentDraft.presentingComplaint,
                            history = currentDraft.history,
                            keyFindings = currentDraft.keyFindings,
                            assessmentNotes = currentDraft.assessmentNotes,
                            planNotes = currentDraft.planNotes,
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

    fun generateWithQwen() {
        val generator = localEngine ?: return
        val transcript = sourceTranscript ?: return
        val state = _uiState.value
        if (state.isGeneratingLocal || state.isGeneratingOnline || state.status != ClinicalDraftStatus.Ready) return
        val draft = state.draft
        val request = ClinicalNoteGenerationRequest(transcript, sourceLanguage, draft.noteFormat,
            draft.noteLanguage, draft.specialtyName, draft.patientAge, sourceVisitReason)
        _uiState.update { it.copy(isGeneratingLocal = true, localGenerationError = null,
            localGenerationDetail = "Preparing local Qwen3 report…") }
        localGenerationJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val generationContext = coroutineContext
                generator.generateWithProgress(request, { generationContext.ensureActive() }, { detail ->
                    generationContext.ensureActive()
                    _uiState.update { it.copy(localGenerationDetail = detail) }
                })
            }
            _uiState.update {
                when (result) {
                    is ClinicalNoteGenerationResult.Success -> it.copy(draft = result.draft.copy(
                        presentingComplaint = draft.presentingComplaint, history = draft.history,
                        keyFindings = draft.keyFindings, assessmentNotes = draft.assessmentNotes,
                        planNotes = draft.planNotes, medications = draft.medications),
                        isGeneratingLocal = false, localGenerationDetail = null,
                        localGenerationError = null, hasAttemptedContinue = false)
                    is ClinicalNoteGenerationResult.Failure -> it.copy(isGeneratingLocal = false,
                        localGenerationDetail = null, localGenerationError = result.message)
                }
            }
        }
    }

    fun cancelLocalGeneration() {
        localGenerationJob?.cancel()
        localGenerationJob = null
        _uiState.update { it.copy(isGeneratingLocal = false, localGenerationDetail = null) }
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
        cancelLocalGeneration()
        viewModelScope.coroutineContext.cancelChildren()
        sourceTranscript = null
        sourceLanguage = TranscriptionLanguage.English
        sourceSpecialtyName = ""
        sourcePatientAge = ""
        sourceVisitReason = ""
        _uiState.value = ClinicalDraftUiState()
    }

    private fun updateDraft(transform: com.carelipik.app.domain.model.ClinicalDraft.() -> com.carelipik.app.domain.model.ClinicalDraft) {
        if (_uiState.value.isGeneratingLocal) return
        _uiState.update {
            it.copy(status = ClinicalDraftStatus.Ready, draft = it.draft.transform(), errorMessage = null)
        }
    }

    private fun applyResult(result: ClinicalExtractionResult) {
        _uiState.value = when (result) {
            is ClinicalExtractionResult.Success -> ClinicalDraftUiState(
                status = ClinicalDraftStatus.Ready,
                draft = ClinicalNoteTemplates.apply(
                    draft = result.draft.copy(
                        patientAge = sourcePatientAge.ifBlank { result.draft.patientAge },
                        reviewedTranscript = sourceTranscript.orEmpty()
                    ),
                    format = ClinicalNoteFormat.Soap,
                    language = ClinicalNoteLanguage.Original,
                    specialtyName = sourceSpecialtyName
                ),
                sourceLanguage = sourceLanguage
            )
            is ClinicalExtractionResult.Failure -> ClinicalDraftUiState(
                status = ClinicalDraftStatus.Error,
                errorMessage = result.message,
                sourceLanguage = sourceLanguage
            )
        }
        if (result is ClinicalExtractionResult.Success && localEngine != null) generateWithQwen()
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ClinicalDraftViewModel::class.java))
            val keyProvider = DeviceApiKeyProvider(applicationContext)
            return ClinicalDraftViewModel(
                localEngine = QwenClinicalNoteGenerationEngine(applicationContext),
                onlineEngine = PreferDeviceKeyClinicalNoteGenerationEngine(
                    hasDeviceKey = { keyProvider.get(ApiProvider.Gemini) != null },
                    direct = DirectGeminiClinicalNoteGenerationEngine {
                        keyProvider.get(ApiProvider.Gemini)
                    },
                    fallback = HttpGeminiClinicalNoteGenerationEngine(
                        backendBaseUrl = com.carelipik.app.BuildConfig.TRANSCRIPTION_BACKEND_URL,
                        allowInsecureLocalhost = com.carelipik.app.BuildConfig.DEBUG
                    )
                )
            ) as T
        }
    }
}
