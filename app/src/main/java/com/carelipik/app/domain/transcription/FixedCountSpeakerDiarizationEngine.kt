package com.carelipik.app.domain.transcription

/** Pins clustering to the consultation's chosen count for every inference call. */
class FixedCountSpeakerDiarizationEngine(
    private val delegate: SpeakerDiarizationEngine,
    speakerCount: Int
) : SpeakerDiarizationEngine {
    private val count = SpeakerCount.validate(speakerCount)

    override fun diarize(
        samples: FloatArray,
        sampleRate: Int,
        expectedSpeakerCount: Int
    ): SpeakerDiarizationResult = delegate.diarize(samples, sampleRate, count)
}
