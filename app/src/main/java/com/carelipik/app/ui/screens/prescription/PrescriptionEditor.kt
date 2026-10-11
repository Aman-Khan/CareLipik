package com.carelipik.app.ui.screens.prescription

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.content.pm.PackageManager
import android.Manifest
import android.net.Uri
import java.io.File
import com.carelipik.app.domain.model.MedicationDraft
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.carelipik.app.domain.prescription.PrescriptionTemplates
import com.carelipik.app.domain.prescription.PrescriptionDetail

@Composable
fun LetterheadSetup(state: PrescriptionUiState, viewModel: PrescriptionViewModel) {
    val context = LocalContext.current
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) captureUri?.let { viewModel.convert(Uri.parse(it)) }
        else cameraMessage = "Photo cancelled. You can try again or import a file."
    }
    val takePhoto = {
        runCatching {
            val directory = File(context.cacheDir, "letterhead_capture").apply { mkdirs() }
            val file = File(directory, "letterhead.jpg")
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            captureUri = uri.toString()
            camera.launch(uri)
        }.onFailure { cameraMessage = "Camera unavailable. Import an image or PDF instead." }
        Unit
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePhoto() else cameraMessage = "Camera permission denied. Image and PDF import remain available."
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::convert)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Prescription letterhead", style = MaterialTheme.typography.titleLarge)
            Text("Photograph or import an image/PDF of a blank letter pad. Gemini converts its header and footer to LaTeX using the key saved above. Review and save the template for offline prescriptions.")
            Row {
                Checkbox(checked = state.consent, onCheckedChange = viewModel::setConsent, enabled = !state.busy)
                Text("Send this blank letterhead image or PDF to Gemini for conversion")
            }
            Button(onClick = { picker.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp")) }, enabled = state.consent && !state.busy) {
                Text(if (state.busy) "Working…" else "Import letter pad image/PDF")
            }
            OutlinedButton(onClick = {
                cameraMessage = null
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) takePhoto()
                else permission.launch(Manifest.permission.CAMERA)
            }, enabled = state.consent && !state.busy) { Text("Photograph letter pad") }
            cameraMessage?.let { Text(it) }
            OutlinedTextField(value = state.template, onValueChange = viewModel::setTemplate,
                label = { Text("Letterhead LaTeX template") }, minLines = 6, maxLines = 14,
                enabled = !state.busy, textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth())
            Text("Generated templates contain placeholders for doctor details, patient details and the prescription body. A blank template uses the common prescription layout.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::saveTemplate, enabled = !state.busy && state.template.isNotBlank()) { Text("Save letterhead") }
                OutlinedButton(onClick = { viewModel.setTemplate(PrescriptionTemplates.common) }, enabled = !state.busy) { Text("Use common layout") }
            }
            state.message?.let { Text(it) }
        }
    }
}

