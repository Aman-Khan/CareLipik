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

@Composable
fun ConsultationExportScreen(
    uiState: ConsultationExportUiState,
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
            subtitle = "Create a temporary PDF only when you need to save or share it.",
            currentStep = 7,
            totalSteps = 7,
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
                }
            }
        }
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
                Text("Creating PDF privately on this device...")
            }
            ConsultationExportStatus.Generated -> GeneratedPdfCard(
                displayName = uiState.pdf?.displayName.orEmpty(),
                sizeBytes = uiState.pdf?.sizeBytes ?: 0L,
                onShare = onShare
            )
            ConsultationExportStatus.Error -> Text(
                uiState.errorMessage ?: "The PDF could not be created.",
                color = MaterialTheme.colorScheme.error
            )
            ConsultationExportStatus.ReadyToGenerate -> Unit
        }
        if (uiState.status != ConsultationExportStatus.Generated) {
            Button(
                onClick = onGenerate,
                enabled = uiState.canGenerate,
                modifier = Modifier.fillMaxWidth().testTag("generate_consultation_pdf")
            ) {
                Text(if (uiState.status == ConsultationExportStatus.Error) "Try again" else "Create PDF")
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
                "The PDF contains sensitive clinical information. CareLipik creates it in " +
                    "private cache and never includes consultation audio."
            )
        }
    }
}

@Composable
private fun GeneratedPdfCard(displayName: String, sizeBytes: Long, onShare: () -> Unit) {
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
            Text("PDF ready", fontWeight = FontWeight.Bold)
            Text(displayName, style = MaterialTheme.typography.bodySmall)
            Text("${(sizeBytes / 1_024L).coerceAtLeast(1L)} KB")
            Button(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth().testTag("share_consultation_pdf")
            ) {
                Text("Share or save PDF")
            }
        }
    }
}
