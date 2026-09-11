#!/usr/bin/env python3
"""Export focus-portrait 1-NN bank → app/src/main/assets/vision/focus_portrait_bank.json

Usage:
  ./scripts/export_focus_portrait_bank.py
  ./scripts/export_focus_portrait_bank.py --dataset logs/2026-08-10/focus_portrait_dataset
"""

from __future__ import annotations

import argparse
import importlib.util
import json
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_OUT = ROOT / "app/src/main/assets/vision/focus_portrait_bank.json"


def load_watcher():
    spec = importlib.util.spec_from_file_location(
        "watch_focus_portrait",
        ROOT / "scripts/watch_focus_portrait.py",
    )
    mod = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(mod)
    return mod


def find_dataset() -> Path:
    logs = ROOT / "logs"
    for c in sorted(logs.glob("*/focus_portrait_dataset"), reverse=True):
        if (c / "labels" / "manifest.csv").exists():
            return c
    raise SystemExit(f"No focus_portrait_dataset under {logs}")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--dataset", type=Path, default=None)
    ap.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = ap.parse_args()

    w = load_watcher()
    ds = args.dataset or find_dataset()
    bank, scale = w.load_training(ds)
    payload = {
        "version": 1,
        "ref_w": 1280,
        "ref_h": 720,
        "face": [w.FACE_L, w.FACE_T, w.FACE_R, w.FACE_B],
        "scale": [float(x) for x in scale],
        "samples": [
            {"label": lab, "feat": [float(x) for x in feat], "src": name}
            for lab, feat, name in bank
        ],
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(payload, indent=2) + "\n")
    counts: dict[str, int] = defaultdict(int)
    for lab, _, _ in bank:
        counts[lab] += 1
    print(f"wrote {args.out} n={len(bank)} {dict(counts)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
