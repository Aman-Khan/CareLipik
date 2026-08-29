package com.carelipik.app.ui.screens.clinicaldraft

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carelipik.app.data.extraction.TranscriptBackedClinicalExtractionEngine
import com.carelipik.app.domain.extraction.ClinicalExtractionEngine
import com.carelipik.app.domain.extraction.ClinicalExtractionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClinicalDraftViewModel(
    private val engine: ClinicalExtractionEngine = TranscriptBackedClinicalExtractionEngine(),
    private val processAsynchronously: Boolean = true
) : ViewModel() {
    private val _uiState = MutableStateFlow(ClinicalDraftUiState())
    val uiState: StateFlow<ClinicalDraftUiState> = _uiState.asStateFlow()
    private var sourceTranscript: String? = null

    fun generate(transcript: String) {
        sourceTranscript = transcript
        _uiState.value = ClinicalDraftUiState(status = ClinicalDraftStatus.Processing)
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
        sourceTranscript?.let(::generate)
    }

    fun setPatientAge(value: String) {
        if (value.all(Char::isDigit)) updateDraft { copy(patientAge = value) }
    }
    fun setPresentingComplaint(value: String) = updateDraft { copy(presentingComplaint = value) }
    fun setHistory(value: String) = updateDraft { copy(history = value) }
    fun setKeyFindings(value: String) = updateDraft { copy(keyFindings = value) }
    fun setAssessmentNotes(value: String) = updateDraft { copy(assessmentNotes = value) }
    fun setPlanNotes(value: String) = updateDraft { copy(planNotes = value) }

    fun validateForContinue(): Boolean {
        _uiState.update { it.copy(hasAttemptedContinue = true) }
        return _uiState.value.canContinue
    }

    fun currentDraft(): com.carelipik.app.domain.model.ClinicalDraft = _uiState.value.draft

    private fun updateDraft(transform: com.carelipik.app.domain.model.ClinicalDraft.() -> com.carelipik.app.domain.model.ClinicalDraft) {
        _uiState.update {
            it.copy(status = ClinicalDraftStatus.Ready, draft = it.draft.transform(), errorMessage = null)
        }
    }

    private fun applyResult(result: ClinicalExtractionResult) {
        _uiState.value = when (result) {
            is ClinicalExtractionResult.Success -> ClinicalDraftUiState(
                status = ClinicalDraftStatus.Ready,
                draft = result.draft
            )
            is ClinicalExtractionResult.Failure -> ClinicalDraftUiState(
                status = ClinicalDraftStatus.Error,
                errorMessage = result.message
            )
        }
    }
}
