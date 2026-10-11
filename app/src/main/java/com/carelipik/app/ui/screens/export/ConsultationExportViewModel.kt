package com.carelipik.app.ui.screens.export

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carelipik.app.data.export.AndroidConsultationStandardExporter
import com.carelipik.app.data.export.PrescriptionImageImporter
import com.carelipik.app.data.local.EncryptedConsultationReportRepository
import com.carelipik.app.domain.export.ConsultationExportFormat
import com.carelipik.app.domain.export.ConsultationExportResult
import com.carelipik.app.domain.export.ConsultationStandardExporter
import com.carelipik.app.domain.export.ExportedConsultationFile
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.repository.ConsultationReportArtifact
import com.carelipik.app.domain.repository.ConsultationReportRepository
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val errorMessage: String? = null,
    val electronicallySign: Boolean = false,
    val signerName: String = "",
    val handwrittenSignature: String = "",
    val prescriptionImagePaths: List<String> = emptyList(),
    val prescriptionMessage: String? = null
) {
    val canGenerate: Boolean
        get() = consultation != null && status !in setOf(
            ConsultationExportStatus.Generating,
            ConsultationExportStatus.Empty
        ) && (!electronicallySign || (signerName.isNotBlank() && handwrittenSignature.isNotBlank())) &&
            (prescriptionImagePaths.isEmpty() || electronicallySign)

    val isConfigurationEditable: Boolean
        get() = status in setOf(
            ConsultationExportStatus.ReadyToGenerate,
            ConsultationExportStatus.Error
        )
}

class ConsultationExportViewModel(
    private val exporter: ConsultationStandardExporter,
    private val reportRepository: ConsultationReportRepository? = null,
    private val prescriptionImageImporter: PrescriptionImageImporter? = null,
    private val processAsynchronously: Boolean = true
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConsultationExportUiState())
    val uiState: StateFlow<ConsultationExportUiState> = _uiState.asStateFlow()

    fun load(
        consultation: ApprovedConsultation,
        defaultSignerName: String = "",
        savedSignature: String = ""
    ) {
        _uiState.value = ConsultationExportUiState(
            status = ConsultationExportStatus.ReadyToGenerate,
            consultation = consultation,
            signerName = defaultSignerName,
            handwrittenSignature = savedSignature,
            electronicallySign = defaultSignerName.isNotBlank() && savedSignature.isNotBlank()
        )
    }

    fun resetForNewConsultation() {
        _uiState.value = ConsultationExportUiState()
    }

    fun selectFormat(format: ConsultationExportFormat) {
        if (!_uiState.value.isConfigurationEditable) return
        _uiState.value = _uiState.value.copy(
            status = ConsultationExportStatus.ReadyToGenerate,
            selectedFormat = format,
            exportedFile = null,
            savedArtifact = null,
            persistenceWarning = null,
            errorMessage = null
        )
    }

    fun setElectronicallySign(enabled: Boolean) {
        if (!_uiState.value.isConfigurationEditable) return
        if (!enabled && _uiState.value.prescriptionImagePaths.isNotEmpty()) {
            _uiState.update {
                it.copy(prescriptionMessage = "A prescription attachment must be electronically signed.")
            }
            return
        }
        _uiState.value = _uiState.value.copy(electronicallySign = enabled)
    }

    fun setSignerName(name: String) {
        if (!_uiState.value.isConfigurationEditable) return
        _uiState.value = _uiState.value.copy(signerName = name.take(120))
    }

    fun setHandwrittenSignature(signature: String) {
        if (!_uiState.value.isConfigurationEditable) return
        _uiState.value = _uiState.value.copy(handwrittenSignature = signature)
    }

    fun importPrescriptionImage(uriString: String) {
        if (!_uiState.value.isConfigurationEditable) return
        val importer = prescriptionImageImporter ?: return
        val operation: suspend () -> Unit = {
            importer.import(Uri.parse(uriString))
                .onSuccess { path ->
                    _uiState.update {
                        it.copy(
                            prescriptionImagePaths = it.prescriptionImagePaths + path,
                            electronicallySign = true,
                            prescriptionMessage = if (
                                it.signerName.isBlank() || it.handwrittenSignature.isBlank()
                            ) {
                                "Add the doctor's name and handwritten signature to use this prescription."
                            } else {
                                "Prescription attached. Electronic signature is required."
                            }
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(prescriptionMessage = error.message ?: "Prescription import failed.")
                    }
                }
        }
        if (processAsynchronously) viewModelScope.launch { operation() } else runBlocking {
            operation()
        }
    }

    fun removePrescriptionImage(index: Int) {
        if (!_uiState.value.isConfigurationEditable) return
        val path = _uiState.value.prescriptionImagePaths.getOrNull(index) ?: return
        runCatching { File(path).delete() }
        _uiState.update {
            it.copy(
                prescriptionImagePaths = it.prescriptionImagePaths.filterIndexed {
                    itemIndex, _ -> itemIndex != index
                },
                prescriptionMessage = null
            )
        }
    }

    fun generate() {
        val consultation = _uiState.value.consultation ?: return
        val exportConsultation = if (_uiState.value.electronicallySign) {
            consultation.copy(
                electronicSignerName = _uiState.value.signerName.trim(),
                electronicallySignedAtMillis = System.currentTimeMillis(),
                handwrittenSignature = _uiState.value.handwrittenSignature,
                prescriptionImagePaths = _uiState.value.prescriptionImagePaths
            )
        } else {
            consultation.copy(
                electronicSignerName = "",
                electronicallySignedAtMillis = null,
                handwrittenSignature = "",
                prescriptionImagePaths = emptyList()
            )
        }
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
            _uiState.value = when (val result = exporter.export(exportConsultation, format)) {
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
                reportRepository = EncryptedConsultationReportRepository(applicationContext),
                prescriptionImageImporter = PrescriptionImageImporter(applicationContext)
            ) as T
        }
    }
}
