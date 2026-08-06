"""Lazy-loading Hugging Face backend for the explicitly approved RunPod smoke."""

import time
from typing import Any, Dict, Optional

from .errors import TeacherConfigurationError


class HuggingFaceBackend:
    """One pinned checkpoint used through separate teacher and critic calls."""

    def __init__(self, model_id: str, revision: str, tokenizer_revision: str, *, provider: str = "RunPod"):
        if not revision or not tokenizer_revision:
            raise TeacherConfigurationError("Immutable model and tokenizer revisions are required")
        self.model_id, self.revision, self.tokenizer_revision, self.provider = model_id, revision, tokenizer_revision, provider
        self._model = self._processor = None

    def _load(self):
        if self._model is not None:
            return
        try:
            import torch
            from transformers import AutoProcessor, Gemma3ForConditionalGeneration
        except ImportError as error:
            raise TeacherConfigurationError("RunPod inference dependencies are not installed") from error
        self._processor = AutoProcessor.from_pretrained(self.model_id, revision=self.tokenizer_revision)
        self._model = Gemma3ForConditionalGeneration.from_pretrained(
            self.model_id, revision=self.revision, torch_dtype=torch.bfloat16, device_map="auto", attn_implementation="eager"
        ).eval()

    @staticmethod
    def _processor_messages(messages):
        return [
            {**message, "content": [{"type": "text", "text": message["content"]}]}
            if isinstance(message.get("content"), str) else message
            for message in messages
        ]

    def _infer(self, messages, *, max_new_tokens: int, seed: Optional[int] = None):
        self._load()
        import torch
        if seed is not None:
            torch.manual_seed(seed)
            if torch.cuda.is_available():
                torch.cuda.manual_seed_all(seed)
        started = time.perf_counter()
        processor_messages = self._processor_messages(messages)
        inputs = self._processor.apply_chat_template(processor_messages, add_generation_prompt=True, tokenize=True, return_dict=True, return_tensors="pt")
        inputs = {key: value.to(self._model.device) for key, value in inputs.items()}
        input_tokens = int(inputs["input_ids"].shape[-1])
        with torch.inference_mode():
            generated = self._model.generate(**inputs, max_new_tokens=max_new_tokens, do_sample=True, temperature=0.2, top_p=0.9)
        continuation = generated[0, input_tokens:]
        text = self._processor.decode(continuation, skip_special_tokens=True)
        return {"text": text, "inputTokens": input_tokens, "outputTokens": int(continuation.shape[-1]), "latencyMs": (time.perf_counter() - started) * 1000}

    def generate(self, messages, *, seed: int, max_new_tokens: int) -> Dict[str, Any]:
        return self._infer(messages, seed=seed, max_new_tokens=max_new_tokens)

    def review(self, messages, *, max_new_tokens: int) -> Dict[str, Any]:
        return self._infer(messages, seed=20260806, max_new_tokens=max_new_tokens)

    def manifest(self) -> Dict[str, Any]:
        return {"provider": self.provider, "model": self.model_id, "revision": self.revision, "tokenizerRevision": self.tokenizer_revision}
