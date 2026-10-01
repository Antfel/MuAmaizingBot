#!/usr/bin/env python3
"""Sibling mitigation prototype for map-name NCC (offline, separate from CurrentMapOcr).

Pipeline:
  1) NCC all *_current chips in HUD ROI
  2) Take family of best hit (kalima / raklion / plains / …)
  3) Re-rank ONLY within that family
  4) If family margin < delta → digit-glyph NCC on the right strip of the HUD name

Usage:
  python3 scripts/proto_map_name_mitigate.py
  python3 scripts/proto_map_name_mitigate.py --margin 0.035 --digit-margin 0.02
"""

from __future__ import annotations

import argparse
import json
import re
import time
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np

import proto_map_name_classifier as base

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "logs/2026-08-10/map_name_proto"


@dataclass
class Ranked:
    map_id: str
    name: str
    score: float


def family_of(map_id: str) -> str:
    if map_id.startswith("temple_of_kalima_"):
        return "kalima"
    if map_id.startswith("raklion_"):
        return "raklion"
    if map_id.startswith("plain_of_four_winds_"):
        return "plains"
    if map_id.startswith("lost_tower_"):
        return "lost_tower"
    if map_id.startswith("dungeon_"):
        return "dungeon"
    if map_id.startswith("icarus_"):
        return "icarus"
    if map_id.startswith("atlans_"):
        return "atlans"
    if map_id.startswith("tarkan_"):
        return "tarkan"
    if map_id.startswith("aida_"):
        return "aida"
    if map_id.startswith("kanturu_"):
        return "kanturu"
    # singleton / floorless
    return map_id


def trailing_floor(map_id: str) -> int | None:
    m = re.search(r"_(\d+)$", map_id)
    return int(m.group(1)) if m else None


def score_chip(hud: np.ndarray, chip: np.ndarray) -> float:
    tpl = chip
    hay = hud
    if hay.shape[0] < tpl.shape[0] or hay.shape[1] < tpl.shape[1]:
        scale = hay.shape[0] / max(tpl.shape[0], 1)
        tpl = cv2.resize(
            tpl,
            (max(1, int(tpl.shape[1] * scale)), hay.shape[0]),
            interpolation=cv2.INTER_AREA,
        )
    if hay.shape[1] < tpl.shape[1] or hay.shape[0] < tpl.shape[0]:
        return -1.0
    return float(cv2.matchTemplate(hay, tpl, cv2.TM_CCOEFF_NORMED).max())


def rank_ncc(hud: np.ndarray, bank: list[base.BankItem]) -> list[Ranked]:
    ranked: list[Ranked] = []
    for item in bank:
        ranked.append(Ranked(item.map_id, item.name, score_chip(hud, item.chip_bgr)))
    ranked.sort(key=lambda r: -r.score)
    return ranked


def extract_digit_glyph(chip: np.ndarray, floor: int) -> np.ndarray | None:
    """Crop rightmost digit area from a numbered current chip."""
    h, w = chip.shape[:2]
    # Digits are near the right edge; take ~18% width, min 10px.
    dw = max(10, int(w * 0.18))
    glyph = chip[:, max(0, w - dw) : w]
    if glyph.size == 0:
        return None
    # Optional: keep only bright ink
    return glyph


def build_digit_bank(bank: list[base.BankItem]) -> dict[str, dict[int, np.ndarray]]:
    """family -> floor -> digit glyph (averaged if multiple sources later)."""
    out: dict[str, dict[int, np.ndarray]] = {}
    for item in bank:
        fam = family_of(item.map_id)
        fl = trailing_floor(item.map_id)
        if fl is None:
            continue
        glyph = extract_digit_glyph(item.chip_bgr, fl)
        if glyph is None:
            continue
        out.setdefault(fam, {})[fl] = glyph
    return out


def digit_roi(hud: np.ndarray) -> np.ndarray:
    """Right strip of HUD name band where floor number usually sits."""
    h, w = hud.shape[:2]
    # name digits sit left of wire bleed; use right 28% but leave a few px
    x0 = int(w * 0.70)
    return hud[:, x0:w]


def pick_digit(
    hud: np.ndarray,
    fam: str,
    digit_bank: dict[str, dict[int, np.ndarray]],
    candidate_floors: list[int] | None = None,
) -> tuple[int | None, float, float]:
    """Return (floor, best_score, margin)."""
    glyphs = digit_bank.get(fam) or {}
    if not glyphs:
        return None, -1.0, 0.0
    roi = digit_roi(hud)
    scores: list[tuple[int, float]] = []
    for fl, glyph in glyphs.items():
        if candidate_floors is not None and fl not in candidate_floors:
            continue
        scores.append((fl, score_chip(roi, glyph)))
    if not scores:
        return None, -1.0, 0.0
    scores.sort(key=lambda x: -x[1])
    best_fl, best_s = scores[0]
    second_s = scores[1][1] if len(scores) > 1 else -1.0
    return best_fl, best_s, best_s - second_s


