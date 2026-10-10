package com.carelipik.app.domain.transcription

import kotlin.math.sqrt

/** Complete-link agglomerative clustering cut at exactly the requested number of groups. */
object FixedCountVoiceClusterer {
    fun cluster(embeddings: List<FloatArray>, speakerCount: Int): List<Int> {
        SpeakerCount.validate(speakerCount)
        require(embeddings.size >= speakerCount) { "Not enough speech samples for $speakerCount speakers." }
        val dimension = embeddings.first().size
        require(dimension > 0 && embeddings.all { it.size == dimension && it.all(Float::isFinite) }) {
            "Invalid voice embeddings."
        }
        val normalized = embeddings.map { embedding ->
            val norm = sqrt(embedding.sumOf { it.toDouble() * it })
            require(norm > 0) { "Empty voice embedding." }
            DoubleArray(dimension) { embedding[it] / norm }
        }
        val distances = Array(embeddings.size) { DoubleArray(embeddings.size) }
        for (i in normalized.indices) {
            for (j in i + 1 until normalized.size) {
                val distance = 1.0 - normalized[i].indices.sumOf { normalized[i][it] * normalized[j][it] }
                distances[i][j] = distance
                distances[j][i] = distance
            }
        }
        val groups = embeddings.indices.associateWith { mutableListOf(it) }.toMutableMap()
        while (groups.size > speakerCount) {
            val ids = groups.keys.toList()
            var bestFirst = ids[0]
            var bestSecond = ids[1]
            var bestDistance = Double.POSITIVE_INFINITY
            for (i in ids.indices) {
                for (j in i + 1 until ids.size) {
                    val distance = distances[ids[i]][ids[j]]
                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestFirst = ids[i]
                        bestSecond = ids[j]
                    }
                }
            }
            groups.getValue(bestFirst).addAll(groups.remove(bestSecond)!!)
            groups.keys.forEach { other ->
                if (other != bestFirst) {
                    val distance = maxOf(distances[bestFirst][other], distances[bestSecond][other])
                    distances[bestFirst][other] = distance
                    distances[other][bestFirst] = distance
                }
            }
        }
        val assignments = MutableList(embeddings.size) { 0 }
        groups.values.sortedBy { it.min() }.forEachIndexed { label, members ->
            members.forEach { assignments[it] = label }
        }
        return assignments
    }
}
