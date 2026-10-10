#!/bin/sh
set -eu

# Exports the public Apollo/Medical-NER checkpoint as an app-private Android bundle.
# The downloaded checkpoint and generated ONNX files belong under .local-models/ and are ignored.

output_dir="${1:-.local-models/apollo-medical-ner}"
model_id="blaze999/Medical-NER"

command -v python3 >/dev/null || { echo "python3 is required." >&2; exit 1; }
command -v optimum-cli >/dev/null || {
    echo "Install the exporter first: python3 -m pip install 'optimum[onnxruntime]' transformers" >&2
    exit 1
}

mkdir -p "$output_dir/fp32"
optimum-cli export onnx --model "$model_id" --task token-classification "$output_dir/fp32"

MODEL_INPUT="$output_dir/fp32/model.onnx"
[ -f "$MODEL_INPUT" ] || { echo "Expected ONNX export at $MODEL_INPUT" >&2; exit 1; }
python3 - "$MODEL_INPUT" "$output_dir/model.int8.onnx" <<'PY'
import sys
from onnxruntime.quantization import QuantType, quantize_dynamic

quantize_dynamic(sys.argv[1], sys.argv[2], weight_type=QuantType.QInt8)
PY

for required in tokenizer.json config.json; do
    [ -f "$output_dir/fp32/$required" ] || {
        echo "Exporter did not produce $required." >&2
        exit 1
    }
    cp "$output_dir/fp32/$required" "$output_dir/$required"
done

echo "Apollo Medical-NER bundle is ready in $output_dir"
echo "Review the checkpoint licence and run tools/install_apollo_medical_ner.sh to install it privately."
