"""Capture non-secret canonical environment facts on RunPod."""

import argparse
import json
import os
import platform
import shutil
import subprocess
from pathlib import Path


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    import torch
    import transformers

    config = json.loads(args.config.read_text(encoding="utf-8"))
    disk = shutil.disk_usage(Path.cwd())
    result = {
        "formatVersion": "1.0",
        "python": platform.python_version(),
        "platform": platform.platform(),
        "torch": torch.__version__,
        "transformers": transformers.__version__,
        "cudaRuntime": torch.version.cuda,
        "cudaAvailable": torch.cuda.is_available(),
        "bf16Supported": torch.cuda.is_available() and torch.cuda.is_bf16_supported(),
        "gpuName": torch.cuda.get_device_name(0) if torch.cuda.is_available() else None,
        "gpuMemoryBytes": torch.cuda.get_device_properties(0).total_memory if torch.cuda.is_available() else None,
        "diskTotalBytes": disk.total,
        "diskFreeBytes": disk.free,
        "gitCommit": subprocess.run(["git", "rev-parse", "HEAD"], check=True, capture_output=True, text=True).stdout.strip(),
        "config": config,
        "secretPresence": {"HF_TOKEN": bool(os.environ.get("HF_TOKEN"))},
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
