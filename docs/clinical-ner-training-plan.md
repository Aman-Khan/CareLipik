# CareLipik clinical NER training plan

## Current status

CareLipik does not yet ship a fine-tuned clinical NER model. The first implemented component is an
offset-preserving normalizer plus explicit boundaries for transliteration and entity recognition.
The normalizer creates a lowercase, punctuation-canonical search representation, but never changes
the doctor-visible transcript. Every normalized character maps back to its original transcript
offset. Hindi transliterations are separate candidates so Devanagari remains intact.

## Target labels

Use BIO tags during token classification and retain character offsets in the source dataset:

- `MEDICATION`
- `GENERIC_SALT`
- `DISEASE`
- `SYMPTOM`
- `ALLERGY`
- `STRENGTH`
- `DOSAGE`
- `FREQUENCY`
- `DURATION`
- `ROUTE`
- `INVESTIGATION`
- `PROCEDURE`
- `ANATOMY`

Assertion is a separate span classification task: `PRESENT`, `NEGATED`, `PAST`, `FAMILY_HISTORY`,
or `POSSIBLE`. Relations are also separate: medication-to-strength, medication-to-dose,
medication-to-frequency, medication-to-route, and investigation-to-result. Do not force assertion
or relations into the token tags.

## Dataset sources

No listed source alone is a ready Hindi/Hinglish medical-conversation NER dataset.

1. **Clinician-authored synthetic consultations** are the primary safe source. Create English,
   Hindi, Devanagari Hindi, and naturally code-mixed Hinglish dialogues covering primary care,
   chronic disease, allergy, medication history, negation, and ambiguous ASR output. Use no real
   patient information.
2. **Doctor-reviewed ASR perturbations** add realistic errors from CareLipik's synthetic audio.
   Run Whisper, MedASR, Saaras, and AssemblyAI on the same synthetic scripts, then annotate the
   resulting errors without copying provider output into the gold reference blindly.
3. **NRCeS BHTS, CDCI, and licensed SNOMED CT India content** provide terminology and aliases for
   weak labelling, entity replacement, and terminology linking. They are terminology resources,
   not sentence-level NER truth. Confirm the applicable distribution and SNOMED affiliate licence
   before downloading, transforming, or packaging any release.
4. **AI4Bharat Naamapadam/IndicNER** can provide general Indian-language NER adaptation, but its
   person/location/organisation annotations do not replace clinical labels.
5. **AI4Bharat Aksharantar and IndicXlit** can generate Hindi-script/Romanized variants. Generated
   medical transliterations require human review because ordinary-word transliteration accuracy
   does not guarantee drug-name accuracy.
6. Restricted English clinical corpora may be used only after verifying their data-use agreements.
   Do not commit them or derived patient text to this repository.

Every imported source must have a dataset card recording owner, URL, version, licence, permitted
uses, transformations, and redistribution restrictions.

## Annotation format

Store training data outside the Android repository. A JSONL record should contain source text,
language/script, character-offset entities, assertions, and relations:

```json
{
  "id": "synthetic-en-0001",
  "text": "I take metformin 500 mg twice daily.",
  "language": "en-IN",
  "entities": [
    {"start": 7, "end": 16, "label": "MEDICATION", "assertion": "PRESENT"},
    {"start": 17, "end": 23, "label": "STRENGTH", "assertion": "PRESENT"},
    {"start": 24, "end": 35, "label": "FREQUENCY", "assertion": "PRESENT"}
  ],
  "relations": [
    {"head": 0, "tail": 1, "type": "HAS_STRENGTH"},
    {"head": 0, "tail": 2, "type": "HAS_FREQUENCY"}
  ]
}
```

Create written annotation guidelines and double-annotate at least the evaluation set. Measure
inter-annotator agreement and adjudicate disagreements with a clinician.

## Splits and minimum baseline

- Begin with 3,000–5,000 carefully reviewed synthetic utterances, balanced across English, Hindi,
  and Hinglish. This is a prototype baseline, not clinical validation.
- Split by complete consultation scenario and template family, not random sentence, to prevent
  near-duplicate leakage.
- Freeze clean and ASR-noisy test sets before model selection.
- Keep separate results by language, script, entity type, assertion, ASR engine, and noise level.
- Report strict entity-level precision, recall, F1, exact-span accuracy, assertion F1, relation F1,
  and terminology-linking top-1/top-3 accuracy.

## MuRIL teacher training

1. Start from `google/muril-base-cased` with a token-classification head.
2. Tokenize with offset mappings and align BIO labels to WordPiece tokens. Ignore special tokens;
   use a documented policy for continuation pieces.
3. Train with class weighting or balanced sampling because non-entity tokens dominate.
4. Tune learning rate, sequence length, batch size, and epochs on the development set; use strict
   entity F1 and early stopping rather than training loss alone.
5. Evaluate clean text and actual ASR output independently. Never select a checkpoint using the
   final test set.
6. Train assertion and relation heads separately after the entity baseline is stable.

MuRIL is the accuracy-oriented teacher, not automatically the phone model.

## Smaller ONNX student

1. Benchmark IndicBERT and a compact multilingual encoder as student candidates.
2. Distil using gold BIO labels plus MuRIL teacher logits. Preserve a normal supervised loss;
   teacher output must not become unquestioned ground truth.
3. Export the best student to ONNX and compare FP32, dynamic INT8, and static INT8 accuracy.
4. Reject quantization if entity or assertion recall falls outside the agreed tolerance.
5. Benchmark cold start, warm latency, peak RAM, battery, and thermal behaviour on the iQOO.
6. Package only a versioned, licensed model after it passes the frozen regression set.

## Continuous improvement

Do not train continuously inside the Android app. Doctor corrections remain local by default. A
future opt-in learning programme must de-identify submissions, obtain explicit consent, require
human annotation/adjudication, train offline, evaluate against frozen tests, and distribute a
signed versioned model with rollback support.

## Delivery order

1. Offset-preserving normalization boundary — implemented on this branch.
2. IndicXlit-compatible transliteration adapter and evaluation fixture.
3. Versioned terminology-index interface and licensed-data importer outside the APK.
4. Exact/fuzzy/phonetic terminology linker with top-k doctor-reviewed candidates.
5. Dataset schema, validator, annotation guide, and synthetic seed set.
6. MuRIL training/evaluation scripts outside the Android runtime.
7. Student distillation, ONNX INT8 export, Android inference adapter, and device benchmarks.
8. Assertion/relation models, optional Gemini fallback, and end-to-end doctor review.
