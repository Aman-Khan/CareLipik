package com.carelipik.app.ui.screens.clinicaldraft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carelipik.app.domain.model.MedicationDraft
import com.carelipik.app.domain.model.ClinicalNoteFormat
import com.carelipik.app.domain.model.ClinicalNoteLanguage
import com.carelipik.app.ui.components.ConsultationScreenHeader

@Composable
fun ClinicalDraftScreen(
    uiState: ClinicalDraftUiState,
    onPatientAgeChanged: (String) -> Unit,
    onNoteFormatSelected: (ClinicalNoteFormat) -> Unit,
    onNoteLanguageSelected: (ClinicalNoteLanguage) -> Unit,
    onSpecialtyNameChanged: (String) -> Unit,
    onSectionChanged: (Int, String) -> Unit,
    onOnlineGenerationConsentChanged: (Boolean) -> Unit,
    onGenerateWithGemini: () -> Unit,
    onAddMedication: () -> Unit,
    onMedicationChanged: (Int, MedicationDraft) -> Unit,
    onRemoveMedication: (Int) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ConsultationScreenHeader(
            title = "Clinical draft",
            subtitle = "Turn the reviewed transcript into structured notes, then verify every section.",
            currentStep = 5,
            totalSteps = 7,
            onBack = onBack,
            backEnabled = uiState.status != ClinicalDraftStatus.Processing
        )
        ReviewNotice()
        when (uiState.status) {
            ClinicalDraftStatus.Idle,
            ClinicalDraftStatus.Processing -> ProcessingDraft()
            ClinicalDraftStatus.Error -> DraftError(
                message = uiState.errorMessage ?: "The draft could not be prepared.",
                onRetry = onRetry
            )
            ClinicalDraftStatus.Ready -> {
                DraftField(
                    label = "Patient age from reviewed transcript",
                    value = uiState.draft.patientAge,
                    onValueChanged = onPatientAgeChanged,
                    minLines = 1
                )
                NoteFormatOptions(
                    selected = uiState.draft.noteFormat,
                    onSelected = onNoteFormatSelected
                )
                NoteLanguageOptions(
                    selected = uiState.draft.noteLanguage,
                    onSelected = onNoteLanguageSelected
                )
                if (uiState.draft.noteFormat == ClinicalNoteFormat.CustomSpecialty) {
                    DraftField(
                        label = "Clinical specialty",
                        value = uiState.draft.specialtyName,
                        onValueChanged = onSpecialtyNameChanged,
                        minLines = 1
                    )
                }
                GeminiGenerationCard(
                    hasConsent = uiState.hasOnlineGenerationConsent,
                    isGenerating = uiState.isGeneratingOnline,
                    generationSource = uiState.draft.generationSource.displayName,
                    error = uiState.onlineGenerationError,
                    onConsentChanged = onOnlineGenerationConsentChanged,
                    onGenerate = onGenerateWithGemini
                )
                if (uiState.draft.coverageWarnings.isNotEmpty()) {
                    CoverageWarnings(uiState.draft.coverageWarnings)
                }
                Text(
                    "${uiState.draft.noteFormat.displayName} sections",
                    style = MaterialTheme.typography.titleMedium
                )
                uiState.draft.structuredSections.forEachIndexed { index, section ->
                    DraftField(
                        label = section.title,
                        value = section.content,
                        onValueChanged = { onSectionChanged(index, it) }
                    )
                    if (section.sourceTurnIds.isNotEmpty()) {
                        Text(
                            "Transcript evidence: ${section.sourceTurnIds.joinToString()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                MedicationEditor(
                    medications = uiState.draft.medications,
                    onAdd = onAddMedication,
                    onChanged = onMedicationChanged,
                    onRemove = onRemoveMedication
                )
                ReadOnlySourceTranscript(uiState.draft.reviewedTranscript)
                uiState.draftError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue to doctor review")
                }
            }
        }
    }
}

