#!/usr/bin/env python3
"""Pre-scale template PNGs from the reference resolution into sibling folders.

Uses OpenCV INTER_AREA for downscaling (sharper UI/text than Pillow LANCZOS).

Layout:
  app/src/main/assets/templates/2560x1440/mu/...  (source / originals)
  app/src/main/assets/templates/1920x1080/mu/...  (generated)
  app/src/main/assets/templates/1280x720/mu/...   (generated)

Run from repo root:
  python3 scripts/generate_template_resolutions.py
"""

from __future__ import annotations

import sys
from pathlib import Path

try:
    import cv2
    import numpy as np
except ImportError:
    print("Install OpenCV: pip install opencv-python-headless numpy", file=sys.stderr)
    sys.exit(1)

REPO_ROOT = Path(__file__).resolve().parents[1]
ASSETS_TEMPLATES = REPO_ROOT / "app" / "src" / "main" / "assets" / "templates"

REF_WIDTH = 2560
REF_HEIGHT = 1440
REF_KEY = f"{REF_WIDTH}x{REF_HEIGHT}"

TARGETS = {
    "1920x1080": (1920, 1080),
    "1280x720": (1280, 720),
}


def uniform_scale(target_w: int, target_h: int) -> float:
    return min(target_w / REF_WIDTH, target_h / REF_HEIGHT)


def resize_rgba(img_rgba: np.ndarray, new_w: int, new_h: int) -> np.ndarray:
    """Downscale with INTER_AREA; optional half-step for large reductions."""
    h, w = img_rgba.shape[:2]
    if new_w == w and new_h == h:
        return img_rgba.copy()

    current = img_rgba
    cw, ch = w, h
    # Progressive halving keeps thin UI strokes sharper than one-shot resize.
    while cw // 2 >= int(new_w * 1.25) and ch // 2 >= int(new_h * 1.25):
        cw //= 2
        ch //= 2
        current = cv2.resize(current, (cw, ch), interpolation=cv2.INTER_AREA)

    return cv2.resize(current, (new_w, new_h), interpolation=cv2.INTER_AREA)


def scale_image(src: Path, dst: Path, scale: float) -> None:
    raw = src.read_bytes()
    buf = np.frombuffer(raw, dtype=np.uint8)
    img = cv2.imdecode(buf, cv2.IMREAD_UNCHANGED)
    if img is None:
        raise RuntimeError(f"Failed to decode {src}")

    if img.ndim == 2:
        img = cv2.cvtColor(img, cv2.COLOR_GRAY2BGRA)
    elif img.shape[2] == 3:
        img = cv2.cvtColor(img, cv2.COLOR_BGR2BGRA)

    new_w = max(1, round(img.shape[1] * scale))
    new_h = max(1, round(img.shape[0] * scale))
    resized = resize_rgba(img, new_w, new_h)

    dst.parent.mkdir(parents=True, exist_ok=True)
    ok, encoded = cv2.imencode(".png", resized)
    if not ok:
        raise RuntimeError(f"Failed to encode {dst}")
    dst.write_bytes(encoded.tobytes())


def main() -> int:
    src_root = ASSETS_TEMPLATES / REF_KEY / "mu"
    legacy_root = ASSETS_TEMPLATES / "mu"

    if legacy_root.is_dir() and not src_root.is_dir():
        print(f"Moving legacy {legacy_root} -> {src_root.parent}/")
        src_root.parent.mkdir(parents=True, exist_ok=True)
        legacy_root.rename(src_root)
    elif not src_root.is_dir():
        print(f"Source not found: {src_root}", file=sys.stderr)
        return 1

    png_files = sorted(src_root.rglob("*.png"))
    if not png_files:
        print(f"No PNG files under {src_root}", file=sys.stderr)
        return 1

    print(f"Reference: {src_root} ({len(png_files)} PNGs)")

    for key, (tw, th) in TARGETS.items():
        scale = uniform_scale(tw, th)
        dst_root = ASSETS_TEMPLATES / key / "mu"
        print(f"\nGenerating {key} scale={scale:.4f} -> {dst_root}")
        for src in png_files:
            rel = src.relative_to(src_root)
            dst = dst_root / rel
            scale_image(src, dst, scale)
        print(f"  wrote {len(png_files)} files")

    print("\nDone.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
