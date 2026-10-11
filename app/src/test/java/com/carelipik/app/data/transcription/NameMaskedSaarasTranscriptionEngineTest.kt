package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NameMaskedSaarasTranscriptionEngineTest {
    @Test fun progress_reportsPreprocessingBeforeUploadAndFinalizationAfterResponse() {
        val events = mutableListOf<TranscriptionStage>()
        val original = files.newFile("original.wav")
        val copy = files.newFile("masked.wav")
        val remote = object : AudioTranscriptionEngine {
            override val option = TranscriptionEngineOption.SaarasHindiHinglish
            override fun transcribe(audioPath: String, language: TranscriptionLanguage): TranscriptionResult {
                assertEquals(TranscriptionStage.SarvamTranscription, events.last())
                assertEquals(copy.absolutePath, audioPath)
                return TranscriptionResult.Success("Test Person feels better")
            }
        }
        val engine = NameMaskedSaarasTranscriptionEngine(remote,
            PatientNameAudioMasker { _, _, _ ->
                assertEquals(listOf(TranscriptionStage.PreprocessingAudio), events)
                PatientNameMaskingResult.Success(copy.path, listOf(AudioMaskWindow(0.0, 1.0)))
            }, "Test Person")
        val result = engine.transcribe(original.path, TranscriptionLanguage.English, events::add, {}) as TranscriptionResult.Success
        assertEquals(listOf(TranscriptionStage.PreprocessingAudio, TranscriptionStage.SarvamTranscription,
            TranscriptionStage.RedactingPatientName), events)
        assertEquals("Patient feels better", result.transcript)
        assertFalse(copy.exists())
    }

    @Test fun failedMasking_neverReportsSendingAudio() {
        val events = mutableListOf<TranscriptionStage>()
        val engine = NameMaskedSaarasTranscriptionEngine(delegate,
            PatientNameAudioMasker { _, _, _ -> PatientNameMaskingResult.Failure("Uncertain") }, "Test Person")
        engine.transcribe("original.wav", TranscriptionLanguage.English, events::add, {})
        assertEquals(listOf(TranscriptionStage.PreprocessingAudio), events)
        assertNull(uploaded)
    }

    @Test fun cancellationAfterMasking_deletesCopyAndBlocksUpload() {
        val original = files.newFile("original.wav")
        val copy = files.newFile("masked.wav")
        var checks = 0
        val engine = NameMaskedSaarasTranscriptionEngine(delegate,
            PatientNameAudioMasker { _, _, _ -> PatientNameMaskingResult.Success(copy.path,
                listOf(AudioMaskWindow(0.0, 1.0))) }, "Test Person")
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            engine.transcribe(original.path, TranscriptionLanguage.English, {}, {
                if (++checks == 2) throw kotlinx.coroutines.CancellationException()
            })
        }
        assertFalse(copy.exists())
        assertNull(uploaded)
    }

    @Test fun unsupportedLanguage_blocksScanAndUpload() {
        val engine = NameMaskedSaarasTranscriptionEngine(delegate,
            PatientNameAudioMasker { _, _, _ -> error("Must not scan") }, "Test Person")
        assertTrue(engine.transcribe("original", TranscriptionLanguage.Hindi) is TranscriptionResult.Failure)
        assertNull(uploaded)
    }
    @get:Rule val files = TemporaryFolder()
    private var uploaded: String? = null
    private val delegate = object : AudioTranscriptionEngine {
        override val option = TranscriptionEngineOption.SaarasHindiHinglish
        override fun transcribe(audioPath: String, language: TranscriptionLanguage): TranscriptionResult {
            uploaded = audioPath
            return TranscriptionResult.Success("Reviewed speech")
        }
    }

    @Test fun failedDetection_blocksUpload() {
        val engine = NameMaskedSaarasTranscriptionEngine(delegate,
            PatientNameAudioMasker { _, _, _ -> PatientNameMaskingResult.Failure("Uncertain") }, "Test Person")
        assertTrue(engine.transcribe("original", TranscriptionLanguage.English) is TranscriptionResult.Failure)
        assertNull(uploaded)
    }

    @Test fun blankName_blocksDetectorAndUpload() {
        val engine = NameMaskedSaarasTranscriptionEngine(delegate,
            PatientNameAudioMasker { _, _, _ -> error("Must not scan") }, "")
        assertTrue(engine.transcribe("original", TranscriptionLanguage.English) is TranscriptionResult.Failure)
        assertNull(uploaded)
    }

    @Test fun scannerException_blocksUpload() {
        val engine = NameMaskedSaarasTranscriptionEngine(delegate,
            PatientNameAudioMasker { _, _, _ -> throw IllegalStateException("Unavailable") }, "Test Person")
        assertTrue(engine.transcribe("original", TranscriptionLanguage.English) is TranscriptionResult.Failure)
        assertNull(uploaded)
    }

    @Test fun sourceFileReturnedByDetector_isNeverUploadedOrDeleted() {
        val original = files.newFile("original.wav").apply { writeText("local audio") }
        val engine = NameMaskedSaarasTranscriptionEngine(delegate,
            PatientNameAudioMasker { _, _, _ -> PatientNameMaskingResult.Success(original.path, listOf(AudioMaskWindow(0.0, 1.0))) }, "Test Person")
        assertTrue(engine.transcribe(original.path, TranscriptionLanguage.English) is TranscriptionResult.Failure)
        assertNull(uploaded)
        assertEquals("local audio", original.readText())
    }

    @Test fun successfulMask_uploadsOnlyCopyAndDeletesIt() {
        val original = files.newFile("original.wav").apply { writeText("local audio") }
        val copy = files.newFile("masked.wav").apply { writeText("noise") }
        val engine = NameMaskedSaarasTranscriptionEngine(delegate,
            PatientNameAudioMasker { _, _, _ -> PatientNameMaskingResult.Success(copy.path, listOf(AudioMaskWindow(0.0, 1.0))) }, "Test Person")
        assertTrue(engine.transcribe(original.path, TranscriptionLanguage.English) is TranscriptionResult.Success)
        assertEquals(copy.absolutePath, uploaded)
        assertFalse(copy.exists())
        assertEquals("local audio", original.readText())
    }

    @Test fun remoteNameEcho_isRedactedAcrossAllSpeakersBeforeReturning() {
        val original = files.newFile("original.wav")
        val copy = files.newFile("masked.wav")
        val remote = object : AudioTranscriptionEngine {
            override val option = TranscriptionEngineOption.SaarasHindiHinglish
            override fun transcribe(audioPath: String, language: TranscriptionLanguage) =
                TranscriptionResult.Success("Alice Example, Alice, Example",
                    listOf("Alice Example", "Alice", "Example", "Other speech").mapIndexed { index, text ->
                        TranscriptSegment("speaker${index + 1}", text, index.toDouble(), index + 0.5)
                    })
        }
        val engine = NameMaskedSaarasTranscriptionEngine(remote,
            PatientNameAudioMasker { _, _, _ -> PatientNameMaskingResult.Success(copy.path,
                listOf(AudioMaskWindow(0.0, 1.0))) }, "Alice Example")
        val result = engine.transcribe(original.path, TranscriptionLanguage.English) as TranscriptionResult.Success
        assertEquals("Patient, Patient, Patient", result.transcript)
        assertEquals(listOf("Patient", "Patient", "Patient", "Other speech"), result.segments.map { it.transcript })
        assertEquals(listOf("speaker1", "speaker2", "speaker3", "speaker4"), result.segments.map { it.speakerId })
        assertEquals(3.0, result.segments.last().startTimeSeconds!!, 0.0)
        assertFalse(copy.exists())
    }

    @Test fun uploadException_stillDeletesMaskedCopy() {
        val original = files.newFile("original.wav")
        val copy = files.newFile("masked.wav")
        val failing = object : AudioTranscriptionEngine {
            override val option = TranscriptionEngineOption.SaarasHindiHinglish
            override fun transcribe(audioPath: String, language: TranscriptionLanguage): TranscriptionResult = error("Network failed")
        }
        val engine = NameMaskedSaarasTranscriptionEngine(failing,
            PatientNameAudioMasker { _, _, _ -> PatientNameMaskingResult.Success(copy.path, listOf(AudioMaskWindow(0.0, 1.0))) }, "Test Person")
        assertTrue(engine.transcribe(original.path, TranscriptionLanguage.English) is TranscriptionResult.Failure)
        assertFalse(copy.exists())
        assertTrue(original.exists())
    }
}
