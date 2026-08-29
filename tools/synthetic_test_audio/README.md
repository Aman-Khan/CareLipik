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
