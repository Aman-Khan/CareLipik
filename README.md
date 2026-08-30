# CareLipik

CareLipik is an Android clinical-documentation assistant built with Kotlin, Jetpack Compose,
Material 3, MVVM, and `StateFlow`. It records a consented consultation, transcribes it, lets the
doctor correct terms and speaker roles, prepares a structured draft, and saves documentation only
after final doctor approval.

CareLipik is not an autonomous diagnosis or prescription system. Never use real patient data in
development recordings, screenshots, fixtures, or tests.

## Current capabilities

- Offline English transcription with MedASR.
- Offline multilingual transcription with Whisper.
- Optional online English, Hindi, and Hinglish transcription with Sarvam Saaras Batch diarization.
- Offline two-speaker diarization.
- Offline doctor-voice enrollment and confidence-gated Doctor/Patient role matching.
- Manual correction of transcript text, medical terms, and speaker roles.
- Offline transcript-backed clinical drafts that preserve every reviewed patient statement and the
  complete reviewed transcript for doctor approval.
- Optional Gemini drafts in SOAP, APSO, H&P, problem-oriented, progress, DAP, BIRP, GIRP,
  procedure, and doctor-specialty formats, with transcript-turn evidence and coverage warnings.
- Optional English clinical-note generation from a reviewed Hindi or Hinglish transcript; the
  original conversation log is always retained unchanged.
- A separate prescribed-medicine and dosage section whose entries must each be verified by the
  doctor before final approval.
- AES-GCM encrypted, app-private consultation history after final doctor approval.
- AES-GCM encrypted doctor profile that restores after app and phone restarts.
- Approved report files linked to consultation history as encrypted A4 PDF, structured JSON, HL7
  FHIR R4 Bundle, or plain text, with open, share, regenerate, and delete controls.
- Temporary consultation audio in the Android cache by default.
- Optional consent-gated Gemini medical-term enhancement for transcripts produced by Whisper,
  MedASR, or Saaras, plus structured-note drafting; no provider key in the APK.
- No cloud consultation database. All AI output remains subject to doctor confirmation.

## Requirements

- macOS, Linux, or Windows with Android Studio.
- Android SDK for API 29 or newer.
- Android SDK Platform-Tools for `adb`.
- Android Studio's bundled Gradle JDK, or another compatible configured `JAVA_HOME`.
- A phone with USB debugging enabled or an Android emulator.
- For online transcription testing: macOS Keychain and a Sarvam API key.
- For online medical-term extraction or clinical-note generation: a Gemini API key stored in
  macOS Keychain.

Model binaries are intentionally ignored by Git. Obtain them through the repository download
scripts rather than committing them.

The local diarization path uses Sherpa-ONNX 1.13.6 on CPU with Pyannote Segmentation 3.0,
NeMo TitaNet Small speaker embeddings, and fixed two-cluster fast clustering. Gemini is not used
for diarization: it receives reviewed transcript text only for optional medical-term analysis or
structured-note drafting and cannot observe acoustic speaker changes.

## Clone and open

```sh
git clone https://github.com/Aman-Khan/CareLipik.git
cd CareLipik-Hackathon
```

Open the repository root in Android Studio and allow Gradle sync to complete. Do not commit
`local.properties`, model binaries, API keys, recordings, signing files, or patient information.

## Start here: complete setup from a fresh clone

CareLipik has two supported operating paths. The fully offline path needs no backend and no API
key. The optional online features use the Mac development backend because provider keys must never
be embedded in the APK.

| Feature | Runs where | Backend | Required key |
| --- | --- | --- | --- |
| Whisper transcription | Android phone | No | None |
| MedASR transcription | Android phone | No | None |
| Pyannote + TitaNet diarization | Android phone | No | None |
| Doctor voice matching | Android phone | No | None |
| Saaras transcription + diarization | Sarvam through local proxy | Yes | Sarvam |
| Gemini medical-term enhancement | Gemini through local proxy | Yes | Gemini |
| Gemini structured/English note | Gemini through local proxy | Yes | Gemini |

### A. One-time Android and model setup