@Composable
private fun NoteFormatOptions(
    selected: ClinicalNoteFormat,
    onSelected: (ClinicalNoteFormat) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Clinical note format", style = MaterialTheme.typography.titleMedium)
        ClinicalNoteFormat.entries.forEach { format ->
            Card(
                onClick = { onSelected(format) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (format == selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(format.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(format.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun NoteLanguageOptions(
    selected: ClinicalNoteLanguage,
    onSelected: (ClinicalNoteLanguage) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Note language", style = MaterialTheme.typography.titleMedium)
        ClinicalNoteLanguage.entries.forEach { language ->
            Card(
                onClick = { onSelected(language) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (language == selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Text(language.displayName, modifier = Modifier.padding(12.dp))
            }
        }
        Text(
            "English generation translates the reviewed clinical content but keeps the original " +
                "conversation log unchanged.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun GeminiGenerationCard(
    hasConsent: Boolean,
    isGenerating: Boolean,
    generationSource: String,
    error: String?,
    onConsentChanged: (Boolean) -> Unit,
    onGenerate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Gemini structured note", style = MaterialTheme.typography.titleMedium)
            Text("Current source: $generationSource")
            Text(
                "Only the reviewed transcript and limited consultation metadata are sent. " +
                    "Audio is never sent. The result remains an unverified draft.",
                style = MaterialTheme.typography.bodySmall
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = hasConsent,
                    onCheckedChange = onConsentChanged,
                    enabled = !isGenerating
                )
                Text("I have consent to send this reviewed transcript for online drafting")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = onGenerate,
                enabled = hasConsent && !isGenerating,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isGenerating) "Generating…" else "Generate selected note with Gemini")
            }
        }
    }
}

@Composable
private fun CoverageWarnings(warnings: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Transcript coverage warnings", style = MaterialTheme.typography.titleSmall)
            warnings.forEach { warning -> Text("• $warning") }
            Text("Review the conversation log and add missing information before approval.")
        }
    }
}

@Composable
private fun MedicationEditor(
    medications: List<MedicationDraft>,
    onAdd: () -> Unit,
    onChanged: (Int, MedicationDraft) -> Unit,
    onRemove: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Prescribed medicines and dosages", style = MaterialTheme.typography.titleMedium)
        Text(
            "Add only medicines explicitly prescribed in this consultation. Verify every name, " +
                "strength, dose, route, frequency, and duration before continuing.",
            style = MaterialTheme.typography.bodyMedium
        )
        medications.forEachIndexed { index, medication ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Medicine ${index + 1}", style = MaterialTheme.typography.titleSmall)
                    MedicationField("Medicine name", medication.name) {
                        onChanged(index, medication.copy(name = it, isDoctorReviewed = false))
                    }
                    MedicationField("Generic name / salt", medication.genericName) {
                        onChanged(index, medication.copy(genericName = it, isDoctorReviewed = false))
                    }
                    MedicationField("Strength", medication.strength) {
                        onChanged(index, medication.copy(strength = it, isDoctorReviewed = false))
                    }
                    MedicationField("Dose", medication.dose) {
                        onChanged(index, medication.copy(dose = it, isDoctorReviewed = false))
                    }
                    MedicationField("Route", medication.route) {
                        onChanged(index, medication.copy(route = it, isDoctorReviewed = false))
                    }
                    MedicationField("Frequency", medication.frequency) {
                        onChanged(index, medication.copy(frequency = it, isDoctorReviewed = false))
                    }
                    MedicationField("Duration", medication.duration) {
                        onChanged(index, medication.copy(duration = it, isDoctorReviewed = false))
                    }
                    MedicationField("Instructions", medication.instructions) {
                        onChanged(index, medication.copy(instructions = it, isDoctorReviewed = false))
                    }
                    if (medication.sourceEvidence.isNotBlank()) {
                        Text(
                            "Transcript evidence: ${medication.sourceEvidence}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = medication.isDoctorReviewed,
                            enabled = medication.hasContent,
                            onCheckedChange = {
                                onChanged(index, medication.copy(isDoctorReviewed = it))
                            }
                        )
                        Text("I verified this medicine and dosage")
                    }
                    OutlinedButton(onClick = { onRemove(index) }) {
                        Text("Remove medicine")
                    }
                }
            }
        }
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
            Text("Add prescribed medicine")
        }
    }
}

@Composable
private fun MedicationField(label: String, value: String, onValueChanged: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ReviewNotice() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Transcript-backed draft", style = MaterialTheme.typography.titleSmall)
            Text(
                "Patient statements are copied from the reviewed transcript without clinical " +
                    "interpretation. Organize and correct every section before approval. The " +
                    "complete reviewed transcript is retained as a source appendix.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun DraftField(
    label: String,
    value: String,
    onValueChanged: (String) -> Unit,
    minLines: Int = 3
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        label = { Text(label) },
        minLines = minLines,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun ReadOnlySourceTranscript(transcript: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Reviewed transcript appendix", style = MaterialTheme.typography.titleSmall)
            Text(
                transcript.ifBlank { "No reviewed transcript available" },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ProcessingDraft() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator()
        Text("Preparing structured draft…", style = MaterialTheme.typography.titleMedium)
        Text("Processing stays on this device.")
    }
}

@Composable
private fun DraftError(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Draft unavailable", style = MaterialTheme.typography.titleMedium)
            Text(message)
            OutlinedButton(onClick = onRetry) { Text("Try again") }
        }
    }
}
