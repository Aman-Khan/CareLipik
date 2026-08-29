package com.carelipik.app.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.ui.components.ConsultationScreenHeader
import java.text.DateFormat
import java.util.Date

@Composable
fun ConsultationHistoryScreen(
    uiState: ConsultationHistoryUiState,
    onOpen: (String) -> Unit,
    onExport: (ApprovedConsultation) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = uiState.selected
    if (selected != null) {
        ConsultationHistoryDetail(selected, onExport, onDelete, onBack, modifier)
        return
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConsultationScreenHeader(
            title = "Consultation history",
            subtitle = "Doctor-approved notes encrypted on this device.",
            currentStep = 1,
            totalSteps = 1,
            onBack = onBack
        )
        if (!uiState.isLoading && uiState.consultations.isEmpty()) {
            Text("No approved consultations have been saved.")
        }
        uiState.consultations.forEach { item ->
            Card(
                onClick = { onOpen(item.id) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(item.patientName, fontWeight = FontWeight.Bold)
                    Text(formatDate(item.approvedAtMillis), style = MaterialTheme.typography.bodySmall)
                    Text(item.visitReason.ifBlank { "No visit reason recorded" })
                }
            }
        }
    }
}

@Composable
private fun ConsultationHistoryDetail(
    item: ApprovedConsultation,
    onExport: (ApprovedConsultation) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConsultationScreenHeader(
            title = item.patientName,
            subtitle = "Approved ${formatDate(item.approvedAtMillis)} • Age ${item.patientAge.ifBlank { "not recorded" }}",
            currentStep = 1,
            totalSteps = 1,
            onBack = onBack
        )
        HistorySection("Visit reason", item.visitReason)
        HistorySection("Presenting complaint", item.draft.presentingComplaint)
        HistorySection("History", item.draft.history)
        HistorySection("Key findings", item.draft.keyFindings)
        HistorySection("Assessment notes", item.draft.assessmentNotes)
        HistorySection("Plan notes", item.draft.planNotes)
        HistorySection(
            "Complete reviewed transcript",
            item.draft.reviewedTranscript.ifBlank { "Not available for this older record" }
        )
        androidx.compose.material3.Button(
            onClick = { onExport(item) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Export approved PDF")
        }
        OutlinedButton(onClick = { onDelete(item.id) }, modifier = Modifier.fillMaxWidth()) {
            Text("Delete consultation")
        }
    }
}

@Composable
private fun HistorySection(title: String, content: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(content.ifBlank { "Not documented" })
    }
}

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
