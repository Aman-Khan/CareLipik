package com.carelipik.app.ui.screens.export

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carelipik.app.data.export.AndroidConsultationPdfExporter
import com.carelipik.app.domain.export.ConsultationPdf
import com.carelipik.app.domain.export.ConsultationPdfExportResult
import com.carelipik.app.domain.export.ConsultationPdfExporter
import com.carelipik.app.domain.model.ApprovedConsultation
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
    val pdf: ConsultationPdf? = null,
    val errorMessage: String? = null
) {
    val canGenerate: Boolean
        get() = consultation != null && status !in setOf(
            ConsultationExportStatus.Generating,
            ConsultationExportStatus.Empty
        )
}

class ConsultationExportViewModel(
    private val exporter: ConsultationPdfExporter,
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

    fun generate() {
        val consultation = _uiState.value.consultation ?: return
        if (_uiState.value.status == ConsultationExportStatus.Generating) return
        _uiState.value = _uiState.value.copy(
            status = ConsultationExportStatus.Generating,
            pdf = null,
            errorMessage = null
        )
        val operation: suspend () -> Unit = {
            _uiState.value = when (val result = exporter.export(consultation)) {
                is ConsultationPdfExportResult.Success -> _uiState.value.copy(
                    status = ConsultationExportStatus.Generated,
                    pdf = result.pdf,
                    errorMessage = null
                )
                is ConsultationPdfExportResult.Failure -> _uiState.value.copy(
                    status = ConsultationExportStatus.Error,
                    pdf = null,
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
                AndroidConsultationPdfExporter(applicationContext)
            ) as T
        }
    }
}
