package com.carelipik.app.ui.screens.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carelipik.app.domain.model.ConsultationDestination

@Composable
fun PlaceholderScreen(
    destination: ConsultationDestination,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(destination.title, style = MaterialTheme.typography.headlineMedium)
        Text("This local workflow step will be built in a later component.")
        Button(onClick = onNext) { Text("Next") }
    }
}

@Composable
fun UpcomingFeatureScreen(
    destination: ConsultationDestination,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("Back to home")
        }
        Text(destination.title, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = when (destination) {
                ConsultationDestination.DoctorProfile ->
                    "Doctor identity, clinic details and processing preferences are the next component."
                ConsultationDestination.ConsultationHistory ->
                    "Reviewed consultations will appear here after secure local storage is added."
                else -> "This workspace component is coming next."
            },
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
