package com.carelipik.app.ui.screens.clinicaldraft

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carelipik.app.ui.components.ConsultationScreenHeader

@Composable
fun ClinicalDraftScreen(
    uiState: ClinicalDraftUiState,
    onPresentingComplaintChanged: (String) -> Unit,
    onHistoryChanged: (String) -> Unit,
    onKeyFindingsChanged: (String) -> Unit,
    onAssessmentNotesChanged: (String) -> Unit,
    onPlanNotesChanged: (String) -> Unit,
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
                    label = "Presenting complaint",
                    value = uiState.draft.presentingComplaint,
                    onValueChanged = onPresentingComplaintChanged
                )
                DraftField(
                    label = "History",
                    value = uiState.draft.history,
                    onValueChanged = onHistoryChanged
                )
                DraftField(
                    label = "Key findings",
                    value = uiState.draft.keyFindings,
                    onValueChanged = onKeyFindingsChanged
                )
                DraftField(
                    label = "Assessment notes",
                    value = uiState.draft.assessmentNotes,
                    onValueChanged = onAssessmentNotesChanged
                )
                DraftField(
                    label = "Plan notes",
                    value = uiState.draft.planNotes,
                    onValueChanged = onPlanNotesChanged
                )
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
            Text("Unverified prototype draft", style = MaterialTheme.typography.titleSmall)
            Text(
                "This sample was not extracted from your recording. Do not use it as a medical record. The doctor must verify, edit, or remove every statement.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun DraftField(label: String, value: String, onValueChanged: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        label = { Text(label) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth()
    )
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
