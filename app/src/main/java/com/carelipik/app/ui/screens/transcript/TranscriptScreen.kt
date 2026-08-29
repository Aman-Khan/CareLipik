package com.carelipik.app.ui.screens.transcript

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
fun TranscriptScreen(
    uiState: TranscriptUiState,
    onTranscriptChanged: (String) -> Unit,
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
            title = "Review transcript",
            subtitle = "Check every line against the consultation and correct anything that is unclear.",
            currentStep = 4,
            totalSteps = 7,
            onBack = onBack,
            backEnabled = uiState.status != TranscriptStatus.Processing
        )
        TranscriptNotice(
            languageName = uiState.language.displayName,
            engineName = uiState.engine.displayName
        )
        when (uiState.status) {
            TranscriptStatus.Idle,
            TranscriptStatus.Processing -> ProcessingTranscript()
            TranscriptStatus.Error -> ErrorTranscript(
                message = uiState.errorMessage ?: "Transcription could not be completed.",
                onRetry = onRetry
            )
            TranscriptStatus.Ready -> {
                OutlinedTextField(
                    value = uiState.transcript,
                    onValueChange = onTranscriptChanged,
                    label = { Text("Consultation transcript") },
                    supportingText = {
                        Text(uiState.transcriptError ?: "Speaker labels can be corrected here")
                    },
                    isError = uiState.transcriptError != null,
                    minLines = 12,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue to clinical draft")
                }
            }
        }
    }
}

@Composable
private fun TranscriptNotice(languageName: String, engineName: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Offline multilingual transcription", style = MaterialTheme.typography.titleSmall)
            Text(
                "Selected mode: $languageName",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                "Engine: $engineName",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                "This text was generated from your recording on this device. English, Hindi, and Hinglish are supported on a best-effort basis. Verify every word and add speaker labels before continuing.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ProcessingTranscript() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator()
        Text("Preparing transcript…", style = MaterialTheme.typography.titleMedium)
        Text(
            "Processing stays on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorTranscript(message: String, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Transcript unavailable", style = MaterialTheme.typography.titleMedium)
            Text(message)
            OutlinedButton(onClick = onRetry) { Text("Try again") }
        }
    }
}
