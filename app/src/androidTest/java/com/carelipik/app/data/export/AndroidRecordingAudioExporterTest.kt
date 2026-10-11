package com.carelipik.app.data.export

import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.carelipik.app.data.audio.AndroidMicrophoneRecorder
import com.carelipik.app.domain.export.RecordingAudioExportResult
import com.carelipik.app.domain.model.RecordedAudio
import com.carelipik.app.domain.recording.AudioImportResult
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidRecordingAudioExporterTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val id = UUID.randomUUID().toString()
    private val source = File(context.cacheDir, "consultation_audio/synthetic-download-$id.wav")
    private val destination = File(context.cacheDir, "consultation_exports/synthetic-download-$id.wav")

    @After
    fun clearSyntheticFiles() {
        source.delete()
        destination.delete()
    }

    private fun audio(): RecordedAudio {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(32_036); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(16_000); putInt(32_000)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(32_000)
        }.array()
        source.parentFile!!.mkdirs()
        source.writeBytes(header + ByteArray(32_000))
        return RecordedAudio(source.absolutePath, source.length(), durationMillis = 1_000L)
    }

    private fun destinationUri(): String {
        destination.parentFile!!.mkdirs()
        destination.createNewFile()
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", destination).toString()
    }

    @Test
    fun downloadedWav_matchesOriginalAndCanBeImportedAgain() = runBlocking {
        val audio = audio()
        val original = source.readBytes()
        val uri = destinationUri()
        val result = AndroidRecordingAudioExporter(context).export(audio, uri)

        assertTrue(result is RecordingAudioExportResult.Success)
        assertEquals(audio.sizeBytes, (result as RecordingAudioExportResult.Success).sizeBytes)
        assertArrayEquals(original, destination.readBytes())
        val recorder = AndroidMicrophoneRecorder(context)
        try {
            val imported = recorder.importAudio(uri)
            assertTrue(imported is AudioImportResult.Success)
            assertArrayEquals(original, File((imported as AudioImportResult.Success).audio.localPath).readBytes())
        } finally {
            recorder.close()
        }
        assertTrue(destination.exists())
        assertTrue(source.exists())
    }

    @Test
    fun failedCopy_removesNewEmptyDocumentAndKeepsSource() = runBlocking {
        val audio = audio()
        source.writeText("Synthetic damaged WAV")
        val result = AndroidRecordingAudioExporter(context).export(audio, destinationUri())

        assertTrue(result is RecordingAudioExportResult.Failure)
        assertFalse(destination.exists())
        assertTrue(source.exists())
    }
}
