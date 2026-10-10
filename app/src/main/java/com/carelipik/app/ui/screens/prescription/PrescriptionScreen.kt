package com.carelipik.app.ui.screens.prescription

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.carelipik.app.domain.prescription.Prescription
import com.carelipik.app.domain.prescription.PrescriptionExporter
import com.carelipik.app.ui.components.ConsultationScreenHeader

@Composable
fun PrescriptionScreen(
    uiState: PrescriptionUiState,
    viewModel: PrescriptionViewModel,
    exporter: PrescriptionExporter,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pdfDownload = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) {
        it?.let { uri -> viewModel.save(exporter, uri.toString(), latex = false) }
    }
    val texDownload = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-tex")) {
        it?.let { uri -> viewModel.save(exporter, uri.toString(), latex = true) }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
        .navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ConsultationScreenHeader(title = "Report and prescription", subtitle = "Review the report or prepare a prescription from discussed medicines.",
            currentStep = 6, totalSteps = 8, onBack = onBack, backEnabled = !uiState.busy)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = !uiState.showPrescription, onClick = { viewModel.selectTab(false) }, label = { Text("Report") })
            FilterChip(selected = uiState.showPrescription, onClick = { viewModel.selectTab(true) }, label = { Text("Prescription") })
        }
        if (!uiState.showPrescription) {
            Text(uiState.report.noteFormat.displayName, style = MaterialTheme.typography.titleLarge)
            uiState.report.effectiveSections.forEach { section ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                    Text(section.content.ifBlank { "Not documented" })
                } }
            }
            OutlinedButton(onClick = onBack, enabled = !uiState.busy) { Text("Edit report") }
        } else {
            val rx = uiState.prescription
            Text("Common prescription template", style = MaterialTheme.typography.titleLarge)
            Text("Select mentions for review. A mentioned medicine may be historical, declined or discussed without being prescribed. Only medicines you add appear in the PDF.")
            fun update(block: Prescription.() -> Prescription) = viewModel.update(block)
            Field("Clinic", rx.clinicName, uiState.busy) { update { copy(clinicName = it) } }
            Field("Doctor name", rx.doctorName, uiState.busy) { update { copy(doctorName = it) } }
            Field("Registration number", rx.registrationNumber, uiState.busy) { update { copy(registrationNumber = it) } }
            Field("Patient name", rx.patientName, uiState.busy) { update { copy(patientName = it) } }
            Field("Age", rx.patientAge, uiState.busy) { update { copy(patientAge = it) } }
            Field("Date", rx.date, uiState.busy) { update { copy(date = it) } }
            Text("Medicines mentioned", style = MaterialTheme.typography.titleMedium)
            if (uiState.suggestions.isEmpty()) Text("No medicine names recognized. Add a medicine manually below.")
            uiState.suggestions.forEach { suggestion ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(suggestion.name, style = MaterialTheme.typography.titleMedium)
                    if (suggestion.sourceEvidence.isNotBlank()) Text(suggestion.sourceEvidence, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { viewModel.add(suggestion) }, enabled = !uiState.busy && rx.medicines.size < 30) { Text("Add to prescription") }
                } }
            }
            rx.medicines.forEachIndexed { index, item ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Medicine ${index + 1}", style = MaterialTheme.typography.titleMedium)
                    val med = item.medicine
                    Field("Medicine name", med.name, uiState.busy) { viewModel.change(item.id, item.copy(medicine = med.copy(name = it))) }
                    Field("Generic name (optional)", med.genericName, uiState.busy) { viewModel.change(item.id, item.copy(medicine = med.copy(genericName = it))) }
                    Field("Strength", med.strength, uiState.busy) { viewModel.change(item.id, item.copy(medicine = med.copy(strength = it))) }
                    Field("Dose", med.dose, uiState.busy) { viewModel.change(item.id, item.copy(medicine = med.copy(dose = it))) }
                    Field("Route", med.route, uiState.busy) { viewModel.change(item.id, item.copy(medicine = med.copy(route = it))) }
                    Field("Frequency", med.frequency, uiState.busy) { viewModel.change(item.id, item.copy(medicine = med.copy(frequency = it))) }
                    Field("Duration", med.duration, uiState.busy) { viewModel.change(item.id, item.copy(medicine = med.copy(duration = it))) }
                    Field("Instructions", med.instructions, uiState.busy) { viewModel.change(item.id, item.copy(medicine = med.copy(instructions = it))) }
                    OutlinedTextField(value = item.quantity, onValueChange = { value ->
                        if (value.length <= 4 && value.all(Char::isDigit)) viewModel.change(item.id, item.copy(quantity = value))
                    }, label = { Text("Quantity / count") }, enabled = !uiState.busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { viewModel.remove(item.id) }, enabled = !uiState.busy) { Text("Remove medicine") }
                } }
            }
            OutlinedButton(onClick = { viewModel.add() }, enabled = !uiState.busy && rx.medicines.size < 30) { Text("Add medicine manually") }
            Field("Advice", rx.advice, uiState.busy) { update { copy(advice = it) } }
            Field("Follow-up", rx.followUp, uiState.busy) { update { copy(followUp = it) } }
            Row {
                Checkbox(checked = uiState.confirmed, onCheckedChange = viewModel::confirm, enabled = !uiState.busy)
                Text("I have reviewed the medicines, dosage instructions and quantities in this prescription.", Modifier.padding(top = 12.dp))
            }
            if (uiState.busy) {
                CircularProgressIndicator()
                Text(uiState.progress.ifBlank { "Saving prescription…" })
                TextButton(onClick = viewModel::cancel) { Text("Cancel") }
            }
            Button(onClick = { viewModel.generate(exporter) }, enabled = !uiState.busy && uiState.confirmed,
                modifier = Modifier.fillMaxWidth()) { Text("Create prescription PDF") }
            uiState.files?.let { files ->
                Button(onClick = { pdfDownload.launch("${files.baseName}.pdf") }, enabled = !uiState.busy) { Text("Download prescription PDF") }
                OutlinedButton(onClick = { texDownload.launch("${files.baseName}.tex") }, enabled = !uiState.busy) { Text("Download LaTeX source") }
            }
        }
        uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        uiState.message?.let { Text(it) }
        Button(onClick = onContinue, enabled = !uiState.busy, modifier = Modifier.fillMaxWidth()) { Text("Continue to doctor review") }
    }
}

@Composable
private fun Field(label: String, value: String, busy: Boolean, onChanged: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = { if (it.length <= 500) onChanged(it) },
        label = { Text(label) }, enabled = !busy, modifier = Modifier.fillMaxWidth())
}
