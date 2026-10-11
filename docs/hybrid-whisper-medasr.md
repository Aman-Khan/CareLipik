# Experimental Whisper + MedASR hybrid

Choose **Whisper + MedASR (Hybrid)** on the recording screen. The main flow remains offline.
Whisper transcribes once, doubtful words are identified automatically, and eligible audio is
checked with MedASR before the review screen becomes ready. Tapping a prepared phrase applies
its existing suggestion; taps never start inference. Nothing is replaced automatically.

## Actual decoder confidence

CareLipik rebuilds the existing Sherpa **1.13.6** native runtime with the patch in
`tools/native_sherpa/whisper-confidence.patch`. Kotlin classes and the existing ONNX Runtime
**1.27.1** binaries are preserved from the original AAR, for all four original Android ABIs.
No models or dependency versions are changed. The patched JNI library replaces the original
JNI library in a generated, ignored AAR; an unpatched fallback is not packaged.

Hybrid streams explicitly opt in using `carelipik.whisper.collect_confidence=1`. The greedy
decoder computes the selected token's log probability using numerically stable log-sum-exp
on the same filtered logits used to choose that token. Timestamp tokens are excluded from
the text-score array. Metadata is returned through the existing OfflineStream.getOption API
under `carelipik.whisper.confidence.v1`; there is no Kotlin result-constructor/JNI ABI change.
Standard Whisper streams do not request this extra score collection.

`WhisperConfidenceMapper` groups overlapping token scores into words only when the joined
token text matches the original text exactly. Word probability is the geometric mean of
its contributing token probabilities. A separate minimum-token probability catches a weak
subword hidden by the mean. Character ranges are kept against the original transcript.
Missing, invalid, nonfinite, or mismatched data does not become a fabricated probability.
The review reports when confidence is unavailable for an affected region.

`ConservativeWhisperUncertaintyDetector` flags words below either configurable probability
threshold, alongside repetition, text-compression, sparse-text, and timestamp-consistency
checks. Scores are decoder preferences, not calibrated percentages of recognition accuracy.
They can miss confidently wrong words. No medical dictionary or separate vocabulary dataset
is required.

## Audio alignment and automatic verification

Hybrid Whisper enables native timestamp-token decoding using the existing model, without
requiring attention-output model exports. Valid native speech segments become smaller
verification regions. Their audio times come from Whisper timestamp tokens, not a proportional
estimate based on word positions. Exact word timestamps are not claimed. If native segment
alignment is invalid, retain the containing decoded chunk and actual scores where text
alignment is still valid.

The existing recorder produces mono 16 kHz, 16-bit PCM WAV. The importer validates this format;
incompatible WAV files are rejected rather than resampled. The hybrid adapter reads PCM once,
retains speaker identity and original source times, and selects uncertain English-compatible
regions. Explicitly non-English language and non-Latin text are excluded. When language is
missing, only explicitly selected English is eligible. Hinglish uses Whisper auto language
detection; English islands within a mixed or Hindi-detected region can still be skipped.

`AudioSegmentExtractor` adds bounded context, avoids crossing known speaker/language
boundaries, merges adjacent windows, skips negligible-energy audio, and enforces its limits.
It never verifies the whole recording or truncates a region merely to fit the budget.
MedASR loads lazily only when eligible windows exist, reuses its recognizer across them,
and releases native resources in finally blocks. Cancellation is checked between native
calls; an in-progress native decode cannot be interrupted. ViewModel requests are serialized.

## Settings

Configure `HybridTranscriptionConfig` when constructing the hybrid engine:

| Setting | Default |
| --- | --- |
| Word geometric-mean probability threshold | 0.55 |
| Minimum contributing token probability threshold | 0.20 |
| Consecutive repetition count | 3 |
| Text compression ratio | 2.4, for at least 100 characters |
| Sparse decoded text | At least 8 seconds with at most 2 words |
| Context padding | 400 ms per side |
| Merge gap | 250 ms |
| Verification duration | 500 ms to 30 seconds |
| Maximum MedASR inference calls | 4 |
| Maximum fraction of original audio | 25%, including padding |
| Minimum window RMS | 0.002 |
| Maximum changed words per suggestion | 4, counting both outputs |
| Minimum matching anchors | 3 words and 60% of Whisper words |

These are experimental thresholds, not calibrated accuracy guarantees. Short recordings or
long regions may exceed the budget; skipped words remain reviewable manually. The review
retains checked intervals and explanations. MedASR agreement does not certify correctness.

## Reviewing prepared suggestions

The review screen stays in processing through Whisper and automatic MedASR verification.
**Words to review** displays the current transcript. Gray words had low decoder scores;
colored phrases have prepared, safely aligned model disagreements. Tap a colored phrase or
its displayed original-to-alternative button to apply that cached replacement. Insertions
use an explicit alternative button because they have no original characters to highlight.

The review card preserves both full model contexts, the checked audio interval, and reasons.
**Use suggested phrase** applies only that phrase. **Keep current transcript** rejects it.
Unreliable or edge-only alignment retains the complete alternative as unresolved, requiring
manual editing or keeping Whisper. Resolve pending disagreements before continuing.
Medication names, doses, units, negations, and all other changes require explicit selection.

**Replay this audio region** plays the original source interval locally. Only one review
region plays at a time. Playback stops at its boundary and releases the player when the
screen stops or leaves. **Show original Whisper transcript** preserves the primary output
even after editing. Manual edits invalidate pending correction offsets; accepting a prepared
phrase shifts other nonoverlapping offsets safely. Review state survives rotation through
the Activity-scoped ViewModel StateFlow. Saved recordings preserve the selected engine.

Whisper failures use the existing retryable error flow. MedASR failures preserve Whisper and
report a notice. Optional online medical-term analysis remains a separately consented feature.

## Native build prerequisites

Install Python 3.9+ (with `git` available) and Android SDK packages **ndk;27.2.12479018** and
**cmake;3.22.1**, alongside the existing Android build prerequisites. Python can be overridden
with `-Pcarelipik.pythonCommand=/path/to/python`. SDK location follows sdk.dir in local.properties first,
then ANDROID_SDK_ROOT or ANDROID_HOME. Install native packages with Android Studio's
SDK Manager, or sdkmanager using the existing SDK licenses.

`gradle/sherpa-confidence.gradle.kts` wires `buildSherpaConfidenceRuntime` into ordinary app
builds. The first build downloads the pinned, SHA-256-verified Sherpa source and matching ONNX
headers; CMake fetches its pinned upstream build dependencies. Later builds reuse the ignored
`tmp/sherpa-confidence` cache and generated `build/native-sherpa` AAR. An offline Gradle build
requires those native inputs to be cached already. This is build-time network use, not an
application network requirement. No native/model binaries are committed. The source patch,
header hashes, and build script make the generated runtime reproducible from pinned inputs.
The rebuilt runtime includes ASR, speaker embeddings, and diarization; unused TTS is excluded.

No automated tests, benchmarks, evaluation scripts, or performance reports were created or
run for this feature, per the feature request. Build/lint and native compilation checks are
separate from device recognition accuracy. Actual confidence thresholds, code-switched audio,
playback, and correction behavior still require manual device evaluation with synthetic audio.
