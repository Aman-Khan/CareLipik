package com.carelipik.app.ui.screens.export

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carelipik.app.ui.components.ConsultationScreenHeader
import com.carelipik.app.domain.export.ConsultationExportFormat

@Composable
fun ConsultationExportScreen(
    uiState: ConsultationExportUiState,
    onFormatSelected: (ConsultationExportFormat) -> Unit,
    onGenerate: () -> Unit,
    onShare: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
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
            title = "Export approved note",
            subtitle = "Generate an encrypted report linked to this consultation history.",
            currentStep = 8,
            totalSteps = 8,
            onBack = onBack,
            backEnabled = uiState.status != ConsultationExportStatus.Generating
        )
        PrivacyCard()
        val consultation = uiState.consultation
        if (consultation != null) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Approved consultation", fontWeight = FontWeight.Bold)
                    Text(consultation.patientName)
                    Text(
                        consultation.visitReason.ifBlank { "No visit reason recorded" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Report: ${consultation.draft.noteFormat.displayName} • " +
                            consultation.draft.noteLanguage.displayName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        ExportFormatOptions(
            selected = uiState.selectedFormat,
            enabled = uiState.status != ConsultationExportStatus.Generating,
            onSelected = onFormatSelected
        )
        when (uiState.status) {
            ConsultationExportStatus.Empty -> Text(
                "No approved consultation is available for export.",
                color = MaterialTheme.colorScheme.error
            )
            ConsultationExportStatus.Generating -> Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator()
                Text("Creating ${uiState.selectedFormat.displayName} privately on this device...")
            }
            ConsultationExportStatus.Generated -> GeneratedExportCard(
                displayName = uiState.exportedFile?.displayName.orEmpty(),
                sizeBytes = uiState.exportedFile?.sizeBytes ?: 0L,
                formatName = uiState.exportedFile?.format?.displayName.orEmpty(),
                isSavedToHistory = uiState.savedArtifact != null,
                persistenceWarning = uiState.persistenceWarning,
                onShare = onShare
            )
            ConsultationExportStatus.Error -> Text(
                uiState.errorMessage ?: "The export could not be created.",
                color = MaterialTheme.colorScheme.error
            )
            ConsultationExportStatus.ReadyToGenerate -> Unit
        }
        if (uiState.status != ConsultationExportStatus.Generated) {
            Button(
                onClick = onGenerate,
                enabled = uiState.canGenerate,
                modifier = Modifier.fillMaxWidth().testTag("generate_consultation_export")
            ) {
                Text(
                    if (uiState.status == ConsultationExportStatus.Error) {
                        "Try again"
                    } else {
                        "Create ${consultation?.draft?.noteFormat?.displayName.orEmpty()} as " +
                            uiState.selectedFormat.displayName
                    }
                )
            }
        }
        OutlinedButton(
            onClick = onFinish,
            enabled = uiState.status != ConsultationExportStatus.Generating,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Finish")
        }
    }
}

@Composable
private fun PrivacyCard() {
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
            Text("Share carefully", fontWeight = FontWeight.Bold)
            Text(
                "Exports contain sensitive clinical information. CareLipik stores an encrypted " +
                    "copy with approved history and creates a temporary readable copy only for " +
                    "opening or sharing. Consultation audio is never included."
            )
        }
    }
}

@Composable
private fun ExportFormatOptions(
    selected: ConsultationExportFormat,
    enabled: Boolean,
    onSelected: (ConsultationExportFormat) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Export file format",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        ConsultationExportFormat.entries.forEach { format ->
            Card(
                onClick = { onSelected(format) },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().testTag("export_format_${format.name}"),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected == format) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(format.displayName, fontWeight = FontWeight.Bold)
                    Text(format.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun GeneratedExportCard(
    displayName: String,
    sizeBytes: Long,
    formatName: String,
    isSavedToHistory: Boolean,
    persistenceWarning: String?,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("$formatName ready", fontWeight = FontWeight.Bold)
            Text(displayName, style = MaterialTheme.typography.bodySmall)
            Text("${(sizeBytes / 1_024L).coerceAtLeast(1L)} KB")
            Text(
                if (isSavedToHistory) {
                    "Encrypted copy saved with consultation history"
                } else {
                    persistenceWarning ?: "History copy is unavailable"
                },
                color = if (isSavedToHistory) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            Button(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth().testTag("share_consultation_export")
            ) {
                Text("Share or save file")
            }
        }
    }
}
