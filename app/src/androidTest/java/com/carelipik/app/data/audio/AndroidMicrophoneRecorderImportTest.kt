package com.carelipik.app.data.audio

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.carelipik.app.domain.model.RecordedAudioSource
import com.carelipik.app.domain.recording.AudioImportResult
import java.io.DataOutputStream
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidMicrophoneRecorderImportTest {
    @Test
    fun importAudio_copiesCompatibleWaveIntoPrivateTemporaryStorage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "synthetic-import-source.wav")
        writeWave(source, sampleCount = 16_000)
        val recorder = AndroidMicrophoneRecorder(context)

        try {
            val result = recorder.importAudio(Uri.fromFile(source).toString())

            assertTrue(result is AudioImportResult.Success)
            val audio = (result as AudioImportResult.Success).audio
            assertEquals(RecordedAudioSource.Imported, audio.source)
            assertEquals(1_000L, audio.durationMillis)
            assertNotEquals(source.absolutePath, audio.localPath)
            assertTrue(File(audio.localPath).isFile)
            assertTrue(File(audio.localPath).absolutePath.startsWith(context.cacheDir.absolutePath))
        } finally {
            recorder.close()
            source.delete()
        }
    }

    private fun writeWave(file: File, sampleCount: Int) {
        val dataSize = sampleCount * 2
        DataOutputStream(file.outputStream()).use { output ->
            output.writeBytes("RIFF")
            output.writeLittleEndianInt(dataSize + 36)
            output.writeBytes("WAVEfmt ")
            output.writeLittleEndianInt(16)
            output.writeLittleEndianShort(1)
            output.writeLittleEndianShort(1)
            output.writeLittleEndianInt(16_000)
            output.writeLittleEndianInt(32_000)
            output.writeLittleEndianShort(2)
            output.writeLittleEndianShort(16)
            output.writeBytes("data")
            output.writeLittleEndianInt(dataSize)
            repeat(sampleCount) { output.writeLittleEndianShort(0) }
        }
    }

    private fun DataOutputStream.writeLittleEndianInt(value: Int) {
        writeByte(value)
        writeByte(value shr 8)
        writeByte(value shr 16)
        writeByte(value shr 24)
    }

    private fun DataOutputStream.writeLittleEndianShort(value: Int) {
        writeByte(value)
        writeByte(value shr 8)
    }
}
