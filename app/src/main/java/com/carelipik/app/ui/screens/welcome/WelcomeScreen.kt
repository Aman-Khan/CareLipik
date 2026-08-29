package com.carelipik.app.ui.screens.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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

@Composable
fun WelcomeScreen(
    uiState: WelcomeUiState,
    onConsentChanged: (Boolean) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(text = "CareLipik", style = MaterialTheme.typography.displaySmall)
        Text(
            text = "An offline clinical-scribe prototype that helps turn a consultation into a draft for doctor review.",
            style = MaterialTheme.typography.bodyLarge
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Recording and processing consent", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Before recording, confirm that the patient has agreed to the consultation being recorded and processed for clinical documentation.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .semantics { contentDescription = "Recording consent" }
                        .toggleable(
                            value = uiState.hasRecordingConsent,
                            role = Role.Checkbox,
                            onValueChange = onConsentChanged
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = uiState.hasRecordingConsent, onCheckedChange = null)
                    Text(
                        "I confirm that consent has been obtained.",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
        Text(
            "Privacy: this prototype processes information locally on this device. It does not send consultation information to a cloud service.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(
            onClick = onContinue,
            enabled = uiState.canContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue to patient details")
        }
    }
}