From the repository root on macOS:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"

./scripts/download-medasr-model.sh
./scripts/download-whisper-model.sh
./tools/offline_speaker_diarization/setup.sh
./tools/offline_speaker_diarization/setup.sh --check

./gradlew test
./gradlew lint
./gradlew assembleDebug
```

Create or confirm the untracked `local.properties` file:

```properties
sdk.dir=/Users/your-name/Library/Android/sdk
carelipik.transcriptionBackendUrl=http://127.0.0.1:8787
```

The backend URL is harmless when using only offline modes. The app attempts to contact it only for
an explicitly selected and consented online operation.

### B. Fully offline phone run

No Sarvam key, Gemini key, Python server, internet connection, or `adb reverse` is required:

```sh
adb devices
./gradlew installDebug
```

Open CareLipik and select **Offline medical English (MedASR)** or **Offline multilingual
(Whisper)**. Use only synthetic test audio during development.

### C. One-time optional online setup

The development backend requires macOS, `/usr/bin/python3`, internet access, and macOS Keychain.
Store provider keys through the secure prompts:

```sh
./tools/local_transcription_backend/store_sarvam_key.sh
./tools/local_transcription_backend/store_gemini_key.sh
```

Create Sarvam credentials in the [official Sarvam dashboard](https://dashboard.sarvam.ai/key-management)
and Gemini credentials in [Google AI Studio](https://aistudio.google.com/app/apikey). Sarvam is
needed only for Saaras. Gemini is needed only for online term enhancement and Gemini-generated
clinical notes. Either key may be omitted when its features are not needed.

### D. Every online development session

Terminal 1 — keep the backend running:

```sh
./tools/local_transcription_backend/run.sh
```

Terminal 2 — connect the authorized phone, verify the complete boundary, then install:

```sh
./tools/local_transcription_backend/connect_android.sh
./tools/local_transcription_backend/status.sh
./gradlew installDebug
```

Repeat `connect_android.sh` after reconnecting the phone, restarting ADB, or restarting the
emulator. `status.sh` should show the phone as `device`, a `tcp:8787` reverse mapping, and the
expected backend capabilities.

### E. Replace an expired or quota-exhausted provider key

Running a storage script updates macOS Keychain, but a running backend keeps its old in-memory key.
Always restart it:

```sh
./tools/local_transcription_backend/store_gemini_key.sh
./tools/local_transcription_backend/stop.sh
./tools/local_transcription_backend/run.sh
```

Use `store_sarvam_key.sh` instead for Sarvam. A healthy `/health` response confirms that a key was
loaded; it does not prove provider validity, billing, or remaining quota. Only a real synthetic
provider request validates those conditions.

### F. Script reference

| Script | Purpose |
| --- | --- |
| `scripts/download-medasr-model.sh` | Downloads ignored MedASR assets |
| `scripts/download-whisper-model.sh` | Downloads ignored Whisper assets |
| `tools/offline_speaker_diarization/setup.sh` | Installs/checks ignored local diarization assets |
| `tools/local_transcription_backend/store_sarvam_key.sh` | Adds or replaces the Sarvam Keychain credential |
| `tools/local_transcription_backend/store_gemini_key.sh` | Adds or replaces the Gemini Keychain credential |
| `tools/local_transcription_backend/run.sh` | Starts or reuses the backend on port 8787 |
| `tools/local_transcription_backend/stop.sh` | Stops only the backend process managed by the script |
| `tools/local_transcription_backend/connect_android.sh` | Checks device authorization and creates `adb reverse` |
| `tools/local_transcription_backend/status.sh` | Reports keys, backend health, devices, and reverse mappings without printing secrets |

## Local Android configuration

Android Studio normally generates an untracked `local.properties` containing the SDK path:

```properties
sdk.dir=/Users/your-name/Library/Android/sdk
```

For local online transcription and clinical-analysis testing, add the non-secret local URL:

```properties
carelipik.transcriptionBackendUrl=http://127.0.0.1:8787
```

The URL is embedded in `BuildConfig` during compilation. Rebuild and reinstall the app whenever
it changes. Never put the Sarvam API key in this file or anywhere in the Android project.

## Model setup

Download the required ignored model assets from the repository root:

```sh
./scripts/download-medasr-model.sh
./scripts/download-whisper-model.sh
./tools/offline_speaker_diarization/setup.sh
./tools/offline_speaker_diarization/setup.sh --check
```

The expected ignored asset directories are under:

```text
app/src/main/assets/models/
```

Do not force-add these binaries to Git.

## Build and verify

Use Android Studio's configured Gradle JDK. On macOS, the bundled JDK can be selected for a shell
session with:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Required verification commands:

```sh
./gradlew test
./gradlew lint
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest
```

`connectedDebugAndroidTest` requires an online emulator or USB-debugging phone. Some native ONNX
tests can take significantly longer on physical hardware.

Install the debug build with:

```sh
./gradlew installDebug
```

## Online transcription and clinical-analysis setup

The Android app never contains Sarvam or Gemini credentials. A local development proxy reads them
from macOS Keychain. Audio upload files are removed after submission, clinical transcript bodies
are not logged, and API responses are marked `no-store`.

### Complete one-time checklist

Requirements are macOS, Android Studio with SDK Platform-Tools, USB debugging enabled on the
phone, internet access, Python 3 from `/usr/bin/python3`, a Sarvam API key, and a Gemini API key.
The backend uses only Python's standard library; there is no `pip install` or virtual environment.

From the repository root:

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
./tools/local_transcription_backend/store_sarvam_key.sh
./tools/local_transcription_backend/store_gemini_key.sh
./tools/local_transcription_backend/status.sh
```

