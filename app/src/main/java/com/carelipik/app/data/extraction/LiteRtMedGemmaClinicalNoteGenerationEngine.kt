package com.carelipik.app.data.extraction

import android.content.Context
import android.util.Log
import com.carelipik.app.domain.extraction.ClinicalNoteGenerationEngine
import com.carelipik.app.domain.extraction.ClinicalNoteGenerationRequest
import com.carelipik.app.domain.extraction.ClinicalNoteGenerationResult
import com.carelipik.app.domain.model.ClinicalNoteGenerationSource
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File
import kotlinx.coroutines.runBlocking

class LiteRtMedGemmaClinicalNoteGenerationEngine(
    context: Context,
    private val modelFile: () -> File = {
        File(context.filesDir, "models/$MODEL_FILE_NAME")
    }
) : ClinicalNoteGenerationEngine {
    private val applicationContext = context.applicationContext
    private val parser = HttpGeminiClinicalNoteGenerationEngine("https://unused.invalid")

    override fun generate(request: ClinicalNoteGenerationRequest): ClinicalNoteGenerationResult {
        if (request.reviewedTranscript.isBlank()) {
            return ClinicalNoteGenerationResult.Failure("A reviewed transcript is required.")
        }
        val preferredModel = File(applicationContext.filesDir, "models/$GEMMA4_MODEL_FILE_NAME")
        val model = if (preferredModel.isFile) preferredModel else modelFile()
        val modelProfile = if (model.name == GEMMA4_MODEL_FILE_NAME) GEMMA4_PROFILE else MEDGEMMA_PROFILE
        if (!model.isFile || model.length() < MINIMUM_MODEL_BYTES) {
            return ClinicalNoteGenerationResult.Failure(
                "An on-device note model is not installed. Install the approved LiteRT-LM " +
                    "model bundle before using on-device report generation."
            )
        }
        return runCatching {
            val response = runBlocking { generateResponse(model, modelProfile, request) }
            Log.i(
                TAG,
                "Generation completed: chars=${response.length}, " +
                    "hasJsonStart=${response.contains('{')}, hasJsonEnd=${response.contains('}')}"
            )
            val normalized = MedGemmaPromptBuilder.normalizeJson(response, request)
            ClinicalNoteGenerationResult.Success(
                parser.parseDraftResponse(
                    body = normalized,
                    request = request,
                    generationSource = modelProfile.generationSource
                )
            )
        }.getOrElse { error ->
            // Never log model output or transcript content: both can contain patient information.
            Log.e(TAG, "On-device generation failed: ${error.javaClass.simpleName}")
            ClinicalNoteGenerationResult.Failure(
                when {
                    error.message.orEmpty().contains("Failed to create engine") ->
                        "The on-device model could not start. Close other large apps and try again."
                    else -> "On-device report generation failed. Try again or keep the offline draft."
                }
            )
        }
    }

    private suspend fun generateResponse(
        model: File,
        profile: ModelProfile,
        request: ClinicalNoteGenerationRequest
    ): String {
        val config = EngineConfig(
            modelPath = model.absolutePath,
            // The published Q4 text bundle contains operations that cannot be fully delegated
            // to the Android GPU. LiteRT-LM requires full delegation, so CPU is the compatible
            // backend for this bundle. Do not silently fall back after native initialization.
            backend = profile.backend,
            maxNumTokens = profile.maxNumTokens,
            cacheDir = File(applicationContext.cacheDir, profile.cacheDirectory).apply { mkdirs() }.path
        )
        return Engine(config).use { engine ->
            engine.initialize()
            val conversationConfig = ConversationConfig(
                samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0)
            )
            engine.createConversation(conversationConfig).use { conversation ->
                conversation.sendMessage(MedGemmaPromptBuilder.build(request))
                    .contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString(separator = "") { it.text }
            }
        }
    }

    companion object {
        const val MODEL_FILE_NAME = "medgemma-1.5-4b-it-int4.litertlm"
        const val GEMMA4_MODEL_FILE_NAME = "gemma-4-e2b-it-gpu.litertlm"
        private const val TAG = "CareLipikMedGemma"
        private const val MINIMUM_MODEL_BYTES = 100L * 1024L * 1024L
        private val MEDGEMMA_PROFILE = ModelProfile(
            backend = Backend.CPU(),
            maxNumTokens = 2_048,
            cacheDirectory = "medgemma",
            generationSource = ClinicalNoteGenerationSource.MedGemma
        )
        private val GEMMA4_PROFILE = ModelProfile(
            backend = Backend.GPU(),
            maxNumTokens = 8_192,
            cacheDirectory = "gemma4",
            generationSource = ClinicalNoteGenerationSource.Gemma4
        )
    }

    private data class ModelProfile(
        val backend: Backend,
        val maxNumTokens: Int,
        val cacheDirectory: String,
        val generationSource: ClinicalNoteGenerationSource
    )
}
