package com.carelipik.app.ui.screens.recording

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    onImportAudio: (String) -> Unit,
    onTogglePlayback: () -> Unit,
    onTranscriptionLanguageChanged: (TranscriptionLanguage) -> Unit,
    onTranscriptionEngineChanged: (TranscriptionEngineOption) -> Unit,
    onOnlineProcessingConsentChanged: (Boolean) -> Unit,
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
    val audioFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.toString()?.let(onImportAudio)
    }
    val chooseAudioFile = {
        audioFileLauncher.launch(arrayOf("audio/*"))
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
            subtitle = "Record now or import a reusable WAV test file.",
            currentStep = 3,
            totalSteps = 7,
            onBack = onBack,
            backEnabled = uiState.status !in setOf(RecordingStatus.Recording, RecordingStatus.Paused)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.status == RecordingStatus.Completed) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
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
                        RecordingStatus.Completed -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = when (uiState.status) {
                                RecordingStatus.Recording -> "REC"
                                RecordingStatus.Paused -> "II"
                                RecordingStatus.Completed -> if (uiState.isImportedAudio) {
                                    "FILE"
                                } else {
                                    "OK"
                                }
                                RecordingStatus.Ready -> "MIC"
                            },
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
            if (uiState.isImportedAudio) {
                "A temporary private copy was created. It is removed when you discard it."
            } else if (uiState.hasSavedAudio) {
                "Saved temporarily and privately on this device. Review it before continuing."
            } else {
                "Audio is recorded only into this app's private temporary storage."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        RecordingActions(
            status = uiState.status,
            onStart = startWithPermission,
            onImport = chooseAudioFile,
            onPause = onPause,
            onResume = onResume,
            onStop = onStop,
            isImporting = uiState.isImporting
        )
        uiState.importError?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }
        if (permissionDenied) {
            Text(
                "Microphone permission is needed to show the live sound level.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (uiState.status == RecordingStatus.Completed && uiState.hasSavedAudio) {
            if (uiState.isImportedAudio) {
                ImportedAudioDetails(uiState = uiState)
            }
            CompletedRecordingActions(
                isPlaying = uiState.isPlaying,
                isImportedAudio = uiState.isImportedAudio,
                onTogglePlayback = onTogglePlayback,
                onDiscard = onDiscard,
                onReplace = chooseAudioFile
            )
            TranscriptionSetupPanel(
                selectedLanguage = uiState.transcriptionLanguage,
                selectedEngine = uiState.transcriptionEngine,
                hasOnlineProcessingConsent = uiState.hasOnlineProcessingConsent,
                onLanguageChanged = onTranscriptionLanguageChanged,
                onEngineChanged = onTranscriptionEngineChanged,
                onOnlineProcessingConsentChanged = onOnlineProcessingConsentChanged
            )
            TranscriptContinueSection(
                uiState = uiState,
                onContinue = onContinue
            )
        }
    }
}

@Composable
private fun CompletedRecordingActions(
    isPlaying: Boolean,
    isImportedAudio: Boolean,
    onTogglePlayback: () -> Unit,
    onDiscard: () -> Unit,
    onReplace: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onTogglePlayback, modifier = Modifier.weight(1f)) {
                Text(if (isPlaying) "Stop audio" else "Listen")
            }
            if (isImportedAudio) {
                OutlinedButton(onClick = onReplace, modifier = Modifier.weight(1f)) {
                    Text("Replace")
                }
            } else {
                OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) {
                    Text("Record again")
                }
            }
        }
        if (isImportedAudio) {
            OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) {
                Text("Remove imported audio")
            }
        }
    }
}

