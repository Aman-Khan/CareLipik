package com.carelipik.app.ui.screens.patientdetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.carelipik.app.ui.components.ConsultationScreenHeader

@Composable
fun PatientDetailsScreen(
    uiState: PatientDetailsUiState,
    onPatientNameChanged: (String) -> Unit,
    onAgeChanged: (String) -> Unit,
    onVisitReasonChanged: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConsultationScreenHeader(
            title = "Patient details",
            subtitle = "Enter only what is needed for this consultation. Information stays on this device.",
            currentStep = 2,
            totalSteps = 7,
            onBack = onBack
        )
        OutlinedTextField(
            value = uiState.patientName,
            onValueChange = onPatientNameChanged,
            label = { Text("Patient name or reference") },
            supportingText = {
                Text(uiState.patientNameError ?: "Required")
            },
            isError = uiState.patientNameError != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.age,
            onValueChange = onAgeChanged,
            label = { Text("Age") },
            supportingText = {
                Text(uiState.ageError ?: "Optional")
            },
            isError = uiState.ageError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = uiState.visitReason,
            onValueChange = onVisitReasonChanged,
            label = { Text("Reason for visit") },
            supportingText = { Text("Optional; you can add this during the consultation") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continue to recording")
        }
    }
}
