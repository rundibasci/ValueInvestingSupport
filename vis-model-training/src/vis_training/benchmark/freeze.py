"""Hash and verify immutable inputs before canonical inference."""

import hashlib
from pathlib import Path
from typing import Any, Dict, Iterable

from .io import read_json, write_json

FORMAT_VERSION = "1.0"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with Path(path).open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_freeze_manifest(
    files: Iterable[Path], *, root: Path, output: Path
) -> Dict[str, Any]:
    root = Path(root).resolve()
    entries = []
    for candidate in sorted((Path(item).resolve() for item in files), key=str):
        try:
            relative = candidate.relative_to(root)
        except ValueError as error:
            raise ValueError(f"Freeze input is outside root: {candidate}") from error
        entries.append(
            {
                "path": relative.as_posix(),
                "sha256": sha256_file(candidate),
                "bytes": candidate.stat().st_size,
            }
        )
    manifest = {"formatVersion": FORMAT_VERSION, "algorithm": "sha256", "files": entries}
    write_json(output, manifest)
    return manifest


def verify_freeze_manifest(manifest_path: Path, *, root: Path) -> list:
    manifest = read_json(manifest_path)
    if manifest.get("formatVersion") != FORMAT_VERSION or manifest.get("algorithm") != "sha256":
        return ["unsupported freeze manifest format"]
    root = Path(root).resolve()
    failures = []
    for entry in manifest.get("files", []):
        if not isinstance(entry, dict) or not isinstance(entry.get("path"), str):
            failures.append("malformed freeze manifest entry")
            continue
        candidate = (root / entry["path"]).resolve()
        try:
            candidate.relative_to(root)
        except ValueError:
            failures.append(f"path escapes root: {entry['path']}")
            continue
        if not candidate.is_file():
            failures.append(f"missing: {entry['path']}")
        elif sha256_file(candidate) != entry.get("sha256"):
            failures.append(f"hash mismatch: {entry['path']}")
    return failures
