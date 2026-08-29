# Offline doctor/patient speaker detection

CareLipik uses the existing Sherpa-ONNX Android runtime for offline speaker diarization.
It combines a Pyannote segmentation model with a 3D-Speaker embedding model and fixes
the expected cluster count at two for a doctor/patient consultation.

Install the local model files from the repository root:

```sh
./tools/offline_speaker_diarization/setup.sh
```

The download is approximately 47 MB. Models are placed under
`app/src/main/assets/models/`, which is already ignored by Git and must never be committed.

Check installation without downloading:

```sh
./tools/offline_speaker_diarization/setup.sh --check
```

When models are installed, Whisper and MedASR run diarization first and transcribe each
detected speaker turn locally. If the models are missing or diarization fails, transcription
continues in the existing unsegmented mode rather than blocking the consultation.

Speaker diarization assigns anonymous `Speaker 1` and `Speaker 2` labels. It does not know
which voice belongs to the doctor until the doctor confirms the roles. Voice enrollment and
automatic doctor matching are a separate later component.
