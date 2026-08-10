#!/usr/bin/env python3
"""
A/B bench: enemy focus detection — old (red HP only) vs new (red OR clear_x).

Same N live screencaps from the emulator; both modes scored on each frame.

Usage:
  # Focus a player on 5584, then:
  python3 scripts/bench_enemy_focus.py
  python3 scripts/bench_enemy_focus.py --device emulator-5584 --attempts 10
"""

from __future__ import annotations

import argparse
import csv
import subprocess
import time
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

import cv2
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/templates/mu/ui"
RED_TPL = ASSETS / "focus_hp_bar.png"
CLEAR_X_TPL = ASSETS / "focus_clear_x.png"

# Match Android ElfBuffFocusHud thresholds / ROIs @ 1280×720
RED_THRESHOLD = 0.80
CLEAR_X_THRESHOLD = 0.85
# hudRoi ref (500,20)-(2100,360) @ 2560×1440 → screen
RED_ROI = (250, 10, 1050, 180)  # left, top, right, bottom
# clearXRoi @ 1280×720
CLEAR_X_ROI = (488, 0, 544, 54)


@dataclass
class Probe:
    attempt: int
    red_score: float
    clear_x_score: float
    old_hit: bool
    new_hit: bool
    frame_path: str


def screencap(device: str) -> np.ndarray:
    raw = subprocess.check_output(
        ["adb", "-s", device, "exec-out", "screencap", "-p"],
        stderr=subprocess.DEVNULL,
    )
    arr = np.frombuffer(raw, dtype=np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        raise RuntimeError("failed to decode screencap")
    return img


def load_bgr(path: Path) -> np.ndarray:
    img = cv2.imread(str(path), cv2.IMREAD_UNCHANGED)
    if img is None:
        raise FileNotFoundError(path)
    if img.ndim == 3 and img.shape[2] == 4:
        bgr = cv2.cvtColor(img, cv2.COLOR_BGRA2BGR)
    else:
        bgr = img
    return bgr


def circular_mask(w: int, h: int) -> np.ndarray:
    mask = np.zeros((h, w), dtype=np.uint8)
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    radius = int(min(w, h) / 2.0)
    cv2.circle(mask, (int(cx), int(cy)), radius, 255, thickness=-1)
    return mask


def best_score_ccoeff(hay: np.ndarray, needle: np.ndarray) -> float:
    if hay.shape[0] < needle.shape[0] or hay.shape[1] < needle.shape[1]:
        return 0.0
    res = cv2.matchTemplate(hay, needle, cv2.TM_CCOEFF_NORMED)
    return float(res.max())


def best_score_ccorr_masked(hay: np.ndarray, needle: np.ndarray, mask: np.ndarray) -> float:
    if hay.shape[0] < needle.shape[0] or hay.shape[1] < needle.shape[1]:
        return 0.0
    # OpenCV 4+/5: mask supported with TM_CCORR_NORMED
    res = cv2.matchTemplate(hay, needle, cv2.TM_CCORR_NORMED, mask=mask)
    return float(res.max())


def crop_roi(frame: np.ndarray, roi: tuple[int, int, int, int]) -> np.ndarray:
    l, t, r, b = roi
    h, w = frame.shape[:2]
    l, t = max(0, l), max(0, t)
    r, b = min(w, r), min(h, b)
    return frame[t:b, l:r]


def probe_frame(
    frame: np.ndarray,
    red_tpl: np.ndarray,
    clear_tpl: np.ndarray,
    clear_mask: np.ndarray,
    attempt: int,
    frame_path: Path,
) -> Probe:
    red_roi = crop_roi(frame, RED_ROI)
    clear_roi = crop_roi(frame, CLEAR_X_ROI)
    red_score = best_score_ccoeff(red_roi, red_tpl)
    clear_score = best_score_ccorr_masked(clear_roi, clear_tpl, clear_mask)
    old_hit = red_score >= RED_THRESHOLD
    new_hit = old_hit or (clear_score >= CLEAR_X_THRESHOLD)
    return Probe(
        attempt=attempt,
        red_score=red_score,
        clear_x_score=clear_score,
        old_hit=old_hit,
        new_hit=new_hit,
        frame_path=str(frame_path),
    )


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--device", default="emulator-5584")
    ap.add_argument("--attempts", type=int, default=10)
    ap.add_argument("--delay-ms", type=int, default=400, help="pause between captures")
    ap.add_argument(
        "--out",
        default="",
        help="output dir (default logs/YYYY-MM-DD/enemy_focus_ab_bench)",
    )
    args = ap.parse_args()

    day = datetime.now().strftime("%Y-%m-%d")
    out = Path(args.out) if args.out else ROOT / "logs" / day / "enemy_focus_ab_bench"
    out.mkdir(parents=True, exist_ok=True)
    frames_dir = out / "frames"
    frames_dir.mkdir(exist_ok=True)

    red_tpl = load_bgr(RED_TPL)
    clear_tpl = load_bgr(CLEAR_X_TPL)
    clear_mask = circular_mask(clear_tpl.shape[1], clear_tpl.shape[0])

    print(f"device={args.device} attempts={args.attempts}")
    print(f"old: red>={RED_THRESHOLD} in ROI {RED_ROI}")
    print(f"new: red>={RED_THRESHOLD} OR clear_x>={CLEAR_X_THRESHOLD} in ROI {CLEAR_X_ROI}")
    print(f"out={out}")
    print("Focus a PJ enemy on screen, then waiting 1.5s…")
    time.sleep(1.5)

    probes: list[Probe] = []
    for i in range(1, args.attempts + 1):
        frame = screencap(args.device)
        if frame.shape[1] != 1280 or frame.shape[0] != 720:
            print(f"WARN frame size {frame.shape[1]}x{frame.shape[0]} (expected 1280x720)")
        path = frames_dir / f"{i:02d}.png"
        cv2.imwrite(str(path), frame)
        p = probe_frame(frame, red_tpl, clear_tpl, clear_mask, i, path)
        probes.append(p)
        print(
            f"  #{i:02d} red={p.red_score:.3f} clear_x={p.clear_x_score:.3f} "
            f"old={'HIT' if p.old_hit else 'miss'} new={'HIT' if p.new_hit else 'miss'}"
        )
        time.sleep(args.delay_ms / 1000.0)

    old_hits = sum(1 for p in probes if p.old_hit)
    new_hits = sum(1 for p in probes if p.new_hit)
    n = len(probes)

    csv_path = out / "results.csv"
    with csv_path.open("w", newline="") as f:
        w = csv.DictWriter(
            f,
            fieldnames=["attempt", "red_score", "clear_x_score", "old_hit", "new_hit", "frame"],
        )
        w.writeheader()
        for p in probes:
            w.writerow(
                {
                    "attempt": p.attempt,
                    "red_score": f"{p.red_score:.4f}",
                    "clear_x_score": f"{p.clear_x_score:.4f}",
                    "old_hit": int(p.old_hit),
                    "new_hit": int(p.new_hit),
                    "frame": Path(p.frame_path).name,
                }
            )

    summary = out / "summary.txt"
    lines = [
        f"enemy focus A/B bench — {datetime.now().isoformat(timespec='seconds')}",
        f"device={args.device} attempts={n}",
        f"OLD (red only @{RED_THRESHOLD}): {old_hits}/{n} ({100.0 * old_hits / n:.0f}%)",
        f"NEW (red OR clear_x @{CLEAR_X_THRESHOLD}): {new_hits}/{n} ({100.0 * new_hits / n:.0f}%)",
        f"delta new-old: {new_hits - old_hits:+d}",
        "",
        "per attempt:",
    ]
    for p in probes:
        lines.append(
            f"  #{p.attempt:02d} red={p.red_score:.3f} clear_x={p.clear_x_score:.3f} "
            f"old={int(p.old_hit)} new={int(p.new_hit)}"
        )
    summary.write_text("\n".join(lines) + "\n")
    print()
    print("\n".join(lines[:6]))
    print(f"csv={csv_path}")
    print(f"summary={summary}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
