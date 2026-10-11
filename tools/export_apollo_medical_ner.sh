#!/bin/sh
set -eu

# Exports the public Apollo/Medical-NER checkpoint as an app-private Android bundle.
# The downloaded checkpoint and generated ONNX files belong under .local-models/ and are ignored.

output_dir="${1:-.local-models/apollo-medical-ner}"
model_id="blaze999/Medical-NER"

command -v python3 >/dev/null || { echo "python3 is required." >&2; exit 1; }
python_bin="python3"
optimum_cli="optimum-cli"

mkdir -p "$output_dir/fp32"
if command -v uv >/dev/null; then
    # macOS's Command Line Tools Python is commonly 3.9 with an old pip resolver. Use an
    # isolated, current environment so it cannot select Python-2-era NetworkX releases.
    virtual_env="$output_dir/.venv"
    uv venv --python 3.13 "$virtual_env"
    uv pip install --python "$virtual_env/bin/python" "optimum[onnxruntime]" transformers
    python_bin="$virtual_env/bin/python"
    optimum_cli="$virtual_env/bin/optimum-cli"
elif ! command -v optimum-cli >/dev/null; then
    echo "Install uv (recommended) or upgrade pip, then install: optimum[onnxruntime] transformers" >&2
    exit 1
fi

"$optimum_cli" export onnx --model "$model_id" --task token-classification "$output_dir/fp32"

MODEL_INPUT="$output_dir/fp32/model.onnx"
[ -f "$MODEL_INPUT" ] || { echo "Expected ONNX export at $MODEL_INPUT" >&2; exit 1; }
"$python_bin" - "$MODEL_INPUT" "$output_dir/model.int8.onnx" <<'PY'
import sys
from onnxruntime.quantization import QuantType, quantize_dynamic

quantize_dynamic(sys.argv[1], sys.argv[2], weight_type=QuantType.QInt8,
                 per_channel=True, reduce_range=True, op_types_to_quantize=["MatMul"])
PY

for required in tokenizer.json config.json; do
    [ -f "$output_dir/fp32/$required" ] || {
        echo "Exporter did not produce $required." >&2
        exit 1
    }
    cp "$output_dir/fp32/$required" "$output_dir/$required"
done

script_directory=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
"$python_bin" "$script_directory/verify_apollo_medical_ner.py" "$output_dir"

echo "Apollo Medical-NER bundle is ready in $output_dir"
echo "Review the checkpoint licence and run tools/install_apollo_medical_ner.sh to install it privately."
