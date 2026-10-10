package com.carelipik.app.ui.screens.prescription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.carelipik.app.domain.model.ClinicalDraft
import com.carelipik.app.domain.model.DoctorProfile
import com.carelipik.app.domain.model.MedicationDraft
import com.carelipik.app.domain.prescription.DiscussedMedicineSuggestions
import com.carelipik.app.domain.prescription.Prescription
import com.carelipik.app.domain.prescription.PrescriptionExporter
import com.carelipik.app.domain.prescription.PrescriptionFiles
import com.carelipik.app.domain.prescription.PrescriptionMedicine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrescriptionUiState(
    val prescription: Prescription = Prescription(),
    val report: ClinicalDraft = ClinicalDraft(),
    val suggestions: List<MedicationDraft> = emptyList(),
    val showPrescription: Boolean = true,
    val confirmed: Boolean = false,
    val busy: Boolean = false,
    val progress: String = "",
    val files: PrescriptionFiles? = null,
    val message: String? = null,
    val error: String? = null
)

class PrescriptionViewModel : ViewModel() {
    private val state = MutableStateFlow(PrescriptionUiState())
    val uiState = state.asStateFlow()
    private var job: Job? = null
    private var initialized = false

    fun load(draft: ClinicalDraft, patientName: String, age: String, doctor: DoctorProfile?) {
        state.update { it.copy(report = draft, suggestions = DiscussedMedicineSuggestions.from(draft)) }
        if (initialized) return
        initialized = true
        state.update { it.copy(prescription = Prescription(patientName = patientName,
            patientAge = age.ifBlank { draft.patientAge }, doctorName = doctor?.fullName.orEmpty(),
            registrationNumber = doctor?.registrationNumber.orEmpty(), clinicName = doctor?.clinicName.orEmpty(),
            date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()))) }
    }

    fun reset() { job?.cancel(); initialized = false; state.value = PrescriptionUiState() }
    fun selectTab(prescription: Boolean) { state.update { it.copy(showPrescription = prescription) } }
    fun confirm(value: Boolean) { if (!state.value.busy) state.update { it.copy(confirmed = value, files = null) } }
    fun update(transform: Prescription.() -> Prescription) {
        if (!state.value.busy) state.update { it.copy(prescription = it.prescription.transform(), confirmed = false,
            files = null, message = null, error = null) }
    }
    fun add(medicine: MedicationDraft = MedicationDraft()) {
        if (state.value.prescription.medicines.size >= 30) return
        update { copy(medicines = medicines + PrescriptionMedicine(UUID.randomUUID().toString(),
            medicine.copy(isDoctorReviewed = false))) }
    }
    fun change(id: String, medicine: PrescriptionMedicine) = update {
        copy(medicines = medicines.map { if (it.id == id) medicine else it })
    }
    fun remove(id: String) = update { copy(medicines = medicines.filterNot { it.id == id }) }
    fun cancel() { job?.cancel(); state.update { it.copy(busy = false) } }

    fun generate(exporter: PrescriptionExporter) {
        if (state.value.busy) return
        val value = state.value
        val prescription = value.prescription
        val error = when {
            prescription.patientName.isBlank() -> "Enter the patient name."
            prescription.date.isBlank() -> "Enter the prescription date."
            prescription.doctorName.isBlank() || prescription.registrationNumber.isBlank() -> "Enter the doctor name and registration number."
            prescription.medicines.isEmpty() -> "Add at least one medicine."
            prescription.medicines.any { it.medicine.name.isBlank() || it.quantity.toIntOrNull()?.let { n -> n in 1..9999 } != true } ->
                "Every medicine needs a name and a quantity from 1 to 9999."
            prescription.medicines.any {
                with(it.medicine) { listOf(strength, dose, route, frequency, duration, instructions).sumOf(String::length) > 1500 }
            } -> "Shorten each medicine's directions to 1500 characters or fewer."
            !value.confirmed -> "Confirm that you have reviewed this prescription."
            else -> null
        }
        if (error != null) { state.update { it.copy(error = error) }; return }
        state.update { it.copy(busy = true, error = null, files = null, message = null) }
        job = viewModelScope.launch {
            try {
                val files = exporter.generate(prescription) { detail -> state.update { it.copy(progress = detail) } }
                state.update { it.copy(files = files, busy = false, message = "Prescription PDF is ready to download.") }
            } catch (_: TimeoutCancellationException) {
                state.update { it.copy(busy = false, error = "LaTeX compilation timed out after 3 minutes. Please retry.") }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { state.update { it.copy(busy = false, error = "LaTeX compilation failed. Please retry or check the offline compiler setup.") } }
        }
    }

    fun save(exporter: PrescriptionExporter, destination: String, latex: Boolean) {
        val files = state.value.files ?: return
        if (state.value.busy) return
        state.update { it.copy(busy = true, error = null, message = null) }
        job = viewModelScope.launch {
            try {
                exporter.save(if (latex) files.latexPath else files.pdfPath, destination)
                state.update { it.copy(busy = false, message = "${if (latex) "LaTeX source" else "Prescription PDF"} saved.") }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { state.update { it.copy(busy = false, error = "The document could not be saved. Choose a destination and retry.") } }
        }
    }
}
