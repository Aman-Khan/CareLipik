package com.carelipik.app.domain.export

import com.carelipik.app.domain.model.ApprovedConsultation
import java.time.Instant

object ConsultationExportContent {
    fun structuredJson(consultation: ApprovedConsultation): String = """
        {
          "schema": "https://carelipik.app/schemas/approved-consultation/v1",
          "schemaVersion": 1,
          "exportType": "doctor-approved-clinical-note",
          "consultationId": ${consultation.id.json()},
          "approvedAt": ${approvedAt(consultation).json()},
          "patient": {
            "reference": ${consultation.patientName.json()},
            "age": ${consultation.patientAge.json()},
            "visitReason": ${consultation.visitReason.json()}
          },
          "clinicalNote": {
            "presentingComplaint": ${consultation.draft.presentingComplaint.json()},
            "history": ${consultation.draft.history.json()},
            "keyFindings": ${consultation.draft.keyFindings.json()},
            "assessmentNotes": ${consultation.draft.assessmentNotes.json()},
            "planNotes": ${consultation.draft.planNotes.json()},
            "reviewedTranscript": ${consultation.draft.reviewedTranscript.json()}
          },
          "doctorApproved": true,
          "includesConsultationAudio": false
        }
    """.trimIndent() + "\n"

    fun plainText(consultation: ApprovedConsultation): String = """
        CARELIPIK - DOCTOR-APPROVED CLINICAL NOTE

        Consultation ID: ${consultation.id}
        Approved at: ${approvedAt(consultation)}
        Patient / reference: ${consultation.patientName}
        Age: ${consultation.patientAge.ifBlank { "Not recorded" }}
        Visit reason: ${consultation.visitReason.ifBlank { "Not recorded" }}

        PRESENTING COMPLAINT
        ${consultation.draft.presentingComplaint.ifBlank { "Not documented" }}

        HISTORY
        ${consultation.draft.history.ifBlank { "Not documented" }}

        KEY FINDINGS
        ${consultation.draft.keyFindings.ifBlank { "Not documented" }}

        ASSESSMENT NOTES
        ${consultation.draft.assessmentNotes.ifBlank { "Not documented" }}

        PLAN NOTES
        ${consultation.draft.planNotes.ifBlank { "Not documented" }}

        COMPLETE REVIEWED TRANSCRIPT
        ${consultation.draft.reviewedTranscript.ifBlank { "Not available for this older record" }}

        CareLipik assists with documentation. Clinical accuracy remains the doctor's responsibility.
        Consultation audio is not included.
    """.trimIndent() + "\n"

    fun fhirR4Bundle(consultation: ApprovedConsultation): String {
        val bundleId = "bundle-${consultation.id}"
        val compositionId = "composition-${consultation.id}"
        val patientId = "patient-${consultation.id}"
        val deviceId = "carelipik-author"
        val documentReferenceId = "document-reference-${consultation.id}"
        val approvedAt = approvedAt(consultation)
        val sections = listOf(
            "Patient details" to "Age: ${consultation.patientAge.ifBlank { "Not recorded" }}. " +
                "Visit reason: ${consultation.visitReason.ifBlank { "Not recorded" }}.",
            "Presenting complaint" to consultation.draft.presentingComplaint,
            "History" to consultation.draft.history,
            "Key findings" to consultation.draft.keyFindings,
            "Assessment notes" to consultation.draft.assessmentNotes,
            "Plan notes" to consultation.draft.planNotes,
            "Complete reviewed transcript" to consultation.draft.reviewedTranscript
        ).joinToString(",\n") { (title, text) ->
            """
                {
                  "title": ${title.json()},
                  "text": {
                    "status": "generated",
                    "div": ${"<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>${text.ifBlank { "Not documented" }.xml()}</p></div>".json()}
                  }
                }
            """.trimIndent().prependIndent("          ")
        }
        return """
            {
              "resourceType": "Bundle",
              "id": ${bundleId.json()},
              "identifier": {
                "system": "urn:carelipik:consultation",
                "value": ${consultation.id.json()}
              },
              "type": "document",
              "timestamp": ${approvedAt.json()},
              "entry": [
                {
                  "fullUrl": ${"urn:uuid:$compositionId".json()},
                  "resource": {
                    "resourceType": "Composition",
                    "id": ${compositionId.json()},
                    "status": "final",
                    "type": {
                      "coding": [{
                        "system": "http://loinc.org",
                        "code": "11488-4",
                        "display": "Consult note"
                      }],
                      "text": "Doctor-approved clinical note"
                    },
                    "subject": { "reference": ${"urn:uuid:$patientId".json()} },
                    "date": ${approvedAt.json()},
                    "author": [{ "reference": ${"urn:uuid:$deviceId".json()} }],
                    "title": "Doctor-approved clinical note",
                    "confidentiality": "N",
                    "attester": [{
                      "mode": "professional",
                      "time": ${approvedAt.json()}
                    }],
                    "section": [
            $sections
                    ]
                  }
                },
                {
                  "fullUrl": ${"urn:uuid:$patientId".json()},
                  "resource": {
                    "resourceType": "Patient",
                    "id": ${patientId.json()},
                    "identifier": [{
                      "system": "urn:carelipik:patient-reference",
                      "value": ${consultation.patientName.json()}
                    }],
                    "name": [{ "text": ${consultation.patientName.json()} }]
                  }
                },
                {
                  "fullUrl": ${"urn:uuid:$deviceId".json()},
                  "resource": {
                    "resourceType": "Device",
                    "id": ${deviceId.json()},
                    "status": "active",
                    "deviceName": [{
                      "name": "CareLipik clinical documentation assistant",
                      "type": "manufacturer-name"
                    }]
                  }
                },
                {
                  "fullUrl": ${"urn:uuid:$documentReferenceId".json()},
                  "resource": {
                    "resourceType": "DocumentReference",
                    "id": ${documentReferenceId.json()},
                    "masterIdentifier": {
                      "system": "urn:carelipik:consultation",
                      "value": ${consultation.id.json()}
                    },
                    "status": "current",
                    "type": {
                      "coding": [{
                        "system": "http://loinc.org",
                        "code": "11488-4",
                        "display": "Consult note"
                      }]
                    },
                    "subject": { "reference": ${"urn:uuid:$patientId".json()} },
                    "date": ${approvedAt.json()},
                    "author": [{ "reference": ${"urn:uuid:$deviceId".json()} }],
                    "content": [{
                      "attachment": {
                        "contentType": "application/fhir+json",
                        "url": ${"urn:carelipik:consultation:${consultation.id}".json()},
                        "title": "Doctor-approved clinical note"
                      }
                    }]
                  }
                }
              ]
            }
        """.trimIndent() + "\n"
    }

    private fun approvedAt(consultation: ApprovedConsultation): String =
        Instant.ofEpochMilli(consultation.approvedAtMillis).toString()

    private fun String.json(): String = buildString {
        append('"')
        this@json.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u%04x".format(character.code))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private fun String.xml(): String = replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
