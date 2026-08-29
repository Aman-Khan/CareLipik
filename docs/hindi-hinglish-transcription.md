# Hindi and Hinglish transcription

CareLipik uses a provider adapter so the recording and transcript UI are not tied to one
speech model.

## Engine routing

- English defaults to the offline Google MedASR model.
- Hindi defaults to Saaras v3 Batch with `language_code=hi-IN` and `mode=transcribe`.
- Hinglish defaults to Saaras v3 Batch with `language_code=hi-IN` and `mode=codemix`.
- Whisper Small remains available as the offline Hindi/Hinglish fallback.

Saaras Batch is used because consultations are commonly longer than the 30-second REST
limit and speaker diarization is a Batch-only feature. The request asks for two speakers.
The result deliberately labels them `Speaker 1` and `Speaker 2`; diarization does not prove
which voice belongs to the doctor or patient.

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
num_speakers=2
file=<16 kHz mono PCM WAV>
```

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