def classify_mitigated(
    hud: np.ndarray,
    bank: list[base.BankItem],
    digit_bank: dict[str, dict[int, np.ndarray]],
    *,
    min_score: float = 0.85,
    family_margin: float = 0.035,
    digit_margin: float = 0.02,
) -> dict:
    ranked = rank_ncc(hud, bank)
    if not ranked or ranked[0].score < min_score:
        return {
            "map_id": "unknown",
            "reason": "low_score",
            "best": ranked[0].map_id if ranked else None,
            "best_score": ranked[0].score if ranked else -1.0,
            "margin": 0.0,
            "family": None,
            "digit": None,
        }

    best = ranked[0]
    fam = family_of(best.map_id)
    family_members = [r for r in ranked if family_of(r.map_id) == fam]
    fam_best = family_members[0]
    fam_second = family_members[1] if len(family_members) > 1 else None
    fam_margin = fam_best.score - (fam_second.score if fam_second else 0.0)

    result = {
        "map_id": fam_best.map_id,
        "reason": "family_ncc",
        "best": best.map_id,
        "best_score": best.score,
        "family": fam,
        "family_best": fam_best.map_id,
        "family_second": fam_second.map_id if fam_second else None,
        "family_margin": fam_margin,
        "global_second": ranked[1].map_id if len(ranked) > 1 else None,
        "global_margin": best.score - (ranked[1].score if len(ranked) > 1 else 0.0),
        "digit": None,
        "digit_score": None,
        "digit_margin": None,
    }

    # Comfortable family margin → done (also covers singletons).
    if fam_second is None or fam_margin >= family_margin:
        result["reason"] = "family_margin_ok"
        return result

    # Tight siblings → digit tie-break among top family candidates close to best.
    close = [
        trailing_floor(r.map_id)
        for r in family_members
        if trailing_floor(r.map_id) is not None and (fam_best.score - r.score) <= 0.08
    ]
    close = [f for f in close if f is not None]
    digit, dscore, dmargin = pick_digit(hud, fam, digit_bank, candidate_floors=close or None)
    result["digit"] = digit
    result["digit_score"] = dscore
    result["digit_margin"] = dmargin

    if digit is not None and dscore >= 0.55 and dmargin >= digit_margin:
        # map_id with this floor in family
        for r in family_members:
            if trailing_floor(r.map_id) == digit:
                result["map_id"] = r.map_id
                result["reason"] = "digit_tiebreak"
                return result

    # Still ambiguous
    result["map_id"] = "unknown"
    result["reason"] = "ambiguous_sibling"
    result["candidates"] = [r.map_id for r in family_members[:3]]
    return result


