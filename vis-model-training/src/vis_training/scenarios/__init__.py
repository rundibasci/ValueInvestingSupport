"""Deterministic TRAIN-04 financial scenario generation."""

from .generator import generate_scenarios
from .validator import validate_records

__all__ = ["generate_scenarios", "validate_records"]
