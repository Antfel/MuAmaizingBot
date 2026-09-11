#!/usr/bin/env python3
"""Focus-portrait coach watcher — continuous capture of doubtful classifications.

Polls the emulator, runs the same 1-NN bank as the bot, and when a frame looks
suspicious (low margin between classes, sudden flip, or far from all samples)
saves it into a review queue for AI / human labeling:

  logs/YYYY-MM-DD/focus_portrait_dataset/review_queue/
    <ts>_<pred>_m<margin>.png          # full frame
    <ts>_<pred>_m<margin>_hud.png      # top HUD band
    <ts>_<pred>_m<margin>_face.png     # 32×32 face crop
    queue.csv                          # pending reviews

Usage:
  ./scripts/watch_focus_portrait_coach.py emulator-5584
  ./scripts/watch_focus_portrait_coach.py emulator-5584 --hz 2 --margin 0.45

Then either:
  - Ask the agent: "revisa la cola de focus portrait"
  - Or /loop 5m to have the agent drain the queue periodically
  - Or: ./scripts/review_focus_portrait_queue.py --list

After labels are applied, re-export + install:
  ./scripts/export_focus_portrait_bank.py
  ./scripts/install_bluestacks.sh emulator-5584
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import importlib.util
import subprocess
import time
from pathlib import Path

import cv2
import numpy as np

ROOT = Path(__file__).resolve().parents[1]

HUD = (470, 0, 620, 90)  # same provisional band as capture_focus_portrait


def load_watcher():
    spec = importlib.util.spec_from_file_location(
        "watch_focus_portrait",
        ROOT / "scripts/watch_focus_portrait.py",
    )
    mod = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(mod)
    return mod


def dataset_dir(w) -> Path:
    return w.find_dataset_dir()


def queue_dir(ds: Path) -> Path:
    q = ds / "review_queue"
    q.mkdir(parents=True, exist_ok=True)
    return q


def queue_csv(q: Path) -> Path:
    p = q / "queue.csv"
    if not p.exists():
        with p.open("w", newline="") as f:
            csv.writer(f).writerow(
                [
                    "timestamp",
                    "status",
                    "pred",
                    "margin",
                    "d_pj",
                    "d_boss",
                    "d_empty",
                    "reason",
                    "raw_file",
                    "hud_file",
                    "face_file",
                    "true_label",
                    "notes",
                ]
            )
    return p


def per_class_dists(feat, bank, scale) -> dict[str, float]:
    per: dict[str, float] = {}
    for lab, vec, _name in bank:
        d = float(np.linalg.norm((feat - vec) * scale))
        if lab not in per or d < per[lab]:
            per[lab] = d
    return per


def should_enqueue(
    pred: str,
    per: dict[str, float],
    prev_pred: str | None,
    margin_thresh: float,
    far_thresh: float,
) -> tuple[bool, str, float]:
    """Return (enqueue?, reason, margin). margin = 2nd_best - best."""
    ordered = sorted(per.items(), key=lambda kv: kv[1])
    best_lab, best_d = ordered[0]
    second_d = ordered[1][1] if len(ordered) > 1 else best_d + 99.0
    margin = second_d - best_d

    if prev_pred is not None and pred != prev_pred and pred in ("pj", "boss", "empty"):
        # Flip always interesting (arrival / kill / ghost).
        return True, f"flip:{prev_pred}->{pred}", margin

    if margin < margin_thresh:
        return True, f"low_margin:{margin:.2f}", margin

    if best_d > far_thresh:
        return True, f"far_from_bank:{best_d:.2f}", margin

    # Boss fight ghost: pj while very close to empty (classic terrain FP).
    if pred == "pj" and "empty" in per and (per["empty"] - best_d) < margin_thresh:
        return True, "pj_near_empty", margin

    return False, "", margin


def save_candidate(
    q: Path,
    frame: np.ndarray,
    face: np.ndarray,
    pred: str,
    margin: float,
    per: dict[str, float],
    reason: str,
) -> None:
    ts = dt.datetime.now().strftime("%H%M%S_%f")[:-3]
    stem = f"{ts}_{pred}_m{margin:.2f}"
    raw_name = f"{stem}.png"
    hud_name = f"{stem}_hud.png"
    face_name = f"{stem}_face.png"

    cv2.imwrite(str(q / raw_name), frame)
    l, t, r, b = HUD
    h, w = frame.shape[:2]
    # scale HUD if not 1280×720
    sx, sy = w / 1280.0, h / 720.0
    hl, ht, hr, hb = int(l * sx), int(t * sy), int(r * sx), int(b * sy)
    hud = frame[ht:hb, hl:hr]
    cv2.imwrite(str(q / hud_name), hud)
    cv2.imwrite(str(q / face_name), face)

    with queue_csv(q).open("a", newline="") as f:
        csv.writer(f).writerow(
            [
                dt.datetime.now().isoformat(timespec="seconds"),
                "pending",
                pred,
                f"{margin:.3f}",
                f"{per.get('pj', -1):.3f}",
                f"{per.get('boss', -1):.3f}",
                f"{per.get('empty', -1):.3f}",
                reason,
                raw_name,
                hud_name,
                face_name,
                "",
                "",
            ]
        )
    print(
        f"[queue] +{stem} pred={pred} margin={margin:.2f} reason={reason} "
        f"pj={per.get('pj', -1):.2f} boss={per.get('boss', -1):.2f} empty={per.get('empty', -1):.2f}",
        flush=True,
    )


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("device", nargs="?", default="emulator-5584")
    ap.add_argument("--hz", type=float, default=2.0, help="poll rate (default 2)")
    ap.add_argument(
        "--margin",
        type=float,
        default=0.45,
        help="enqueue when (2nd-best - best) < this (default 0.45)",
    )
    ap.add_argument(
        "--far",
        type=float,
        default=2.5,
        help="enqueue when best distance > this (default 2.5)",
    )
    ap.add_argument(
        "--cooldown",
        type=float,
        default=2.5,
        help="min seconds between enqueues (default 2.5)",
    )
    ap.add_argument(
        "--max-pending",
        type=int,
        default=80,
        help="stop enqueueing when pending rows exceed this (default 80)",
    )
    args = ap.parse_args()

    w = load_watcher()
    ds = dataset_dir(w)
    q = queue_dir(ds)
    queue_csv(q)
    bank, scale = w.load_training(ds)
    print(
        f"[coach] device={args.device} dataset={ds} queue={q} "
        f"bank={len(bank)} hz={args.hz} margin<{args.margin} far>{args.far}",
        flush=True,
    )
    print("---", flush=True)

    interval = 1.0 / max(args.hz, 0.2)
    prev = None
    last_enq = 0.0
    hist: list[str] = []

    while True:
        t0 = time.time()
        try:
            # Cap queue growth.
            pending = 0
            with queue_csv(q).open() as f:
                for row in csv.DictReader(f):
                    if row.get("status") == "pending":
                        pending += 1
            if pending >= args.max_pending:
                print(
                    f"[coach] queue full pending={pending} — waiting (review/drain)",
                    flush=True,
                )
                time.sleep(5.0)
                continue

            frame = w.screencap_bgr(args.device)
            face = w.face_crop(frame)
            feat = w.features(face)
            pred, dist, dbg, nn = w.classify(feat, bank, scale)
            per = per_class_dists(feat, bank, scale)

            hist.append(pred)
            if len(hist) > 5:
                hist.pop(0)
            # Smooth print label (majority) for console only.
            counts: dict[str, int] = {}
            for h in hist:
                counts[h] = counts.get(h, 0) + 1
            smooth = max(counts, key=lambda k: counts[k])
            text = w.PRINT.get(smooth, smooth)

            enq, reason, margin = should_enqueue(
                pred, per, prev, args.margin, args.far
            )
            now = time.time()
            if enq and (now - last_enq) >= args.cooldown:
                save_candidate(q, frame, face, pred, margin, per, reason)
                last_enq = now
                print(f"{text}  ⚠ queued ({reason})", flush=True)
            else:
                # change-only-ish console
                line = f"{text}  ({dbg} d={dist:.2f} Δ={margin:.2f})"
                if prev != pred:
                    print(line, flush=True)

            prev = pred
        except Exception as exc:
            print(f"[coach] error: {exc}", flush=True)

        elapsed = time.time() - t0
        time.sleep(max(0.0, interval - elapsed))


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("\n[coach] stopped", flush=True)
        raise SystemExit(0)
