package com.carelipik.app.data.transcription

import android.content.Context
import com.carelipik.app.domain.transcription.AudioMaskWindow
import com.carelipik.app.domain.transcription.EnglishNameKeywords
import com.carelipik.app.domain.transcription.PatientNameAudioLocator
import com.carelipik.app.domain.transcription.TranscriptionLanguage
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

/** Small English KWS model; conservative windows avoid relying on reset-relative token timestamps. */
class EnglishKeywordPatientNameLocator(context: Context) : PatientNameAudioLocator {
    private val assets = context.applicationContext.assets

    override fun locate(samples: FloatArray, patientName: String, language: TranscriptionLanguage): List<AudioMaskWindow> =
        withSession { it.locate(samples, patientName, language) }

    override fun <T> withSession(block: (PatientNameAudioLocator) -> T): T {
        val scores = assets.open("$MODEL_DIRECTORY/bpe-scores.tsv").bufferedReader().useLines { lines ->
            lines.associate { line ->
                val fields = line.split('\t')
                fields[0] to fields[1].toFloat()
            }
        }
        val tokenizer = EnglishNameKeywords(scores)
        val spotter = KeywordSpotter(assets, KeywordSpotterConfig(
            featConfig = FeatureConfig(sampleRate = PcmWaveAudio.sampleRate, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$MODEL_DIRECTORY/encoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                    decoder = "$MODEL_DIRECTORY/decoder-epoch-12-avg-2-chunk-16-left-64.int8.onnx",
                    joiner = "$MODEL_DIRECTORY/joiner-epoch-12-avg-2-chunk-16-left-64.int8.onnx"
                ),
                tokens = "$MODEL_DIRECTORY/tokens.txt", modelType = "zipformer2",
                numThreads = 2, provider = "cpu"
            ),
            keywordsFile = "$MODEL_DIRECTORY/empty-keywords.txt",
            keywordsScore = 1.0f, keywordsThreshold = 0.25f
        ))
        return try {
            block(PatientNameAudioLocator { samples, name, language ->
                require(language == TranscriptionLanguage.English) { "Name masking supports English only." }
                require(samples.isNotEmpty())
                val keywords = tokenizer.encode(name)
                val windows = mutableListOf<AudioMaskWindow>()
                val rate = PcmWaveAudio.sampleRate
                var start = 0
                while (start < samples.size) {
                    if (Thread.currentThread().isInterrupted) throw kotlinx.coroutines.CancellationException()
                    val end = (start + 5 * rate).coerceAtMost(samples.size)
                    val stream = spotter.createStream(keywords)
                    try {
                        stream.acceptWaveform(samples.copyOfRange(start, end), rate)
                        stream.acceptWaveform(FloatArray(rate), rate)
                        stream.inputFinished()
                        while (spotter.isReady(stream)) {
                            spotter.decode(stream)
                            val result = spotter.getResult(stream)
                            if (result.keyword.isNotEmpty()) {
                                check(result.keyword.startsWith("name") && result.timestamps.isNotEmpty())
                                windows += AudioMaskWindow(start.toDouble() / rate, end.toDouble() / rate)
                                break
                            }
                        }
                    } finally {
                        stream.release()
                    }
                    if (end == samples.size) break
                    start += 3 * rate
                }
                merge(windows)
            })
        } finally {
            spotter.release()
        }
    }

    private fun merge(windows: List<AudioMaskWindow>): List<AudioMaskWindow> {
        val merged = mutableListOf<AudioMaskWindow>()
        windows.forEach { window ->
            val previous = merged.lastOrNull()
            if (previous != null && window.startSeconds <= previous.endSeconds) {
                merged[merged.lastIndex] = AudioMaskWindow(previous.startSeconds, maxOf(previous.endSeconds, window.endSeconds))
            } else merged += window
        }
        return merged
    }

    companion object {
        const val MODEL_DIRECTORY = "models/english-name-kws"
    }
}
