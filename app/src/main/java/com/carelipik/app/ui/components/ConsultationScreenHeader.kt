package com.carelipik.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.carelipik.app.ui.theme.spacing

@Composable
fun ConsultationScreenHeader(
    title: String,
    subtitle: String,
    currentStep: Int,
    totalSteps: Int,
    onBack: (() -> Unit)? = null,
    backEnabled: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                TextButton(onClick = onBack, enabled = backEnabled) {
                    Text("Back")
                }
            }
            Text(
                text = "Step $currentStep of $totalSteps",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        LinearProgressIndicator(
            progress = { currentStep.toFloat() / totalSteps },
            modifier = Modifier.fillMaxWidth()
        )
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
