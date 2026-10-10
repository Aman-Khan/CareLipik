package com.carelipik.app.domain.transcription

object SpeakerCount {
    const val DEFAULT = 2
    val supported = 1..10

    fun validate(count: Int): Int {
        require(count in supported) { "Choose between 1 and 10 speakers." }
        return count
    }
}
