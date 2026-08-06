"""Configuration, governance, prompt, and manifest validation."""

import re
from pathlib import Path
from typing import Any, Dict

from .errors import TeacherConfigurationError
from .io import read_object, sha256_file


REQUIRED_LICENSE_STATUS = "ENGINEERING_EVALUATION_ONLY"
IMMUTABLE_REVISION = re.compile(r"^[a-f0-9]{40}$")


def load_local_config(root: Path, config_path: Path) -> Dict[str, Any]:
    root = Path(root)
    config_path = Path(config_path)
    if not config_path.is_absolute():
        config_path = root / config_path
    config = read_object(config_path)
    if config.get("formatVersion") != "1.0" or config.get("candidateCountPerScenario") != 2:
        raise TeacherConfigurationError("Teacher config must use format 1.0 and two candidates per scenario")
    teacher = config.get("teacher", {})
    critic = config.get("critic", {})
    if teacher.get("modelId") != "google/gemma-3-27b-it" or critic.get("modelId") != teacher.get("modelId"):
        raise TeacherConfigurationError("Teacher and critic must use the approved Gemma 3 27B checkpoint")
    revisions = (teacher.get("modelRevision"), teacher.get("tokenizerRevision"), critic.get("modelRevision"))
    if any(value is not None and not IMMUTABLE_REVISION.fullmatch(value) for value in revisions):
        raise TeacherConfigurationError("Model and tokenizer revisions must be immutable 40-character Git SHAs")
    if critic.get("sameCheckpointRequired") and critic.get("modelRevision") != teacher.get("modelRevision"):
        raise TeacherConfigurationError("Teacher and critic revisions must match")
    if not config.get("retention", {}).get("bulkGenerationRequiresExplicitApproval"):
        raise TeacherConfigurationError("Bulk generation approval guard is required")
    license_path = root / config["licenseReviewPath"]
    license_review = read_object(license_path)
    if license_review.get("licenseReviewId") != config.get("licenseReviewId") or license_review.get("status") != REQUIRED_LICENSE_STATUS:
        raise TeacherConfigurationError("License review is absent or incompatible")
    paths = {
        key: root / config[key]
        for key in ("teacherPromptPath", "criticPromptPath", "outputSchemaPath", "candidateSchemaPath", "criticSchemaPath")
    }
    for label, path in paths.items():
        if not path.is_file():
            raise TeacherConfigurationError(f"Configured artifact not found ({label}): {path}")
    return {"config": config, "licenseReview": license_review, "paths": paths}


def readiness(root: Path, config_path: Path) -> Dict[str, Any]:
    loaded = load_local_config(root, config_path)
    config = loaded["config"]
    teacher = config["teacher"]
    critic = config["critic"]
    blockers = []
    if not teacher.get("modelRevision") or not teacher.get("tokenizerRevision"):
        blockers.append("IMMUTABLE_TEACHER_REVISION_REQUIRED")
    if not critic.get("modelRevision"):
        blockers.append("IMMUTABLE_CRITIC_REVISION_REQUIRED")
    hashes = {label: sha256_file(path) for label, path in loaded["paths"].items()}
    return {
        "formatVersion": "1.0",
        "localToolingReady": True,
        "smokeReady": not blockers,
        "smokeBlockers": blockers,
        "licenseReviewId": config["licenseReviewId"],
        "artifactSha256": dict(sorted(hashes.items())),
    }


def build_manifest(root: Path, config_path: Path, backend_manifest: Dict[str, Any], *, run_id: str, hardware_profile: str) -> Dict[str, Any]:
    loaded = load_local_config(root, config_path)
    config = loaded["config"]
    return {
        "formatVersion": "1.0",
        "runId": run_id,
        "hardwareProfile": hardware_profile,
        "backend": backend_manifest,
        "candidateCountPerScenario": config["candidateCountPerScenario"],
        "generationParameters": config["decoding"],
        "licenseReviewId": config["licenseReviewId"],
        "teacherPromptVersion": "teacher-prompt-v1",
        "criticPromptVersion": "critic-prompt-v1",
        "artifactSha256": {label: sha256_file(path) for label, path in sorted(loaded["paths"].items())},
        "automaticTrainingPromotion": False,
    }
