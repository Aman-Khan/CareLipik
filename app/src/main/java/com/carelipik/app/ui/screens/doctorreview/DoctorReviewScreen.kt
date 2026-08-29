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
        if (uiState.missingSections.isNotEmpty()) {
            MissingInformationCard(uiState.missingSections)
        }
        ReviewSection("Presenting complaint", uiState.draft.presentingComplaint)
        ReviewSection("History", uiState.draft.history)
        ReviewSection("Key findings", uiState.draft.keyFindings)
        ReviewSection("Assessment notes", uiState.draft.assessmentNotes)
        ReviewSection("Plan notes", uiState.draft.planNotes)
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
