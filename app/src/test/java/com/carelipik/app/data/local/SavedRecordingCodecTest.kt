package com.carelipik.app.data.local

import com.carelipik.app.domain.model.RecordedAudioSource
import com.carelipik.app.domain.model.SavedRecording
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedRecordingCodecTest {
    private val recording = SavedRecording(
        "00000000-0000-0000-0000-000000000001", 100L, "Synthetic patient", "30",
        "Synthetic visit – हिंदी", TranscriptionLanguage.Hinglish,
        TranscriptionEngineOption.WhisperMultilingual, "Synthetic audio.wav", 60_000L,
        RecordedAudioSource.Microphone, true
    )

    @Test
    fun metadata_preservesPatientDetailsLanguageAndAudioSource() {
        assertEquals(recording, SavedRecordingCodec.decode(SavedRecordingCodec.encode(recording)))
    }

    @Test
    fun metadata_preservesSpeakerCount() {
        val configured = recording.copy(speakerCount = 4)
        assertEquals(configured, SavedRecordingCodec.decode(SavedRecordingCodec.encode(configured)))
    }

    @Test
    fun metadata_readsOldRecordingsWithDefaultSpeakerCount() {
        val bytes = SavedRecordingCodec.encode(recording)
        // Version 1 has identical fields without the trailing speaker-count integer.
        val legacy = bytes.copyOf(bytes.size - 4)
        legacy[3] = 1
        assertEquals(2, SavedRecordingCodec.decode(legacy).speakerCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun metadata_rejectsInvalidSpeakerCount() {
        SavedRecordingCodec.decode(SavedRecordingCodec.encode(recording.copy(speakerCount = 0)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun metadata_rejectsUnsupportedLanguageEngineCombination() {
        SavedRecordingCodec.decode(SavedRecordingCodec.encode(recording.copy(engine = TranscriptionEngineOption.MedAsrEnglish)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun metadata_rejectsMissingRecordingConsent() {
        SavedRecordingCodec.decode(SavedRecordingCodec.encode(recording.copy(hasRecordingConsent = false)))
    }
}
