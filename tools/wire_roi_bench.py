#!/usr/bin/env python3
"""Bench wire/map ROI pixel load + OpenCV matchTemplate cost (host proxy for device)."""

from __future__ import annotations

import time
from pathlib import Path

import cv2
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
LOGS = ROOT / "logs/2026-08-08/wire_1_to_2"
TPL = ROOT / "app/src/main/assets/templates/mu/wires/common"

# Before → after @ 1280×720
MAP_OLD = (1040, 0, 1235, 36)
MAP_NEW = (1068, 2, 1230, 30)
LIST_OLD = (399, 148, 879, 598)
LIST_NEW = (488, 172, 794, 476)
WIRE_HUD_OLD = (900, 0, 1280, 90)
WIRE_HUD_NEW = (1060, 0, 1280, 90)
ENTER_ROI = (520, 500, 760, 600)  # former template search band

MAP_UPSCALE = 3.0
LIST_UPSCALE = 2.5


def area(r: tuple[int, int, int, int]) -> int:
    return max(0, r[2] - r[0]) * max(0, r[3] - r[1])


def crop(img: np.ndarray, r: tuple[int, int, int, int]) -> np.ndarray:
    x1, y1, x2, y2 = r
    return img[y1:y2, x1:x2]


def bench_match(hay: np.ndarray, needle: np.ndarray, loops: int = 40) -> tuple[float, float]:
    """Return (median_ms, best_score) for TM_CCOEFF_NORMED."""
    if hay.size == 0 or needle.size == 0:
        return float("nan"), float("nan")
    if hay.shape[0] < needle.shape[0] or hay.shape[1] < needle.shape[1]:
        return float("nan"), float("nan")
    times = []
    best = -1.0
    for _ in range(loops):
        t0 = time.perf_counter()
        res = cv2.matchTemplate(hay, needle, cv2.TM_CCOEFF_NORMED)
        _, max_val, _, _ = cv2.minMaxLoc(res)
        times.append((time.perf_counter() - t0) * 1000.0)
        best = max(best, float(max_val))
    times.sort()
    return times[len(times) // 2], best


def pct(new: float, old: float) -> str:
    if old == 0:
        return "n/a"
    return f"{(new / old - 1.0) * 100:+.1f}%"


def main() -> None:
    hud = cv2.imread(str(LOGS / "01_hud_wire1.png"))
    popup = cv2.imread(str(LOGS / "02_popup_open.png"))
    assert hud is not None and popup is not None

    wire1 = cv2.imread(str(TPL / "wire_1_hud.png"))
    enter = cv2.imread(str(TPL / "wire_enter_button.png"))
    assert wire1 is not None and enter is not None

    print("=== Pixel / OCR input load @1280×720 ===")
    rows = [
        ("map OCR crop", MAP_OLD, MAP_NEW, MAP_UPSCALE),
        ("list OCR crop", LIST_OLD, LIST_NEW, LIST_UPSCALE),
        ("wire HUD template ROI", WIRE_HUD_OLD, WIRE_HUD_NEW, 1.0),
    ]
    for name, old, new, up in rows:
        ao, an = area(old), area(new)
        print(
            f"{name:24s}  px {ao:7d} → {an:7d} ({pct(an, ao)})  "
            f"OCR-in≈{int(ao * up * up):7d} → {int(an * up * up):7d} ({pct(an * up * up, ao * up * up)})"
        )

    print("\n=== OpenCV matchTemplate median ms (host) ===")
    for label, roi_old, roi_new, tpl, frame in [
        ("wire_1_hud ×6 wires ROI", WIRE_HUD_OLD, WIRE_HUD_NEW, wire1, hud),
        ("enter_button (removed)", ENTER_ROI, ENTER_ROI, enter, popup),
    ]:
        med_old, sc_old = bench_match(crop(frame, roi_old), tpl)
        med_new, sc_new = bench_match(crop(frame, roi_new), tpl)
        if "removed" in label:
            print(
                f"{label:28s}  was {med_old:6.2f}ms score={sc_old:.3f}  "
                f"→ STATIC tap (0 template searches)"
            )
        else:
            # Simulate probing 6 wire HUD templates once each
            print(
                f"{label:28s}  {med_old:6.2f}ms×6={med_old * 6:6.2f}ms score={sc_old:.3f} → "
                f"{med_new:6.2f}ms×6={med_new * 6:6.2f}ms score={sc_new:.3f} ({pct(med_new, med_old)})"
            )

    print("\n=== Switch confirm path ===")
    print("before: findTemplate enter (listRoi-derived + static) + wait up to ~5.5s")
    print("after:  single RefCoords tap (1280,1102) — no matchTemplate / no waitForTemplate")

    # Write annotated overlay for visual QA
    out = LOGS / "BENCH_rois_overlay.png"
    vis = popup.copy()
    for r, color in [(LIST_OLD, (0, 255, 255)), (LIST_NEW, (0, 140, 255))]:
        cv2.rectangle(vis, (r[0], r[1]), (r[2], r[3]), color, 2)
    cv2.imwrite(str(out), vis)
    print(f"\nwrote {out} (yellow=old list, orange=new list)")


if __name__ == "__main__":
    main()
