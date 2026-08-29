package com.carelipik.app.ui.screens.doctorprofile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.carelipik.app.domain.model.ProcessingPreference
import com.carelipik.app.domain.transcription.TranscriptionLanguage

@Composable
fun DoctorProfileScreen(
    uiState: DoctorProfileUiState,
    onFullNameChanged: (String) -> Unit,
    onSpecialtyChanged: (String) -> Unit,
    onRegistrationNumberChanged: (String) -> Unit,
    onClinicNameChanged: (String) -> Unit,
    onPreferredLanguageChanged: (TranscriptionLanguage) -> Unit,
    onProcessingPreferenceChanged: (ProcessingPreference) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ProfileHeader(onBack = onBack)
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
        VoiceRecognitionPreview()
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("doctor_profile_save")
        ) {
            Text("Save profile", fontWeight = FontWeight.Bold)
        }
        Text(
            text = "Profile changes currently remain available for this app session. Secure local persistence is the next component.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ProfileHeader(onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = onBack) {
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
private fun VoiceRecognitionPreview() {
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
                    text = "PLANNED",
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
                text = "After offline speaker segmentation is available, you will be able to record, replace or delete an on-device doctor voice sample.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
