# Offline doctor/patient speaker detection

CareLipik uses the existing Sherpa-ONNX Android runtime for offline speaker diarization.
It combines a Pyannote segmentation model with a NeMo TitaNet Small embedding model and fixes
the expected cluster count at two for a doctor/patient consultation.

APK builds automatically prepare these models with `:app:prepareOfflineModels`. The same
`nemo_en_titanet_small.onnx` asset is used for diarization and doctor voice matching.
Alternatively, install the local model files manually from the repository root:

```sh
./tools/offline_speaker_diarization/setup.sh
```

The speaker model download is approximately 47 MB. Models are placed under
`app/src/main/assets/models/`, which is already ignored by Git and must never be committed.

Check installation without downloading:

```sh
./tools/offline_speaker_diarization/setup.sh --check
```

When models are installed, Whisper and MedASR run diarization first and transcribe each
detected speaker turn locally. If the models are missing or diarization fails, transcription
continues in the existing unsegmented mode rather than blocking the consultation.

Speaker diarization initially assigns anonymous `Speaker 1` and `Speaker 2` labels. When a doctor
voice sample is enrolled, CareLipik compares local speaker embeddings and assigns Doctor/Patient
roles only when both similarity and confidence-margin gates pass. Uncertain matches remain
unassigned, and every automatic role can still be corrected manually.
