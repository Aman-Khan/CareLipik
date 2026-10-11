package com.carelipik.app.ui.screens.savedrecordings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun SavedRecordingsScreen(
    uiState: SavedRecordingsUiState,
    onResume: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var deleteId by remember { mutableStateOf<String?>(null) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack, enabled = !uiState.isBusy) { Text("Back") }
        Text("Saved recordings", style = MaterialTheme.typography.headlineMedium)
        Text("Continue an unfinished consultation from its recording.")
        Text(
            "Recordings and patient details stay encrypted on this device until you delete them " +
                "or approve the consultation. They survive app and phone restarts.",
            style = MaterialTheme.typography.bodySmall
        )
        if (uiState.isBusy) Text("Opening or updating recordings…")
        uiState.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onRefresh, enabled = !uiState.isBusy) { Text("Try again") }
        }
        if (!uiState.isBusy && uiState.recordings.isEmpty() && uiState.error == null) {
            Text("No saved recordings yet. Finish a recording and tap Save and continue later.")
        }
        uiState.recordings.forEach { recording ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(recording.patientName, style = MaterialTheme.typography.titleMedium)
                    if (recording.visitReason.isNotBlank()) Text(recording.visitReason)
                    Text(
                        DateFormat.getDateTimeInstance().format(Date(recording.savedAtMillis)) +
                            " · ${recording.durationMillis / 1_000}s · ${recording.language.displayName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text("Recording saved · Awaiting transcript and doctor review")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { onResume(recording.id) },
                            enabled = !uiState.isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Continue consultation")
                        }
                        TextButton(onClick = { deleteId = recording.id }, enabled = !uiState.isBusy) {
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete saved recording?") },
            text = { Text("This permanently removes the recording and its unfinished consultation details.") },
            confirmButton = {
                TextButton(onClick = { deleteId = null; onDelete(id) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } }
        )
    }
}
