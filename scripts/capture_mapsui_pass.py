#!/usr/bin/env python3
"""Capture map-list pass: screencap → list ROI → match known mapsui → propose unknown row crops.

Usage:
  python3 scripts/capture_mapsui_pass.py --device emulator-5584
  python3 scripts/capture_mapsui_pass.py --device emulator-5584 --tag scroll2
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
from datetime import datetime
from pathlib import Path

import cv2
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/templates/mu/maps"
OUT_ROOT = ROOT / "logs/2026-08-10/mapsui_harvest"

# Left parchment map-list column @ native 1280×720 (NOT the swipe-pad ROI —
# that only covers the bottom Craft NPC strip).
LIST_ROI_REF = (145, 130, 420, 500)  # L,T,R,B
# Right side of each row is "Lv.N"; exclude when cropping name chips.
LV_COL_REF_W = 95
NAME_BRIGHT_THR = 115
CHIP_H = 22


def screencap(device: str) -> np.ndarray:
    raw = subprocess.check_output(["adb", "-s", device, "exec-out", "screencap", "-p"])
    img = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise RuntimeError("screencap decode failed")
    return img


def list_roi_rect(w: int, h: int) -> tuple[int, int, int, int]:
    l, t, r, b = LIST_ROI_REF
    sx, sy = w / 1280.0, h / 720.0
    left = int(l * sx)
    top = int(t * sy)
    right = int(r * sx)
    bottom = int(b * sy)
    return (
        max(0, left),
        max(0, top),
        min(w, right),
        min(h, bottom),
    )


def load_known() -> list[tuple[str, np.ndarray]]:
    items = []
    for path in sorted(ASSETS.glob("**/mapsui/*.png")):
        im = cv2.imread(str(path))
        if im is None:
            continue
        rel = str(path.relative_to(ASSETS))
        items.append((rel, im))
    return items


def score_chip(hay: np.ndarray, chip: np.ndarray) -> tuple[float, int, int]:
    tpl = chip
    if hay.shape[0] < tpl.shape[0] or hay.shape[1] < tpl.shape[1]:
        return -1.0, 0, 0
    res = cv2.matchTemplate(hay, tpl, cv2.TM_CCOEFF_NORMED)
    _, max_val, _, max_loc = cv2.minMaxLoc(res)
    return float(max_val), int(max_loc[0]), int(max_loc[1])


def propose_text_bands(list_bgr: np.ndarray) -> list[tuple[int, int, int, int]]:
    """Name chips (exclude Lv column) → (x,y,w,h) in list crop coords."""
    gray = cv2.cvtColor(list_bgr, cv2.COLOR_BGR2GRAY)
    name_w = max(40, list_bgr.shape[1] - LV_COL_REF_W)
    bright = (gray[:, :name_w] > NAME_BRIGHT_THR).astype(np.uint8)
    row_sum = bright.sum(axis=1)
    thr = max(5, int(name_w * 0.03))
    bands: list[tuple[int, int]] = []
    in_band = False
    start = 0
    for y, v in enumerate(row_sum >= thr):
        if v and not in_band:
            in_band = True
            start = y
        elif not v and in_band:
            in_band = False
            bands.append((start, y))
    if in_band:
        bands.append((start, len(row_sum)))

    boxes: list[tuple[int, int, int, int]] = []
    h = list_bgr.shape[0]
    for y0, y1 in bands:
        if not (7 <= (y1 - y0) <= 22):
            continue
        cy = (y0 + y1) // 2
        y0e = max(0, cy - CHIP_H // 2)
        y1e = min(h, y0e + CHIP_H)
        strip = gray[y0e:y1e, :name_w]
        xs = np.where((strip > NAME_BRIGHT_THR).sum(axis=0) > 0)[0]
        if xs.size < 6:
            continue
        x0 = max(0, int(xs[0]) - 2)
        x1 = min(name_w, int(xs[-1]) + 3)
        bw, bh = x1 - x0, y1e - y0e
        if bw < 24 or bh < 12:
            continue
        boxes.append((x0, y0e, bw, bh))
    return boxes


def box_iou_y(a: tuple[int, int, int, int], cy: int, th: int, tol: int = 10) -> bool:
    _, ay, _, ah = a
    return abs((ay + ah // 2) - (cy + th // 2)) <= tol


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--device", default="emulator-5584")
    ap.add_argument("--tag", default="")
    ap.add_argument(
        "--parent",
        default="",
        help="Expanded head slug (e.g. dungeon) → writes under children/<parent>/",
    )
    ap.add_argument("--threshold", type=float, default=0.72)
    args = ap.parse_args()

    stamp = datetime.now().strftime("%H%M%S")
    tag = args.tag.strip() or stamp
    parent = args.parent.strip().lower().replace(" ", "_")
    if parent:
        out = OUT_ROOT / "children" / parent / f"pass_{tag}"
    else:
        out = OUT_ROOT / f"pass_{tag}"
    out.mkdir(parents=True, exist_ok=True)
    crops = out / "crops"
    crops.mkdir(exist_ok=True)

    frame = screencap(args.device)
    h, w = frame.shape[:2]
    cv2.imwrite(str(out / "full.png"), frame)

    l, t, r, b = list_roi_rect(w, h)
    list_img = frame[t:b, l:r].copy()
    cv2.imwrite(str(out / "list_roi.png"), list_img)

    known = load_known()
    hits = []
    overlay = list_img.copy()
    for rel, chip in known:
        score, x, y = score_chip(list_img, chip)
        if score < args.threshold:
            continue
        th, tw = chip.shape[:2]
        hits.append(
            {
                "template": rel,
                "score": round(score, 4),
                "x": x,
                "y": y,
                "w": tw,
                "h": th,
                "center_y": y + th // 2,
            }
        )
        cv2.rectangle(overlay, (x, y), (x + tw, y + th), (0, 255, 0), 1)
        cv2.putText(
            overlay,
            f"{Path(rel).stem}:{score:.2f}",
            (x, max(12, y - 2)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.35,
            (0, 255, 0),
            1,
            cv2.LINE_AA,
        )

    hits.sort(key=lambda h: h["center_y"])
    bands = propose_text_bands(list_img)
    unknown = []
    for i, (x, y, bw, bh) in enumerate(bands):
        covered = any(box_iou_y((hx["x"], hx["y"], hx["w"], hx["h"]), y, bh) for hx in hits)
        crop = list_img[y : y + bh, x : x + bw]
        name = f"band_{i:02d}_y{y}"
        path = crops / f"{name}.png"
        cv2.imwrite(str(path), crop)
        entry = {
            "file": path.name,
            "x": x,
            "y": y,
            "w": bw,
            "h": bh,
            "matched": covered,
        }
        if covered:
            entry["note"] = "overlaps_known"
        else:
            unknown.append(entry)
            cv2.rectangle(overlay, (x, y), (x + bw, y + bh), (0, 165, 255), 1)
            cv2.putText(
                overlay,
                f"new?{i}",
                (x, max(12, y - 2)),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.35,
                (0, 165, 255),
                1,
                cv2.LINE_AA,
            )

    cv2.imwrite(str(out / "overlay.png"), overlay)
    report = {
        "device": args.device,
        "tag": tag,
        "frame": f"{w}x{h}",
        "list_roi": {"l": l, "t": t, "r": r, "b": b},
        "known_hits": hits,
        "bands": bands,
        "unknown_candidates": unknown,
        "all_band_crops": [p.name for p in sorted(crops.glob("band_*.png"))],
    }
    (out / "report.json").write_text(json.dumps(report, indent=2) + "\n")

    print(f"wrote {out}")
    print(f"frame={w}x{h} list_roi=({l},{t})-({r},{b})")
    print(f"known hits ({len(hits)}):")
    for h in hits:
        print(f"  {h['score']:.3f}  y={h['center_y']:3d}  {h['template']}")
    print(f"unknown band candidates: {len(unknown)}")
    for u in unknown:
        print(f"  {u['file']} y={u['y']} size={u['w']}x{u['h']}")


if __name__ == "__main__":
    main()
