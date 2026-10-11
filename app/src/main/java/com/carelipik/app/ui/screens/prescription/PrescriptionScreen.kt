package com.carelipik.app.ui.screens.prescription

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carelipik.app.ui.components.ConsultationScreenHeader

@Composable
fun PrescriptionScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    generationContent: @Composable () -> Unit,
    supportContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSupport by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
            .navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ConsultationScreenHeader(
            title = "Prescription", subtitle = "Prepare the prescription and verify the recorded medicines.",
            currentStep = 6, totalSteps = 8, onBack = onBack
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(modifier = Modifier.weight(1f), selected = !showSupport, onClick = { showSupport = false },
                label = { Text("Prescription generation") })
            FilterChip(modifier = Modifier.weight(1f), selected = showSupport, onClick = { showSupport = true },
                label = { Text("Prescription support") })
        }
        if (showSupport) supportContent() else generationContent()
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continue to doctor review")
        }
    }
}
