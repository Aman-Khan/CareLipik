package com.carelipik.app.data.extraction

import com.carelipik.app.domain.extraction.ClinicalNoteContentFormatter
import com.carelipik.app.domain.model.ClinicalNoteSection
import org.json.JSONArray
import org.json.JSONObject

/** Shared prescription schema for local and cloud note generation. */
internal object PrescriptionContextJson {
    val fields = linkedMapOf("symptoms" to "Symptoms", "advice" to "Advice",
        "precautions" to "Precautions", "investigations" to "Investigations", "follow_up" to "Follow-up")
    const val instruction = "Return prescription_context BEFORE report sections: medicine_suggestions must list every explicitly discussed medicine with separate name, strength, dose, route, frequency, duration and instructions; leave unstated attributes empty. Put concise clinical context in symptoms, advice, precautions, investigations and follow_up arrays, each item with content and source_turn_ids. Advice must exclude medicine regimens, conversation history, speaker labels, greetings and administrative actions. Separate each actionable advice item. Use only transcript facts, never infer treatment. Empty unsupported arrays."

    fun schema(medicineSchema: JSONObject): JSONObject = JSONObject().put("type", "OBJECT")
        .put("properties", JSONObject().apply {
            put("medicine_suggestions", JSONObject(medicineSchema.toString()))
            fields.keys.forEach { field ->
                put(field, JSONObject().put("type", "ARRAY").put("items", JSONObject()
                    .put("type", "OBJECT").put("properties", JSONObject()
                        .put("content", JSONObject().put("type", "STRING"))
                        .put("source_turn_ids", JSONObject().put("type", "ARRAY")
                            .put("items", JSONObject().put("type", "STRING"))))
                    .put("required", JSONArray(listOf("content", "source_turn_ids")))))
            }
        }).put("required", JSONArray(listOf("medicine_suggestions") + fields.keys))

    fun normalize(raw: JSONObject?, transcript: String): JSONObject {
        val turns = turns(transcript)
        val validIds = turns.indices.map { "T${it + 1}" }.toSet()
        fun supportedIds(item: JSONObject): List<String> {
            val ids = item.optJSONArray("source_turn_ids") ?: return emptyList()
            return (0 until ids.length()).map { ids.optString(it) }.filter { it in validIds }.distinct()
        }
        val context = JSONObject()
        fields.forEach { (field, _) ->
            val items = raw?.optJSONArray(field) ?: JSONArray()
            context.put(field, JSONArray().apply {
                for (index in 0 until minOf(items.length(), 32)) {
                    val item = items.optJSONObject(index) ?: continue
                    val ids = supportedIds(item)
                    val text = ClinicalNoteContentFormatter.clean(item.optString("content"))
                    if (ids.isNotEmpty() && text.isNotBlank()) put(JSONObject()
                        .put("content", text).put("source_turn_ids", JSONArray(ids)))
                }
            })
        }
        val medicines = raw?.optJSONArray("medicine_suggestions") ?: JSONArray()
        context.put("medicine_suggestions", JSONArray().apply {
            for (index in 0 until minOf(medicines.length(), 64)) {
                val item = medicines.optJSONObject(index) ?: continue
                val ids = supportedIds(item)
                val name = item.optString("name").trim()
                val evidence = ids.mapNotNull { id -> turns.getOrNull(id.drop(1).toInt() - 1) }.joinToString("\n")
                if (name.isNotBlank() && ids.isNotEmpty() &&
                    Regex("(?<![\\p{L}\\p{N}])${Regex.escape(name)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
                        .containsMatchIn(evidence)) {
                    put(JSONObject(item.toString()).put("source_turn_ids", JSONArray(ids))
                        .put("source_evidence", evidence))
                }
            }
        })
        return context
    }

    fun sections(context: JSONObject): List<ClinicalNoteSection> = fields.flatMap { (field, title) ->
        val items = context.optJSONArray(field) ?: JSONArray()
        (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val ids = item.optJSONArray("source_turn_ids") ?: JSONArray()
            ClinicalNoteSection(field, title, item.optString("content"),
                (0 until ids.length()).map { ids.optString(it) })
        }
    }.take(32)

    fun turns(transcript: String): List<String> {
        val explicit = Regex("(?m)(?=^(?:Doctor|Patient|Other|Noise|Speaker\\s+[^:]+):)")
            .split(transcript.trim()).map(String::trim).filter(String::isNotBlank)
        return if (explicit.size > 1) explicit else transcript.lineSequence().map(String::trim)
            .filter(String::isNotBlank).toList()
    }
}
