# Hindi and Hinglish transcription

CareLipik uses a provider adapter so the recording and transcript UI are not tied to one
speech model.

## Engine routing

- English defaults to the offline Google MedASR model.
- Hindi defaults to Saaras v3 Batch with `language_code=hi-IN` and `mode=transcribe`.
- Hinglish defaults to Saaras v3 Batch with `language_code=hi-IN` and `mode=codemix`.
- Whisper Small remains available as the offline Hindi/Hinglish fallback.

Saaras Batch is used because consultations are commonly longer than the 30-second REST
limit and speaker diarization is a Batch-only feature. The request omits `num_speakers` to
allow automatic speaker counting. The result uses stable numbered speaker IDs; the review
screen shows Person 1, Person 2, Person 3, and so on with optional editable names.
Diarization does not prove which voice belongs to the doctor or patient. Confirm each role;
additional people can be marked Other.

## Security boundary

The Android app must never contain a Sarvam API key. It uploads only after the user confirms
patient consent and only to a CareLipik-controlled HTTPS backend. That backend owns the
Sarvam credential, submits and polls the Saaras batch job, returns the result, and deletes
temporary audio according to the application's retention policy.

For a local build, put the non-secret backend URL in the untracked `local.properties` file:

```properties
carelipik.transcriptionBackendUrl=https://your-secure-backend.example
```

Do not add a Sarvam key to `local.properties`, Gradle, `BuildConfig`, source code, or the APK.

## CareLipik backend contract

The app submits:

```http
POST /v1/transcriptions
Content-Type: multipart/form-data

provider=sarvam
model=saaras:v3
language_code=hi-IN
mode=transcribe|codemix
with_diarization=true
file=<16 kHz mono PCM WAV>
```

`num_speakers` is optional. When absent, the backend must omit it from the provider request
and allow automatic counting instead of defaulting to two speakers.

The backend acknowledges the asynchronous job:

```json
{
  "id": "job_123",
  "status": "queued"
}
```

The app polls `GET /v1/transcriptions/job_123`. A completed response is:

```json
{
  "id": "job_123",
  "status": "completed",
  "transcript": "Full transcript",
  "segments": [
    {
      "speaker_id": "0",
      "transcript": "First speaker text"
    },
    {
      "speaker_id": "1",
      "transcript": "Second speaker text"
    }
  ]
}
```

Queued jobs may return `queued`, `processing`, or `running`. Failed jobs return:

```json
{
  "id": "job_123",
  "status": "failed",
  "message": "Safe user-facing message"
}
```

Production requests also need user/device authentication, rate limiting, request-size
limits, audit logging without audio or transcript content, encryption in transit and at
rest, and an explicit deletion/retention policy. The Android adapter does not make a direct
request to Sarvam.

## Test sequence

1. Record a short Hindi consultation and choose **Hindi**.
2. Confirm that Saaras Online is selected and the online-processing consent is required.
3. Confirm consent and continue. Without a configured backend, the app should show a clear
   setup error rather than uploading anywhere.
4. Configure the HTTPS backend URL and repeat with Hindi.
5. Go back to the recording and repeat with **Hinglish** using the same audio.
6. Compare the result against Whisper Offline.
7. Verify every medical term and manually confirm which speaker is the doctor and patient.

Never use real patient information in development recordings or fixtures.

## Local end-to-end test on macOS

The repository contains a local-only proxy in `tools/local_transcription_backend`. It uses
only the Python standard library, listens on `127.0.0.1:8787`, and deletes its temporary WAV
immediately after uploading it to Sarvam.

From the repository root, store the Sarvam key in macOS Keychain. The key is entered into a
secure prompt and is not written to the shell history or repository:

```sh
./tools/local_transcription_backend/store_sarvam_key.sh
```

Start the backend in a terminal and keep it open:

```sh
./tools/local_transcription_backend/run.sh
```

In another terminal, connect the phone's localhost port to the Mac and install a debug build
configured for that local address:

```sh
adb reverse tcp:8787 tcp:8787
./gradlew -Pcarelipik.transcriptionBackendUrl=http://127.0.0.1:8787 installDebug
```

The HTTP localhost exception exists only in debug builds. Release builds still require an
HTTPS backend. The local server binds to loopback, so it is not exposed to other devices on
the Wi-Fi network.

Confirm the backend is reachable before recording:

```sh
curl http://127.0.0.1:8787/health
```

Then record synthetic Hindi and Hinglish conversations in the app, confirm online-processing
consent, and compare Saaras against Whisper using the same recording. Keep the backend terminal
open until the transcript finishes.

### Complete local setup checklist

Run these steps from the repository root. They configure a development connection only; the
Sarvam key remains in macOS Keychain and must never be placed in the Android app.

