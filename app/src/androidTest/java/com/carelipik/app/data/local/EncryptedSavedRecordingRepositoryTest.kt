package com.carelipik.app.data.local

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.carelipik.app.domain.model.RecordedAudio
import com.carelipik.app.domain.model.RecordedAudioSource
import com.carelipik.app.domain.model.SavedRecording
import com.carelipik.app.domain.transcription.TranscriptionEngineOption
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedSavedRecordingRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val directory = File(context.filesDir, "synthetic_saved_recordings_test")
    private val cache = File(context.cacheDir, "synthetic_saved_recordings_test")
    private val id = "00000000-0000-0000-0000-000000000123"
    private val recording = SavedRecording(
        id, 1_000L, "Synthetic patient", "30", "Synthetic visit", TranscriptionLanguage.Hindi,
        TranscriptionEngineOption.WhisperMultilingual, "Synthetic WAV", 1_000L,
        RecordedAudioSource.Microphone, true
    )
    private fun repository() = EncryptedSavedRecordingRepository(context, directory, cache)

    @Before
    fun prepare() {
        clear()
        cache.mkdirs()
    }

    @After
    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
        cache.listFiles()?.forEach { it.delete() }
        directory.delete()
        cache.delete()
    }

    private fun syntheticAudio(): RecordedAudio {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(32_036); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(16_000); putInt(32_000)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(32_000)
        }.array()
        val source = File(cache, "synthetic.wav")
        source.writeBytes(header + ByteArray(32_000))
        return RecordedAudio(source.absolutePath, source.length(), durationMillis = 1_000L)
    }

    @Test
    fun recordingAndDetails_surviveRepositoryRecreationWithoutPlaintextAtRest() = runBlocking {
        val audio = syntheticAudio()
        val original = File(audio.localPath).readBytes()
        repository().save(recording, audio)
        File(audio.localPath).delete()

        val recreated = repository()
        assertEquals(listOf(recording), recreated.list())
        val restored = recreated.restore(id)
        assertArrayEquals(original, File(restored.audio.localPath).readBytes())
        val encrypted = File(directory, "$id.clr").readText(Charsets.ISO_8859_1)
        assertFalse(encrypted.contains("Synthetic patient"))
        assertFalse(encrypted.contains("RIFF"))

        File(restored.audio.localPath).delete()
        assertEquals(1, recreated.list().size)
        recreated.delete(id)
        assertTrue(recreated.list().isEmpty())
    }

    @Test
    fun failedReplacement_preservesPreviousSavedRecording() = runBlocking {
        val audio = syntheticAudio()
        repository().save(recording, audio)
        File(audio.localPath).writeText("Synthetic invalid audio")

        assertTrue(runCatching { repository().save(recording.copy(patientName = "Replacement"), audio) }.isFailure)
        assertEquals(listOf(recording), repository().list())
        assertEquals(1_000L, repository().restore(id).audio.durationMillis)
    }

    @Test
    fun tamperedAudio_isRejectedAndDecryptedTemporaryFileIsRemoved() = runBlocking {
        repository().save(recording, syntheticAudio())
        RandomAccessFile(File(directory, "$id.clr"), "rw").use {
            it.seek(it.length() - 1)
            val last = it.readByte()
            it.seek(it.length() - 1)
            it.writeByte(last.toInt() xor 1)
        }

        assertTrue(runCatching { repository().restore(id) }.isFailure)
        assertTrue(cache.listFiles().orEmpty().none { it.name.startsWith("restored-") })
    }
}
