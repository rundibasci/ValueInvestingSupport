# Local and Reference Training Environment

- Assessment date: 2026-08-01
- Phase: TRAIN-00

## Observed Local Host

| Item | Observed value |
|---|---|
| Device | MacBook Air (`Mac16,12`) |
| CPU/SoC | Apple M4, 10 CPU cores (4 performance, 6 efficiency) |
| GPU | Integrated Apple M4 GPU, 10 cores, Metal supported |
| Unified memory | 24 GB |
| Architecture | ARM64 |
| Operating system | macOS 15.7.7 (build 24G720) |
| Free workspace-volume capacity | Approximately 317 GiB at assessment time |
| NVIDIA GPU/driver | Not present/detected |
| CUDA visible to PyTorch | Not applicable; no NVIDIA/CUDA environment detected |
| System Python | 3.9.6 |

Serial numbers, hardware UUIDs, provisioning identifiers, usernames, and host-specific identifiers are deliberately excluded from this document.

## Local-Host Classification

The local host is approved for:

- documentation and repository development;
- schema and dataset validation;
- scenario generation and evaluation tooling;
- unit tests and small deterministic experiments;
- optional bounded Apple Metal inference only after a separate reproducibility check.

It is not the reference environment for the planned Hugging Face/bitsandbytes CUDA QLoRA workflow. TRAIN-00 does not claim that the 4B QLoRA training or 27B teacher inference will fit or run correctly through Apple Metal.

## External Single-GPU Reference Profile

Planning baseline for TRAIN-07:

| Resource | Minimum planning assumption | Recommended starting profile |
|---|---:|---:|
| NVIDIA GPU | Recent CUDA-capable GPU | Data-center or workstation GPU supported by the chosen PyTorch/bitsandbytes versions |
| VRAM | 16 GiB for an initial 4B QLoRA smoke attempt | 24 GiB or more for safer 4B iteration headroom |
| System RAM | 32 GiB | 64 GiB |
| Free disk | 50 GiB | 100 GiB or more |
| OS | Supported 64-bit Linux | Current supported Ubuntu LTS or equivalent |
| Software | Version-pinned NVIDIA driver, CUDA runtime as required by pinned PyTorch, Python environment, Transformers, TRL, PEFT, Accelerate, bitsandbytes | Same, captured by lock file/container and run manifest |

These are planning assumptions, not measured guarantees. TRAIN-07 must record the actual GPU, VRAM peak, system RAM, disk use, driver, CUDA version visible to PyTorch, package lock, sequence length, batch/accumulation settings, and successful adapter save/reload.

The 27B teacher may require materially more memory than the student QLoRA run. TRAIN-05 must select an appropriately sized external inference configuration or use an approved hosted endpoint only after separate terms, privacy, retention, and cost review.

## Reproducibility Rules

- Never install or silently modify system-wide ML dependencies as part of a training run.
- Pin model and tokenizer revisions, Python dependencies, CUDA/PyTorch compatibility, prompt version, and dataset hash.
- Store secrets only through approved environment/secret mechanisms, never in notebooks, shell history committed to Git, configs, reports, or manifests.
- Do not commit model weights, caches, checkpoints, adapters, raw candidate corpora, or experiment trackers containing sensitive data.
- TRAIN-07 owns the smoke-test GO/NO-GO for actual QLoRA feasibility.

