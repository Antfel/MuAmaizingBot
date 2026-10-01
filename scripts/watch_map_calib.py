#!/usr/bin/env python3
"""Autonomous map affine calibration via bosses/goldens (adb).

Correct procedure per landmark:
  1. Open the in-zone map
  2. Tap a **boss** or **golden** icon (alive or dead is fine)
  3. Assist with Random seals while pathing (optional)
  4. Close map and wait until the character **arrives** (stable HUD coords)
  5. Record (icon_pixel_x, icon_pixel_y, arrived_game_x, arrived_game_y)

Need ≥3 well-separated arrivals, then least-squares affine → navigation JSON.

Usage:
  python3 scripts/watch_map_calib.py --device emulator-5584 --map-id raklion_1 --write
  python3 scripts/watch_map_calib.py --device emulator-5584 --map-id raklion_1 --skip-nav --write
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import time
from pathlib import Path

import cv2
import numpy as np

REPO = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO / "scripts"))
from fit_map_affine import fit_affine, mapping_block, write_map  # noqa: E402

ASSETS = REPO / "app/src/main/assets"
NAV_MAPS = REPO / "app/src/main/assets/navigation/maps"
CALIB_DIR = REPO / "scripts/map_calib_points"
OUT_DIR = REPO / "logs/2026-08-10/map_calib"

MAP_BUTTON = (1155, 45)  # minimap / map chrome @1280×720 (1220,60 often misses)
MAP_CLOSE_FALLBACK = (1188, 55)  # top-right X approx @1280
COORD_ROI = (1185, 100, 1278, 155)  # HUD (x,y) under minimap @1280×720
ZONE_ROI = (300, 90, 1080, 620)  # open-map canvas (excludes left list)

TARGET_TEMPLATES = (
    ("golden", "templates/mu/ui/map/golden_alive.png", 0.55),
    ("boss", "templates/mu/ui/map/boss_alive.png", 0.55),
    ("boss", "templates/mu/ui/map/boss_dead.png", 0.55),
)


def adb(device: str, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["adb", "-s", device, *args],
        check=check,
        capture_output=True,
        text=True,
    )


def screencap(device: str, timeout_s: float = 12.0) -> np.ndarray:
    try:
        raw = subprocess.check_output(
            ["adb", "-s", device, "exec-out", "screencap", "-p"],
            timeout=timeout_s,
        )
    except subprocess.TimeoutExpired as exc:
        raise RuntimeError(f"screencap timed out after {timeout_s:.0f}s") from exc
    arr = np.frombuffer(raw, dtype=np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        raise RuntimeError("screencap decode failed")
    return img


def tap(device: str, x: int, y: int) -> None:
    adb(device, "shell", "input", "tap", str(int(x)), str(int(y)))


def swipe(device: str, x1: int, y1: int, x2: int, y2: int, dur_ms: int = 250) -> None:
    adb(
        device,
        "shell",
        "input",
        "swipe",
        str(x1),
        str(y1),
        str(x2),
        str(y2),
        str(dur_ms),
    )


def list_region_similarity(a: np.ndarray, b: np.ndarray, roi: tuple[int, int, int, int]) -> float:
    """Cheap settle metric: normalized absdiff on list ROI (1 = identical)."""
    x1, y1, x2, y2 = roi
    ca = a[y1:y2, x1:x2]
    cb = b[y1:y2, x1:x2]
    if ca.shape != cb.shape or ca.size == 0:
        return 0.0
    ga = cv2.cvtColor(ca, cv2.COLOR_BGR2GRAY).astype(np.float32)
    gb = cv2.cvtColor(cb, cv2.COLOR_BGR2GRAY).astype(np.float32)
    diff = np.mean(np.abs(ga - gb))
    return float(1.0 - min(diff / 40.0, 1.0))


def wait_list_settled(
    device: str,
    roi: tuple[int, int, int, int],
    timeout_s: float = 4.0,
    interval_s: float = 0.25,
    need_stable: int = 3,
    threshold: float = 0.985,
) -> np.ndarray:
    """Poll until map-list ROI stops moving; return last frame."""
    deadline = time.time() + timeout_s
    prev = screencap(device)
    stable = 0
    while time.time() < deadline:
        time.sleep(interval_s)
        cur = screencap(device)
        sim = list_region_similarity(prev, cur, roi)
        if sim >= threshold:
            stable += 1
            if stable >= need_stable:
                print(f"  list settled sim={sim:.3f}", flush=True)
                return cur
        else:
            stable = 0
        prev = cur
    print("  list settle timeout — continuing", flush=True)
    return prev


def soft_list_scroll(device: str, list_roi: tuple[int, int, int, int]) -> np.ndarray:
    """Short upward drag inside the list column, then wait for settle."""
    # Gentle: ~90px travel, slower duration — avoids flinging past Raklion.
    x = (list_roi[0] + list_roi[2]) // 2
    y1 = list_roi[1] + int((list_roi[3] - list_roi[1]) * 0.72)
    y2 = list_roi[1] + int((list_roi[3] - list_roi[1]) * 0.48)
    swipe(device, x, y1, x, y2, dur_ms=420)
    return wait_list_settled(device, list_roi)


def normalize_coord_ocr(raw: str) -> str:
    s = raw.strip().replace("，", ",").replace("{", "(").replace("}", ")")
    s = re.sub(r"(?<![0-9])[nNilI|](?=\d)", "1", s)
    return s


def parse_coords(raw: str) -> tuple[int, int] | None:
    text = normalize_coord_ocr(raw)
    m = re.search(r"\(\s*(\d+)\s*,\s*(\d+)\s*\)", text)
    if m:
        return int(m.group(1)), int(m.group(2))
    m = re.search(r"(\d+)\s*,\s*(\d+)", text)
    if m:
        return int(m.group(1)), int(m.group(2))
    return None


def ocr_coords(img: np.ndarray, dump: Path | None = None) -> tuple[int, int] | None:
    x1, y1, x2, y2 = COORD_ROI
    crop = img[y1:y2, x1:x2]
    up = cv2.resize(crop, None, fx=5, fy=5, interpolation=cv2.INTER_CUBIC)
    gray = cv2.cvtColor(up, cv2.COLOR_BGR2GRAY)
    candidates: list[np.ndarray] = []
    # Bright HUD text on dark glass — fixed thr beats Otsu when chrome/glow pollutes.
    for thr in (130, 150, 170):
        _, bw = cv2.threshold(gray, thr, 255, cv2.THRESH_BINARY)
        candidates.append(bw)
        _, inv = cv2.threshold(gray, thr, 255, cv2.THRESH_BINARY_INV)
        candidates.append(inv)
    _, otsu = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    candidates.append(otsu)
    if dump is not None:
        dump.parent.mkdir(parents=True, exist_ok=True)
        cv2.imwrite(str(dump), candidates[0])
    parsed_opts: list[tuple[int, int]] = []
    for bw in candidates:
        ok, buf = cv2.imencode(".png", bw)
        if not ok:
            continue
        proc = subprocess.run(
            [
                "tesseract",
                "stdin",
                "stdout",
                "-c",
                "tessedit_char_whitelist=0123456789(),",
                "--psm",
                "7",
            ],
            input=buf.tobytes(),
            capture_output=True,
        )
        raw = proc.stdout.decode("utf-8", errors="ignore").strip()
        parsed = parse_coords(raw)
        if parsed is None:
            m = re.search(r"\(?\s*(\d+)\s*,\s*(\d+)\s*\)?", normalize_coord_ocr(raw))
            if m:
                parsed = int(m.group(1)), int(m.group(2))
        if parsed is not None:
            parsed_opts.append(parsed)
    if not parsed_opts:
        return None
    parsed_opts = [
        c for c in parsed_opts if 0 <= c[0] <= 400 and 0 <= c[1] <= 400
    ]
    if not parsed_opts:
        return None
    # Prefer richest digit count (avoid truncated 170→70 / 161→61).
    parsed_opts.sort(key=lambda c: (len(str(c[0])) + len(str(c[1])), c[0] + c[1]), reverse=True)
    return parsed_opts[0]


def wait_stable_coords(
    device: str,
    timeout_s: float = 45.0,
    settle_s: float = 2.5,
    poll_s: float = 0.7,
    bounds: tuple[int, int, int, int] | None = None,
    require_stable: bool = False,
) -> tuple[int, int] | None:
    """Wait until HUD coords stay the same for settle_s.

    bounds = (x_min, x_max, y_min, y_max) optional OCR sanity gate.
    Uses a short majority window so truncated OCR (170↔70, 161↔61) does not
    reset stability forever.
    If [require_stable] is true, timeout returns None instead of the last guess.
    """
    deadline = time.time() + timeout_s
    recent: list[tuple[int, int]] = []
    last_mode: tuple[int, int] | None = None
    stable_since: float | None = None
    miss_n = 0
    last_heartbeat = 0.0
    while time.time() < deadline:
        now = time.time()
        try:
            img = screencap(device)
        except RuntimeError as exc:
            print(f"  screencap fail: {exc}", flush=True)
            time.sleep(poll_s)
            continue
        coords = ocr_coords(img)
        if coords is None:
            miss_n += 1
            if miss_n == 1 or miss_n % 3 == 0:
                left = max(0.0, deadline - now)
                print(
                    f"  coords OCR miss x{miss_n} ({left:.0f}s left) "
                    f"— cierra el mapa si sigue abierto",
                    flush=True,
                )
            time.sleep(poll_s)
            continue
        if bounds is not None:
            xmin, xmax, ymin, ymax = bounds
            gx, gy = coords
            if not (xmin <= gx <= xmax and ymin <= gy <= ymax):
                print(f"  coords OCR reject {coords} (out of bounds)", flush=True)
                time.sleep(poll_s)
                continue
        miss_n = 0
        recent.append(coords)
        if len(recent) > 8:
            recent = recent[-8:]
        scores: dict[tuple[int, int], float] = {}
        for c in recent:
            rich = len(str(c[0])) + len(str(c[1]))
            scores[c] = scores.get(c, 0.0) + 1.0 + 0.15 * rich
        mode = max(scores, key=scores.get)
        mode_n = sum(1 for c in recent if c == mode)
        if mode != last_mode:
            if (
                last_mode is not None
                and abs(mode[0] - last_mode[0]) <= 2
                and abs(mode[1] - last_mode[1]) <= 2
            ):
                mode = last_mode
            else:
                last_mode = mode
                stable_since = now
                print(f"  coords moving → {mode} (n={mode_n}/{len(recent)})", flush=True)
                time.sleep(poll_s)
                continue
        if (
            mode_n >= 3
            and stable_since is not None
            and now - stable_since >= settle_s
        ):
            print(f"  coords stable {mode}", flush=True)
            return mode
        if now - last_heartbeat >= 5.0:
            last_heartbeat = now
            left = max(0.0, deadline - now)
            print(
                f"  still waiting stable {mode} n={mode_n}/3 ({left:.0f}s left)",
                flush=True,
            )
        time.sleep(poll_s)
    print(
        f"  coords timeout last={last_mode} require_stable={require_stable}",
        flush=True,
    )
    if require_stable:
        return None
    return last_mode


def tap_random_if_available(device: str, img: np.ndarray | None = None) -> bool:
    """Tap stocked Random seal button on open map. False if empty/missing."""
    frame = img if img is not None else screencap(device)
    active = find_template(
        frame, "templates/mu/ui/common/map_random_button.png", threshold=0.78
    )
    empty = find_template(
        frame, "templates/mu/ui/common/map_random_button_empty.png", threshold=0.78
    )
    if active is None:
        if empty is not None:
            print("  Random empty — walk rest", flush=True)
        else:
            print("  Random button miss", flush=True)
        return False
    if empty is not None and empty[2] >= active[2]:
        print("  Random empty (score>=active) — walk rest", flush=True)
        return False
    print(f"  Random tap score={active[2]:.3f} at=({active[0]},{active[1]})", flush=True)
    tap(device, active[0], active[1])
    return True


def collect_point(
    device: str,
    ref_x: int,
    ref_y: int,
    idx: int,
    *,
    bounds: tuple[int, int, int, int] | None = None,
    max_random: int = 3,
    label: str = "target",
) -> dict | None:
    """Tap a map icon, Random-assist, wait until ARRIVAL, then record HUD coords."""
    print(f"\n=== point {idx} {label} tap ref=({ref_x},{ref_y}) ===", flush=True)
    ensure_map_open(device)
    time.sleep(0.35)
    before = ocr_coords(screencap(device))
    tap(device, ref_x, ref_y)
    time.sleep(0.8)  # path paints

    seals = 0
    for _ in range(max_random):
        img = screencap(device)
        if not tap_random_if_available(device, img):
            break
        seals += 1
        time.sleep(1.5)

    print(f"  seals_used={seals} — close map & wait ARRIVAL", flush=True)
    ensure_map_closed(device)
    timeout = 50.0 if seals > 0 else 75.0
    coords = wait_stable_coords(
        device, timeout_s=timeout, settle_s=2.2, poll_s=0.5, bounds=bounds
    )
    if coords is None:
        print("  FAILED: never arrived (no stable coords)", flush=True)
        return None
    gx, gy = coords
    if before is not None:
        moved = abs(gx - before[0]) + abs(gy - before[1])
        if moved < 8:
            print(
                f"  WARN: barely moved from {before} → {coords} (moved={moved}) "
                f"— icon tap may have missed",
                flush=True,
            )
    print(f"  ARRIVED game=({gx},{gy}) from icon pixel=({ref_x},{ref_y})", flush=True)
    return {
        "label": f"{label}_{idx}",
        "ref_x": float(ref_x),
        "ref_y": float(ref_y),
        "game_x": int(gx),
        "game_y": int(gy),
        "seals_used": seals,
        "kind": label,
    }


def find_live_targets(img: np.ndarray) -> list[dict]:
    """Detect boss/golden icons on the currently open map frame."""
    x1, y1, x2, y2 = ZONE_ROI
    hay = img[y1:y2, x1:x2]
    found: list[dict] = []

    def add(kind: str, cx: int, cy: int, score: float) -> None:
        if any(abs(cx - t["x"]) < 36 and abs(cy - t["y"]) < 36 for t in found):
            return
        found.append({"kind": kind, "x": cx, "y": cy, "score": score})

    for kind, rel, thr in TARGET_TEMPLATES:
        path = ASSETS / rel
        tpl = cv2.imread(str(path))
        if tpl is None:
            continue
        for scale in (0.9, 1.0, 1.2, 1.4):
            tw = max(8, int(tpl.shape[1] * scale))
            th = max(8, int(tpl.shape[0] * scale))
            scaled = cv2.resize(tpl, (tw, th))
            if hay.shape[0] < th or hay.shape[1] < tw:
                continue
            res = cv2.matchTemplate(hay, scaled, cv2.TM_CCOEFF_NORMED)
            flat = res.reshape(-1)
            idxs = np.argsort(flat)[::-1]
            local: list[tuple[int, int]] = []
            for idx in idxs[:50]:
                score = float(flat[idx])
                if score < thr:
                    break
                yy, xx = divmod(int(idx), res.shape[1])
                cx = x1 + xx + tw // 2
                cy = y1 + yy + th // 2
                if any(abs(cx - lx) < 30 and abs(cy - ly) < 30 for lx, ly in local):
                    continue
                local.append((cx, cy))
                add(kind, cx, cy, score)

    # Yellow skull fallback (goldens often score poorly vs template)
    hsv = cv2.cvtColor(hay, cv2.COLOR_BGR2HSV)
    mask = cv2.inRange(hsv, (18, 100, 140), (38, 255, 255))
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, np.ones((2, 2), np.uint8))
    n, _labels, stats, cents = cv2.connectedComponentsWithStats(mask)
    for i in range(1, n):
        area = int(stats[i, cv2.CC_STAT_AREA])
        w = int(stats[i, cv2.CC_STAT_WIDTH])
        h = int(stats[i, cv2.CC_STAT_HEIGHT])
        if not (50 <= area <= 350):
            continue
        ar = w / max(h, 1)
        if ar < 0.55 or ar > 1.8 or min(w, h) < 10:
            continue
        cx = int(cents[i][0]) + x1
        cy = int(cents[i][1]) + y1
        add("golden", cx, cy, 0.50 + min(area, 200) / 400.0)

    found.sort(key=lambda t: (-t["score"], t["y"], t["x"]))
    return found


def pick_separated_targets(targets: list[dict], need: int = 3, min_sep: int = 100) -> list[dict]:
    chosen: list[dict] = []
    for t in targets:
        if all(abs(t["x"] - c["x"]) + abs(t["y"] - c["y"]) >= min_sep for c in chosen):
            chosen.append(t)
        if len(chosen) >= need:
            break
    return chosen


def ensure_map_open(device: str) -> np.ndarray:
    img = screencap(device)
    if is_map_open(img) or find_template(
        img, "templates/mu/ui/common/map_random_button.png", 0.70
    ) or find_template(img, "templates/mu/ui/common/map_random_button_empty.png", 0.70):
        return img
    print("opening map…", flush=True)
    for btn in (MAP_BUTTON, (1220, 60), (1185, 55)):
        tap(device, *btn)
        time.sleep(1.2)
        img = screencap(device)
        if is_map_open(img) or find_template(
            img, "templates/mu/ui/common/map_random_button.png", 0.70
        ) or find_template(img, "templates/mu/ui/common/map_random_button_empty.png", 0.70):
            return img
    return img


def collect_via_bosses_goldens(
    device: str,
    *,
    bounds: tuple[int, int, int, int] | None,
    need: int = 3,
    max_random: int = 3,
    seed_points: list[dict] | None = None,
) -> list[dict]:
    """Open map → pick separated boss/golden icons → arrive → record."""
    points: list[dict] = list(seed_points or [])
    used_pixels: list[tuple[int, int]] = [(int(p["ref_x"]), int(p["ref_y"])) for p in points]

    attempts = 0
    while len(points) < need and attempts < need + 4:
        attempts += 1
        img = ensure_map_open(device)
        time.sleep(0.3)
        img = screencap(device)
        targets = find_live_targets(img)
        print(f"live targets={len(targets)}: " + ", ".join(
            f"{t['kind']}@{t['x']},{t['y']}({t['score']:.2f})" for t in targets[:8]
        ), flush=True)
        # drop already used
        fresh = [
            t for t in targets
            if all(abs(t["x"] - ux) + abs(t["y"] - uy) >= 50 for ux, uy in used_pixels)
        ]
        chosen = pick_separated_targets(fresh, need=1, min_sep=100)
        if not chosen:
            # try any remaining
            chosen = fresh[:1]
        if not chosen:
            print("  no unused boss/golden visible — abort", flush=True)
            break
        t = chosen[0]
        row = collect_point(
            device,
            t["x"],
            t["y"],
            len(points) + 1,
            bounds=bounds,
            max_random=max_random,
            label=t["kind"],
        )
        if row is None:
            used_pixels.append((t["x"], t["y"]))  # don't retry same miss forever
            continue
        # reject near-duplicate arrivals
        if any(
            abs(row["game_x"] - p["game_x"]) + abs(row["game_y"] - p["game_y"]) < 20
            for p in points
        ):
            print("  reject: arrival too close to prior point", flush=True)
            used_pixels.append((t["x"], t["y"]))
            continue
        points.append(row)
        used_pixels.append((t["x"], t["y"]))
        print(f"  kept {len(points)}/{need}", flush=True)
    return points


def is_map_open(img: np.ndarray) -> bool:
    if find_template(img, "templates/mu/ui/common/map_random_button.png", 0.70):
        return True
    if find_template(img, "templates/mu/ui/common/map_random_button_empty.png", 0.70):
        return True
    hit = find_template(img, "templates/mu/ui/common/map_window_open.png", threshold=0.70)
    if hit is not None:
        return True
    band = img[20:70, 500:780]
    mean = float(np.mean(band))
    x_roi = img[40:75, 1165:1210]
    x_mean = float(np.mean(x_roi))
    return mean > 100 and x_mean > 80


def find_template(
    img: np.ndarray,
    rel_asset: str,
    threshold: float = 0.72,
    roi: tuple[int, int, int, int] | None = None,
) -> tuple[int, int, float] | None:
    path = ASSETS / rel_asset
    if not path.exists():
        alt = ASSETS / rel_asset.replace("templates/maps/", "templates/mu/maps/")
        path = alt if alt.exists() else path
    if not path.exists() and rel_asset.startswith("templates/") and "/mu/" not in rel_asset:
        path = ASSETS / rel_asset.replace("templates/", "templates/mu/", 1)
    tpl = cv2.imread(str(path))
    if tpl is None:
        return None
    hay = img
    ox = oy = 0
    if roi is not None:
        x1, y1, x2, y2 = roi
        hay = img[y1:y2, x1:x2]
        ox, oy = x1, y1
    if hay.shape[0] < tpl.shape[0] or hay.shape[1] < tpl.shape[1]:
        return None
    res = cv2.matchTemplate(hay, tpl, cv2.TM_CCOEFF_NORMED)
    _, max_val, _, max_loc = cv2.minMaxLoc(res)
    if max_val < threshold:
        return None
    h, w = tpl.shape[:2]
    cx = ox + max_loc[0] + w // 2
    cy = oy + max_loc[1] + h // 2
    return cx, cy, float(max_val)


def ensure_map_closed(device: str) -> None:
    img = screencap(device)
    if not is_map_open(img):
        return
    hit = find_template(img, "templates/mu/ui/common/close_x.png", threshold=0.70)
    if hit is not None:
        tap(device, hit[0], hit[1])
        time.sleep(0.8)
        return
    tap(device, *MAP_CLOSE_FALLBACK)
    time.sleep(0.8)


def load_map_json(map_id: str) -> dict:
    path = NAV_MAPS / f"{map_id}.json"
    return json.loads(path.read_text())


def default_landmarks(map_id: str) -> list[tuple[int, int]]:
    """Deprecated blind fallback — prefer live boss/golden detection."""
    return [(450, 220), (780, 320), (620, 480)]


def sibling_option_paths(option_a: str) -> list[str]:
    """All floor chips in the same mapsui folder (raklion_1/2/3 …)."""
    path = ASSETS / option_a
    if not path.exists():
        return [option_a]
    out: list[str] = []
    for p in sorted(path.parent.glob("*.png")):
        if p.stem.endswith("_head") or p.stem.endswith("head"):
            continue
        # keep relative under assets
        rel = str(p.relative_to(ASSETS))
        out.append(rel)
    return out or [option_a]


def best_floor_option(
    img: np.ndarray,
    option_a: str,
    *,
    list_roi: tuple[int, int, int, int],
    threshold: float = 0.72,
    min_margin: float = 0.03,
) -> tuple[int, int, float] | None:
    """Pick target floor chip; only treat as ambiguous if a rival peaks nearby."""
    siblings = sibling_option_paths(option_a)
    scores: list[tuple[str, float, int, int]] = []
    for rel in siblings:
        hit = find_template(img, rel, threshold=0.50, roi=list_roi)
        if hit is None:
            continue
        cx, cy, sc = hit
        scores.append((rel, sc, cx, cy))
        print(f"    sibling {Path(rel).stem} score={sc:.3f} at=({cx},{cy})", flush=True)
    if not scores:
        return None
    target = next(
        (s for s in scores if s[0] == option_a or s[0].endswith(Path(option_a).name)),
        None,
    )
    if target is None:
        return find_template(img, option_a, threshold=threshold, roi=list_roi)
    if target[1] < threshold:
        return None
    # Different list rows = distinct floors; only confuse when peaks overlap.
    near_rivals = [
        s
        for s in scores
        if s[0] != target[0] and abs(s[2] - target[2]) + abs(s[3] - target[3]) < 28
    ]
    if near_rivals:
        best_other = max(s[1] for s in near_rivals)
        if target[1] - best_other < min_margin:
            print(
                f"  ambiguous floor (same row): target={target[1]:.3f} "
                f"rival={best_other:.3f} — scroll/retry",
                flush=True,
            )
            return None
    return target[2], target[3], target[1]


def navigate_to_map(device: str, map_id: str) -> bool:
    """Open world-map list, expand head if needed, tap floor option, wait load."""
    data = load_map_json(map_id)
    nav = data.get("navigation") or {}
    option = nav.get("map_option_template", "")
    head = nav.get("map_head_template", "")

    def assetify(p: str) -> str:
        p = p.replace("templates/maps/", "templates/mu/maps/")
        if p.startswith("templates/") and "/mu/" not in p:
            p = p.replace("templates/", "templates/mu/", 1)
        return p

    option_a = assetify(option)
    head_a = assetify(head) if head else ""
    # Harvest-proven left parchment list column @1280×720
    list_roi = (145, 130, 420, 500)

    print(f"navigate → {map_id}", flush=True)
    img = ensure_map_open(device)
    img = wait_list_settled(device, list_roi, timeout_s=3.0)
    expanded = False

    for attempt in range(28):
        # Prefer floor option once head was expanded (or no head).
        opt_hit = None
        if expanded or not head_a:
            opt_hit = best_floor_option(img, option_a, list_roi=list_roi)
        if opt_hit is not None:
            cx, cy, score = opt_hit
            print(f"  option hit score={score:.3f} at=({cx},{cy}) attempt={attempt}", flush=True)
            # Re-check settle right before tap
            img = wait_list_settled(device, list_roi, timeout_s=2.5, need_stable=2)
            opt_hit = best_floor_option(img, option_a, list_roi=list_roi)
            if opt_hit is None:
                print("  option lost after settle — retry", flush=True)
                continue
            cx, cy, score = opt_hit
            tap(device, cx, cy)
            time.sleep(float(nav.get("enter_wait", 10)))
            ensure_map_closed(device)
            # HUD name check via tesseract
            img3 = screencap(device)
            name_roi = img3[2:30, 1068:1230]
            up = cv2.resize(name_roi, None, fx=4, fy=4, interpolation=cv2.INTER_CUBIC)
            gray = cv2.cvtColor(up, cv2.COLOR_BGR2GRAY)
            _, bw = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
            ok, buf = cv2.imencode(".png", bw)
            raw = ""
            if ok:
                proc = subprocess.run(
                    ["tesseract", "stdin", "stdout", "--psm", "7"],
                    input=buf.tobytes(),
                    capture_output=True,
                )
                raw = proc.stdout.decode("utf-8", errors="ignore").strip()
            expect = (data.get("name") or map_id).lower()
            raw_l = raw.lower().replace(" ", "")
            print(f"  HUD name OCR={raw!r} expect~{expect!r}", flush=True)
            family_ok = expect.split()[0][:4] in raw_l or (
                "raklion" in raw_l and "raklion" in expect
            )
            # Multi-floor maps: digit in HUD must match map_id suffix (Raklion 3 ≠ raklion_2).
            floor = re.search(r"(\d+)$", map_id)
            if floor:
                hud_floor = re.search(r"(\d+)", raw)
                if hud_floor is None or hud_floor.group(1) != floor.group(1):
                    print(
                        f"  HUD FLOOR MISMATCH: want {map_id} got OCR={raw!r} — abort",
                        flush=True,
                    )
                    return False
            if family_ok:
                return True
            print("  HUD verify failed — abort", flush=True)
            return False

        if head_a and not expanded:
            head_hit = find_template(img, head_a, threshold=0.72, roi=list_roi)
            if head_hit is not None:
                # Critical: never tap while the list is still coasting.
                img = wait_list_settled(device, list_roi, timeout_s=3.0, need_stable=3)
                head_hit = find_template(img, head_a, threshold=0.72, roi=list_roi)
                if head_hit is None:
                    print("  head lost after settle — scroll again", flush=True)
                else:
                    cx, cy, score = head_hit
                    print(f"  head hit score={score:.3f} at=({cx},{cy}) attempt={attempt}", flush=True)
                    tap(device, cx, cy)
                    time.sleep(0.4)
                    img = wait_list_settled(device, list_roi, timeout_s=3.0, need_stable=3)
                    expanded = True
                    continue
        elif head_a and expanded:
            # Scrolled away from children — allow re-expand.
            opt_any = find_template(img, option_a, threshold=0.72, roi=list_roi)
            if opt_any is None:
                print("  expanded group lost — reset head search", flush=True)
                expanded = False
                continue

        print(f"  soft-scroll list attempt={attempt}", flush=True)
        img = soft_list_scroll(device, list_roi)

    print("  failed to find map in list", flush=True)
    return False


def map_coord_bounds(map_id: str) -> tuple[int, int, int, int] | None:
    data = load_map_json(map_id)
    b = data.get("coordinate_bounds") or {}
    if not b:
        return (0, 400, 0, 400)  # generous default for early maps
    return (
        int(b.get("x_min", 0)),
        int(b.get("x_max", 400)),
        int(b.get("y_min", 0)),
        int(b.get("y_max", 400)),
    )


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--device", default="emulator-5584")
    ap.add_argument("--map-id", required=True)
    ap.add_argument(
        "--points",
        nargs="*",
        help="optional manual ref_x,ref_y (icon pixels). Default: auto bosses/goldens",
    )
    ap.add_argument("--skip-nav", action="store_true", help="already on target map")
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--broadcast-to", nargs="*", default=[])
    ap.add_argument("--need", type=int, default=3, help="arrival points to collect")
    ap.add_argument("--max-random", type=int, default=3)
    ap.add_argument(
        "--seed",
        nargs="*",
        help="known good points ref_x,ref_y,game_x,game_y (skip travel)",
    )
    args = ap.parse_args()

    OUT_DIR.mkdir(parents=True, exist_ok=True)

    # stop bot if running
    adb(args.device, "shell", "pidof", "com.example.muamaizingbot", check=False)
    adb(args.device, "shell", "am", "force-stop", "com.example.muamaizingbot", check=False)
    time.sleep(0.5)

    if not args.skip_nav:
        if not navigate_to_map(args.device, args.map_id):
            raise SystemExit("navigation failed")

    bounds = map_coord_bounds(args.map_id)
    print(f"coord_bounds={bounds}", flush=True)
    print("mode=bosses/goldens → tap icon → wait ARRIVAL → record HUD", flush=True)

    seed_points: list[dict] = []
    for tok in args.seed or []:
        parts = [p.strip() for p in tok.split(",")]
        if len(parts) != 4:
            raise SystemExit(f"bad --seed {tok!r} (want ref_x,ref_y,game_x,game_y)")
        rx, ry, gx, gy = (float(parts[0]), float(parts[1]), int(float(parts[2])), int(float(parts[3])))
        seed_points.append(
            {
                "label": f"seed_{len(seed_points)+1}",
                "ref_x": rx,
                "ref_y": ry,
                "game_x": gx,
                "game_y": gy,
                "seals_used": 0,
                "kind": "seed",
            }
        )
        print(f"seed point pixel=({rx:.0f},{ry:.0f}) game=({gx},{gy})", flush=True)

    if args.points:
        # Manual icon pixels still use arrival validation
        points = list(seed_points)
        for i, tok in enumerate(args.points, start=1):
            a, b = tok.split(",")
            row = collect_point(
                args.device,
                int(a),
                int(b),
                len(points) + 1,
                bounds=bounds,
                max_random=args.max_random,
                label="manual",
            )
            if row:
                points.append(row)
            if len(points) >= args.need:
                break
    else:
        points = collect_via_bosses_goldens(
            args.device,
            bounds=bounds,
            need=args.need,
            max_random=args.max_random,
            seed_points=seed_points,
        )

    if len(points) < 3:
        raise SystemExit(f"need ≥3 arrival points, got {len(points)}")

    calib = {
        "map_id": args.map_id,
        "pixel_space": "png_1280_native",
        "method": "boss_golden_arrival",
        "points": points,
    }
    calib_path = CALIB_DIR / f"{args.map_id}.json"
    calib_path.write_text(json.dumps(calib, indent=2) + "\n")
    print(f"wrote {calib_path}", flush=True)

    fit_pts = [(p["ref_x"], p["ref_y"], p["game_x"], p["game_y"]) for p in points]
    ax, ay, res = fit_affine(fit_pts)
    block = mapping_block(ax, ay)
    print(json.dumps(block, indent=2))
    print(f"max_abs_residual={res:.6f}", flush=True)
    if args.write:
        targets = [args.map_id] + list(args.broadcast_to or [])
        for mid in targets:
            out = write_map(mid, block)
            print(f"wrote {out}", flush=True)


if __name__ == "__main__":
    main()
