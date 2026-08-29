package com.carelipik.app.ui.screens.doctorprofile

enum class DoctorVoiceEnrollmentStatus {
    NotEnrolled,
    Recording,
    Saving,
    Enrolled,
    Error
}

data class DoctorVoiceEnrollmentUiState(
    val status: DoctorVoiceEnrollmentStatus = DoctorVoiceEnrollmentStatus.NotEnrolled,
    val amplitude: Float = 0f,
    val elapsedSeconds: Int = 0,
    val sampleDurationMillis: Long = 0,
    val hasExistingSample: Boolean = false,
    val message: String? = null
) {
    val formattedElapsed: String
        get() = "00:%02d".format(elapsedSeconds.coerceAtMost(59))

    val formattedSampleDuration: String
        get() = "%.0f sec".format(sampleDurationMillis / 1_000f)
}
