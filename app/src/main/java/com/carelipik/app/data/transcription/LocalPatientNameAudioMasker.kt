package com.carelipik.app.data.transcription

import android.content.Context
import com.carelipik.app.domain.transcription.AudioMaskWindow
import com.carelipik.app.domain.transcription.AudioWaveformScrubber
import com.carelipik.app.domain.transcription.PatientNameAudioLocator
import com.carelipik.app.domain.transcription.PatientNameAudioMasker
import com.carelipik.app.domain.transcription.PatientNameMaskingResult
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import java.io.File
import java.io.BufferedOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom

class LocalPatientNameAudioMasker(
    private val outputDirectory: File,
    private val locator: PatientNameAudioLocator
) : PatientNameAudioMasker {
    constructor(context: Context) : this(
        File(context.cacheDir, "masked_sarvam_audio"), EnglishKeywordPatientNameLocator(context)
    )

    override fun mask(audioPath: String, patientName: String, language: TranscriptionLanguage): PatientNameMaskingResult {
        var output: File? = null
        return try {
            require(language == TranscriptionLanguage.English) { "Patient-name masking supports English only." }
            locator.withSession { scanner ->
                val samples = PcmWaveAudio.readMono16Khz(File(audioPath))
                val windows = scanner.locate(samples, patientName, language)
                if (windows.isEmpty()) return@withSession PatientNameMaskingResult.Failure(
                    "The patient name could not be confirmed in the recording. Masked Sarvam stopped; no audio was uploaded. Check the entered name and recording."
                )
                val random = SecureRandom()
                val scrubbed = AudioWaveformScrubber.scrub(samples, PcmWaveAudio.sampleRate, windows) {
                    (random.nextFloat() * 2 - 1) * 0.025f
                }
                check(scanner.locate(scrubbed, patientName, language).isEmpty()) {
                    "The patient name was still detected after masking."
                }
                check(outputDirectory.isDirectory || outputDirectory.mkdirs())
                output = File.createTempFile("masked-", ".wav", outputDirectory)
                writeWave(output, scrubbed)
                PatientNameMaskingResult.Success(output.absolutePath, windows)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            output?.delete()
            throw cancelled
        } catch (_: Exception) {
            output?.delete()
            PatientNameMaskingResult.Failure(
                "Local patient-name masking is unavailable or uncertain. No audio was uploaded. Check that the name-masking model is installed."
            )
        }
    }

    private fun writeWave(file: File, samples: FloatArray) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + samples.size * 2).put("WAVEfmt ".toByteArray())
        header.putInt(16).putShort(1).putShort(1)
        header.putInt(PcmWaveAudio.sampleRate).putInt(PcmWaveAudio.sampleRate * 2).putShort(2).putShort(16)
        header.put("data".toByteArray()).putInt(samples.size * 2)
        BufferedOutputStream(file.outputStream()).use { output ->
            output.write(header.array())
            samples.forEach {
                val value = (it.coerceIn(-1f, 1f) * 32767).toInt()
                output.write(value and 255)
                output.write(value ushr 8 and 255)
            }
        }
    }
}
