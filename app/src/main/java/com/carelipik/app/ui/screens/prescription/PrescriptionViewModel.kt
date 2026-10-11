package com.carelipik.app.ui.screens.prescription

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.carelipik.app.data.local.DeviceApiKeyProvider
import com.carelipik.app.data.local.EncryptedLetterheadRepository
import com.carelipik.app.data.prescription.GeminiLetterheadConversionEngine
import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.model.MedicationDraft
import com.carelipik.app.domain.prescription.TranscriptMedicineSuggestions
import com.carelipik.app.domain.prescription.ReportPrescriptionSuggestions
import com.carelipik.app.domain.prescription.PrescriptionAdviceSuggestion
import com.carelipik.app.domain.prescription.LetterheadConversionEngine
import com.carelipik.app.domain.prescription.LetterheadRepository
import com.carelipik.app.domain.prescription.PrescriptionTemplates
import com.carelipik.app.domain.prescription.PrescriptionDetail
import com.carelipik.app.domain.repository.ApiProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class PrescriptionUiState(
    val template: String = "",
    val savedTemplate: String = "",
    val source: String = "",
    val suggestions: List<MedicationDraft> = emptyList(),
    val medicines: List<MedicationDraft> = emptyList(),
    val adviceSuggestions: List<PrescriptionAdviceSuggestion> = emptyList(),
    val selectedAdvice: List<PrescriptionAdviceSuggestion> = emptyList(),
    val symptomReferences: List<PrescriptionAdviceSuggestion> = emptyList(),
    val details: Map<PrescriptionDetail, String> = emptyMap(),
    val suggestionSource: String = "Transcript mentions",
    val busy: Boolean = false,
    val loading: Boolean = true,
    val consent: Boolean = false,
    val message: String? = null
)

