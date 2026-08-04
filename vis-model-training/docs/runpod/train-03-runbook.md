# TRAIN-03 RunPod Secure Cloud Runbook

This runbook never creates paid resources automatically. The operator confirms the displayed RunPod price before deployment and records the estimate in the run notes.

## 1. Prerequisites

1. In Hugging Face, sign in and accept the Gemma license for `google/gemma-3-4b-it`.
2. Create a fine-grained, read-only Hugging Face token limited to model downloads where supported.
3. In RunPod, create a Secret named `HF_TOKEN`; never paste the token into repository files, Docker build arguments, notebook cells, or shell commands.
4. Ensure the approved commit contains a valid freeze manifest and a clean `git status`.

## 2. Pod selection

Create a Pod manually with:

- Cloud tier: Secure Cloud
- GPU: one NVIDIA L4, 24 GB
- System memory: 48 GB or more
- Container disk plus persistent volume: at least 100 GB total usable space
- Image: either the TRAIN-03 image built from `docker/train-03.Dockerfile` or its pinned official base `pytorch/pytorch:2.7.1-cuda12.8-cudnn9-runtime@sha256:c16f4c749e2d9e96878875cdf6cc45cddda1d1a36fddd371dd6f2360f1b6e2a2`
- Mounted secret: `HF_TOKEN`
- Exposed services: SSH only when required; do not expose Jupyter publicly
- Container start command: `sleep infinity` when using the pinned official base image; the custom TRAIN-03 image already defines this command

Record region, displayed hourly price, storage price and the non-secret Pod identifier. Do not start if the selected GPU, cloud tier or price differs from the approved values.

## 3. Checkout and verification

Inside the Pod, clone through an approved read-only mechanism and checkout the exact commit. Never embed a GitHub token in the clone URL.

```bash
cd /workspace/ValueInvestingSupport
git status --short --branch
git rev-parse HEAD
cd vis-model-training
python -m pip install --requirement requirements-runpod.lock
python -m pip install --no-deps --no-build-isolation .
python -m vis_training.benchmark.cli verify-freeze \
  --root . \
  --manifest datasets/benchmark/base-benchmark-v1.freeze.json
nvidia-smi
python scripts/capture_runpod_environment.py \
  --config config/benchmark-v1.json \
  --output outputs/train-03/environment.json
```

Stop if freeze verification, CUDA visibility, BF16 support, disk availability or model access fails.

## 4. Non-canonical smoke test

```bash
GEMMA_MODEL_REVISION=093f9f388b31de276ce2de164bdc2081324b9767 \
python -m vis_training.benchmark.cli run \
  --dataset datasets/benchmark/base-benchmark-v1.jsonl \
  --output outputs/train-03/smoke-results.jsonl \
  --manifest datasets/benchmark/base-benchmark-v1.freeze.json \
  --limit 3
```

Inspect completion, peak VRAM, latency, output parsing and free disk. Smoke outputs are non-canonical and must not be copied into the canonical result path.

## 5. Canonical run

Start only after explicit user approval of the observed smoke-test cost and behavior.

```bash
GEMMA_MODEL_REVISION=093f9f388b31de276ce2de164bdc2081324b9767 \
python -m vis_training.benchmark.cli run \
  --dataset datasets/benchmark/base-benchmark-v1.jsonl \
  --output outputs/train-03/canonical-results.jsonl \
  --manifest datasets/benchmark/base-benchmark-v1.freeze.json

python -m vis_training.benchmark.cli metrics \
  --results outputs/train-03/canonical-results.jsonl \
  --dataset datasets/benchmark/base-benchmark-v1.jsonl \
  --output-schema schemas/thesis-output.schema.json \
  --output outputs/train-03/metrics.json
```

Do not retry individual cases into the canonical file. Resume is allowed only for missing IDs and preserves every completed first output.

## 6. Export and cleanup

1. Create SHA-256 checksums for results, manifest, environment and metrics.
2. Download artifacts to the local encrypted workspace and verify all checksums.
3. Confirm that all expected example IDs are present exactly once.
4. Record elapsed time and RunPod cost without payment or account details.
5. Stop and delete the Pod, network volume and chargeable IP after local verification, unless the user explicitly approves retention.
6. Confirm in the RunPod console that no TRAIN-03 resource continues accruing charges.