Create the keys in the Sarvam developer dashboard and Google AI Studio respectively. The Sarvam
key enables Saaras batch transcription and provider diarization. The Gemini key enables online
medical-term candidates and structured clinical-note generation. Provider accounts must have an
active project, billing/quota where required, and access to the configured model. CareLipik defaults
to `gemini-2.5-flash`; override it only for local testing with `GEMINI_MODEL=model-name` when running
the backend. Never paste either key into a shell command, Gradle property, Android resource, source
file, screenshot, or chat.

### 1. Store provider keys securely

```sh
./tools/local_transcription_backend/store_sarvam_key.sh
```

For online medical-term analysis and structured-note generation, also store the Gemini key:

```sh
./tools/local_transcription_backend/store_gemini_key.sh
```

The terminal invokes macOS Keychain. Paste secrets only into the Keychain prompt, never into
source files, `local.properties`, chat messages, screenshots, or shell-history command arguments.
Run the relevant script again when a key needs to be replaced.

### 2. Start exactly one backend

```sh
./tools/local_transcription_backend/run.sh
```

`run.sh` now detects an existing healthy instance and reuses it instead of producing an
`Address already in use` traceback. It also explains when another application owns port 8787 and
records a managed PID so the backend can be stopped safely. Keep this terminal open. Use
`Control-C` for a normal stop, or from another terminal run:

```sh
./tools/local_transcription_backend/stop.sh
```

Keep this terminal open until transcription completes. In another terminal, verify it:

```sh
./tools/local_transcription_backend/status.sh
```

Expected response:

```json
{"status": "ok", "clinical_entity_extraction": true, "clinical_note_generation": true}
```

Both Gemini capability fields are `false` when the Gemini key is absent; Saaras transcription can
still work. Health confirms that keys were loaded into the local server; it cannot validate
provider quota, billing, internet access, or whether a key has expired. Those are validated only
when the corresponding provider request is made. `status.sh` also reports Keychain presence, ADB
devices, and reverse-port mappings without printing either secret.

### 3. Make ADB available

Check for a connected target:

```sh
adb devices
```

If zsh reports `command not found: adb`, use the typical macOS SDK path:

```sh
"$HOME/Library/Android/sdk/platform-tools/adb" devices
```

Optionally add it to future zsh sessions:

```sh
echo 'export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"' >> ~/.zshrc
source ~/.zshrc
```

If the executable is missing, install **Android SDK Platform-Tools** in Android Studio's SDK
Manager.

