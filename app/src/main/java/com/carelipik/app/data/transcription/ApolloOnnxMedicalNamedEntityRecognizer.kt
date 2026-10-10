package com.carelipik.app.data.transcription

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.carelipik.app.domain.transcription.MedicalEntity
import com.carelipik.app.domain.transcription.MedicalNamedEntityRecognizer
import java.io.File
import java.nio.LongBuffer
import org.json.JSONObject

/**
 * Runs the optional Apollo Medical-NER ONNX bundle entirely in app-private storage.
 *
 * The bundle is deliberately an explicit developer-installed asset: model weights and tokenizer
 * are not checked into Git or packaged into the APK. If it is absent or incompatible, callers
 * receive [ApolloModelUnavailableException] and retain the deterministic offline review rules.
 */
class ApolloOnnxMedicalNamedEntityRecognizer(context: Context) : MedicalNamedEntityRecognizer, AutoCloseable {
    private val modelDirectory = File(context.applicationContext.filesDir, MODEL_DIRECTORY)
    private val modelFile = File(modelDirectory, MODEL_FILE)
    private val tokenizerFile = File(modelDirectory, TOKENIZER_FILE)
    private val configFile = File(modelDirectory, CONFIG_FILE)
    private val environment by lazy { OrtEnvironment.getEnvironment() }
    private val tokenizer by lazy { ApolloSentencePieceTokenizer(tokenizerFile.readText()) }
    private val labels by lazy { readLabels(configFile.readText()) }
    private val decoder by lazy { ApolloNerDecoder(labels) }
    private val session = lazy {
        requireAvailable()
        OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(4)
            environment.createSession(modelFile.absolutePath, options)
        }
    }

    override fun recognize(text: String): List<MedicalEntity> {
        if (text.isBlank()) return emptyList()
        requireAvailable()
        val encoded = tokenizer.encode(text, MAX_SEQUENCE_LENGTH)
        val shape = longArrayOf(1, encoded.ids.size.toLong())
        val tensors = mutableMapOf<String, OnnxTensor>()
        try {
            session.value.inputNames.forEach { name ->
                val values = when (name) {
                    "input_ids" -> encoded.ids
                    "attention_mask" -> LongArray(encoded.ids.size) { 1L }
                    "token_type_ids" -> LongArray(encoded.ids.size)
                    else -> throw ApolloModelUnavailableException("Apollo model uses unsupported input: $name")
                }
                tensors[name] = OnnxTensor.createTensor(environment, LongBuffer.wrap(values), shape)
            }
            session.value.run(tensors).use { results ->
                val output = results.firstOrNull()?.value as? Array<*>
                    ?: throw ApolloModelUnavailableException("Apollo model returned no token logits.")
                val batch = output.firstOrNull() as? Array<*>
                    ?: throw ApolloModelUnavailableException("Apollo model returned malformed logits.")
                val logits = batch.mapNotNull { row ->
                    when (row) {
                        is FloatArray -> row
                        is Array<*> -> row.filterIsInstance<Float>().toFloatArray()
                        else -> null
                    }
                }.toTypedArray()
                if (logits.size < encoded.ids.size) {
                    throw ApolloModelUnavailableException("Apollo model returned incomplete token logits.")
                }
                return decoder.decode(text, encoded.pieces, logits)
            }
        } catch (error: ApolloModelUnavailableException) {
            throw error
        } catch (error: Exception) {
            throw ApolloModelUnavailableException("Apollo Medical-NER could not run on this device.", error)
        } finally {
            tensors.values.forEach(OnnxTensor::close)
        }
    }

    fun isAvailable(): Boolean = modelFile.isFile && tokenizerFile.isFile && configFile.isFile

    override fun close() {
        if (session.isInitialized()) session.value.close()
    }

    private fun requireAvailable() {
        if (!isAvailable()) {
            throw ApolloModelUnavailableException(
                "Apollo Medical-NER is not installed. Install the approved offline model bundle first."
            )
        }
    }

    private fun readLabels(config: String): List<String> {
        val idToLabel = JSONObject(config).optJSONObject("id2label")
            ?: throw ApolloModelUnavailableException("Apollo model config has no id2label mapping.")
        return (0 until idToLabel.length()).map { index ->
            idToLabel.optString(index.toString()).ifBlank { "O" }
        }
    }

    companion object {
        const val MODEL_DIRECTORY = "models/apollo-medical-ner"
        const val MODEL_FILE = "model.int8.onnx"
        const val TOKENIZER_FILE = "tokenizer.json"
        const val CONFIG_FILE = "config.json"
        const val MAX_SEQUENCE_LENGTH = 256
    }
}

class ApolloModelUnavailableException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
