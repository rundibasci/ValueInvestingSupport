"""Lazy Hugging Face backend used only in the pinned RunPod environment."""

import os
import platform
from typing import Any, Dict

from .runner import GenerationBackend


class HuggingFaceBackend(GenerationBackend):
    def __init__(self, model_id: str, revision: str, *, device: str = "cuda", seed: int = 20260804) -> None:
        if not revision or revision in {"main", "latest"}:
            raise ValueError("An immutable model revision is required")
        os.environ.setdefault("TORCHDYNAMO_DISABLE", "1")
        try:
            import torch
            import transformers
            from transformers import AutoProcessor, Gemma3ForConditionalGeneration
        except ImportError as error:
            raise RuntimeError("Install requirements-runpod.lock in the RunPod image") from error
        if device != "cuda" or not torch.cuda.is_available():
            raise RuntimeError("Canonical TRAIN-03 inference requires CUDA")
        token = os.environ.get("HF_TOKEN")
        if not token:
            raise RuntimeError("HF_TOKEN is required")
        self._torch = torch
        self._transformers_version = transformers.__version__
        self.model_id = model_id
        self.revision = revision
        self.seed = seed
        transformers.set_seed(seed)
        torch.manual_seed(seed)
        torch.cuda.manual_seed_all(seed)
        torch.use_deterministic_algorithms(True)
        torch.backends.cudnn.benchmark = False
        torch.backends.cudnn.deterministic = True
        self.processor = AutoProcessor.from_pretrained(
            model_id, revision=revision, token=token, use_fast=False
        )
        self.model = Gemma3ForConditionalGeneration.from_pretrained(
            model_id,
            revision=revision,
            token=token,
            torch_dtype=torch.bfloat16,
            device_map=device,
        ).eval()

    def generate(self, messages: list, *, max_new_tokens: int) -> Dict[str, Any]:
        torch = self._torch
        formatted = []
        for message in messages:
            formatted.append(
                {"role": message["role"], "content": [{"type": "text", "text": message["content"]}]}
            )
        inputs = self.processor.apply_chat_template(
            formatted,
            add_generation_prompt=True,
            tokenize=True,
            return_dict=True,
            return_tensors="pt",
        ).to(self.model.device)
        input_length = inputs["input_ids"].shape[-1]
        with torch.inference_mode():
            generated = self.model.generate(
                **inputs,
                do_sample=False,
                max_new_tokens=max_new_tokens,
                use_cache=True,
            )
        output_ids = generated[0][input_length:]
        return {
            "text": self.processor.decode(output_ids, skip_special_tokens=True).strip(),
            "inputTokens": int(input_length),
            "outputTokens": int(output_ids.shape[-1]),
        }

    def manifest(self) -> Dict[str, Any]:
        torch = self._torch
        properties = torch.cuda.get_device_properties(0)
        return {
            "modelId": self.model_id,
            "modelRevision": self.revision,
            "precision": "bfloat16",
            "seed": self.seed,
            "deterministicAlgorithms": True,
            "torchDynamoDisabled": os.environ.get("TORCHDYNAMO_DISABLE") == "1",
            "processorUseFast": False,
            "python": platform.python_version(),
            "torch": torch.__version__,
            "transformers": self._transformers_version,
            "cudaRuntime": torch.version.cuda,
            "gpuName": properties.name,
            "gpuMemoryBytes": properties.total_memory,
        }
