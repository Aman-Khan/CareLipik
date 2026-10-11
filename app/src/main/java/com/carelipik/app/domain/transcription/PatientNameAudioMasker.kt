package com.carelipik.app.domain.transcription

data class AudioMaskWindow(val startSeconds: Double, val endSeconds: Double) {
    init {
        require(startSeconds.isFinite() && endSeconds.isFinite())
        require(startSeconds >= 0 && endSeconds > startSeconds)
    }
}

sealed interface PatientNameMaskingResult {
    data class Success(val audioPath: String, val windows: List<AudioMaskWindow>) : PatientNameMaskingResult
    data class Failure(val message: String) : PatientNameMaskingResult
}

/** Must return a separate audio file. A failure must never fall back to uploading the original. */
fun interface PatientNameAudioMasker {
    fun mask(audioPath: String, patientName: String, language: TranscriptionLanguage): PatientNameMaskingResult
}

fun interface PatientNameAudioLocator {
    fun locate(samples: FloatArray, patientName: String, language: TranscriptionLanguage): List<AudioMaskWindow>

    /** Share native model weights across original and scrubbed scans, releasing them on every exit. */
    fun <T> withSession(block: (PatientNameAudioLocator) -> T): T = block(this)
}

/** Whole-word matches also cover a first name or surname spoken without the full name. */
object PatientNameMatcher {
    private val words = Regex("[\\p{L}\\p{M}]+")

    fun containsName(text: String, patientName: String): Boolean {
        val names = words.findAll(normalize(patientName)).map { it.value }.filter { it.length >= 2 }.toSet()
        require(names.isNotEmpty()) { "Enter the patient name before using masked Sarvam." }
        return words.findAll(normalize(text)).any { it.value in names }
    }

    private fun normalize(value: String): String = java.text.Normalizer
        .normalize(value, java.text.Normalizer.Form.NFKC).lowercase(java.util.Locale.ROOT)
}

object AudioWaveformScrubber {
    fun scrub(samples: FloatArray, sampleRate: Int, windows: List<AudioMaskWindow>, noise: () -> Float): FloatArray {
        require(sampleRate > 0 && samples.isNotEmpty() && windows.isNotEmpty())
        val result = samples.copyOf()
        windows.forEach { window ->
            require(window.endSeconds <= samples.size.toDouble() / sampleRate + 0.001)
            val start = kotlin.math.floor(window.startSeconds * sampleRate).toInt().coerceAtLeast(0)
            val end = kotlin.math.ceil(window.endSeconds * sampleRate).toInt().coerceAtMost(result.size)
            require(end > start)
            for (index in start until end) result[index] = noise().coerceIn(-0.04f, 0.04f)
        }
        return result
    }
}
