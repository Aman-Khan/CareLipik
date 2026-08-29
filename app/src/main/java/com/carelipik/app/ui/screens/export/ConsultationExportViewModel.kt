package com.carelipik.app.ui.screens.export

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carelipik.app.data.export.AndroidConsultationStandardExporter
import com.carelipik.app.data.local.EncryptedConsultationReportRepository
import com.carelipik.app.domain.export.ConsultationExportFormat
import com.carelipik.app.domain.export.ConsultationExportResult
import com.carelipik.app.domain.export.ConsultationStandardExporter
import com.carelipik.app.domain.export.ExportedConsultationFile
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.repository.ConsultationReportArtifact
import com.carelipik.app.domain.repository.ConsultationReportRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

enum class ConsultationExportStatus {
    Empty,
    ReadyToGenerate,
    Generating,
    Generated,
    Error
}

data class ConsultationExportUiState(
    val status: ConsultationExportStatus = ConsultationExportStatus.Empty,
    val consultation: ApprovedConsultation? = null,
    val selectedFormat: ConsultationExportFormat = ConsultationExportFormat.ClinicalPdf,
    val exportedFile: ExportedConsultationFile? = null,
    val savedArtifact: ConsultationReportArtifact? = null,
    val persistenceWarning: String? = null,
    val errorMessage: String? = null
) {
    val canGenerate: Boolean
        get() = consultation != null && status !in setOf(
            ConsultationExportStatus.Generating,
            ConsultationExportStatus.Empty
        )
}

class ConsultationExportViewModel(
    private val exporter: ConsultationStandardExporter,
    private val reportRepository: ConsultationReportRepository? = null,
    private val processAsynchronously: Boolean = true
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConsultationExportUiState())
    val uiState: StateFlow<ConsultationExportUiState> = _uiState.asStateFlow()

    fun load(consultation: ApprovedConsultation) {
        _uiState.value = ConsultationExportUiState(
            status = ConsultationExportStatus.ReadyToGenerate,
            consultation = consultation
        )
    }

    fun resetForNewConsultation() {
        _uiState.value = ConsultationExportUiState()
    }

    fun selectFormat(format: ConsultationExportFormat) {
        if (_uiState.value.status == ConsultationExportStatus.Generating) return
        _uiState.value = _uiState.value.copy(
            status = ConsultationExportStatus.ReadyToGenerate,
            selectedFormat = format,
            exportedFile = null,
            savedArtifact = null,
            persistenceWarning = null,
            errorMessage = null
        )
    }

    fun generate() {
        val consultation = _uiState.value.consultation ?: return
        if (_uiState.value.status == ConsultationExportStatus.Generating) return
        _uiState.value = _uiState.value.copy(
            status = ConsultationExportStatus.Generating,
            exportedFile = null,
            savedArtifact = null,
            persistenceWarning = null,
            errorMessage = null
        )
        val operation: suspend () -> Unit = {
            val format = _uiState.value.selectedFormat
            _uiState.value = when (val result = exporter.export(consultation, format)) {
                is ConsultationExportResult.Success -> {
                    val savedArtifact = runCatching {
                        reportRepository?.save(consultation.id, result.file)
                    }
                    _uiState.value.copy(
                        status = ConsultationExportStatus.Generated,
                        exportedFile = result.file,
                        savedArtifact = savedArtifact.getOrNull(),
                        persistenceWarning = savedArtifact.exceptionOrNull()?.let {
                            "The file is ready to share, but its encrypted history copy could not be saved."
                        },
                        errorMessage = null
                    )
                }
                is ConsultationExportResult.Failure -> _uiState.value.copy(
                    status = ConsultationExportStatus.Error,
                    exportedFile = null,
                    errorMessage = result.message
                )
            }
        }
        if (processAsynchronously) viewModelScope.launch { operation() } else runBlocking {
            operation()
        }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val applicationContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ConsultationExportViewModel::class.java))
            return ConsultationExportViewModel(
                exporter = AndroidConsultationStandardExporter(applicationContext),
                reportRepository = EncryptedConsultationReportRepository(applicationContext)
            ) as T
        }
    }
}
