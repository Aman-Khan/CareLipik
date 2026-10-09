package com.carelipik.app.data.extraction

import com.carelipik.app.domain.extraction.ClinicalNoteGenerationEngine
import com.carelipik.app.domain.extraction.ClinicalNoteGenerationRequest
import com.carelipik.app.domain.extraction.ClinicalNoteGenerationResult

class PreferDeviceKeyClinicalNoteGenerationEngine(
    private val hasDeviceKey: () -> Boolean,
    private val direct: ClinicalNoteGenerationEngine,
    private val fallback: ClinicalNoteGenerationEngine
) : ClinicalNoteGenerationEngine {
    override fun generate(request: ClinicalNoteGenerationRequest): ClinicalNoteGenerationResult =
        if (hasDeviceKey()) direct.generate(request) else fallback.generate(request)
}