### 4. Forward Android localhost to the Mac

The configured `127.0.0.1` normally refers to Android itself. Create the development bridge:

```sh
adb reverse tcp:8787 tcp:8787
adb reverse --list
```

If multiple targets are connected:

```sh
adb -s DEVICE_SERIAL reverse tcp:8787 tcp:8787
adb -s DEVICE_SERIAL reverse --list
```

The target must appear as `device`, not `offline` or `unauthorized`. Accept the USB-debugging
prompt on a physical phone. Repeat port reversal after reconnecting the device, restarting the
emulator, or restarting ADB.

### 5. Rebuild, install, and select the correct mode

```sh
./gradlew installDebug
```

For every new USB/device session, the reliable startup sequence is:

```sh
./tools/local_transcription_backend/run.sh
./tools/local_transcription_backend/connect_android.sh
./tools/local_transcription_backend/status.sh
```

Run the backend in one terminal and the remaining commands in another because `run.sh` remains in
the foreground. The app must be built with this non-secret entry in untracked `local.properties`:

```properties
carelipik.transcriptionBackendUrl=http://127.0.0.1:8787
```

`connect_android.sh` finds Android Studio's bundled `adb`, checks authorization, handles the common
single-device case, and recreates `adb reverse`. With multiple devices, pass the required serial:

```sh
./tools/local_transcription_backend/connect_android.sh DEVICE_SERIAL
```

On the recording screen:

1. Choose **English**, **Hindi**, or **Hinglish** for the recorded conversation.
2. Select **Online multilingual (Saaras)** when reliable online speaker separation is needed.
3. Confirm online-processing consent.
4. Submit only synthetic test audio.
5. Keep the backend running until the transcript completes.

Doctor-profile language preferences provide recommendations; they do not select the language for
the current recording. The transcript screen's `Selected mode` label shows what was actually sent.
Hinglish uses `mode=codemix`; English and Hindi use `mode=transcribe`. Saaras Batch is requested
with `with_diarization=true` and `num_speakers=2`. The app preserves the provider's chronological
speaker turns and timestamps. If exactly two usable voices are not returned, it shows the full
transcript with a warning instead of presenting unreliable Doctor/Patient labels.

After transcription, the backend can send the transcript text to Gemini for structured medical
term candidates after separate consent. This option is available for Whisper, MedASR, and Saaras
transcripts. Offline ASR-error suggestions are retained and merged with Gemini candidates rather
than replaced. The backend rejects any candidate whose `source_text` is not an exact transcript
span. The model is not permitted to diagnose, prescribe, or invent terminology codes. Every term
must be confirmed by the doctor. Editing the transcript invalidates the previous AI offsets; tap
**Analyze again** to refresh candidates.

### Clinical note formats and English generation

After the transcript and speaker roles have been reviewed, the clinical-draft screen offers SOAP,
APSO, H&P, problem-oriented, progress, DAP, BIRP, GIRP, procedure, and doctor-specialty notes. The
doctor selects the structure and either keeps the consultation language or requests an English
note. English generation from Hindi or Hinglish requires Gemini and separate online consent. If
the backend is unavailable, CareLipik retains the conservative offline draft; it does not block
transcript review or silently invent translated text.

The backend numbers every labelled Doctor/Patient turn before asking Gemini to draft. Non-empty
sections must cite valid source turn IDs. A deterministic validator reports Patient turns that are
not represented in any section. Evidence links and coverage warnings assist review but do not
guarantee semantic accuracy or replace doctor verification.

Gemini may return a prescribed-medicine candidate only when it cites an explicit Doctor turn. The
backend rejects medication candidates supported only by Patient turns, such as existing medicines,
past medicines, pharmacy suggestions, or medicines the patient did not start. Every retained entry
starts unverified; changing its name, salt, strength, dose, route, frequency, duration, or
instructions clears its review state. Final approval is blocked until all medicine entries are
complete and doctor-verified.

### 6. Phone verification by component

For Saaras diarization:

