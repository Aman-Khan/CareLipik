# Offline Whisper Turbo

Whisper Turbo is an optional multilingual offline transcription engine. Whisper Small remains
available and is not replaced.

The INT8 Sherpa-ONNX export requires approximately 1.04 GB before APK packaging:

- `turbo-encoder.int8.onnx`: approximately 675 MB;
- `turbo-decoder.int8.onnx`: approximately 361 MB;
- `turbo-tokens.txt`: approximately 817 KB.

Install the ignored local assets:

```sh
./tools/offline_whisper_turbo/setup.sh
./tools/offline_whisper_turbo/setup.sh --check
./gradlew assembleDebug
```

Then reinstall the APK and choose **Multilingual (Whisper Turbo)** on the recording screen. The
model runs through Sherpa-ONNX on device and uses the same offline diarization and doctor-voice
matching pipeline as Whisper Small. No audio is uploaded.

The model files are intentionally excluded from Git. Do not commit or push them.
