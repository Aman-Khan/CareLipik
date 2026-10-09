package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.OnlineTranscriptReviewAnalyzer
import com.carelipik.app.domain.transcription.OnlineTranscriptReviewResult
import com.carelipik.app.domain.transcription.TranscriptionLanguage

class PreferDeviceKeyTranscriptReviewAnalyzer(
    private val hasDeviceKey: () -> Boolean,
    private val direct: OnlineTranscriptReviewAnalyzer,
    private val fallback: OnlineTranscriptReviewAnalyzer
) : OnlineTranscriptReviewAnalyzer {
    override fun analyze(transcript: String, language: TranscriptionLanguage): OnlineTranscriptReviewResult =
        if (hasDeviceKey()) direct.analyze(transcript, language) else fallback.analyze(transcript, language)
}
