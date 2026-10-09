# Synthetic consultation test audio

This developer-only tool creates reusable two-speaker English, Hindi and Hinglish
consultations with Sarvam Bulbul v3. Every case is synthetic and includes an exact reference
transcript. Generated audio is mono 16 kHz, 16-bit PCM WAV so it can be imported into
CareLipik and tested with MedASR, Whisper or Saaras.

The tool reads the existing local-test Sarvam key from macOS Keychain. It never writes the
key into the repository.

## Generate all cases

From the repository root:

```sh
./tools/synthetic_test_audio/generate.sh
```

This calls Sarvam TTS once per conversation turn and consumes API credits. Generated files
are written to `tools/synthetic_test_audio/output/`, which is ignored by Git.

Generate only one case:

```sh
./tools/synthetic_test_audio/generate.sh --case hinglish-dry-cough
```

Available case ids are in `cases.json`:

- `english-dry-cough`
- `hindi-dry-cough`
- `hinglish-dry-cough`

Each WAV has a matching JSON file with speaker roles, exact turn text and a combined reference
transcript. The voices are deliberately different so online speaker diarization can be tested.

## Run generator tests without an API call

```sh
/usr/bin/python3 tools/synthetic_test_audio/test_generate.py
```

Do not add real patient text, recordings or identifiers to these fixtures.

## Generate offline stress-test cases

On macOS, generate deterministic privacy-safe fixtures using built-in system voices. This does
not need an API key or consume Sarvam credits:

```sh
./tools/synthetic_test_audio/generate_stress.sh
```

The ignored `output/stress/` directory receives WAV and JSON pairs for:

- long pauses between two speakers;
- three speakers with an overlapping caregiver interruption;
- four speakers with short hand-offs;
- steady background/fan noise;
- clipped speech;
- distant/quiet speech;
- a mostly-silent recording with one short utterance.

Each JSON file contains expected speaker events, approximate timestamps, and the behavior that
should be verified. System TTS timing and acoustic clustering vary by macOS/phone model, so the
speaker-count expectations are deliberately approximate for stress cases.