def sibling_eval_frames() -> list[tuple[str, Path]]:
    """Use captured live frames from prior polls."""
    pairs = [
        ("plain_of_four_winds_1", OUT / "live_scr.png"),  # may be old; optional
        ("plain_of_four_winds_2", OUT / "live_plains2.png"),
        ("raklion_1", OUT / "live_raklion_1.png"),
        ("raklion_2", OUT / "live_raklion_2.png"),
        ("raklion_3", OUT / "live_raklion_3.png"),
        ("temple_of_kalima_1", OUT / "live_temple_of_kalima_1.png"),
        ("temple_of_kalima_2", OUT / "live_temple_of_kalima_2.png"),
        ("temple_of_kalima_3", OUT / "live_temple_of_kalima_3.png"),
    ]
    # Also plains from calib if live_scr is plains1
    pairs.extend(
        [
            ("plain_of_four_winds_1", ROOT / "logs/2026-08-10/map_calib/before_swipe.png"),
            ("plain_of_four_winds_1", ROOT / "logs/2026-08-10/map_calib/scr_after_stop.png"),
        ]
    )
    out: list[tuple[str, Path]] = []
    seen_truth_paths: set[tuple[str, str]] = set()
    for truth, path in pairs:
        if not path.exists():
            continue
        key = (truth, str(path))
        if key in seen_truth_paths:
            continue
        seen_truth_paths.add(key)
        out.append((truth, path))
    return out


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--margin", type=float, default=0.035, help="min family NCC margin")
    ap.add_argument("--digit-margin", type=float, default=0.02, help="min digit glyph margin")
    ap.add_argument("--min-score", type=float, default=0.85)
    ap.add_argument("--bench-ms", type=int, default=40)
    args = ap.parse_args()

    catalog = base.load_map_catalog()
    bank = base.build_bank(catalog)
    digit_bank = build_digit_bank(bank)
    print(
        "digit glyphs:",
        {fam: sorted(floors.keys()) for fam, floors in digit_bank.items()},
    )

    # Dump digit glyphs for inspection
    dig_dir = OUT / "digit_glyphs"
    dig_dir.mkdir(parents=True, exist_ok=True)
    for fam, floors in digit_bank.items():
        for fl, g in floors.items():
            cv2.imwrite(str(dig_dir / f"{fam}_{fl}.png"), g)

    frames = sibling_eval_frames()
    print(f"\n=== mitigated eval ({len(frames)} frames) margin={args.margin} digit_margin={args.digit_margin} ===")
    rows = []
    for truth, path in frames:
        frame = cv2.imread(str(path))
        if frame is None:
            continue
        hud = base.hud_crop(frame)
        plain = rank_ncc(hud, bank)
        plain_id = plain[0].map_id if plain else "unknown"
        plain_margin = (
            plain[0].score - plain[1].score if len(plain) > 1 else plain[0].score
        )
        mit = classify_mitigated(
            hud,
            bank,
            digit_bank,
            min_score=args.min_score,
            family_margin=args.margin,
            digit_margin=args.digit_margin,
        )
        # Also: naive global NCC without mitigation
        rows.append(
            {
                "file": path.name,
                "truth": truth,
                "plain_ncc": plain_id,
                "plain_ok": plain_id == truth,
                "plain_score": round(plain[0].score, 4) if plain else None,
                "plain_margin": round(plain_margin, 4) if plain else None,
                "mitigated": mit["map_id"],
                "mitigated_ok": mit["map_id"] == truth,
                "reason": mit["reason"],
                "family": mit.get("family"),
                "family_margin": None
                if mit.get("family_margin") is None
                else round(float(mit["family_margin"]), 4),
                "digit": mit.get("digit"),
                "digit_margin": None
                if mit.get("digit_margin") is None
                else round(float(mit["digit_margin"]), 4),
            }
        )
        mark = "OK" if mit["map_id"] == truth else ("WEAK" if mit["map_id"] == "unknown" else "MISS")
        print(
            f"{path.name:40s} truth={truth:24s} "
            f"plain={plain_id}({plain[0].score:.3f},m={plain_margin:+.3f}) "
            f"mit={mit['map_id']} [{mit['reason']}] {mark}"
            + (
                f" fam_m={mit.get('family_margin'):+.3f}"
                if mit.get("family_margin") is not None
                else ""
            )
            + (f" digit={mit.get('digit')} dm={mit.get('digit_margin'):+.3f}" if mit.get("digit") is not None else "")
        )

    n = len(rows)
    if n:
        plain_ok = sum(r["plain_ok"] for r in rows)
        mit_ok = sum(r["mitigated_ok"] for r in rows)
        mit_weak = sum(r["mitigated"] == "unknown" for r in rows)
        mit_hard_miss = sum(
            (not r["mitigated_ok"]) and r["mitigated"] != "unknown" for r in rows
        )
        print("\n=== summary ===")
        print(f"frames={n}")
        print(f"plain NCC correct:     {plain_ok}/{n}")
        print(f"mitigated correct:     {mit_ok}/{n}")
        print(f"mitigated weak/unknown:{mit_weak}/{n}")
        print(f"mitigated hard miss:   {mit_hard_miss}/{n}")

    # Timing
    sample = None
    for _, path in frames:
        fr = cv2.imread(str(path))
        if fr is not None:
            sample = base.hud_crop(fr)
            break
    if sample is not None and args.bench_ms > 0:
        n = args.bench_ms
        t0 = time.perf_counter()
        for _ in range(n):
            classify_mitigated(
                sample,
                bank,
                digit_bank,
                min_score=args.min_score,
                family_margin=args.margin,
                digit_margin=args.digit_margin,
            )
        ms = (time.perf_counter() - t0) * 1000.0 / n
        print(f"mitigated pipeline: {ms:.2f} ms/call")

    report = {
        "params": {
            "min_score": args.min_score,
            "family_margin": args.margin,
            "digit_margin": args.digit_margin,
        },
        "rows": rows,
    }
    out_path = OUT / "mitigate_eval.json"
    out_path.write_text(json.dumps(report, indent=2) + "\n")
    print(f"wrote {out_path}")


if __name__ == "__main__":
    main()
