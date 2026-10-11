package com.carelipik.app.ui.screens.doctorprofile

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.carelipik.app.domain.model.ProcessingPreference
import com.carelipik.app.domain.repository.ApiProvider
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.carelipik.app.ui.screens.export.HandwrittenSignaturePad
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun DoctorProfileScreen(
    uiState: DoctorProfileUiState,
    voiceUiState: DoctorVoiceEnrollmentUiState,
    apiCredentialsUiState: ApiCredentialsUiState = ApiCredentialsUiState(),
    onFullNameChanged: (String) -> Unit,
    onSpecialtyChanged: (String) -> Unit,
    onRegistrationNumberChanged: (String) -> Unit,
    onClinicNameChanged: (String) -> Unit,
    onPreferredLanguageChanged: (TranscriptionLanguage) -> Unit,
    onProcessingPreferenceChanged: (ProcessingPreference) -> Unit,
    onHandwrittenSignatureChanged: (String) -> Unit,
    onImportSignatureImage: (String, Float, Float, Float, Float) -> Unit,
    onStartVoiceSample: () -> Unit,
    onStopVoiceSample: () -> Unit,
    onImportVoiceSample: (String) -> Unit,
    onDeleteVoiceSample: () -> Unit,
    onSarvamKeyChanged: (String) -> Unit = {},
    onGeminiKeyChanged: (String) -> Unit = {},
    onAssemblyAiKeyChanged: (String) -> Unit = {},
    onSaveApiKey: (ApiProvider) -> Unit = {},
    onDeleteApiKey: (ApiProvider) -> Unit = {},
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    var isEditingSignature by remember(uiState.handwrittenSignature) {
        mutableStateOf(uiState.handwrittenSignature.isBlank())
    }
    var pendingSignatureCaptureUri by remember { mutableStateOf<Uri?>(null) }
    var pendingSignatureCropUri by remember { mutableStateOf<Uri?>(null) }
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionDenied = !isGranted
        if (isGranted) onStartVoiceSample()
    }
    val voiceSampleFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.toString()?.let(onImportVoiceSample)
    }
    val signatureImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        pendingSignatureCropUri = uri
    }
    val signatureCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { captured ->
        if (captured) pendingSignatureCropUri = pendingSignatureCaptureUri
    }
    val captureSignature = {
        val directory = File(context.cacheDir, "signature_capture").apply { mkdirs() }
        val file = File(directory, "doctor_signature_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file).also {
            pendingSignatureCaptureUri = it
            signatureCameraLauncher.launch(it)
        }
        Unit
    }
    val chooseVoiceSampleFile = {
        voiceSampleFileLauncher.launch(arrayOf("audio/wav", "audio/x-wav", "audio/*"))
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
    pendingSignatureCropUri?.let { uri ->
        SignatureCropDialog(
            uri = uri,
            onDismiss = { pendingSignatureCropUri = null },
            onCrop = { left, top, right, bottom ->
                onImportSignatureImage(uri.toString(), left, top, right, bottom)
                pendingSignatureCropUri = null
            }
        )
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
        ProfileSectionCard(
            title = "Saved signature",
            supportingText = "Draw it, photograph it, or upload an image. Photos are processed locally into black ink on a white background before the encrypted signature is saved."
        ) {
            HandwrittenSignaturePad(
                encodedSignature = uiState.handwrittenSignature,
                enabled = !uiState.isLoading && isEditingSignature,
                onSignatureChanged = onHandwrittenSignatureChanged,
                showClearAction = false
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isEditingSignature) {
                    OutlinedButton(
                        onClick = { isEditingSignature = false },
                        enabled = uiState.handwrittenSignature.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Done drawing") }
                } else {
                    OutlinedButton(
                        onClick = { isEditingSignature = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("Update signature") }
                }
                TextButton(
                    onClick = {
                        onHandwrittenSignatureChanged("")
                        isEditingSignature = true
                    },
                    enabled = uiState.handwrittenSignature.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("Clear signature") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = captureSignature,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.weight(1f)
                ) { Text("Use camera") }
                OutlinedButton(
                    onClick = { signatureImageLauncher.launch(arrayOf("image/*")) },
                    enabled = !uiState.isLoading,
                    modifier = Modifier.weight(1f)
                ) { Text("Upload image") }
            }
        }
        ApiCredentialsCard(
            uiState = apiCredentialsUiState,
            onSarvamKeyChanged = onSarvamKeyChanged,
            onGeminiKeyChanged = onGeminiKeyChanged,
            onAssemblyAiKeyChanged = onAssemblyAiKeyChanged,
            onSave = onSaveApiKey,
            onDelete = onDeleteApiKey
        )
        VoiceRecognitionCard(
            uiState = voiceUiState,
            permissionDenied = permissionDenied,
            onStart = startVoiceSampleWithPermission,
            onStop = onStopVoiceSample,
            onImport = chooseVoiceSampleFile,
            onDelete = onDeleteVoiceSample
        )
        Button(
            onClick = onSave,
            enabled = !uiState.isLoading && uiState.canSave && uiState.hasUnsavedChanges &&
                voiceUiState.status !in setOf(
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
        uiState.saveMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("doctor_profile_save_confirmation")
            )
        }
        Text(
            text = "Your profile, provider keys, and doctor voice enrollment stay saved on this phone across app and phone restarts. They are removed only when you delete them, clear app storage, or uninstall CareLipik.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SignatureCropDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onCrop: (Float, Float, Float, Float) -> Unit
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }
    }
    var horizontal by remember(uri) { mutableStateOf(0f..1f) }
    var vertical by remember(uri) { mutableStateOf(0f..1f) }
    val cropBorderColor = MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crop signature") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Move the sliders until the highlighted box contains only the signature.")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(Color.White)
                ) {
                    bitmap?.let {
                        androidx.compose.foundation.Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Signature photo to crop",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Canvas(Modifier.fillMaxSize()) {
                        val left = horizontal.start * size.width
                        val right = horizontal.endInclusive * size.width
                        val top = vertical.start * size.height
                        val bottom = vertical.endInclusive * size.height
                        val shade = Color.Black.copy(alpha = 0.48f)
                        drawRect(shade, size = androidx.compose.ui.geometry.Size(size.width, top))
                        drawRect(
                            shade,
                            topLeft = Offset(0f, bottom),
                            size = androidx.compose.ui.geometry.Size(size.width, size.height - bottom)
                        )
                        drawRect(
                            shade,
                            topLeft = Offset(0f, top),
                            size = androidx.compose.ui.geometry.Size(left, bottom - top)
                        )
                        drawRect(
                            shade,
                            topLeft = Offset(right, top),
                            size = androidx.compose.ui.geometry.Size(size.width - right, bottom - top)
                        )
                        drawRect(
                            color = cropBorderColor,
                            topLeft = Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 5f)
                        )
                    }
                }
                Text("Horizontal crop", style = MaterialTheme.typography.labelMedium)
                RangeSlider(
                    value = horizontal,
                    onValueChange = { if (it.endInclusive - it.start >= 0.08f) horizontal = it },
                    valueRange = 0f..1f
                )
                Text("Vertical crop", style = MaterialTheme.typography.labelMedium)
                RangeSlider(
                    value = vertical,
                    onValueChange = { if (it.endInclusive - it.start >= 0.08f) vertical = it },
                    valueRange = 0f..1f
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onCrop(horizontal.start, vertical.start, horizontal.endInclusive, vertical.endInclusive)
            }) { Text("Use cropped signature") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ApiCredentialsCard(
    uiState: ApiCredentialsUiState,
    onSarvamKeyChanged: (String) -> Unit,
    onGeminiKeyChanged: (String) -> Unit,
    onAssemblyAiKeyChanged: (String) -> Unit,
    onSave: (ApiProvider) -> Unit,
    onDelete: (ApiProvider) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Online service keys",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isExpanded) {
                        Text(
                            text = "Credentials are hidden",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    enabled = !uiState.isWorking,
                    modifier = Modifier
                        .semantics {
                            contentDescription = if (isExpanded) {
                                "Close online service key editor"
                            } else {
                                "Update online service keys"
                            }
                        }
                        .testTag("toggle_api_key_editor")
                ) {
                    ApiKeyEditIcon(close = isExpanded)
                }
            }
            if (isExpanded) {
                Text(
                    text = "Optional. Keys are encrypted with Android Keystore, excluded from backups, and never shown again after saving.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ApiKeyEditor(
                    provider = ApiProvider.Sarvam,
                    value = uiState.sarvamInput,
                    isSaved = uiState.hasSarvamKey,
                    enabled = !uiState.isWorking,
                    onValueChanged = onSarvamKeyChanged,
                    onSave = onSave,
                    onDelete = onDelete
                )
                ApiKeyEditor(
                    provider = ApiProvider.Gemini,
                    value = uiState.geminiInput,
                    isSaved = uiState.hasGeminiKey,
                    enabled = !uiState.isWorking,
                    onValueChanged = onGeminiKeyChanged,
                    onSave = onSave,
                    onDelete = onDelete
                )
                ApiKeyEditor(
                    provider = ApiProvider.AssemblyAI,
                    value = uiState.assemblyAiInput,
                    isSaved = uiState.hasAssemblyAiKey,
                    enabled = !uiState.isWorking,
                    onValueChanged = onAssemblyAiKeyChanged,
                    onSave = onSave,
                    onDelete = onDelete
                )
            }
            uiState.message?.let {
                if (isExpanded) Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isExpanded) Text(
                text = "Important: a key stored in a client app can still be extracted from a compromised device. Restrict provider quotas and rotate keys regularly. A server-held key remains safer for production.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ApiKeyEditIcon(close: Boolean) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(24.dp)) {
        if (close) {
            drawLine(color, Offset(5f, 5f), Offset(size.width - 5f, size.height - 5f), 2.5f)
            drawLine(color, Offset(size.width - 5f, 5f), Offset(5f, size.height - 5f), 2.5f)
        } else {
            drawLine(
                color = color,
                start = Offset(5f, size.height - 5f),
                end = Offset(size.width - 6f, 6f),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(4f, size.height - 4f),
                end = Offset(9f, size.height - 5f),
                strokeWidth = 2f
            )
        }
    }
}

