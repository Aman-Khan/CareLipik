package com.carelipik.app.domain.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FixedCountVoiceClustererTest {
    @Test
    fun threeVoices_keepThreeGroupsAndReturningVoiceKeepsIdentity() {
        val labels = FixedCountVoiceClusterer.cluster(listOf(
            floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 1f, 0f),
            floatArrayOf(0f, 0f, 1f), floatArrayOf(0.99f, 0.01f, 0f),
            floatArrayOf(0.01f, 0.99f, 0f), floatArrayOf(0f, 0.01f, 0.99f)
        ), 3)
        assertEquals(3, labels.distinct().size)
        assertEquals(labels[0], labels[3])
        assertEquals(labels[1], labels[4])
        assertEquals(labels[2], labels[5])
        assertNotEquals(labels[0], labels[1])
        assertNotEquals(labels[1], labels[2])
    }

    @Test
    fun similarEmbeddings_stillHonorsRequestedClusterCount() {
        assertEquals(3, FixedCountVoiceClusterer.cluster(
            List(6) { floatArrayOf(1f, 0.01f * it) }, 3
        ).distinct().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun tooLittleEvidence_doesNotInventMissingSpeaker() {
        FixedCountVoiceClusterer.cluster(listOf(floatArrayOf(1f)), 3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidEvidence_isRejected() {
        FixedCountVoiceClusterer.cluster(listOf(floatArrayOf(Float.NaN)), 1)
    }
}
