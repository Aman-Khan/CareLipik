package com.carelipik.app.data.transcription

import com.carelipik.app.domain.transcription.MedicalEntity
import com.carelipik.app.domain.transcription.MedicalEntityType
import kotlin.math.exp

/** Decodes BIO token-classification logits without altering the source transcript. */
internal class ApolloNerDecoder(
    private val labels: List<String>,
    private val sourceName: String = "apollo-medical-ner"
) {
    fun decode(
        transcript: String,
        pieces: List<ApolloTokenPiece>,
        logits: Array<FloatArray>
    ): List<MedicalEntity> {
        val tags = pieces.mapIndexedNotNull { index, piece ->
            val vector = logits.getOrNull(index + 1) ?: return@mapIndexedNotNull null // skip [CLS]
            val labelIndex = vector.indices.maxByOrNull { vector[it] } ?: return@mapIndexedNotNull null
            val label = labels.getOrNull(labelIndex).orEmpty()
            val parsed = parseLabel(label) ?: return@mapIndexedNotNull null
            TaggedPiece(piece, parsed.prefix, parsed.type, confidence(vector, labelIndex))
        }
        val entities = mutableListOf<WorkingEntity>()
        for (tag in tags) {
            val previous = entities.lastOrNull()
            val canContinue = tag.prefix == "I" && previous != null &&
                previous.type == tag.type && tag.piece.startIndex <= previous.endIndexExclusive + 1
            if (canContinue) {
                previous.endIndexExclusive = maxOf(previous.endIndexExclusive, tag.piece.endIndexExclusive)
                previous.confidences += tag.confidence
            } else {
                entities += WorkingEntity(tag.type, tag.piece.startIndex, tag.piece.endIndexExclusive, mutableListOf(tag.confidence))
            }
        }
        return entities.mapNotNull { entity ->
            if (entity.startIndex !in transcript.indices || entity.endIndexExclusive !in 1..transcript.length ||
                entity.startIndex >= entity.endIndexExclusive
            ) {
                null
            } else {
                MedicalEntity(
                    text = transcript.substring(entity.startIndex, entity.endIndexExclusive),
                    startIndex = entity.startIndex,
                    endIndexExclusive = entity.endIndexExclusive,
                    type = entity.type,
                    confidence = entity.confidences.average().toFloat().coerceIn(0f, 1f),
                    source = sourceName
                )
            }
        }
    }

    private fun parseLabel(raw: String): ParsedLabel? {
        val label = raw.uppercase().replace('-', '_')
        if (label == "O" || label.isBlank()) return null
        val prefix = if (label.startsWith("B_")) "B" else "I"
        val category = label.removePrefix("B_").removePrefix("I_")
        val type = when {
            category.contains("DRUG") || category.contains("MEDICATION") ||
                category.contains("TREATMENT") || category.contains("CHEMICAL") -> MedicalEntityType.Medication
            category.contains("DISEASE") || category.contains("DISORDER") || category.contains("CONDITION") ||
                category.contains("DIAGNOSIS") -> MedicalEntityType.Disease
            category.contains("SYMPTOM") || category.contains("SIGN") -> MedicalEntityType.Symptom
            category.contains("ALLERG") -> MedicalEntityType.Allergy
            category.contains("TEST") || category.contains("INVESTIGATION") || category.contains("PROCEDURE") ->
                MedicalEntityType.Investigation
            else -> MedicalEntityType.Other
        }
        return ParsedLabel(prefix, type)
    }

    private fun confidence(vector: FloatArray, winner: Int): Float {
        val maximum = vector.maxOrNull()?.toDouble() ?: return 0f
        val denominator = vector.sumOf { exp((it - maximum).toDouble()) }
        return (exp((vector[winner] - maximum).toDouble()) / denominator).toFloat()
    }

    private data class ParsedLabel(val prefix: String, val type: MedicalEntityType)
    private data class TaggedPiece(
        val piece: ApolloTokenPiece,
        val prefix: String,
        val type: MedicalEntityType,
        val confidence: Float
    )
    private data class WorkingEntity(
        val type: MedicalEntityType,
        val startIndex: Int,
        var endIndexExclusive: Int,
        val confidences: MutableList<Float>
    )
}