1. Import a synthetic two-voice WAV from `tools/synthetic_test_audio/output/`.
2. Choose the matching language and **Online multilingual (Saaras)**.
3. Confirm online consent and create the transcript.
4. Verify the conversation view alternates between exactly two speaker labels at every known turn.
5. Assign Doctor and Patient manually and swap them once to verify correction remains available.
6. Compare every turn against the fixture's JSON reference; merely seeing two speaker IDs is not
   sufficient verification.

For local diarization on a physical phone:

1. Run `./tools/offline_speaker_diarization/setup.sh --check`, rebuild, and install the app.
2. Choose English with **Offline medical English (MedASR)** or **Offline multilingual
   (Whisper)**. No backend, API key, or Gemini connection is involved.
3. Record or import a synthetic consultation with two clearly different voices and short pauses.
4. Verify every expected hand-off, not merely that two speaker IDs appear somewhere.
5. If a doctor voice was not enrolled, expect neutral Speaker 1/Speaker 2 identities and assign
   Doctor/Patient manually. Acoustic clustering cannot infer occupations from a voice.
6. Correct or swap any wrong role in review and confirm the correction is retained.

The device benchmark requires at least 14 of 16 known alternating turns and requires the first
two turns to be different speakers. On the connected iQOO I2501/SM8850, the accepted Pyannote +
TitaNet configuration processed the 112.6-second synthetic fixture in 11.4-12.9 seconds across
the final validation runs. Real rooms, overlapping speech, microphone distance, and similar voices
still require separate phone recordings and doctor review.

For Gemini term extraction:

1. Confirm `/health` reports `clinical_entity_extraction: true`.
2. Submit a synthetic consultation containing symptoms, negation, history, allergies, Indian
   medicine brands, generic medicines, strength, and frequency.
3. Confirm highlighted candidates refer to exact visible transcript text.
4. Verify negated and historical statements are not presented as new diagnoses.
5. Correct one suggested term, tap **Analyze again**, and confirm the updated offsets are used.
6. Confirm all candidates still require doctor acceptance before continuing.

For clinical report generation and English translation:

1. Use a synthetic, fully reviewed two-speaker transcript and continue to **Clinical draft**.
2. Select each required note format and confirm its expected editable sections appear.
3. For Hindi or Hinglish, select **English**, grant the separate transcript-upload consent, and tap
   **Generate selected note with Gemini**.
4. Confirm the English sections preserve negation, age, symptoms, measurements, past history,
   allergies, and current medicines without creating a diagnosis or examination finding.
5. Confirm each non-empty generated section shows transcript turn IDs and that an intentionally
   omitted Patient turn produces a visible coverage warning.
6. Include an explicit synthetic Doctor prescription. Confirm it appears separately with strength,
   dose, route, frequency, duration, instructions, and an unchecked doctor-review control.
7. Confirm final review is blocked until every medicine is verified.
8. Approve, reopen the consultation from history, and confirm patient details, formatted sections,
   prescribed medicines, coverage warnings, and the complete conversation log are present.
9. Generate PDF, structured JSON, FHIR R4, and plain text. Confirm each contains the selected note
   format and medicine details but no audio path.
10. Return to consultation history and confirm each generated format appears under **Saved
    reports** with **Open report**, **Share report**, and **Delete saved report** controls.
11. Force-stop and reopen CareLipik, then confirm the encrypted report links remain available.
12. Delete the consultation and confirm all of its linked report artifacts are removed.

For a fresh-consultation reset:

1. Enter a synthetic patient reference and select or record audio, then return to Home without
   approving that consultation.
2. Tap **Start a new consultation**.
3. Confirm recording consent, patient reference, age, visit reason, selected audio, playback state,
   transcription mode, transcript, draft, and previous export state have all been cleared.
4. Confirm already approved encrypted history remains available and unchanged.

## Common problems

### Port 8787 is already in use

`OSError: [Errno 48] Address already in use` usually means an earlier backend is already running.
Check it before starting another instance:

```sh
curl http://127.0.0.1:8787/health
lsof -nP -iTCP:8787 -sTCP:LISTEN
```

If health returns `ok`, use the existing process. If the listener is stale, stop only the PID
reported by `lsof`, then restart:

