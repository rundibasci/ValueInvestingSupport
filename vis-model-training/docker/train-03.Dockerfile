FROM pytorch/pytorch:2.7.1-cuda12.8-cudnn9-runtime@sha256:c16f4c749e2d9e96878875cdf6cc45cddda1d1a36fddd371dd6f2360f1b6e2a2

ARG DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
    && apt-get install --yes --no-install-recommends git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace/ValueInvestingSupport/vis-model-training
COPY requirements-runpod.lock /tmp/requirements-runpod.lock
RUN python -m pip install --no-cache-dir --requirement /tmp/requirements-runpod.lock

ENV HF_HOME=/workspace/.huggingface \
    CUBLAS_WORKSPACE_CONFIG=:4096:8 \
    TORCHDYNAMO_DISABLE=1 \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    TOKENIZERS_PARALLELISM=false

CMD ["sleep", "infinity"]
