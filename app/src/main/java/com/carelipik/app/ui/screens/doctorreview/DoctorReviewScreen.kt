package com.carelipik.app.ui.screens.doctorreview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.carelipik.app.ui.components.ConsultationScreenHeader

@Composable
fun DoctorReviewScreen(
    uiState: DoctorReviewUiState,
    onConfirmationChanged: (Boolean) -> Unit,
    onIncludeTranscriptChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    onApprove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ConsultationScreenHeader(
            title = "Doctor review",
            subtitle = "Read the complete note before approving it for export.",
            currentStep = 6,
            totalSteps = 7,
            onBack = onBack
        )
        SafetyNotice()
        ReviewSection(
            "Note format",
            buildString {
                append(uiState.draft.noteFormat.displayName)
                append(" • ${uiState.draft.noteLanguage.displayName}")
                append(" • ${uiState.draft.generationSource.displayName}")
            }
        )
        if (uiState.missingSections.isNotEmpty()) {
            MissingInformationCard(uiState.missingSections)
        }
        if (uiState.draft.coverageWarnings.isNotEmpty()) {
            CoverageWarningCard(uiState.draft.coverageWarnings)
        }
        ReviewSection("Patient age from transcript", uiState.draft.patientAge)
        uiState.draft.effectiveSections.forEach { section ->
            ReviewSection(section.title, section.content)
        }
        MedicationReview(uiState.draft.medications)
        ReviewSection("Complete reviewed transcript", uiState.draft.reviewedTranscript)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = uiState.includeReviewedTranscriptInExport,
                    role = Role.Checkbox,
                    onValueChange = onIncludeTranscriptChanged
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = uiState.includeReviewedTranscriptInExport,
                onCheckedChange = null
            )
            Text(
                "Include complete reviewed transcript in final report",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        uiState.medicationError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        ConfirmationCard(
            isConfirmed = uiState.hasConfirmedReview,
            error = uiState.confirmationError,
            onConfirmationChanged = onConfirmationChanged
        )
        Button(onClick = onApprove, modifier = Modifier.fillMaxWidth()) {
            Text("Approve and continue to export")
        }
    }
}

@Composable
private fun CoverageWarningCard(warnings: List<String>) {
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
            warnings.forEach { Text("• $it") }
            Text("Approval confirms that the doctor reviewed these possible omissions.")
        }
    }
}

@Composable
private fun MedicationReview(medications: List<com.carelipik.app.domain.model.MedicationDraft>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Prescribed medicines and dosages", style = MaterialTheme.typography.titleMedium)
            if (medications.isEmpty()) {
                Text("No prescribed medicines documented")
            }
            medications.forEach { medication ->
                val details = listOf(
                    medication.genericName,
                    medication.strength,
                    medication.dose,
                    medication.route,
                    medication.frequency,
                    medication.duration,
                    medication.instructions
                ).filter(String::isNotBlank).joinToString(" • ")
                Text(
                    buildString {
                        append(medication.name.ifBlank { "Unnamed medicine" })
                        if (details.isNotBlank()) append(" — $details")
                        append(if (medication.isDoctorReviewed) " — Doctor verified" else " — Review required")
                    },
                    color = if (medication.isDoctorReviewed) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
        }
    }
}

@Composable
private fun SafetyNotice() {
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
            Text("Doctor approval required", style = MaterialTheme.typography.titleSmall)
            Text(
                "CareLipik assists with documentation. It does not verify clinical accuracy, diagnose, or prescribe.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MissingInformationCard(missingSections: List<String>) {
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
            Text("Incomplete sections", style = MaterialTheme.typography.titleSmall)
            Text(missingSections.joinToString(separator = " • "))
            Text("Go back to complete them or approve only if they are intentionally blank.")
        }
    }
}

@Composable
private fun ReviewSection(title: String, content: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                content.ifBlank { "Not documented" },
                style = MaterialTheme.typography.bodyLarge,
                color = if (content.isBlank()) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun ConfirmationCard(
    isConfirmed: Boolean,
    error: String?,
    onConfirmationChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Doctor review confirmation" }
                    .toggleable(
                        value = isConfirmed,
                        role = Role.Checkbox,
                        onValueChange = onConfirmationChanged
                    )
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = isConfirmed, onCheckedChange = null)
                Text(
                    "I reviewed and corrected this clinical draft and accept responsibility for its contents.",
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
