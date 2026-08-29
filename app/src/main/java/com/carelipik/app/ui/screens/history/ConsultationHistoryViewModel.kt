package com.carelipik.app.ui.screens.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carelipik.app.data.local.EncryptedLocalConsultationRepository
import com.carelipik.app.data.local.EncryptedConsultationReportRepository
import com.carelipik.app.domain.export.ExportedConsultationFile
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.repository.ConsultationRepository
import com.carelipik.app.domain.repository.ConsultationReportArtifact
import com.carelipik.app.domain.repository.ConsultationReportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class ConsultationHistoryUiState(
    val consultations: List<ApprovedConsultation> = emptyList(),
    val selected: ApprovedConsultation? = null,
    val reports: List<ConsultationReportArtifact> = emptyList(),
    val isLoading: Boolean = true,
    val isPreparingReport: Boolean = false,
    val reportError: String? = null
)

class ConsultationHistoryViewModel(
    private val repository: ConsultationRepository,
    private val reportRepository: ConsultationReportRepository,
    private val processAsynchronously: Boolean = true
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConsultationHistoryUiState())
    val uiState: StateFlow<ConsultationHistoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        runOperation {
            _uiState.value = _uiState.value.copy(
                consultations = repository.list(),
                isLoading = false
            )
        }
    }

    fun select(id: String, onSelected: (Boolean) -> Unit = {}) {
        runOperation {
            val selected = repository.get(id)
            _uiState.value = _uiState.value.copy(
                selected = selected,
                reports = selected?.let { reportRepository.list(it.id) }.orEmpty(),
                reportError = null
            )
            onSelected(selected != null)
        }
    }

    fun closeDetail() {
        _uiState.value = _uiState.value.copy(
            selected = null,
            reports = emptyList(),
            isPreparingReport = false,
            reportError = null
        )
    }

    fun refreshSelectedReports() {
        val consultationId = _uiState.value.selected?.id ?: return
        runOperation {
            _uiState.value = _uiState.value.copy(
                reports = reportRepository.list(consultationId),
                reportError = null
            )
        }
    }

    fun prepareReport(
        artifact: ConsultationReportArtifact,
        onReady: (ExportedConsultationFile) -> Unit
    ) {
        if (_uiState.value.isPreparingReport) return
        runOperation {
            _uiState.value = _uiState.value.copy(
                isPreparingReport = true,
                reportError = null
            )
            runCatching { reportRepository.materialize(artifact) }
                .onSuccess(onReady)
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        reportError = "The saved report could not be prepared. Generate it again."
                    )
                }
            _uiState.value = _uiState.value.copy(isPreparingReport = false)
        }
    }

    fun deleteReport(artifact: ConsultationReportArtifact) {
        runOperation {
            runCatching { reportRepository.delete(artifact) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        reports = reportRepository.list(artifact.consultationId),
                        reportError = null
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        reportError = "The saved report could not be deleted."
                    )
                }
        }
    }

    fun delete(id: String) {
        runOperation {
            runCatching {
                reportRepository.deleteForConsultation(id)
                repository.delete(id)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    consultations = repository.list(),
                    selected = null,
                    reports = emptyList(),
                    isLoading = false,
                    reportError = null
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    reportError = "The consultation and its reports could not be deleted."
                )
            }
        }
    }

    private fun runOperation(operation: suspend () -> Unit) {
        if (processAsynchronously) {
            viewModelScope.launch { operation() }
        } else {
            runBlocking { operation() }
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ConsultationHistoryViewModel::class.java))
            return ConsultationHistoryViewModel(
                repository = EncryptedLocalConsultationRepository(applicationContext),
                reportRepository = EncryptedConsultationReportRepository(applicationContext)
            ) as T
        }
    }
}