```sh
kill PID_FROM_LSOF
./tools/local_transcription_backend/run.sh
```

### Backend health works but the app cannot transcribe

Check each connection boundary:

```sh
curl http://127.0.0.1:8787/health
adb devices
adb reverse --list
```

The reverse list must contain `tcp:8787`. Recreate the mapping if necessary and reinstall the
debug build if `local.properties` changed. A successful Mac health check alone does not establish
Android-to-Mac connectivity.

### The transcript says Hindi after Hinglish was chosen in the profile

Select Hinglish on the current consultation's recording screen. Profile preferences do not set
the active recording mode.

### Provider authentication fails

Replace the Keychain credential, restart the backend so it reloads the key, and watch its terminal
while submitting:

```sh
./tools/local_transcription_backend/store_sarvam_key.sh
./tools/local_transcription_backend/store_gemini_key.sh
./tools/local_transcription_backend/stop.sh
./tools/local_transcription_backend/run.sh
```

HTTP `401` or `403` responses point to the corresponding provider credential or account. A
successful Saaras transcription does not prove that Gemini is configured; check the health
capability field. Port and connection errors occur before provider authentication.

### Two speakers appear inside one block

First confirm which engine was selected. MedASR and Whisper use the fully local Pyannote + TitaNet
acoustic diarizer; Gemini does not repair or assign their speaker turns. Re-run the model setup
script and rebuild if its `--check` fails. Avoid simultaneous speech, place the phone between both
speakers, and leave a short pause at hand-offs. Without doctor enrollment, Speaker 1 and Speaker 2
remain anonymous until manually assigned.

When connectivity and consent are available, **Online multilingual (Saaras)** is the preferred
fallback for provider-side diarization. Saaras diarization is a Batch API feature; the non-batch
REST/streaming endpoints do not return speaker turns. If Saaras returns fewer than two usable
speaker IDs, CareLipik intentionally keeps a continuous transcript and displays a warning rather
than guessing speaker boundaries.

### Medical terms or medicine salts are missing

Check that `/health` reports `clinical_entity_extraction: true`, then use **Analyze again** after
any transcript edit. Gemini results are multilingual AI candidates, not an authoritative Indian
drug database. Production verification of Indian brands and salts still requires a licensed,
versioned NRCeS Common Drug Codes for India terminology service. SNOMED CT, CDCI, RxNorm, ICD, and
LOINC identifiers must never be accepted solely because an LLM generated them.

### Clinical draft or report is missing transcript details

Current builds generate an offline transcript-backed draft. Patient-labelled statements are
copied verbatim into editable sections, a bounded `N years old` patient statement can prefill the
age candidate, and the complete reviewed transcript is stored with the approved note. PDF, JSON,
FHIR, plain text, and encrypted history all include that source appendix.

The structured sections are still a doctor-controlled organization layer; they are not an
autonomous diagnosis or summary. Correct ASR substitutions such as `cuff`/`golf` for `cough`
before continuing. CareLipik presents known confusion fixes as suggestions and never silently
changes the transcript.

For Gemini-generated notes, review the **Transcript coverage warnings** card. Each warning names a
Patient turn that was not cited by any generated note section. Add the missing fact to the correct
section or regenerate after correcting the transcript. The complete reviewed conversation log is
saved and exported even when a concise report intentionally does not repeat every conversational
sentence. CareLipik intentionally does not write patient transcript bodies to Android or backend
diagnostic logs.

Records approved by an older prototype cannot be repaired automatically: those records stored
only the hard-coded sample draft, and consultation audio was discarded after approval. If the
original imported synthetic WAV still exists, process it as a new consultation with the current
build.

### Quick recovery sequence

```sh
curl http://127.0.0.1:8787/health
adb devices
adb reverse tcp:8787 tcp:8787
adb reverse --list
```

Restart the backend only if its health check fails. Rebuild only when Android code or the embedded
backend URL changed.

## Privacy and storage behavior

