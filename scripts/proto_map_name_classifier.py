#!/usr/bin/env python3
"""Offline prototype: closed-set map-name classifier (separate from CurrentMapOcr).

Builds a tiny visual bank from assets `*_current.png` (HUD name chips) and
optional live/historical HUD crops, then evaluates:

  1) NCC template match of bank chips inside the HUD name ROI
  2) Scaled gray 1-NN on a fixed 24×160 crop of that ROI
  3) Tesseract OCR + string match (offline proxy for ML Kit path)

Does NOT modify Android production code.

Usage:
  python3 scripts/proto_map_name_classifier.py
  python3 scripts/proto_map_name_classifier.py --device emulator-5584 --live
  python3 scripts/proto_map_name_classifier.py --bench-ms 50
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import time
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets"
NAV = ASSETS / "navigation/maps"
TEMPLATES = ASSETS / "templates/mu/maps"
OUT = ROOT / "logs/2026-08-10/map_name_proto"

# CurrentMapOcr HUD name band @ 1280×720
ROI = (1068, 2, 1230, 30)  # L,T,R,B
FEAT_H, FEAT_W = 24, 160


@dataclass
class BankItem:
    map_id: str
    name: str
    chip_bgr: np.ndarray  # original current template
    feat: np.ndarray  # L2-normalized gray vector


def load_map_catalog() -> dict[str, str]:
    cat: dict[str, str] = {}
    for path in sorted(NAV.glob("*.json")):
        data = json.loads(path.read_text())
        cat[data["id"]] = data.get("name", data["id"])
    return cat


def current_template_map_id(path: Path) -> str | None:
    """Map asset current/*.png → navigation map id."""
    rel = str(path).replace("\\", "/")
    # plain_of_four_winds_1_current.png / kalima_9_current.png / raklion_1_current.png
    stem = path.stem  # plain_of_four_winds_1_current
    if not stem.endswith("_current"):
        return None
    base = stem[: -len("_current")]
    # Kalima files are kalima_N_current but map ids are temple_of_kalima_N
    if re.fullmatch(r"kalima_\d+", base):
        return "temple_of_" + base
    if base == "corrupted_lands_1":
        return "corrupted_lands"
    if base == "divine_realm_1":
        return "divine_realm_1"
    if base == "land_of_demons_1":
        return "land_of_demons_1"
    return base


def gray_feat(bgr: np.ndarray) -> np.ndarray:
    gray = cv2.cvtColor(bgr, cv2.COLOR_BGR2GRAY)
    gray = cv2.resize(gray, (FEAT_W, FEAT_H), interpolation=cv2.INTER_AREA)
    v = gray.astype(np.float64).reshape(-1)
    v = (v - v.mean()) / (v.std() + 1e-6)
    n = np.linalg.norm(v) + 1e-9
    return v / n


def hud_crop(frame: np.ndarray) -> np.ndarray:
    h, w = frame.shape[:2]
    l, t, r, b = ROI
    sx, sy = w / 1280.0, h / 720.0
    l2, t2 = int(l * sx), int(t * sy)
    r2, b2 = int(r * sx), int(b * sy)
    crop = frame[t2:b2, l2:r2]
    if crop.size == 0:
        raise RuntimeError(f"empty HUD crop on {w}x{h}")
    return crop


def build_bank(catalog: dict[str, str]) -> list[BankItem]:
    items: list[BankItem] = []
    for path in sorted(TEMPLATES.rglob("*_current.png")):
        mid = current_template_map_id(path)
        if mid is None or mid not in catalog:
            # keep if recognizable even if catalog miss
            if mid is None:
                continue
        chip = cv2.imread(str(path))
        if chip is None or chip.size == 0:
            continue
        name = catalog.get(mid, mid)
        items.append(BankItem(map_id=mid, name=name, chip_bgr=chip, feat=gray_feat(chip)))
    return items


def classify_ncc(hud: np.ndarray, bank: list[BankItem]) -> tuple[str, float, str]:
    """Slide each chip on HUD ROI; best CCOEFF_NORMED."""
    best_id, best_score, best_name = "unknown", -1.0, ""
    hay = hud
    for item in bank:
        tpl = item.chip_bgr
        if hay.shape[0] < tpl.shape[0] or hay.shape[1] < tpl.shape[1]:
            # scale chip to ROI height
            scale = hay.shape[0] / tpl.shape[0]
            nw = max(1, int(tpl.shape[1] * scale))
            tpl = cv2.resize(tpl, (nw, hay.shape[0]), interpolation=cv2.INTER_AREA)
        if hay.shape[1] < tpl.shape[1]:
            continue
        res = cv2.matchTemplate(hay, tpl, cv2.TM_CCOEFF_NORMED)
        score = float(res.max())
        if score > best_score:
            best_score = score
            best_id = item.map_id
            best_name = item.name
    return best_id, best_score, best_name


def classify_knn(hud: np.ndarray, bank: list[BankItem]) -> tuple[str, float, str]:
    """Cosine / L2 on normalized gray feat (1-NN)."""
    q = gray_feat(hud)
    best_id, best_sim, best_name = "unknown", -1.0, ""
    for item in bank:
        # feat already L2-normalized → dot = cosine similarity
        sim = float(np.dot(q, item.feat))
        if sim > best_sim:
            best_sim = sim
            best_id = item.map_id
            best_name = item.name
    return best_id, best_sim, best_name


def compress(s: str) -> str:
    return re.sub(r"[^a-z0-9]", "", s.lower())


def ocr_tesseract(hud: np.ndarray) -> str:
    up = cv2.resize(hud, None, fx=4, fy=4, interpolation=cv2.INTER_CUBIC)
    gray = cv2.cvtColor(up, cv2.COLOR_BGR2GRAY)
    _, bw = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    ok, buf = cv2.imencode(".png", bw)
    if not ok:
        return ""
    proc = subprocess.run(
        ["tesseract", "stdin", "stdout", "--psm", "7"],
        input=buf.tobytes(),
        capture_output=True,
    )
    return proc.stdout.decode("utf-8", errors="ignore").strip()


def classify_ocr(raw: str, catalog: dict[str, str]) -> tuple[str, float, str]:
    ocr_c = compress(raw)
    if not ocr_c:
        return "unknown", 0.0, ""
    best_id, best_len, best_name = "unknown", 0, ""
    for mid, name in catalog.items():
        exp = compress(name)
        if exp and exp in ocr_c and len(exp) >= best_len:
            # digit-safe: avoid Raklion 1 matching inside Raklion 11
            idx = ocr_c.find(exp)
            end = idx + len(exp)
            if end < len(ocr_c) and ocr_c[end].isdigit():
                continue
            best_len = len(exp)
            best_id = mid
            best_name = name
    score = best_len / max(len(ocr_c), 1)
    return best_id, score, best_name


def screencap(device: str) -> np.ndarray:
    raw = subprocess.check_output(["adb", "-s", device, "exec-out", "screencap", "-p"])
    img = cv2.imdecode(np.frombuffer(raw, dtype=np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise RuntimeError("screencap decode failed")
    return img


def collect_eval_frames() -> list[tuple[str, Path, str]]:
    """(label_map_id, frame_path, note)."""
    frames: list[tuple[str, Path, str]] = []
    # Historical plains HUD
    for rel, mid in [
        ("logs/2026-08-10/map_calib/before_swipe.png", "plain_of_four_winds_1"),
        ("logs/2026-08-10/map_calib/scr_after_stop.png", "plain_of_four_winds_1"),
        ("logs/2026-08-10/map_calib/scr_pre_nav.png", "plain_of_four_winds_1"),
        ("logs/2026-08-08/wire_1_to_2/ANN_1_hud.png", "land_of_demons_1"),
        ("logs/2026-08-08/wire_1_to_2/S1_hud_wire1.png", "land_of_demons_1"),
        ("logs/2026-08-08/wire_1_to_2/ANN_4_hud_wire2.png", "land_of_demons_1"),
    ]:
        p = ROOT / rel
        if p.exists():
            frames.append((mid, p, "historical"))
    return frames


def bench(fn, n: int) -> float:
    t0 = time.perf_counter()
    for _ in range(n):
        fn()
    return (time.perf_counter() - t0) * 1000.0 / n


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--device", default="emulator-5584")
    ap.add_argument("--live", action="store_true", help="include live screencap labeled as plains_1 guess")
    ap.add_argument("--live-label", default="plain_of_four_winds_1")
    ap.add_argument("--bench-ms", type=int, default=30, help="timing iterations")
    ap.add_argument("--ncc-threshold", type=float, default=0.55)
    args = ap.parse_args()

    OUT.mkdir(parents=True, exist_ok=True)
    catalog = load_map_catalog()
    bank = build_bank(catalog)
    print(f"bank size={len(bank)} maps with *_current.png")
    print("bank ids:", ", ".join(sorted({b.map_id for b in bank})))

    # Sibling stress: match each current chip against every other chip via knn/ncc on padded canvas
    print("\n=== sibling confusion (current chip vs bank, leave-one-out style) ===")
    confusions = []
    for i, probe in enumerate(bank):
        others = [b for j, b in enumerate(bank) if j != i]
        # pad probe as "hud-like" canvas
        canvas = np.zeros((ROI[3] - ROI[1], ROI[2] - ROI[0], 3), dtype=np.uint8)
        canvas[:] = (40, 30, 50)  # dark purple-ish
        ph, pw = probe.chip_bgr.shape[:2]
        # place chip left-aligned like HUD text
        ch = min(ph, canvas.shape[0])
        cw = min(pw, canvas.shape[1])
        scaled = cv2.resize(probe.chip_bgr, (cw, ch), interpolation=cv2.INTER_AREA)
        canvas[0:ch, 0:cw] = scaled
        pred_ncc, score_ncc, _ = classify_ncc(canvas, others + [probe])  # includes self → should be self
        pred_knn, score_knn, _ = classify_knn(canvas, bank)
        # true LOO knn
        pred_loo, score_loo, _ = classify_knn(canvas, others)
        ok_self = pred_knn == probe.map_id
        ok_loo = pred_loo == probe.map_id
        if not ok_loo:
            confusions.append((probe.map_id, pred_loo, score_loo))
        print(
            f"  {probe.map_id:28s} self_knn={pred_knn}({score_knn:.3f}) "
            f"loo={pred_loo}({score_loo:.3f}) {'OK' if ok_loo else 'CONFUSED'}"
        )
    print(f"LOO confusions: {len(confusions)}/{len(bank)}")
    for a, b, s in confusions:
        print(f"  {a} → {b} sim={s:.3f}")

    # Frame eval
    print("\n=== frame eval (historical + optional live) ===")
    rows = []
    frames = collect_eval_frames()
    if args.live:
        try:
            live = screencap(args.device)
            live_path = OUT / "live_scr.png"
            cv2.imwrite(str(live_path), live)
            frames.append((args.live_label, live_path, "live"))
            print(f"live screencap → {live_path}")
        except Exception as e:
            print(f"live screencap failed: {e}")

    for truth, path, note in frames:
        frame = cv2.imread(str(path))
        if frame is None:
            continue
        hud = hud_crop(frame)
        crop_path = OUT / f"crop_{path.stem}.png"
        cv2.imwrite(str(crop_path), hud)

        ncc_id, ncc_s, ncc_name = classify_ncc(hud, bank)
        knn_id, knn_s, knn_name = classify_knn(hud, bank)
        raw = ocr_tesseract(hud)
        ocr_id, ocr_s, ocr_name = classify_ocr(raw, catalog)

        rows.append(
            {
                "file": path.name,
                "note": note,
                "truth": truth,
                "ncc": ncc_id,
                "ncc_score": round(ncc_s, 3),
                "ncc_ok": ncc_id == truth,
                "knn": knn_id,
                "knn_score": round(knn_s, 3),
                "knn_ok": knn_id == truth,
                "ocr_raw": raw,
                "ocr": ocr_id,
                "ocr_ok": ocr_id == truth,
            }
        )
        print(
            f"[{note}] {path.name}\n"
            f"  truth={truth}\n"
            f"  NCC  → {ncc_id} ({ncc_s:.3f}) {'OK' if ncc_id == truth else 'MISS'}\n"
            f"  1NN  → {knn_id} ({knn_s:.3f}) {'OK' if knn_id == truth else 'MISS'}\n"
            f"  OCR  → {ocr_id} raw={raw!r} {'OK' if ocr_id == truth else 'MISS'}"
        )

    if rows:
        n = len(rows)
        print("\n=== summary ===")
        print(f"frames={n}")
        print(f"NCC accuracy: {sum(r['ncc_ok'] for r in rows)}/{n}")
        print(f"1NN accuracy: {sum(r['knn_ok'] for r in rows)}/{n}")
        print(f"OCR accuracy: {sum(r['ocr_ok'] for r in rows)}/{n}")

    # Timing on first available hud
    sample = None
    for _, path, _ in frames:
        fr = cv2.imread(str(path))
        if fr is not None:
            sample = hud_crop(fr)
            break
    if sample is not None and args.bench_ms > 0:
        n = args.bench_ms
        print(f"\n=== timing ms/call (n={n}) ===")
        ms_ncc = bench(lambda: classify_ncc(sample, bank), n)
        ms_knn = bench(lambda: classify_knn(sample, bank), n)
        ms_ocr = bench(lambda: ocr_tesseract(sample), max(5, n // 5))
        print(f"NCC template bank ({len(bank)} chips): {ms_ncc:.2f} ms")
        print(f"1NN gray feat:                      {ms_knn:.2f} ms")
        print(f"Tesseract OCR (proxy):              {ms_ocr:.2f} ms")

    # Write results JSON
    report = {
        "bank_size": len(bank),
        "bank_ids": sorted({b.map_id for b in bank}),
        "loo_confusions": [{"truth": a, "pred": b, "sim": s} for a, b, s in confusions],
        "frames": rows,
    }
    report_path = OUT / "eval_report.json"
    report_path.write_text(json.dumps(report, indent=2) + "\n")
    print(f"\nwrote {report_path}")
    print("NOTE: prototype only — production CurrentMapOcr unchanged.")


if __name__ == "__main__":
    main()
