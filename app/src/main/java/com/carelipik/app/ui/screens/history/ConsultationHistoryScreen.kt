package com.carelipik.app.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.carelipik.app.domain.model.ApprovedConsultation
import com.carelipik.app.domain.repository.ConsultationReportArtifact
import java.text.DateFormat
import java.util.Date

@Composable
fun ConsultationHistoryScreen(
    uiState: ConsultationHistoryUiState,
    onOpen: (String) -> Unit,
    onExport: (ApprovedConsultation) -> Unit,
    onOpenReport: (ConsultationReportArtifact) -> Unit,
    onShareReport: (ConsultationReportArtifact) -> Unit,
    onDeleteReport: (ConsultationReportArtifact) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = uiState.selected
    if (selected != null) {
        ConsultationHistoryDetail(
            item = selected,
            reports = uiState.reports,
            isPreparingReport = uiState.isPreparingReport,
            reportError = uiState.reportError,
            onExport = onExport,
            onOpenReport = onOpenReport,
            onShareReport = onShareReport,
            onDeleteReport = onDeleteReport,
            onDelete = onDelete,
            onBack = onBack,
            modifier = modifier
        )
        return
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HistoryPageHeader(
            title = "Consultation history",
            subtitle = if (uiState.consultations.isEmpty()) {
                "Doctor-approved notes encrypted on this device"
            } else {
                "${uiState.consultations.size} approved consultations on this device"
            },
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
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    PatientInitial(item.patientName)
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            item.patientName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            formatDate(item.approvedAtMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            item.visitReason.ifBlank { "No visit reason recorded" },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${item.draft.noteFormat.displayName} • " +
                                "Age ${item.patientAge.ifBlank { "not recorded" }}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "Approved",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsultationHistoryDetail(
    item: ApprovedConsultation,
    reports: List<ConsultationReportArtifact>,
    isPreparingReport: Boolean,
    reportError: String?,
    onExport: (ApprovedConsultation) -> Unit,
    onOpenReport: (ConsultationReportArtifact) -> Unit,
    onShareReport: (ConsultationReportArtifact) -> Unit,
    onDeleteReport: (ConsultationReportArtifact) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HistoryPageHeader(
            title = item.patientName,
            subtitle = "Approved ${formatDate(item.approvedAtMillis)} • " +
                item.draft.noteFormat.displayName,
            onBack = onBack
        )
        HistorySection("Patient name / reference", item.patientName)
        HistorySection("Age", item.patientAge.ifBlank { "Not recorded" })
        HistorySection("Visit reason", item.visitReason)
        HistorySection(
            "Clinical note format",
            "${item.draft.noteFormat.displayName} • ${item.draft.noteLanguage.displayName}"
        )
        SavedReportsSection(
            reports = reports,
            isPreparing = isPreparingReport,
            onOpen = onOpenReport,
            onShare = onShareReport,
            onDelete = onDeleteReport
        )
        reportError?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = { onExport(item) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (reports.isEmpty()) "Generate report" else "Generate or update report")
        }
        item.draft.effectiveSections.forEach { section ->
            HistorySection(section.title, section.content)
        }
        HistorySection(
            "Prescribed medicines and dosages",
            item.draft.medications.joinToString("\n") { medication ->
                buildList {
                    add(medication.name.ifBlank { "Unnamed medicine" })
                    if (medication.strength.isNotBlank()) add(medication.strength)
                    if (medication.dose.isNotBlank()) add(medication.dose)
                    if (medication.route.isNotBlank()) add(medication.route)
                    if (medication.frequency.isNotBlank()) add(medication.frequency)
                    if (medication.duration.isNotBlank()) add(medication.duration)
                }.joinToString(" • ")
            }.ifBlank { "No prescribed medicines documented" }
        )
        if (item.draft.coverageWarnings.isNotEmpty()) {
            HistorySection(
                "Transcript coverage warnings reviewed at approval",
                item.draft.coverageWarnings.joinToString("\n") { "• $it" }
            )
        }
        HistorySection(
            "Complete reviewed conversation log",
            item.draft.reviewedTranscript.ifBlank { "Not available for this older record" }
        )
        OutlinedButton(onClick = { onDelete(item.id) }, modifier = Modifier.fillMaxWidth()) {
            Text("Delete consultation")
        }
    }
}

@Composable
private fun HistoryPageHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PatientInitial(patientName: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            patientName.trim().firstOrNull()?.uppercase() ?: "?",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun SavedReportsSection(
    reports: List<ConsultationReportArtifact>,
    isPreparing: Boolean,
    onOpen: (ConsultationReportArtifact) -> Unit,
    onShare: (ConsultationReportArtifact) -> Unit,
    onDelete: (ConsultationReportArtifact) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Saved reports", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (reports.isEmpty()) {
            Text("No generated report files are linked to this consultation yet.")
        }
        reports.forEach { report ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(report.format.displayName, fontWeight = FontWeight.Bold)
                    Text(report.displayName, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Generated ${formatDate(report.generatedAtMillis)} • " +
                            "${(report.sizeBytes / 1_024L).coerceAtLeast(1L)} KB",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { onOpen(report) },
                        enabled = !isPreparing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open report")
                    }
                    OutlinedButton(
                        onClick = { onShare(report) },
                        enabled = !isPreparing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Share report")
                    }
                    OutlinedButton(
                        onClick = { onDelete(report) },
                        enabled = !isPreparing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete saved report")
                    }
                }
            }
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
