package com.carelipik.app.ui.screens.transcript

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.carelipik.app.domain.transcription.CorrectionStatus

/** Every tap applies an already computed suggestion; this UI never invokes an ASR engine. */
@Composable
internal fun HybridWordReviewPanel(state: TranscriptUiState, onAccept: (String) -> Unit) {
    val review = state.hybridReview ?: return
    val applicable = review.corrections.filter { correction ->
        val start = correction.transcriptStartIndex
        val end = correction.transcriptEndIndex
        correction.status == CorrectionStatus.Suggested && start != null && end != null &&
            start in 0..state.transcript.length && end in start..state.transcript.length &&
            state.transcript.substring(start, end) == correction.originalText
    }
    val highlighted = buildAnnotatedString {
        append(state.transcript)
        if (state.transcript == review.originalWhisperTranscript) {
            review.uncertainSegments.forEach { uncertain ->
                val region = review.whisperSegments.getOrNull(uncertain.segmentIndex) ?: return@forEach
                if (region.transcriptStartIndex < 0) return@forEach
                uncertain.uncertainWords.forEach { word ->
                    val start = region.transcriptStartIndex + word.startIndex
                    val end = region.transcriptStartIndex + word.endIndex
                    if (start in state.transcript.indices && end in (start + 1)..state.transcript.length) {
                        addStyle(SpanStyle(background = MaterialTheme.colorScheme.surfaceVariant,
                            fontWeight = FontWeight.Bold), start, end)
                    }
                }
            }
        }
        applicable.forEach { correction ->
            val start = requireNotNull(correction.transcriptStartIndex)
            val end = requireNotNull(correction.transcriptEndIndex)
            if (end > start) {
                addLink(LinkAnnotation.Clickable("suggestion-${correction.id}",
                    TextLinkStyles(style = SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer,
                        fontWeight = FontWeight.Bold)), linkInteractionListener = { onAccept(correction.id) }), start, end)
            }
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Words to review", style = MaterialTheme.typography.titleMedium)
            Text(if (review.isLlmGuided)
                "Qwen3 and MedASR suggestions require your review. Check their verification status above before applying a phrase. Tapping applies the displayed suggestion."
                else "Any MedASR alternatives shown here were prepared automatically. Tap a colored phrase or its alternative below to apply it. Tapping does not run a model.")
            Text("Gray words had low Whisper decoder scores. Scores and model agreement do not guarantee correctness; some regions may be skipped by language or verification limits.",
                style = MaterialTheme.typography.bodySmall)
            Text(highlighted, style = MaterialTheme.typography.bodyLarge)
            if (state.transcript == review.originalWhisperTranscript) {
                review.uncertainSegments.filter { it.uncertainWords.isNotEmpty() }.forEach { uncertain ->
                    val region = review.whisperSegments.getOrNull(uncertain.segmentIndex) ?: return@forEach
                    Text("Words to double-check: ${uncertain.uncertainWords.joinToString { it.text }}",
                        style = MaterialTheme.typography.labelLarge)
                    val checked = review.verifiedAudio.any { it.startMs <= region.startMs && it.endMs >= region.endMs }
                    Text(if (checked) "MedASR verification was attempted for this region. Review available alternatives and notices above."
                        else "No MedASR check was available within the language or audio limits. Listen and edit manually.",
                        style = MaterialTheme.typography.bodySmall)
                    AudioRegionReplayButton(state.sourceAudioPath, region.startMs, region.endMs)
                }
            }
            applicable.forEach { correction ->
                OutlinedButton(onClick = { onAccept(correction.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text((if (review.isLlmGuided) {
                        if (correction.qwenSuggestion != null) "Qwen3: " else "MedASR: "
                    } else "") + "${correction.originalText.ifEmpty { "(insert)" }} -> ${correction.suggestedText.ifEmpty { "(remove)" }}")
                }
            }
            if (applicable.isEmpty()) Text("No prepared phrase replacement is available. Review unresolved alternatives above or edit the full transcript.")
        }
    }
}
