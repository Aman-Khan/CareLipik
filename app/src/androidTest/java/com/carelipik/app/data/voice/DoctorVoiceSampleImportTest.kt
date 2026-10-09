package com.carelipik.app.data.voice

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carelipik.app.data.audio.AndroidMicrophoneRecorder
import com.carelipik.app.domain.recording.AudioImportResult
import com.carelipik.app.domain.voice.DoctorVoiceSampleSaveResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DoctorVoiceSampleImportTest {
    @Test
    fun compatibleWaveFile_importsAndSavesPrivately() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = File(context.cacheDir, "synthetic-doctor-voice-import.wav")
        val privateDirectory = File(context.cacheDir, "synthetic-doctor-voice-store")
        source.delete()
        privateDirectory.deleteRecursively()
        InstrumentationRegistry.getInstrumentation().context.assets.open("jfk.wav").use { input ->
            source.outputStream().use(input::copyTo)
        }
        val recorder = AndroidMicrophoneRecorder(context)
        try {
            val imported = recorder.importAudio(Uri.fromFile(source).toString())
            assertTrue(imported is AudioImportResult.Success)
            val audio = (imported as AudioImportResult.Success).audio

            val result = LocalDoctorVoiceSampleStore(context, privateDirectory).save(audio)

            assertTrue(result is DoctorVoiceSampleSaveResult.Success)
            assertTrue(File(privateDirectory, "doctor-voice-sample-1.wav").isFile)

            val store = LocalDoctorVoiceSampleStore(context, privateDirectory)
            store.save(audio)
            val thirdResult = store.save(audio)
            assertTrue(thirdResult is DoctorVoiceSampleSaveResult.Success)
            assertTrue(
                (thirdResult as DoctorVoiceSampleSaveResult.Success)
                    .sample.enrolledSampleCount == 3
            )
            assertTrue(File(privateDirectory, "doctor-voice-sample-2.wav").isFile)
            assertTrue(File(privateDirectory, "doctor-voice-sample-3.wav").isFile)
        } finally {
            recorder.close()
            source.delete()
            privateDirectory.deleteRecursively()
        }
    }
}
