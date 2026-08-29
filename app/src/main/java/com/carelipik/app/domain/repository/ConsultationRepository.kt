package com.carelipik.app.domain.repository

/** Boundary for local consultation data; a real store can replace this later. */
interface ConsultationRepository {
    fun patientDisplayName(): String
}