@Composable
private fun ApiKeyEditor(
    provider: ApiProvider,
    value: String,
    isSaved: Boolean,
    enabled: Boolean,
    onValueChanged: (String) -> Unit,
    onSave: (ApiProvider) -> Unit,
    onDelete: (ApiProvider) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "${provider.name} · ${if (isSaved) "Configured" else "Not configured"}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChanged,
            enabled = enabled,
            label = { Text(if (isSaved) "Replacement API key" else "API key") },
            placeholder = { Text(if (isSaved) "Enter only to replace" else "Paste key") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("${provider.name.lowercase()}_api_key")
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSave(provider) },
                enabled = enabled && value.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isSaved) "Replace" else "Save securely")
            }
            if (isSaved) {
                OutlinedButton(
                    onClick = { onDelete(provider) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Remove")
                }
            }
        }
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
    onImport: () -> Unit,
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
                text = "Used automatically after offline Whisper or MedASR diarization to identify the doctor's speaker cluster. It never leaves this device and is not sent to Saaras.",
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
                                text = uiState.nextPrompt,
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
                                text = if (uiState.enrolledSampleCount >= 3) {
                                    "Doctor voice profile ready"
                                } else {
                                    "${uiState.enrolledSampleCount} of 3 voice samples saved"
                                },
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
                            Text(if (uiState.enrolledSampleCount >= 3) "Replace oldest" else "Add next sample")
                        }
                        OutlinedButton(
                            onClick = onDelete,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete")
                        }
                    }
                    OutlinedButton(
                        onClick = onImport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("doctor_voice_upload")
                    ) {
                        Text("Upload replacement WAV")
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
                        Text(if (uiState.hasExistingSample) "Record next sample" else "Record voice sample")
                    }
                    OutlinedButton(
                        onClick = onImport,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("doctor_voice_upload")
                    ) {
                        Text(if (uiState.hasExistingSample) "Upload next WAV" else "Upload voice sample")
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
            Text(
                text = "Upload format: 8–30 seconds, mono 16 kHz, 16-bit PCM WAV. The file is checked and copied into private on-device storage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
