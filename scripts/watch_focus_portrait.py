#!/usr/bin/env python3
"""Live focus-portrait watcher for BlueStacks.

Trains a tiny scaled 1-NN classifier from the labeled dataset (pj / boss / empty)
on the locked 32×32 face ROI, then polls the emulator and prints:

  empty
  Focus   # player portrait
  Boss    # boss emblem in the same slot

Usage:
  ./scripts/watch_focus_portrait.py emulator-5584
  ./scripts/watch_focus_portrait.py emulator-5584 --hz 4 --debug

Ctrl+C to stop. When you see a wrong line, note the time / what was on screen
so we can capture a corrective sample.
"""

from __future__ import annotations

import argparse
import csv
import math
import subprocess
import sys
import time
from collections import defaultdict
from pathlib import Path

import cv2
import numpy as np

ROOT = Path(__file__).resolve().parents[1]

# Locked inner-face train crop @ 1280×720 (user orange mark).
FACE_L, FACE_T, FACE_R, FACE_B = 532, 26, 564, 58

# Console labels (user-facing).
PRINT = {
    "empty": "empty",
    "pj": "Focus",
    "boss": "Boss",
}


def find_dataset_dir() -> Path:
    logs = ROOT / "logs"
    candidates = sorted(logs.glob("*/focus_portrait_dataset"), reverse=True)
    for c in candidates:
        if (c / "labels" / "manifest.csv").exists():
            return c
    raise SystemExit(f"No focus_portrait_dataset found under {logs}")


def screencap_bgr(device: str) -> np.ndarray:
    raw = subprocess.check_output(
        ["adb", "-s", device, "exec-out", "screencap", "-p"],
        stderr=subprocess.DEVNULL,
    )
    img = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise RuntimeError("failed to decode screencap")
    return img


def face_crop(bgr: np.ndarray) -> np.ndarray:
    h, w = bgr.shape[:2]
    # Scale ROI if not native 1280×720.
    sx = w / 1280.0
    sy = h / 720.0
    l = int(FACE_L * sx)
    t = int(FACE_T * sy)
    r = int(FACE_R * sx)
    b = int(FACE_B * sy)
    crop = bgr[t:b, l:r]
    if crop.size == 0:
        raise RuntimeError(f"empty face crop on frame {w}x{h}")
    # Normalize to 32×32 for stable features vs train crops.
    return cv2.resize(crop, (32, 32), interpolation=cv2.INTER_AREA)


def features(bgr32: np.ndarray) -> np.ndarray:
    """Face features: lum_var, edge, skin_frac, gold_frac.

    gold_frac is for golden-dragon / metallic boss faces that otherwise look
    like high-skin PJ portraits under lum_var-dominated L2.
    """
    gray = cv2.cvtColor(bgr32, cv2.COLOR_BGR2GRAY).astype(np.float64)
    ml = float(gray.mean())
    var = float(((gray - ml) ** 2).mean())

    gx = np.abs(np.diff(gray, axis=1)).mean()
    gy = np.abs(np.diff(gray, axis=0)).mean()
    edge = float(gx + gy)

    b, g, r = cv2.split(bgr32.astype(np.int16))
    skin = float(
        ((r > g) & (r >= b - 5) & (r >= 55) & (r <= 235) & ((r - b) > 12)).mean()
    )

    hsv = cv2.cvtColor(bgr32, cv2.COLOR_BGR2HSV)
    h, s, v = cv2.split(hsv)
    # OpenCV H: 0–179; gold/amber roughly 12–35, saturated + bright.
    gold = float(((h >= 12) & (h <= 35) & (s >= 70) & (v >= 90)).mean())
    return np.array([var, edge, skin, gold], dtype=np.float64)


def load_training(
    dataset: Path,
) -> tuple[list[tuple[str, np.ndarray, str]], np.ndarray]:
    """Return (1-NN bank, per-dim scale) from labeled raw frames.

    Bank entries are (label, feat, raw_name). Scale is 1/std so lum_var (~1e3)
    does not drown skin/gold (~0–1).
    """
    man = dataset / "labels" / "manifest.csv"
    rows = list(csv.DictReader(man.open()))
    samples: list[tuple[str, np.ndarray, str]] = []
    counts: dict[str, int] = defaultdict(int)
    for row in rows:
        lab = row["label"]
        if lab not in ("pj", "boss", "empty"):
            continue
        png = dataset / "raw" / row["raw_file"]
        if not png.exists():
            continue
        bgr = cv2.imread(str(png), cv2.IMREAD_COLOR)
        if bgr is None:
            continue
        samples.append((lab, features(face_crop(bgr)), row["raw_file"]))
        counts[lab] += 1

    for lab in ("pj", "boss", "empty"):
        print(f"[train] {lab}: n={counts.get(lab, 0)}", flush=True)
    if counts.get("empty", 0) < 1 or counts.get("pj", 0) < 1:
        raise SystemExit("Need at least labeled pj + empty samples in the dataset")
    if counts.get("boss", 0) < 1:
        print("[train] WARN: no boss samples — Boss class disabled", flush=True)

    mat = np.stack([v for _, v, _ in samples], axis=0)
    std = mat.std(axis=0)
    std = np.where(std < 1e-6, 1.0, std)
    scale = 1.0 / std
    print(
        f"[train] feat_scale var={scale[0]:.4f} edge={scale[1]:.4f} "
        f"skin={scale[2]:.4f} gold={scale[3]:.4f}",
        flush=True,
    )
    return samples, scale


