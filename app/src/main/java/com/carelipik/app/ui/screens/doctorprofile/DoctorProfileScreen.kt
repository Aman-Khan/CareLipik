package com.carelipik.app.ui.screens.doctorprofile

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.carelipik.app.domain.model.ProcessingPreference
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun DoctorProfileScreen(
    uiState: DoctorProfileUiState,
    voiceUiState: DoctorVoiceEnrollmentUiState,
    onFullNameChanged: (String) -> Unit,
    onSpecialtyChanged: (String) -> Unit,
    onRegistrationNumberChanged: (String) -> Unit,
    onClinicNameChanged: (String) -> Unit,
    onPreferredLanguageChanged: (TranscriptionLanguage) -> Unit,
    onProcessingPreferenceChanged: (ProcessingPreference) -> Unit,
    onStartVoiceSample: () -> Unit,
    onStopVoiceSample: () -> Unit,
    onDeleteVoiceSample: () -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionDenied = !isGranted
        if (isGranted) onStartVoiceSample()
    }
    val startVoiceSampleWithPermission = {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            onStartVoiceSample()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ProfileHeader(
            onBack = onBack,
            backEnabled = voiceUiState.status !in setOf(
                DoctorVoiceEnrollmentStatus.Recording,
                DoctorVoiceEnrollmentStatus.Saving
            )
        )
        ProfileIdentityCard(
            uiState = uiState,
            onFullNameChanged = onFullNameChanged,
            onSpecialtyChanged = onSpecialtyChanged,
            onRegistrationNumberChanged = onRegistrationNumberChanged,
            onClinicNameChanged = onClinicNameChanged
        )
        PreferredLanguagesCard(
            selectedLanguages = uiState.preferredLanguages,
            error = uiState.preferredLanguagesError,
            onLanguageChanged = onPreferredLanguageChanged
        )
        ProcessingPreferenceCard(
            selectedPreference = uiState.processingPreference,
            onPreferenceChanged = onProcessingPreferenceChanged
        )
        VoiceRecognitionCard(
            uiState = voiceUiState,
            permissionDenied = permissionDenied,
            onStart = startVoiceSampleWithPermission,
            onStop = onStopVoiceSample,
            onDelete = onDeleteVoiceSample
        )
        Button(
            onClick = onSave,
            enabled = !uiState.isLoading && voiceUiState.status !in setOf(
                DoctorVoiceEnrollmentStatus.Recording,
                DoctorVoiceEnrollmentStatus.Saving
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("doctor_profile_save")
        ) {
            Text("Save profile", fontWeight = FontWeight.Bold)
        }
        uiState.saveError?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        Text(
            text = "Profile details and the voice sample are stored separately in encrypted, private on-device storage.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ProfileHeader(onBack: () -> Unit, backEnabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack, enabled = backEnabled) {
            Text("Back to home")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "DR",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = "Doctor profile",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Personalize documentation and processing defaults",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProfileIdentityCard(
    uiState: DoctorProfileUiState,
    onFullNameChanged: (String) -> Unit,
    onSpecialtyChanged: (String) -> Unit,
    onRegistrationNumberChanged: (String) -> Unit,
    onClinicNameChanged: (String) -> Unit
) {
    ProfileSectionCard(
        title = "Professional details",
        supportingText = "Used to identify the reviewing clinician and prepare document headers."
    ) {
        ProfileTextField(
            value = uiState.fullName,
            onValueChanged = onFullNameChanged,
            label = "Full name",
            placeholder = "For example, Asha Mehta",
            error = uiState.fullNameError,
            modifier = Modifier.testTag("doctor_profile_name")
        )
        ProfileTextField(
            value = uiState.specialty,
            onValueChanged = onSpecialtyChanged,
            label = "Specialty",
            placeholder = "For example, General medicine"
        )
        ProfileTextField(
            value = uiState.registrationNumber,
            onValueChanged = onRegistrationNumberChanged,
            label = "Medical registration number",
            placeholder = "Optional for now",
            capitalization = KeyboardCapitalization.Characters
        )
        ProfileTextField(
            value = uiState.clinicName,
            onValueChanged = onClinicNameChanged,
            label = "Clinic or facility",
            placeholder = "Optional"
        )
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChanged: (String) -> Unit,
    label: String,
    placeholder: String,
    error: String? = null,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        isError = error != null,
        supportingText = error?.let { message ->
            { Text(message) }
        },
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            imeAction = ImeAction.Next
        ),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun PreferredLanguagesCard(
    selectedLanguages: Set<TranscriptionLanguage>,
    error: String?,
    onLanguageChanged: (TranscriptionLanguage) -> Unit
) {
    ProfileSectionCard(
        title = "Consultation languages",
        supportingText = "Select every language you commonly use. This controls recommendations, not availability."
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LanguageChip(
                language = TranscriptionLanguage.English,
                isSelected = TranscriptionLanguage.English in selectedLanguages,
                onSelected = onLanguageChanged,
                modifier = Modifier.weight(1f)
            )
            LanguageChip(
                language = TranscriptionLanguage.Hindi,
                isSelected = TranscriptionLanguage.Hindi in selectedLanguages,
                onSelected = onLanguageChanged,
                modifier = Modifier.weight(1f)
            )
        }
        LanguageChip(
            language = TranscriptionLanguage.Hinglish,
            isSelected = TranscriptionLanguage.Hinglish in selectedLanguages,
            onSelected = onLanguageChanged,
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun LanguageChip(
    language: TranscriptionLanguage,
    isSelected: Boolean,
    onSelected: (TranscriptionLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick = { onSelected(language) },
        label = {
            Text(
                text = language.displayName,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        },
        modifier = modifier
    )
}

@Composable
private fun ProcessingPreferenceCard(
    selectedPreference: ProcessingPreference,
    onPreferenceChanged: (ProcessingPreference) -> Unit
) {
    ProfileSectionCard(
        title = "Processing preference",
        supportingText = "Online processing always requires consultation-specific confirmation."
    ) {
        ProcessingPreference.entries.forEach { preference ->
            ProcessingOption(
                preference = preference,
                isSelected = preference == selectedPreference,
                isRecommended = preference == ProcessingPreference.SmartHybrid,
                onSelected = { onPreferenceChanged(preference) }
            )
        }
    }
}

@Composable
private fun ProcessingOption(
    preference: ProcessingPreference,
    isSelected: Boolean,
    isRecommended: Boolean,
    onSelected: () -> Unit
) {
    Card(
        onClick = onSelected,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            RadioButton(selected = isSelected, onClick = null)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = preference.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (isRecommended) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = "Recommended",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                Text(
                    text = preference.shortDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VoiceRecognitionCard(
    uiState: DoctorVoiceEnrollmentUiState,
    permissionDenied: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.66f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = if (uiState.hasExistingSample) "ENROLLED · ON DEVICE" else "PRIVATE · ON DEVICE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            Text(
                text = "Doctor voice recognition",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "A short voice sample will later help identify the doctor's turns. It never leaves this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (uiState.status) {
                DoctorVoiceEnrollmentStatus.Recording -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = uiState.formattedElapsed,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            VoiceSampleWaveform(uiState.amplitude)
                            Text(
                                text = "Read the phrase below in your normal consultation voice.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    VoiceEnrollmentPhrase()
                    Button(
                        onClick = onStop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("doctor_voice_finish")
                    ) {
                        Text("Finish and save sample")
                    }
                }
                DoctorVoiceEnrollmentStatus.Saving -> {
                    Text(
                        text = "Checking and saving voice sample…",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                DoctorVoiceEnrollmentStatus.Enrolled -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Voice sample ready",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${uiState.formattedSampleDuration} · Stored only on this phone",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onStart,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("doctor_voice_replace")
                        ) {
                            Text("Replace sample")
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete")
                        }
                    }
                }
                DoctorVoiceEnrollmentStatus.Error,
                DoctorVoiceEnrollmentStatus.NotEnrolled -> {
                    VoiceEnrollmentPhrase()
                    Button(
                        onClick = onStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("doctor_voice_record")
                    ) {
                        Text(if (uiState.hasExistingSample) "Try replacement again" else "Record voice sample")
                    }
                    if (uiState.hasExistingSample) {
                        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                            Text("Delete existing sample")
                        }
                    }
                }
            }
            uiState.message?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uiState.status == DoctorVoiceEnrollmentStatus.Error) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (permissionDenied) {
                Text(
                    text = "Microphone permission is needed only while recording the doctor voice sample.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun VoiceEnrollmentPhrase() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Suggested phrase",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Please tell me what brings you in today, when the symptoms started, and whether you are taking any medicines.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Aim for 10–15 seconds in a quiet room.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VoiceSampleWaveform(amplitude: Float) {
    val waveColor = MaterialTheme.colorScheme.tertiary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics { contentDescription = "Live doctor voice sample microphone level" }
    ) {
        val barCount = 21
        val spacing = size.width / barCount
        val centerY = size.height / 2f
        repeat(barCount) { index ->
            val activity = (0.08f + amplitude.coerceIn(0f, 1f) * 0.92f) *
                (0.55f + abs(sin(index * 0.72f)) * 0.45f)
            val halfHeight = size.height * activity / 2f
            val x = spacing * (index + 0.5f)
            drawLine(
                color = waveColor,
                start = Offset(x, centerY - halfHeight),
                end = Offset(x, centerY + halfHeight),
                strokeWidth = spacing * 0.4f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun ProfileSectionCard(
    title: String,
    supportingText: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}
