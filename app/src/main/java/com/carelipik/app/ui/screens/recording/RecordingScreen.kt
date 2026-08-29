package com.carelipik.app.ui.screens.recording

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.carelipik.app.ui.components.ConsultationScreenHeader
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun RecordingScreen(
    uiState: RecordingUiState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
    onTogglePlayback: () -> Unit,
    onTranscriptionLanguageChanged: (TranscriptionLanguage) -> Unit,
    onTranscriptionEngineChanged: (TranscriptionEngineOption) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionDenied = !isGranted
        if (isGranted) onStart()
    }
    val startWithPermission = {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            onStart()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        ConsultationScreenHeader(
            title = "Record consultation",
            subtitle = "Keep the phone nearby and make sure everyone can be heard clearly.",
            currentStep = 3,
            totalSteps = 7,
            onBack = onBack,
            backEnabled = uiState.status !in setOf(RecordingStatus.Recording, RecordingStatus.Paused)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = when (uiState.status) {
                        RecordingStatus.Recording -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (uiState.status == RecordingStatus.Recording) "REC" else "MIC",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                RecordingWaveform(status = uiState.status, amplitude = uiState.amplitude)
                Text(
                    text = uiState.formattedDuration,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = statusMessage(uiState),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        Text(
            if (uiState.hasSavedAudio) {
                "Saved privately on this device. Review it before continuing."
            } else {
                "Audio is recorded only into this app's private temporary storage."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        RecordingActions(
            status = uiState.status,
            onStart = startWithPermission,
            onPause = onPause,
            onResume = onResume,
            onStop = onStop,
            onDiscard = onDiscard
        )
        if (permissionDenied) {
            Text(
                "Microphone permission is needed to show the live sound level.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (uiState.status == RecordingStatus.Completed && uiState.hasSavedAudio) {
            OutlinedButton(onClick = onTogglePlayback, modifier = Modifier.fillMaxWidth()) {
                Text(if (uiState.isPlaying) "Stop playback" else "Play recording")
            }
            TranscriptionLanguageSelector(
                selectedLanguage = uiState.transcriptionLanguage,
                onLanguageChanged = onTranscriptionLanguageChanged
            )
            TranscriptionEngineSelector(
                selectedLanguage = uiState.transcriptionLanguage,
                selectedEngine = uiState.transcriptionEngine,
                onEngineChanged = onTranscriptionEngineChanged
            )
        }
        if (uiState.canContinue) {
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("Continue to transcript")
            }
        }
    }
}

@Composable
private fun TranscriptionEngineSelector(
    selectedLanguage: TranscriptionLanguage,
    selectedEngine: TranscriptionEngineOption,
    onEngineChanged: (TranscriptionEngineOption) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Transcription engine",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Choose an engine so the same recording can be tested with different models.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            TranscriptionEngineOption.entries
                .filter { it.supports(selectedLanguage) }
                .forEach { engine ->
                    FilterChip(
                        selected = selectedEngine == engine,
                        onClick = { onEngineChanged(engine) },
                        label = {
                            Column {
                                Text(engine.displayName)
                                Text(
                                    text = engine.description,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
        }
    }
}

@Composable
private fun TranscriptionLanguageSelector(
    selectedLanguage: TranscriptionLanguage,
    onLanguageChanged: (TranscriptionLanguage) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Conversation language",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Choose Hindi / Hinglish when either person speaks Hindi, even if English words are mixed in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TranscriptionLanguage.entries.forEach { language ->
                FilterChip(
                    selected = selectedLanguage == language,
                    onClick = { onLanguageChanged(language) },
                    label = { Text(language.displayName) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RecordingWaveform(status: RecordingStatus, amplitude: Float) {
    val waveColor = when (status) {
        RecordingStatus.Recording -> MaterialTheme.colorScheme.error
        RecordingStatus.Paused -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val description = when (status) {
        RecordingStatus.Recording -> "Live microphone level indicator"
        RecordingStatus.Paused -> "Recording activity indicator paused"
        RecordingStatus.Completed -> "Recording activity indicator complete"
        RecordingStatus.Ready -> "Recording activity indicator ready"
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .semantics { contentDescription = description }
    ) {
        val barCount = 25
        val spacing = size.width / barCount
        val centerY = size.height / 2f
        repeat(barCount) { index ->
            val normalizedDistance = abs(index - (barCount - 1) / 2f) / (barCount / 2f)
            val envelope = 1f - (normalizedDistance * 0.55f)
            val activity = if (status == RecordingStatus.Recording) {
                val shapedLevel = 0.08f + amplitude.coerceIn(0f, 1f) * 0.92f
                shapedLevel * (0.55f + abs(sin(index * 0.72f)) * 0.45f)
            } else {
                0.22f + abs(sin(index * 0.72f)) * 0.18f
            }
            val halfHeight = size.height * envelope * activity / 2f
            val x = spacing * (index + 0.5f)
            drawLine(
                color = waveColor,
                start = Offset(x, centerY - halfHeight),
                end = Offset(x, centerY + halfHeight),
                strokeWidth = spacing * 0.42f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun RecordingActions(
    status: RecordingStatus,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit
) {
    when (status) {
        RecordingStatus.Ready -> Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("Start recording")
        }
        RecordingStatus.Recording -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) { Text("Pause") }
            Button(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Finish") }
        }
        RecordingStatus.Paused -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onResume, modifier = Modifier.weight(1f)) { Text("Resume") }
            Button(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Finish") }
        }
        RecordingStatus.Completed -> OutlinedButton(
            onClick = onDiscard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Discard and record again")
        }
    }
}

private fun statusMessage(uiState: RecordingUiState): String = when (uiState.status) {
    RecordingStatus.Ready -> "Ready to record"
    RecordingStatus.Recording -> "Recording in progress"
    RecordingStatus.Paused -> "Recording paused"
    RecordingStatus.Completed -> if (uiState.hasSavedAudio) "Recording ready" else "Saving recording…"
}