def classify(
    feat: np.ndarray,
    bank: list[tuple[str, np.ndarray, str]],
    scale: np.ndarray,
) -> tuple[str, float, str, str]:
    """1-NN with per-dim scaling. Return (class, d, debug, nn_name)."""
    best_lab = None
    best_d = float("inf")
    best_name = ""
    per: dict[str, float] = {}
    for lab, vec, name in bank:
        d = float(np.linalg.norm((feat - vec) * scale))
        if lab not in per or d < per[lab]:
            per[lab] = d
        if d < best_d:
            best_d = d
            best_lab = lab
            best_name = name
    assert best_lab is not None
    dbg = " ".join(f"{k}={per[k]:.2f}" for k in ("pj", "boss", "empty") if k in per)
    return best_lab, best_d, dbg, best_name


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("device", nargs="?", default="emulator-5584")
    ap.add_argument("--hz", type=float, default=3.0, help="poll rate (default 3)")
    ap.add_argument(
        "--change-only",
        action="store_true",
        help="print on class OR target change (nearest train sample); default: every tick",
    )
    ap.add_argument("--debug", action="store_true", help="append distances + nn sample")
    ap.add_argument(
        "--dataset",
        type=Path,
        default=None,
        help="dataset dir (default: latest logs/*/focus_portrait_dataset)",
    )
    # Scaled-feature jump that counts as a new target even if 1-NN name sticks.
    ap.add_argument(
        "--retarget-d",
        type=float,
        default=1.25,
        help="with --change-only, reprint same class if feat moves this far (default 1.25)",
    )
    ap.add_argument(
        "--retarget-cooldown",
        type=float,
        default=1.2,
        help="seconds before another same-class retarget print (default 1.2)",
    )
    args = ap.parse_args()

    dataset = args.dataset or find_dataset_dir()
    print(f"[watch] device={args.device} dataset={dataset}", flush=True)
    bank, scale = load_training(dataset)
    print(
        f"[watch] 1-NN bank={len(bank)} samples — polling ~{args.hz} Hz; Ctrl+C to stop",
        flush=True,
    )
    print("---", flush=True)

    interval = 1.0 / max(args.hz, 0.2)
    last_print = None
    last_nn = None
    last_feat: np.ndarray | None = None
    last_print_t = 0.0
    # Temporal majority: boss emblems animate; a single PJ-like frame should not flip.
    hist: list[str] = []
    nn_hist: list[str] = []
    hist_n = 5
    while True:
        t0 = time.time()
        try:
            frame = screencap_bgr(args.device)
            feat = features(face_crop(frame))
            lab, dist, dbg, nn = classify(feat, bank, scale)
            hist.append(lab)
            nn_hist.append(nn)
            if len(hist) > hist_n:
                hist.pop(0)
                nn_hist.pop(0)
            # Prefer boss if tied with pj (safer for farm_bosses false Focus).
            counts: dict[str, int] = defaultdict(int)
            for h in hist:
                counts[h] += 1
            smooth = max(
                counts.keys(),
                key=lambda k: (counts[k], 1 if k == "boss" else 0, 1 if k == "empty" else 0),
            )
            nn_counts: dict[str, int] = defaultdict(int)
            for name in nn_hist:
                nn_counts[name] += 1
            smooth_nn = max(nn_counts.keys(), key=lambda k: nn_counts[k])

            text = PRINT.get(smooth, smooth)
            jump = (
                float(np.linalg.norm((feat - last_feat) * scale))
                if last_feat is not None
                else 0.0
            )
            cooled = (t0 - last_print_t) >= args.retarget_cooldown
            # Require both a different nearest sample AND a real feat jump, plus cooldown.
            # Animation alone used to spam "Focus *" on the same elf.
            retarget = (
                args.change_only
                and cooled
                and text == last_print
                and text != "empty"
                and smooth_nn != last_nn
                and jump >= args.retarget_d
            )
            if retarget:
                line = f"{text} *"  # same class, new portrait / target
            else:
                line = text
            if args.debug:
                line = f"{line}  raw={PRINT.get(lab, lab)} nn={smooth_nn} ({dbg} d={dist:.2f} Δ={jump:.2f})"

            should = (not args.change_only) or (text != last_print) or retarget
            if should:
                print(line, flush=True)
                last_print = text
                last_nn = smooth_nn
                last_feat = feat.copy()
                last_print_t = t0
        except Exception as exc:
            print(f"[watch] error: {exc}", flush=True)

        elapsed = time.time() - t0
        time.sleep(max(0.0, interval - elapsed))


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("\n[watch] stopped", flush=True)
        raise SystemExit(0)