class PrescriptionViewModel(
    private val repository: LetterheadRepository,
    private val engine: LetterheadConversionEngine,
    private val readPdf: (Uri) -> ByteArray,
    private val writeDocument: (Uri, String) -> Unit,
    private val processAsynchronously: Boolean = true
) : ViewModel() {
    private val state = MutableStateFlow(PrescriptionUiState())
    val uiState = state.asStateFlow()
    init { runOperation {
        val template = repository.load().orEmpty()
        state.update { it.copy(template = template, savedTemplate = template, loading = false) }
    } }
    fun setConsent(value: Boolean) { state.update { it.copy(consent = value) } }
    fun setTemplate(value: String) { state.update { it.copy(template = value.take(100_000)) } }
    fun setSource(value: String) { state.update { it.copy(source = value.take(200_000)) } }
    fun reset() { state.update { it.copy(source = "", suggestions = emptyList(), medicines = emptyList(), adviceSuggestions = emptyList(), selectedAdvice = emptyList(), symptomReferences = emptyList(), details = emptyMap(), message = null) } }
    fun create(draft: ClinicalDraft, patientName: String, doctor: String) {
        if (state.value.loading || state.value.busy) return
        state.update { it.copy(
            symptomReferences = ReportPrescriptionSuggestions.symptoms(draft),
            adviceSuggestions = ReportPrescriptionSuggestions.advice(draft),
            suggestionSource = draft.generationSource.displayName,
            suggestions = TranscriptMedicineSuggestions().let { extractor ->
                val plan = draft.structuredSections.filter { section -> section.id in setOf("plan", "aftercare") }
                    .joinToString("\n") { section -> section.content }.ifBlank { draft.planNotes }
                extractor.extract(draft.reviewedTranscript, draft.medications + draft.discussedMedications +
                    extractor.extractFromPlan(draft.reviewedTranscript, plan))
            },
            source = it.source.ifBlank { PrescriptionTemplates.create(it.savedTemplate, draft, patientName, doctor) }
        ) }
    }
    fun setDetail(field: PrescriptionDetail, value: String) {
        val details = state.value.details + (field to value.take(10_000))
        runCatching { PrescriptionTemplates.updateDetails(state.value.source, details) }
            .onSuccess { source -> state.update { it.copy(details = details, source = source, message = null) } }
            .onFailure { state.update { it.copy(message = "Keep the prescription detail section markers to sync these fields.") } }
    }
    fun addSymptomReference(index: Int) {
        val reference = state.value.symptomReferences.getOrNull(index) ?: return
        val current = state.value.details[PrescriptionDetail.Symptoms].orEmpty()
        setDetail(PrescriptionDetail.Symptoms, listOf(current, reference.content).filter(String::isNotBlank).joinToString("\n"))
    }
    fun addAdviceSuggestion(index: Int) {
        val suggestion = state.value.adviceSuggestions.getOrNull(index) ?: return
        if (suggestion !in state.value.selectedAdvice) syncAdvice(state.value.selectedAdvice + suggestion)
    }
    fun removeAdvice(index: Int) = syncAdvice(state.value.selectedAdvice.filterIndexed { i, _ -> i != index })
    private fun syncAdvice(advice: List<PrescriptionAdviceSuggestion>) {
        runCatching { PrescriptionTemplates.updateAdvice(state.value.source, advice) }
            .onSuccess { source -> state.update { it.copy(selectedAdvice = advice, source = source, message = null) } }
            .onFailure { state.update { it.copy(message = "Restore the advice section markers before adding or removing advice.") } }
    }
    fun addSuggestion(index: Int) {
        val suggestion = state.value.suggestions.getOrNull(index) ?: return
        if (state.value.medicines.any { it.name.equals(suggestion.name, ignoreCase = true) }) return
        syncMedicines(state.value.medicines + suggestion)
    }
    fun addMedicine() = syncMedicines(state.value.medicines + MedicationDraft())
    fun updateMedicine(index: Int, medicine: MedicationDraft) {
        if (index !in state.value.medicines.indices) return
        syncMedicines(state.value.medicines.toMutableList().also { it[index] = medicine })
    }
    fun removeMedicine(index: Int) {
        syncMedicines(state.value.medicines.filterIndexed { i, _ -> i != index })
    }
    private fun syncMedicines(medicines: List<MedicationDraft>) {
        val updated = runCatching { PrescriptionTemplates.updateMedicines(state.value.source, medicines) }
        updated.onSuccess { source -> state.update { it.copy(medicines = medicines, source = source, message = null) } }
            .onFailure { state.update { it.copy(message = "Restore the medicine section markers in the LaTeX source before adding, editing or removing medicines.") } }
    }
    fun saveTemplate() = runOperation {
        val template = state.value.template
        repository.save(template)
        state.update { it.copy(savedTemplate = template, message = "Letterhead template saved on this device.") }
    }
    fun convert(uri: Uri) {
        if (!state.value.consent) return
        runOperation {
            val template = engine.convert(readPdf(uri))
            state.update { it.copy(template = template, message = "Review the converted letterhead, then save it.") }
        }
    }
    fun export(uri: Uri) {
        val source = state.value.source
        if (state.value.medicines.any { it.name.isBlank() || it.dose.isBlank() || it.frequency.isBlank() || it.duration.isBlank() }) {
            state.update { it.copy(message = "Complete each medicine's name, dose, times of day and number of days before export.") }
            return
        }
        runOperation {
            require(source.isNotBlank()) { "Create a prescription first." }
            require(state.value.medicines.all {
                it.name.isNotBlank() && it.dose.isNotBlank() && it.frequency.isNotBlank() && it.duration.isNotBlank()
            }) { "Complete each medicine's name, dose, times and duration before export." }
            writeDocument(uri, source)
            state.update { it.copy(message = "LaTeX source exported.") }
        }
    }
    private fun runOperation(action: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, message = null) }
        suspend fun perform() {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { state.update { it.copy(message = "Operation failed. Check the image/PDF (maximum 8 MB), saved Gemini key, connection or LaTeX template.") } }
            finally { state.update { it.copy(busy = false, loading = false) } }
        }
        if (processAsynchronously) viewModelScope.launch { withContext(Dispatchers.IO) { perform() } }
        else runBlocking { perform() }
    }

    class Factory(context: Context) : ViewModelProvider.Factory {
        private val context = context.applicationContext
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val keys = DeviceApiKeyProvider(context)
            return PrescriptionViewModel(
                EncryptedLetterheadRepository(context),
                GeminiLetterheadConversionEngine { keys.get(ApiProvider.Gemini) },
                { uri ->
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var count = input.read(buffer)
                        while (count != -1) {
                            require(output.size() + count <= 8 * 1024 * 1024)
                            output.write(buffer, 0, count)
                            count = input.read(buffer)
                        }
                        output.toByteArray()
                    } ?: error("PDF unavailable")
                    com.carelipik.app.domain.prescription.LetterheadMedia.mimeType(bytes)
                    bytes
                },
                { uri, source -> context.contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write(source.toByteArray(Charsets.UTF_8))
                } ?: error("Destination unavailable") }
            ) as T
        }
    }
}