- Enrollment audio and consultation history stay in app-private storage.
- Approved consultation records are encrypted with AES-GCM using an Android Keystore key.
- New approved records retain patient details, the selected clinical-note format, doctor-reviewed
  prescribed medicines, coverage warnings, and the complete reviewed conversation log inside the
  encrypted on-device consultation record.
- Generated approved reports are encrypted separately with AES-GCM and linked by consultation ID.
  One current artifact is retained per export format; regenerating that format replaces its older
  encrypted artifact.
- Consultation audio is kept in app-private cache and discarded after successful approval.
- Opening or sharing a saved report decrypts a short-lived copy into app-private cache and exposes
  it through a temporary read-only content URI. No report contains consultation audio.
- Voice enrollment, consultation history, and encrypted report artifacts are excluded from cloud
  backup and device transfer.
- Online audio transcription, transcript-based medical-term analysis, and Gemini note generation
  require explicit consent.
- Provider keys stay in the backend/Keychain and are never embedded in Android.
- AI candidates are not diagnoses or verified medical codes and require doctor confirmation.
- Development recordings must never contain real patient information.

## Project structure

```text
app/src/main/java/com/carelipik/app/  Kotlin, Compose UI, domain, and data code
app/src/main/res/                     Android resources and manifest support
app/src/test/                         Local JUnit tests
app/src/androidTest/                  Emulator/device tests
docs/                                 Detailed technical documentation
tools/                                Local backend and test utilities
gradle/libs.versions.toml             Dependency and plugin versions
```

For the provider contract, security boundary, and detailed transcription behavior, see
[`docs/hindi-hinglish-transcription.md`](docs/hindi-hinglish-transcription.md).

## Approved clinical-note exports

The export screen is available only after final doctor approval, including when reopening an
approved item from encrypted history. It offers:

- **Clinical note PDF**: human-readable A4 output for saving or printing.
- **Structured JSON**: CareLipik's versioned application-interchange schema.
- **HL7 FHIR R4 bundle**: a base R4 document Bundle whose first resource is a Composition and
  which also includes Patient, Device, DocumentReference, and doctor-approved MedicationRequest
  resources when prescriptions are present.
- **Plain-text EHR note**: labelled sections designed for copying into an EHR.

The clinical note structure is selected before doctor approval; the export screen then selects the
file format. For example, one approved H&P note can be exported as PDF, JSON, FHIR, or plain text.
Each generated file is stored as an encrypted artifact linked to that approved consultation. The
history detail screen can reopen, share, regenerate, or delete each format. Deleting a consultation
also deletes its linked reports. Readable cache copies are temporary; the persistent copy remains
encrypted until explicitly deleted.

Approved consultations created before this feature remain readable, but their older temporary
exports are not migrated. Open the consultation and generate each required format once to create
its encrypted report link.

Phone check for history: approve a synthetic consultation, return to Home, and confirm it appears
under **Recent consultations** without restarting the app. Open **Consultations** and confirm the
history page has no workflow step counter, newest records appear first, and each card shows its
approval state, visit reason, note format, and age. Open a record to generate, reopen, share, or
delete its linked reports; deleting the consultation must remove those reports as well.

Phone check for the doctor profile: open **Profile**, save synthetic professional details, then
force-stop and reopen CareLipik. Confirm the Home greeting and every profile field are restored.
The profile file is app-private, encrypted with an Android Keystore key, and excluded from cloud
backup and device transfer. The launcher should show CareLipik's teal clinical-document icon rather
than the default Android icon; some launchers may require returning home to refresh it.

FHIR support is base R4 interoperability output, not certification for a national, hospital, or
vendor-specific profile. Validate it against the receiving system's implementation guide and
terminology requirements before production import. No export contains consultation audio.

## Development rules

- Keep the main consultation flow functional offline.
- Use MVVM with `ViewModel` and `StateFlow`.
- Keep UI, domain logic, and infrastructure separated.
- Add tests for new business logic.
- Do not silently change dependency or plugin versions.
- Do not add a cloud backend or LLM unless explicitly requested.
- Do not commit secrets, local configuration, model binaries, patient data, or recordings.

See [`AGENTS.md`](AGENTS.md) for the complete repository guidelines.
