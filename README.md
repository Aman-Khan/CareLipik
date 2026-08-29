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
- Hindi and Hinglish transcription through a local CareLipik proxy and Sarvam Saaras.
- Offline two-speaker diarization.
- Offline doctor-voice enrollment and confidence-gated Doctor/Patient role matching.
- Manual correction of transcript text, medical terms, and speaker roles.
- AES-GCM encrypted, app-private consultation history after final doctor approval.
- Temporary consultation audio in the Android cache by default.
- No cloud database, embedded provider key, or LLM.

## Requirements

- macOS, Linux, or Windows with Android Studio.
- Android SDK for API 29 or newer.
- Android SDK Platform-Tools for `adb`.
- Android Studio's bundled Gradle JDK, or another compatible configured `JAVA_HOME`.
- A phone with USB debugging enabled or an Android emulator.
- For online Hindi/Hinglish testing only: macOS Keychain and a Sarvam API key.

Model binaries are intentionally ignored by Git. Obtain them through the repository download
scripts rather than committing them.

## Clone and open

```sh
git clone https://github.com/Aman-Khan/CareLipik.git
cd CareLipik-Hackathon
```

Open the repository root in Android Studio and allow Gradle sync to complete. Do not commit
`local.properties`, model binaries, API keys, recordings, signing files, or patient information.

## Local Android configuration

Android Studio normally generates an untracked `local.properties` containing the SDK path:

```properties
sdk.dir=/Users/your-name/Library/Android/sdk
```

For local Hindi/Hinglish backend testing, add the non-secret local URL:

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

## Hindi and Hinglish local backend setup

The Android app never contains the Sarvam credential. A local development proxy reads it from
macOS Keychain, calls Sarvam, and removes its temporary upload file.

### 1. Store the key securely

```sh
./tools/local_transcription_backend/store_sarvam_key.sh
```

Run the script again when the key needs to be replaced.

### 2. Start exactly one backend

```sh
./tools/local_transcription_backend/run.sh
```

Keep this terminal open until transcription completes. In another terminal, verify it:

```sh
curl http://127.0.0.1:8787/health
```

Expected response:

```json
{"status": "ok"}
```

This confirms the Mac server is healthy but does not prove that Android can reach it.

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

On the recording screen:

1. Choose **Hindi** for mostly Hindi speech or **Hinglish** for mixed Hindi/English.
2. Select **Hindi & Hinglish (Saaras)**.
3. Confirm online-processing consent.
4. Submit only synthetic test audio.
5. Keep the backend running until the transcript completes.

Doctor-profile language preferences provide recommendations; they do not select the language for
the current recording. The transcript screen's `Selected mode` label shows what was actually sent.
Hinglish uses `mode=codemix`; Hindi uses `mode=transcribe`.

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
./tools/local_transcription_backend/run.sh
```

HTTP `401` or `403` responses point to the provider credential or account. Port and connection
errors occur before provider authentication.

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
- Consultation audio is kept in app-private cache and discarded after successful approval.
- Voice enrollment and consultation history are excluded from cloud backup and device transfer.
- Online transcription requires explicit consent.
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

## Development rules

- Keep the main consultation flow functional offline.
- Use MVVM with `ViewModel` and `StateFlow`.
- Keep UI, domain logic, and infrastructure separated.
- Add tests for new business logic.
- Do not silently change dependency or plugin versions.
- Do not add a cloud backend or LLM unless explicitly requested.
- Do not commit secrets, local configuration, model binaries, patient data, or recordings.

See [`AGENTS.md`](AGENTS.md) for the complete repository guidelines.
