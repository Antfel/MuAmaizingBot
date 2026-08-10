#!/usr/bin/env python3
"""Capture focus-HUD samples from a BlueStacks emulator for portrait detector work.

Usage:
  # Interactive: you label each shot after capture
  ./scripts/capture_focus_portrait.py emulator-5584

  # Burst N frames without prompting (label later)
  ./scripts/capture_focus_portrait.py emulator-5584 --burst 20 --tag session1

Saves full PNG under logs/YYYY-MM-DD/focus_portrait_dataset/raw/
and a provisional top-HUD crop under .../crops/
Appends a row to .../labels/manifest.csv (label may be pending).
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LABELS = ("pj", "boss", "empty", "other", "pending")

# Provisional portrait / focus panel band @ 1280×720 (refine with user marks).
# Covers emblem/portrait + clear-X area of the top-center focus HUD.
HUD_CROP = (470, 0, 620, 90)  # left, top, right, bottom


def run(cmd: list[str]) -> None:
    subprocess.check_call(cmd)


def today_dir() -> Path:
    d = ROOT / "logs" / dt.date.today().isoformat() / "focus_portrait_dataset"
    (d / "raw").mkdir(parents=True, exist_ok=True)
    (d / "crops").mkdir(parents=True, exist_ok=True)
    (d / "labels").mkdir(parents=True, exist_ok=True)
    return d


def manifest_path(base: Path) -> Path:
    return base / "labels" / "manifest.csv"


def ensure_manifest(path: Path) -> None:
    if path.exists():
        return
    with path.open("w", newline="") as f:
        w = csv.writer(f)
        w.writerow(
            [
                "timestamp",
                "device",
                "tag",
                "label",
                "raw_file",
                "crop_file",
                "notes",
            ]
        )


def adb_screencap(device: str, dest: Path) -> None:
    remote = "/sdcard/focus_portrait_cap.png"
    run(["adb", "-s", device, "shell", "screencap", "-p", remote])
    run(["adb", "-s", device, "pull", remote, str(dest)])


def crop_hud(full_png: Path, crop_png: Path) -> None:
    # Prefer sips on macOS (no PIL required).
    l, t, r, b = HUD_CROP
    w, h = r - l, b - t
    # sips --cropToHeightWidth crops from center by default; use -c with offset via
    # a temp bmp + python if needed. Fallback: ImageMagick-less crop via sips pad trick
    # is awkward — use pure Python BMP path after converting.
    bmp = full_png.with_suffix(".bmp")
    run(["sips", "-s", "format", "bmp", str(full_png), "--out", str(bmp)])
    data = bytearray(bmp.read_bytes())
    import struct

    off = struct.unpack_from("<I", data, 10)[0]
    fw, fh = struct.unpack_from("<ii", data, 18)
    fw, fh = abs(fw), abs(fh)
    rs = ((fw * 32 + 31) // 32) * 4

    def get(x: int, y: int) -> tuple[int, int, int]:
        i = off + y * rs + x * 4
        return data[i + 2], data[i + 1], data[i]

    row = ((w * 3 + 3) // 4) * 4
    out = bytearray(54 + row * h)
    struct.pack_into("<2sIHHI", out, 0, b"BM", 54 + row * h, 0, 0, 54)
    struct.pack_into("<IiiHHIIiiII", out, 14, 40, w, -h, 1, 24, 0, row * h, 0, 0, 0, 0)
    for y in range(h):
        for x in range(w):
            R, G, B = get(l + x, t + y)
            i = 54 + y * row + x * 3
            out[i] = B
            out[i + 1] = G
            out[i + 2] = R
    crop_bmp = crop_png.with_suffix(".bmp")
    crop_bmp.write_bytes(out)
    run(["sips", "-s", "format", "png", str(crop_bmp), "--out", str(crop_png)])
    crop_bmp.unlink(missing_ok=True)
    bmp.unlink(missing_ok=True)


def append_row(
    manifest: Path,
    *,
    device: str,
    tag: str,
    label: str,
    raw: Path,
    crop: Path,
    notes: str,
) -> None:
    with manifest.open("a", newline="") as f:
        csv.writer(f).writerow(
            [
                dt.datetime.now().isoformat(timespec="seconds"),
                device,
                tag,
                label,
                raw.name,
                crop.name,
                notes,
            ]
        )


def prompt_label() -> str:
    print("Label? [pj|boss|empty|other|pending] (default pending): ", end="", flush=True)
    raw = sys.stdin.readline().strip().lower()
    if not raw:
        return "pending"
    if raw not in LABELS:
        print(f"  unknown '{raw}' → pending")
        return "pending"
    return raw


def capture_one(device: str, tag: str, label: str | None, notes: str) -> None:
    base = today_dir()
    man = manifest_path(base)
    ensure_manifest(man)
    ts = dt.datetime.now().strftime("%H%M%S_%f")[:-3]
    raw = base / "raw" / f"{ts}_{tag}.png"
    crop = base / "crops" / f"{ts}_{tag}_hud.png"
    print(f"Capturing {device} → {raw.name}")
    adb_screencap(device, raw)
    crop_hud(raw, crop)
    use_label = label if label else prompt_label()
    append_row(
        man,
        device=device,
        tag=tag,
        label=use_label,
        raw=raw,
        crop=crop,
        notes=notes,
    )
    print(f"  saved crop={crop.name} label={use_label}")


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("device", help="adb serial, e.g. emulator-5584")
    p.add_argument("--tag", default="cap", help="filename tag")
    p.add_argument("--burst", type=int, default=0, help="capture N frames, label=pending")
    p.add_argument(
        "--label",
        choices=LABELS,
        default=None,
        help="force label (skip prompt); burst implies pending unless set",
    )
    p.add_argument("--notes", default="", help="free text for manifest")
    args = p.parse_args()

    if args.burst > 0:
        for i in range(args.burst):
            capture_one(
                args.device,
                f"{args.tag}_{i+1:02d}",
                args.label or "pending",
                args.notes,
            )
        return 0

    capture_one(args.device, args.tag, args.label, args.notes)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