1. Confirm Android Studio's SDK location in the untracked `local.properties` file. A typical
   macOS configuration is:

   ```properties
   sdk.dir=/Users/your-name/Library/Android/sdk
   carelipik.transcriptionBackendUrl=http://127.0.0.1:8787
   ```

2. Store or replace the Sarvam key:

   ```sh
   ./tools/local_transcription_backend/store_sarvam_key.sh
   ```

3. Start one backend instance and keep its terminal open:

   ```sh
   ./tools/local_transcription_backend/run.sh
   ```

4. In another terminal, verify the server itself:

   ```sh
   curl http://127.0.0.1:8787/health
   ```

   The expected response is `{"status": "ok"}`. This proves that the Mac backend is running;
   it does not yet prove that Android can reach it.

5. Locate ADB. If `adb` is not on `PATH`, use the Android SDK executable directly:

   ```sh
   "$HOME/Library/Android/sdk/platform-tools/adb" devices
   ```

   To make that command available in future zsh sessions:

   ```sh
   echo 'export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"' >> ~/.zshrc
   source ~/.zshrc
   ```

6. Forward the Android device's port `8787` to the Mac backend:

   ```sh
   adb reverse tcp:8787 tcp:8787
   adb reverse --list
   ```

   `127.0.0.1` inside Android normally refers to Android itself. `adb reverse` is what makes
   the app's `http://127.0.0.1:8787` URL reach the server on the Mac. Port reversal can be lost
   after disconnecting a phone, restarting an emulator, or restarting ADB, so repeat this step
   when connectivity unexpectedly stops.

   If `adb devices` shows more than one target, specify the intended serial:

   ```sh
   adb -s DEVICE_SERIAL reverse tcp:8787 tcp:8787
   adb -s DEVICE_SERIAL reverse --list
   ```

7. Rebuild and install the app after changing the backend URL because Gradle embeds it in
   `BuildConfig` at build time:

   ```sh
   ./gradlew installDebug
   ```

8. In the recording screen, explicitly choose **Hindi** or **Hinglish** and select Saaras.
   Doctor-profile language preferences only control recommendations; they do not select the
   current recording's language. On the transcript screen, verify that `Selected mode` shows
   the intended value. Hinglish sends `mode=codemix`; Hindi sends `mode=transcribe`.

9. Confirm online-processing consent, submit a synthetic recording, and leave the backend
   running until polling returns the completed transcript.

### Troubleshooting

#### `OSError: [Errno 48] Address already in use`

Port `8787` is already owned by another process, commonly an earlier CareLipik backend that is
still healthy. Do not start a second server before checking it:

```sh
curl http://127.0.0.1:8787/health
lsof -nP -iTCP:8787 -sTCP:LISTEN
```

If health returns `ok`, keep using the existing process. If the listener is stale, stop only the
specific PID reported by `lsof`, then restart the backend:

```sh
kill PID_FROM_LSOF
./tools/local_transcription_backend/run.sh
```

#### `zsh: command not found: adb`

ADB is installed with Android SDK Platform Tools but is not on the shell `PATH`. Use the full
SDK path shown in the checklist or add `platform-tools` to `~/.zshrc`. If the executable is
actually absent, install **Android SDK Platform-Tools** through Android Studio's SDK Manager.

#### Backend health works but the app says transcription is unavailable

Check each boundary in order:

```sh
curl http://127.0.0.1:8787/health
adb devices
adb reverse --list
```

The device must be listed as `device`, not `offline` or `unauthorized`, and the reverse list must
contain the `tcp:8787` mapping. Accept the USB-debugging prompt on a physical phone if needed.
Then reinstall the debug build if `local.properties` changed. A successful Mac health check by
itself does not establish Android-to-Mac connectivity.

#### The transcript screen says Hindi after choosing Hinglish elsewhere

Choose Hinglish on the current consultation's recording screen. Selecting Hinglish in the doctor
profile does not set the active recording mode. The transcript notice is the source of truth for
the language that was sent.

#### Key or provider errors

Run `store_sarvam_key.sh` again to replace the Keychain entry, restart the backend so it reloads
the key, and watch the backend terminal during submission. Never print, paste into source code,
or commit the key. HTTP `401`/`403` responses point to the credential/account; port errors and
connection failures occur before Sarvam authentication.

#### Recovery sequence

When the setup previously worked and then stops, use this short sequence:

```sh
curl http://127.0.0.1:8787/health
adb devices
adb reverse tcp:8787 tcp:8787
adb reverse --list
```

Restart the backend only when its health check fails. Rebuild the app only when its configured
backend URL or Android code has changed.