@Composable
private fun ImportedAudioDetails(uiState: RecordingUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Imported audio",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = uiState.audioDisplayName.ifBlank { "Consultation audio.wav" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${uiState.formattedDuration} · ${formatFileSize(uiState.audioSizeBytes)} · Temporary",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Compatible format: mono 16 kHz, 16-bit PCM WAV.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TranscriptionSetupPanel(
    selectedLanguage: TranscriptionLanguage,
    selectedEngine: TranscriptionEngineOption,
    hasOnlineProcessingConsent: Boolean,
    onLanguageChanged: (TranscriptionLanguage) -> Unit,
    onEngineChanged: (TranscriptionEngineOption) -> Unit,
    onOnlineProcessingConsentChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Prepare transcript",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Choose the conversation language first. We will recommend the best engine.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SectionLabel(number = "1", title = "Conversation language")
            LanguageSelectionControl(
                selectedLanguage = selectedLanguage,
                onLanguageChanged = onLanguageChanged
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SectionLabel(number = "2", title = "Transcription engine")
            Text(
                text = "MedASR and Multilingual Whisper run fully on this device. " +
                    "Multilingual V3 Turbo and Saaras securely send audio for online processing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TranscriptionEngineOption.visibleOptions.forEach { engine ->
                    EngineOptionCard(
                        engine = engine,
                        selectedLanguage = selectedLanguage,
                        isSelected = selectedEngine == engine,
                        isRecommended = TranscriptionEngineOption.defaultFor(selectedLanguage) ==
                            engine,
                        onSelected = { onEngineChanged(engine) }
                    )
                }
            }
            if (selectedEngine == TranscriptionEngineOption.WhisperTurboFullAudioTest) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Comparison mode: the complete recording is transcribed in " +
                            "30-second contextual windows. Pyannote, TitaNet speaker clustering, " +
                            "and automatic Doctor/Patient matching are bypassed, so this mode " +
                            "does not provide speaker labels.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(number: String, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {
            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun LanguageSelectionControl(
    selectedLanguage: TranscriptionLanguage,
    onLanguageChanged: (TranscriptionLanguage) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val languages = TranscriptionLanguage.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            languages.forEachIndexed { index, language ->
                SegmentedButton(
                    selected = selectedLanguage == language,
                    onClick = { onLanguageChanged(language) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = languages.size
                    ),
                    label = {
                        Text(
                            text = languageSegmentLabel(language),
                            maxLines = 1,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = selectedLanguage.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = languageSupportingText(selectedLanguage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun EngineOptionCard(
    engine: TranscriptionEngineOption,
    selectedLanguage: TranscriptionLanguage,
    isSelected: Boolean,
    isRecommended: Boolean,
    onSelected: () -> Unit
) {
    val isSupported = engine.supports(selectedLanguage)
    Surface(
        onClick = onSelected,
        enabled = isSupported,
        modifier = Modifier.fillMaxWidth().alpha(if (isSupported) 1f else 0.58f),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(
                selected = isSelected,
                onClick = null,
                enabled = isSupported
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = engine.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetaPill(if (engine.isOffline) "On device" else "Online")
                    if (isRecommended) MetaPill("Recommended", emphasized = true)
                    if (!isSupported) MetaPill("Unavailable")
                }
                Text(
                    text = if (isSupported) {
                        engine.description
                    } else {
                        engineUnavailableMessage(engine)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetaPill(text: String, emphasized: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (emphasized) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (emphasized) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun OnlineProcessingConsentCard(
    hasConsent: Boolean,
    onConsentChanged: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Patient consent required",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Online transcription securely sends this recording to CareLipik's " +
                    "configured service. Choose Whisper if audio must stay on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = hasConsent,
                    onCheckedChange = onConsentChanged
                )
                Text(
                    text = "Patient agreed to online audio processing",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun TranscriptContinueSection(
    uiState: RecordingUiState,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "${uiState.transcriptionLanguage.displayName} · " +
                uiState.transcriptionEngine.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Button(
            onClick = onContinue,
            enabled = uiState.canContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (uiState.transcriptionEngine.isOffline) {
                    "Create transcript on device"
                } else {
                    "Transcribe securely online"
                }
            )
        }
        Text(
            text = when {
                uiState.transcriptionEngine.isOffline ->
                    "The recording and transcription stay on this device."
                else -> "The recording is sent to an online transcription service when you continue."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun languageSegmentLabel(language: TranscriptionLanguage): String = when (language) {
    TranscriptionLanguage.Auto -> "Auto"
    TranscriptionLanguage.English -> "English"
    TranscriptionLanguage.Hindi -> "Hindi"
    TranscriptionLanguage.Hinglish -> "Hinglish"
}

private fun languageSupportingText(language: TranscriptionLanguage): String = when (language) {
    TranscriptionLanguage.Auto ->
        "Multilingual V3 Turbo detects the language using online transcription."
    TranscriptionLanguage.English ->
        "MedASR is the default offline medical-English engine; Whisper is also available offline."
    TranscriptionLanguage.Hindi -> "Best for conversations spoken mostly in Hindi."
    TranscriptionLanguage.Hinglish -> "Best when Hindi and English are naturally mixed."
}

private fun engineUnavailableMessage(engine: TranscriptionEngineOption): String = when (engine) {
    TranscriptionEngineOption.MedAsrEnglish -> "Choose English to use MedASR."
    TranscriptionEngineOption.SaarasHindiHinglish ->
        "Choose English, Hindi, or Hinglish to use Saaras."
    TranscriptionEngineOption.AssemblyAiUniversal -> "Unavailable for this language."
    TranscriptionEngineOption.WhisperMultilingual -> "Unavailable for this language."
    TranscriptionEngineOption.WhisperTurboMultilingual -> "Unavailable for this language."
    TranscriptionEngineOption.WhisperTurboFullAudioTest -> "Unavailable for this language."
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
    onImport: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    isImporting: Boolean
) {
    when (status) {
        RecordingStatus.Ready -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onStart,
                enabled = !isImporting,
                modifier = Modifier.weight(1f)
            ) {
                Text("Start recording")
            }
            OutlinedButton(
                onClick = onImport,
                enabled = !isImporting,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isImporting) "Importing…" else "Import audio")
            }
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
        RecordingStatus.Completed -> Unit
    }
}

private fun statusMessage(uiState: RecordingUiState): String = when (uiState.status) {
    RecordingStatus.Ready -> if (uiState.isImporting) "Importing audio…" else "Ready for audio"
    RecordingStatus.Recording -> "Recording in progress"
    RecordingStatus.Paused -> "Recording paused"
    RecordingStatus.Completed -> when {
        uiState.isImporting -> "Importing replacement…"
        uiState.isImportedAudio -> "Imported audio ready"
        uiState.hasSavedAudio -> "Recording ready"
        else -> "Saving recording…"
    }
}

private fun formatFileSize(sizeBytes: Long): String = when {
    sizeBytes >= 1024L * 1024L -> "%.1f MB".format(sizeBytes / (1024f * 1024f))
    sizeBytes >= 1024L -> "%.0f KB".format(sizeBytes / 1024f)
    else -> "$sizeBytes B"
}
