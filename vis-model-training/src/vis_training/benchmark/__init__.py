"""Reproducible base-model benchmarking for TRAIN-03."""

from .freeze import build_freeze_manifest, verify_freeze_manifest
from .metrics import compute_metrics
from .runner import BenchmarkRunner, GenerationBackend

__all__ = [
    "BenchmarkRunner",
    "GenerationBackend",
    "build_freeze_manifest",
    "compute_metrics",
    "verify_freeze_manifest",
]
