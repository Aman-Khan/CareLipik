"""Validate exported Apollo quantization against FP32 using synthetic text only."""
import json
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort
from transformers import AutoTokenizer


def verify(directory):
    directory = Path(directory)
    tokenizer = AutoTokenizer.from_pretrained(directory / "fp32", local_files_only=True)
    labels = json.loads((directory / "config.json").read_text())["id2label"]
    reference = ort.InferenceSession(str(directory / "fp32/model.onnx"))
    candidate = ort.InferenceSession(str(directory / "model.int8.onnx"))
    samples = [
        ("Patient takes metformin 500 mg twice daily and has tuberculosis.",
         {"metformin": "MEDICATION", "tuberculosis": "DISEASE_DISORDER"}),
        ("Patient has asthma and takes paracetamol for fever.",
         {"asthma": "DISEASE_DISORDER", "paracetamol": "MEDICATION"}),
    ]
    correct = total = 0
    for text, expected in samples:
        encoded = tokenizer(text, return_tensors="np", return_offsets_mapping=True)
        offsets = encoded.pop("offset_mapping")[0]
        outputs = []
        for session in (reference, candidate):
            inputs = {name: encoded[name] for name in (item.name for item in session.get_inputs())}
            outputs.append(session.run(None, inputs)[0][0].argmax(axis=-1))
        meaningful = np.array([end > start for start, end in offsets])
        correct += int(np.sum((outputs[0] == outputs[1]) & meaningful))
        total += int(meaningful.sum())
        for term, category in expected.items():
            start = text.index(term)
            indices = [i for i, (a, b) in enumerate(offsets) if a < start + len(term) and b > start]
            assert any(category in labels[str(int(outputs[1][i]))] for i in indices), (
                f"Quantized model failed synthetic entity check: {term}"
            )
    agreement = correct / total
    assert agreement >= 0.85, f"INT8/FP32 label agreement too low: {agreement:.1%}"
    print(f"Synthetic model validation passed; INT8/FP32 label agreement: {agreement:.1%}")


if __name__ == "__main__":
    verify(sys.argv[1])
