package com.carelipik.app.ui.screens.transcript

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.carelipik.app.domain.transcription.TranscriptConcern
import com.carelipik.app.domain.transcription.TranscriptConcernType
import com.carelipik.app.domain.transcription.SpeakerRole
import com.carelipik.app.domain.transcription.TranscriptSegment
import com.carelipik.app.domain.voice.DoctorVoiceRoleMatchResult
import com.carelipik.app.ui.components.ConsultationScreenHeader

@Composable
fun TranscriptScreen(
    uiState: TranscriptUiState,
    onTranscriptChanged: (String) -> Unit,
    onConfirmConcern: (String) -> Unit,
    onApplySuggestion: (String) -> Unit,
    onOnlineAnalysisConsentChanged: (Boolean) -> Unit,
    onAnalyzeTermsOnline: () -> Unit,
    onViewModeChanged: (TranscriptViewMode) -> Unit,
    onSpeakerRoleAssigned: (String, SpeakerRole) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSpeakerNameChanged: (String, String) -> Unit = { _, _ -> },
    onAcceptHybridCorrection: (String) -> Unit = {},
    onRejectHybridCorrection: (String) -> Unit = {},
    onCancelTranscription: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ConsultationScreenHeader(
            title = "Review transcript",
            subtitle = "Check every line against the consultation and correct anything that is unclear.",
            currentStep = 4,
            totalSteps = 7,
            onBack = onBack,
            backEnabled = uiState.status != TranscriptStatus.Processing
        )
        TranscriptNotice(
            languageName = uiState.language.displayName,
            engineName = uiState.engine.displayName,
            isOffline = uiState.engine.isOffline
        )
        when (uiState.status) {
            TranscriptStatus.Idle,
            TranscriptStatus.Processing -> {
                ProcessingTranscript(uiState.engine.isOffline, uiState.transcriptionStage?.displayName)
                if (uiState.status == TranscriptStatus.Processing && uiState.transcriptionStage != null) {
                    OutlinedButton(onClick = onCancelTranscription, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel transcription")
                    }
                }
            }
            TranscriptStatus.Error -> ErrorTranscript(
                message = uiState.errorMessage ?: "Transcription could not be completed.",
                onRetry = onRetry
            )
            TranscriptStatus.Ready -> {
                uiState.hybridReview?.let { review ->
                    val playback = remember { RegionPlaybackController() }
                    CompositionLocalProvider(LocalRegionPlayback provides playback) {
                        HybridTranscriptReviewPanel(review, onAcceptHybridCorrection, onRejectHybridCorrection,
                            uiState.sourceAudioPath)
                        HybridWordReviewPanel(uiState, onAcceptHybridCorrection)
                    }
                }
                uiState.speakerSeparationWarning?.let { warning ->
                    SpeakerSeparationWarning(warning)
                }
                OnlineClinicalAnalysisPanel(
                    source = uiState.clinicalAnalysisSource,
                    warning = uiState.clinicalAnalysisWarning,
                    hasConsent = uiState.hasOnlineAnalysisConsent,
                    isAnalyzing = uiState.isAnalyzingTerms,
                    onConsentChanged = onOnlineAnalysisConsentChanged,
                    onAnalyze = onAnalyzeTermsOnline
                )
                if (uiState.concerns.isNotEmpty()) {
                    TranscriptTermReviewPanel(
                        uiState = uiState,
                        onConfirmConcern = onConfirmConcern,
                        onApplySuggestion = onApplySuggestion
                    )
                }
                if (uiState.canShowConversation) {
                    TranscriptViewModeSelector(
                        selectedMode = uiState.viewMode,
                        onModeChanged = onViewModeChanged
                    )
                }
                if (uiState.speakerIds.isNotEmpty()) {
                    SpeakerRoleReviewPanel(
                        uiState = uiState,
                        doctorVoiceMatch = uiState.doctorVoiceMatch,
                        onSpeakerRoleAssigned = onSpeakerRoleAssigned,
                        onSpeakerNameChanged = onSpeakerNameChanged
                    )
                } else {
                    UnsegmentedTranscriptNotice()
                }
                if (uiState.isAnalyzingTerms) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("medical_term_loader"))
                    Text("Enhancing medical terms with Gemini…", style = MaterialTheme.typography.bodyMedium)
                }
                when (uiState.viewMode) {
                    TranscriptViewMode.FullTranscript -> OutlinedTextField(
                        value = uiState.labelProjection.text,
                        onValueChange = onTranscriptChanged,
                        label = { Text("Edit full transcript") },
                        supportingText = { Text(reviewSupportingText(uiState)) },
                        isError = uiState.transcriptError != null,
                        minLines = 12,
                        enabled = !uiState.isAnalyzingTerms,
                        modifier = Modifier.fillMaxWidth().testTag("full_transcript_editor")
                    )
                    TranscriptViewMode.Conversation -> ConversationTranscript(
                        uiState = uiState
                    )
                }
                Button(
                    onClick = onContinue,
                    enabled = uiState.canContinue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (uiState.pendingHybridCorrections) {
                            "Review model disagreements to continue"
                        } else if (uiState.pendingConcerns.isEmpty()) {
                            if (uiState.pendingSpeakerIds.isEmpty()) {
                                "Continue to clinical draft"
                            } else {
                                "Identify speakers to continue"
                            }
                        } else {
                            "Review highlighted terms to continue"
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineClinicalAnalysisPanel(
    source: String?,
    warning: String?,
    hasConsent: Boolean,
    isAnalyzing: Boolean,
    onConsentChanged: (Boolean) -> Unit,
    onAnalyze: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (source == null) {
                    "Optional online medical-term enhancement"
                } else {
                    "$source medical-term enhancement"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = warning ?: "Gemini can find additional medicine and disease candidates. " +
                    "Only the reviewed transcript is sent; consultation audio is never sent.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = hasConsent,
                    onCheckedChange = onConsentChanged,
                    enabled = !isAnalyzing
                )
                Text(
                    "I have consent to send this transcript for online medical-term analysis",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            OutlinedButton(
                onClick = onAnalyze,
                enabled = hasConsent && !isAnalyzing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Enhancing medical terms…")
                } else {
                    Text(if (source == null) "Enhance terms with Gemini" else "Analyze again")
                }
            }
        }
    }
}

@Composable
private fun SpeakerSeparationWarning(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
private fun TranscriptViewModeSelector(
    selectedMode: TranscriptViewMode,
    onModeChanged: (TranscriptViewMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Transcript layout",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TranscriptViewMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = selectedMode == mode,
                    onClick = { onModeChanged(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = TranscriptViewMode.entries.size
                    ),
                    label = {
                        Text(
                            if (mode == TranscriptViewMode.FullTranscript) {
                                "Full transcript"
                            } else {
                                "Conversation"
                            }
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun SpeakerRoleReviewPanel(
    uiState: TranscriptUiState,
    doctorVoiceMatch: DoctorVoiceRoleMatchResult?,
    onSpeakerRoleAssigned: (String, SpeakerRole) -> Unit,
    onSpeakerNameChanged: (String, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Confirm detected speakers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (doctorVoiceMatch) {
                        is DoctorVoiceRoleMatchResult.Matched ->
                            "Matched the enrolled doctor voice locally. Check or correct the roles."
                        is DoctorVoiceRoleMatchResult.Uncertain ->
                            "The enrolled voice match was uncertain. Confirm each person's role."
                        DoctorVoiceRoleMatchResult.NotEnrolled ->
                            "No doctor voice is enrolled. Confirm each person's role."
                        is DoctorVoiceRoleMatchResult.Unavailable ->
                            "Automatic local matching was unavailable. Confirm each person's role."
                        null -> "Choose Doctor, Patient, or Other for each person. Names are optional."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            uiState.speakerIds.forEach { speakerId ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = uiState.personLabel(speakerId),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = uiState.speakerNames[speakerId].orEmpty(),
                        onValueChange = { onSpeakerNameChanged(speakerId, it) },
                        label = { Text("Name (optional)") },
                        placeholder = { Text(uiState.personLabel(speakerId)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("speaker_name_$speakerId")
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf(SpeakerRole.Doctor, SpeakerRole.Patient, SpeakerRole.Other).forEachIndexed { index, role ->
                            SegmentedButton(
                                selected = uiState.speakerRoles[speakerId] == role,
                                onClick = { onSpeakerRoleAssigned(speakerId, role) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                                label = { Text(role.displayName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnsegmentedTranscriptNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = "This engine returned one continuous transcript, so speaker chat is not " +
                "available yet. You can edit and review the full transcript below.",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

@Composable
private fun ConversationTranscript(
    uiState: TranscriptUiState
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        uiState.segments.forEach { segment ->
            val role = uiState.speakerRoles[segment.speakerId] ?: SpeakerRole.Unassigned
            ConversationBubble(segment = segment, role = role, label = uiState.speakerLabel(segment.speakerId))
        }
        Text(
            text = "Switch to Full transcript to edit wording or speaker labels.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConversationBubble(segment: TranscriptSegment, role: SpeakerRole, label: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        if (role == SpeakerRole.Doctor) Spacer(modifier = Modifier.weight(0.16f))
        Surface(
            modifier = Modifier.weight(if (role == SpeakerRole.Unassigned) 1f else 0.84f),
            shape = RoundedCornerShape(18.dp),
            color = when (role) {
                SpeakerRole.Doctor -> MaterialTheme.colorScheme.primaryContainer
                SpeakerRole.Patient -> MaterialTheme.colorScheme.secondaryContainer
                SpeakerRole.Unassigned, SpeakerRole.Other -> MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = label,
                    modifier = Modifier.testTag("conversation_label_${segment.speakerId}"),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = when (role) {
                        SpeakerRole.Doctor -> MaterialTheme.colorScheme.onPrimaryContainer
                        SpeakerRole.Patient -> MaterialTheme.colorScheme.onSecondaryContainer
                        SpeakerRole.Unassigned, SpeakerRole.Other -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(text = segment.transcript, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (role == SpeakerRole.Patient) Spacer(modifier = Modifier.weight(0.16f))
    }
}

private fun reviewSupportingText(uiState: TranscriptUiState): String =
    uiState.transcriptError ?: when {
        uiState.pendingHybridCorrections -> "Review the Whisper/MedASR disagreements above."
        uiState.pendingSpeakerIds.isNotEmpty() ->
            "Confirm each person's role above."
        uiState.pendingConcerns.isNotEmpty() ->
            "Review ${uiState.pendingConcerns.size} highlighted " +
                pluralize(uiState.pendingConcerns.size, "term", "terms") + " above."
        uiState.concerns.isNotEmpty() -> "All highlighted terms have been reviewed."
        else -> "Speaker labels and transcript text can be corrected here."
    }

@Composable
private fun TranscriptTermReviewPanel(
    uiState: TranscriptUiState,
    onConfirmConcern: (String) -> Unit,
    onApplySuggestion: (String) -> Unit
) {
    val pendingColor = MaterialTheme.colorScheme.tertiaryContainer
    val confirmedColor = MaterialTheme.colorScheme.primaryContainer
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Terms to verify",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Soft coral text needs confirmation. Teal text has been reviewed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ReviewCountBadge(
                    reviewed = uiState.confirmedConcernCount,
                    total = uiState.concerns.size
                )
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = buildHighlightedTranscript(
                        transcript = uiState.labelProjection.text,
                        concerns = uiState.concerns.map { concern ->
                            concern.copy(
                                startIndex = uiState.labelProjection.displayOffset(concern.startIndex),
                                endIndexExclusive = uiState.labelProjection.displayOffset(concern.endIndexExclusive)
                            )
                        },
                        confirmedConcernIds = uiState.confirmedConcernIds,
                        pendingColor = pendingColor,
                        confirmedColor = confirmedColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                        .testTag("highlighted_transcript_preview"),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            uiState.pendingConcerns.firstOrNull()?.let { concern ->
                ConcernReviewCard(
                    concern = concern,
                    isConfirmed = false,
                    onConfirm = { onConfirmConcern(concern.id) },
                    onApplySuggestion = { onApplySuggestion(concern.id) }
                )
            } ?: Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "All highlighted terms reviewed",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Text(
                text = if (uiState.clinicalAnalysisSource == null) {
                    "This is a local wording check, not an ASR confidence score or a diagnosis. " +
                        "Always compare highlighted text with the recording."
                } else {
                    "Highlights combine local wording checks with " +
                        "${uiState.clinicalAnalysisSource} candidates. They are not a diagnosis. " +
                        "Always compare highlighted text with the recording."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReviewCountBadge(reviewed: Int, total: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (reviewed == total) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        }
    ) {
        Text(
            text = "$reviewed/$total checked",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ConcernReviewCard(
    concern: TranscriptConcern,
    isConfirmed: Boolean,
    onConfirm: () -> Unit,
    onApplySuggestion: () -> Unit
) {
    val isPossibleError = concern.type == TranscriptConcernType.PossibleRecognitionError
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isConfirmed) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            1.dp,
            if (isConfirmed) {
                MaterialTheme.colorScheme.primary
            } else if (isPossibleError) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (isConfirmed) {
                    "Reviewed"
                } else if (isPossibleError) {
                    "Possible recognition error"
                } else {
                    "Medical term"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isPossibleError && !isConfirmed) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Text(
                text = "“${concern.text}”",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = concern.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!isConfirmed) {
                concern.suggestedReplacement?.let { replacement ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                            Text("Keep as written")
                        }
                        Button(onClick = onApplySuggestion, modifier = Modifier.weight(1f)) {
                            Text("Use “$replacement”")
                        }
                    }
                } ?: Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                    Text("Confirm term")
                }
            }
        }
    }
}

private fun buildHighlightedTranscript(
    transcript: String,
    concerns: List<TranscriptConcern>,
    confirmedConcernIds: Set<String>,
    pendingColor: Color,
    confirmedColor: Color
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    concerns.sortedBy { it.startIndex }.forEach { concern ->
        if (
            concern.startIndex < cursor ||
            concern.startIndex !in transcript.indices ||
            concern.endIndexExclusive !in 1..transcript.length
        ) {
            return@forEach
        }
        append(transcript.substring(cursor, concern.startIndex))
        withStyle(
            SpanStyle(
                background = if (concern.id in confirmedConcernIds) {
                    confirmedColor
                } else {
                    pendingColor
                },
                fontWeight = FontWeight.Bold
            )
        ) {
            append(transcript.substring(concern.startIndex, concern.endIndexExclusive))
        }
        cursor = concern.endIndexExclusive
    }
    append(transcript.substring(cursor))
}

private fun pluralize(count: Int, singular: String, plural: String): String =
    if (count == 1) singular else plural

@Composable
private fun TranscriptNotice(
    languageName: String,
    engineName: String,
    isOffline: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                if (isOffline) "On-device transcription" else "Secure online transcription",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "Selected mode: $languageName",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                "Engine: $engineName",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                if (isOffline) {
                    "This text was generated on this device. Verify every word and add or " +
                        "correct speaker labels before continuing."
                } else {
                    "The recording was sent to CareLipik's configured transcription service. " +
                        "Speaker numbers show different voices, not confirmed doctor or patient " +
                        "roles. Verify every word and role before continuing."
                },
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ProcessingTranscript(isOffline: Boolean, stage: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator()
        Text(stage ?: "Preparing transcript…", style = MaterialTheme.typography.titleMedium)
        Text(
            if (isOffline) {
                "Processing stays on this device."
            } else {
                "Securely uploading and separating speakers. This can take a few minutes."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorTranscript(message: String, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Transcript unavailable", style = MaterialTheme.typography.titleMedium)
            Text(message)
            OutlinedButton(onClick = onRetry) { Text("Try again") }
        }
    }
}
