package com.carelipik.app.ui.screens.transcript

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.carelipik.app.domain.transcription.CorrectionStatus
import com.carelipik.app.domain.transcription.HybridTranscriptionReview
import java.util.Locale

@Composable
internal fun HybridTranscriptReviewPanel(
    review: HybridTranscriptionReview,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
    audioPath: String? = null,
    onAcceptMedAsr: (String) -> Unit = {},
    onManualCorrection: (String, String) -> Unit = { _, _ -> }
) {
    val pending = review.corrections.filter {
        it.status == CorrectionStatus.Suggested || it.status == CorrectionStatus.Unresolved
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Doubtful phrase", style = MaterialTheme.typography.titleMedium)
            Text(
                "Whisper is the original transcript. MedASR checks selected English audio only. " +
                    "No changes are automatic; review disagreements against the recording.",
                style = MaterialTheme.typography.bodySmall
            )
            review.notices.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("${review.verifiedAudio.size} audio regions selected for automatic MedASR checking",
                style = MaterialTheme.typography.labelMedium)
            Text(
                if (pending.isEmpty()) "No pending model disagreements"
                else "${pending.size} model disagreements to review",
                style = MaterialTheme.typography.labelLarge
            )
            pending.firstOrNull()?.let { correction ->
                var manualText by rememberSaveable(correction.id) { mutableStateOf(correction.originalText.ifBlank { correction.whisperContext }) }
                AudioRegionReplayButton(audioPath, correction.startMs, correction.endMs)
                Text(
                    String.format(Locale.ROOT, "Audio %.1f–%.1f seconds", correction.startMs / 1_000.0, correction.endMs / 1_000.0),
                    style = MaterialTheme.typography.labelMedium
                )
                Text("Whisper context", style = MaterialTheme.typography.labelLarge)
                Text(correction.whisperContext)
                correction.speakerId?.let { Text("Speaker: $it", style = MaterialTheme.typography.labelMedium) }
                correction.qwenSuggestion?.let {
                    Text("Qwen3 suggestion: ${correction.originalText} → $it")
                    Text(when {
                        correction.medAsrAlternative.isBlank() -> "Unverified by MedASR (language, limits, or inference unavailable)"
                        correction.modelsAgree == true -> "Qwen3 and MedASR agree on this phrase"
                        correction.modelsAgree == false -> "Qwen3 and MedASR disagree on this phrase"
                        else -> "MedASR ran; agreement on this phrase could not be established"
                    }, style = MaterialTheme.typography.labelMedium)
                }
                if (correction.medAsrAlternative.isNotBlank()) {
                    Text("MedASR alternative", style = MaterialTheme.typography.labelLarge)
                    Text(correction.medAsrAlternative)
                }
                correction.reasons.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (correction.status == CorrectionStatus.Suggested) {
                    Text(
                        "Suggested phrase: ${correction.originalText.ifEmpty { "(insert text)" }} → " +
                            correction.suggestedText.ifEmpty { "(remove text)" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = { onAccept(correction.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (correction.qwenSuggestion != null) "Use Qwen3: ${correction.suggestedText}" else "Use MedASR: ${correction.suggestedText}")
                    }
                    correction.medAsrSuggestedText?.let { alternative ->
                        OutlinedButton(onClick = { onAcceptMedAsr(correction.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Use MedASR phrase: $alternative")
                        }
                    }
                } else {
                    Text("Model replacements could not be aligned safely. Manual replacement is applied only when the original phrase is uniquely located; otherwise edit the full transcript.")
                }
                OutlinedTextField(value = manualText, onValueChange = { manualText = it },
                    label = { Text("Manual replacement for this phrase") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { onManualCorrection(correction.id, manualText) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Use manual text")
                }
                OutlinedButton(onClick = { onReject(correction.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Keep Whisper: ${correction.originalText}")
                }
            }
        }
    }
}
