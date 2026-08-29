package com.carelipik.app.data.voice

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.carelipik.app.data.transcription.PcmWaveAudio
import com.carelipik.app.domain.transcription.DiarizedAudioTurn
import com.carelipik.app.domain.voice.DoctorVoiceRoleMatchResult
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class SherpaDoctorVoiceRoleMatcherTest {
    @Test
    fun bundledModel_comparesEnrollmentAndDetectedTurnsFullyOffline() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val enrollmentFile = File(context.cacheDir, "voice-match-enrollment.wav")
        InstrumentationRegistry.getInstrumentation().context.assets.open("jfk.wav").use { input ->
            enrollmentFile.outputStream().use(input::copyTo)
        }
        try {
            val samples = PcmWaveAudio.readMono16Khz(enrollmentFile)
            val durationSeconds = samples.size / PcmWaveAudio.sampleRate.toFloat()
            val midpoint = durationSeconds / 2f

            val result = SherpaDoctorVoiceRoleMatcher(context, enrollmentFile).match(
                samples = samples,
                sampleRate = PcmWaveAudio.sampleRate,
                turns = listOf(
                    DiarizedAudioTurn("speaker-1", 0f, midpoint),
                    DiarizedAudioTurn("speaker-2", midpoint, durationSeconds)
                )
            )

            assertFalse(result is DoctorVoiceRoleMatchResult.Unavailable)
            assertFalse(result is DoctorVoiceRoleMatchResult.NotEnrolled)
        } finally {
            enrollmentFile.delete()
        }
    }
}