@Composable
fun PrescriptionEditor(state: PrescriptionUiState, viewModel: PrescriptionViewModel, onCreate: () -> Unit) {
    LaunchedEffect(state.loading, state.busy) { if (!state.loading && !state.busy) onCreate() }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-tex")) { uri ->
        uri?.let(viewModel::export)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Prescription", style = MaterialTheme.typography.titleLarge)
        Text("Layout: " + if (state.savedTemplate.isBlank() || state.savedTemplate == PrescriptionTemplates.common) "Common prescription" else "Saved doctor letterhead")
        Text("Review transcript medicine mentions below. Only medicines you explicitly add appear in the prescription.")
        Text("Suggested — ${state.suggestionSource}")
        Text("Symptoms discussed — reference", style = MaterialTheme.typography.titleMedium)
        if (state.symptomReferences.isEmpty()) Text("Symptom references will appear after model processing. Confirm them against the transcript.")
        state.symptomReferences.forEachIndexed { index, reference ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(reference.content)
                    OutlinedButton(onClick = { viewModel.addSymptomReference(index) }, enabled = state.source.isNotBlank()) {
                        Text("Add to symptoms field")
                    }
                }
            }
        }
        Text("Prescription details", style = MaterialTheme.typography.titleMedium)
        PrescriptionDetail.entries.forEach { field ->
            OutlinedTextField(value = state.details[field].orEmpty(),
                onValueChange = { viewModel.setDetail(field, it) }, label = { Text(field.label) },
                minLines = 2, maxLines = 5, enabled = state.source.isNotBlank(), modifier = Modifier.fillMaxWidth())
        }
        Text("Medicine suggestions", style = MaterialTheme.typography.titleMedium)
        if (state.suggestions.isEmpty()) Text("No explicit medicine mentions found. You can add a medicine manually.")
        state.suggestions.forEachIndexed { index, suggestion ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(suggestion.name, style = MaterialTheme.typography.titleMedium)
                    Text(listOf(suggestion.strength, suggestion.dose, suggestion.frequency, suggestion.duration)
                        .filter(String::isNotBlank).joinToString(" · ").ifBlank { "Dose, timing and duration need doctor input." })
                    val added = state.medicines.any { it.name.equals(suggestion.name, ignoreCase = true) }
                    Button(onClick = { viewModel.addSuggestion(index) }, enabled = !added && state.source.isNotBlank()) {
                        Text(if (added) "Added to prescription" else "Add to prescription")
                    }
                }
            }
        }
        Text("Advice and follow-up suggestions", style = MaterialTheme.typography.titleMedium)
        Text("Review each advice, precaution, investigation or follow-up item before adding.")
        if (state.adviceSuggestions.isEmpty()) Text("No advice suggestions available from this consultation.")
        state.adviceSuggestions.forEachIndexed { index, suggestion ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(suggestion.title, style = MaterialTheme.typography.titleMedium)
                    Text(suggestion.content)
                    val added = suggestion in state.selectedAdvice
                    Button(onClick = { viewModel.addAdviceSuggestion(index) }, enabled = !added && state.source.isNotBlank()) {
                        Text(if (added) "Advice added" else "Add advice to prescription")
                    }
                }
            }
        }
        state.selectedAdvice.forEachIndexed { index, advice ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(advice.content)
                    OutlinedButton(onClick = { viewModel.removeAdvice(index) }) { Text("Remove advice from prescription") }
                }
            }
        }
        Text("Doctor-selected medicines", style = MaterialTheme.typography.titleMedium)
        if (state.medicines.isEmpty()) Text("No medicines added to this prescription.")
        state.medicines.forEachIndexed { index, medicine ->
            PrescriptionMedicineCard(medicine, index, viewModel)
        }
        OutlinedButton(onClick = viewModel::addMedicine, enabled = state.source.isNotBlank()) { Text("Add medicine manually") }
        Button(onClick = onCreate, enabled = !state.busy && !state.loading) {
            Text("Refresh transcript suggestions")
        }
        PrescriptionPdfPreview(state)
        var showSource by rememberSaveable { mutableStateOf(false) }
        OutlinedButton(onClick = { showSource = !showSource }) {
            Text(if (showSource) "Hide LaTeX source" else "Advanced: edit LaTeX source")
        }
        if (showSource) {
            Text("Keep the section markers so prescription fields continue to sync.")
            OutlinedTextField(value = state.source, onValueChange = viewModel::setSource,
                label = { Text("Prescription LaTeX source") }, minLines = 16, maxLines = 30,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth())
            Text("Export .tex for full LaTeX compilation in an external editor.")
            OutlinedButton(onClick = { exporter.launch("prescription.tex") }, enabled = state.source.isNotBlank() && !state.busy) { Text("Export .tex") }
        }
        state.message?.let { Text(it) }
    }
}

@Composable
private fun PrescriptionMedicineCard(medicine: MedicationDraft, index: Int, viewModel: PrescriptionViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Medicine ${index + 1}", style = MaterialTheme.typography.titleMedium)
            PrescriptionMedicineField("Medicine name", medicine.name) { viewModel.updateMedicine(index, medicine.copy(name = it)) }
            PrescriptionMedicineField("Strength", medicine.strength) { viewModel.updateMedicine(index, medicine.copy(strength = it)) }
            PrescriptionMedicineField("Dose per intake (e.g. 1 tablet)", medicine.dose) { viewModel.updateMedicine(index, medicine.copy(dose = it)) }
            PrescriptionMedicineField("Times of day / frequency (e.g. morning, night)", medicine.frequency) { viewModel.updateMedicine(index, medicine.copy(frequency = it)) }
            PrescriptionMedicineField("Duration / number of days (e.g. 5 days)", medicine.duration) { viewModel.updateMedicine(index, medicine.copy(duration = it)) }
            PrescriptionMedicineField("Instructions", medicine.instructions) { viewModel.updateMedicine(index, medicine.copy(instructions = it)) }
            OutlinedButton(onClick = { viewModel.removeMedicine(index) }) { Text("Remove from prescription") }
        }
    }
}

@Composable
private fun PrescriptionMedicineField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, modifier = Modifier.fillMaxWidth())
}
