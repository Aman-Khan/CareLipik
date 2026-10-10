package com.carelipik.app.ui.screens.transcript

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    audioPath: String? = null
) {
    var showOriginal by rememberSaveable { mutableStateOf(false) }
    val pending = review.corrections.filter {
        it.status == CorrectionStatus.Suggested || it.status == CorrectionStatus.Unresolved
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Hybrid review · Experimental", style = MaterialTheme.typography.titleMedium)
            Text(
                "Whisper is the original transcript. MedASR checks selected English audio only. " +
                    "No changes are automatic; review disagreements against the recording.",
                style = MaterialTheme.typography.bodySmall
            )
            review.notices.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("${review.verifiedAudio.size} audio regions selected for automatic MedASR checking",
                style = MaterialTheme.typography.labelMedium)
            OutlinedButton(onClick = { showOriginal = !showOriginal }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showOriginal) "Hide original Whisper transcript" else "Show original Whisper transcript")
            }
            if (showOriginal) Text(review.originalWhisperTranscript, style = MaterialTheme.typography.bodyMedium)
            Text(
                if (pending.isEmpty()) "No pending model disagreements"
                else "${pending.size} model disagreements to review",
                style = MaterialTheme.typography.labelLarge
            )
            pending.firstOrNull()?.let { correction ->
                AudioRegionReplayButton(audioPath, correction.startMs, correction.endMs)
                Text(
                    String.format(Locale.ROOT, "Audio %.1f–%.1f seconds", correction.startMs / 1_000.0, correction.endMs / 1_000.0),
                    style = MaterialTheme.typography.labelMedium
                )
                Text("Whisper context", style = MaterialTheme.typography.labelLarge)
                Text(correction.whisperContext)
                Text("MedASR alternative", style = MaterialTheme.typography.labelLarge)
                Text(correction.medAsrAlternative)
                correction.reasons.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (correction.status == CorrectionStatus.Suggested) {
                    Text(
                        "Suggested phrase: ${correction.originalText.ifEmpty { "(insert text)" }} → " +
                            correction.suggestedText.ifEmpty { "(remove text)" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(onClick = { onAccept(correction.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Use suggested phrase")
                    }
                } else {
                    Text("This alternative cannot be aligned safely. Keep Whisper or edit the full transcript manually.")
                }
                OutlinedButton(onClick = { onReject(correction.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Keep current transcript")
                }
            }
        }
    }
}
