package com.carelipik.app.data.prescription

import com.carelipik.app.data.extraction.DirectGeminiClient
import com.carelipik.app.domain.prescription.LetterheadConversionEngine
import com.carelipik.app.domain.prescription.LetterheadMedia
import com.carelipik.app.domain.prescription.PrescriptionTemplates
import org.json.JSONObject

class GeminiLetterheadConversionEngine internal constructor(
    private val generate: (String, JSONObject, ByteArray, String) -> JSONObject
) : LetterheadConversionEngine {
    constructor(apiKey: () -> String?) : this(generate = DirectGeminiClient(apiKey).let { client ->
        { prompt: String, schema: JSONObject, bytes: ByteArray, mimeType: String -> client.generateJson(prompt, schema, bytes, mimeType) }
    })

    override fun convert(pdf: ByteArray): String {
        val mimeType = LetterheadMedia.mimeType(pdf)
        val result = generate(LetterheadPromptBuilder.prompt(), LetterheadPromptBuilder.schema(), pdf, mimeType)
        return PrescriptionTemplates.validateReusableLetterhead(result.getString("latex"))
    }
}
