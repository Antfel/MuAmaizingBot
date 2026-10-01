#!/usr/bin/env python3
"""One-shot: migrate map affine + calib points from REF 2560×1440 → 1280×720.

Affine: gx = a*px2560 + b*py2560 + c with px1280 = px2560/2
  → [a,b,c] becomes [2a, 2b, c] (same for coord_y).

Also forces maintenance.image_width/height to 1280×720 and halves ref_*
in scripts/map_calib_points/*.json when still in 2560 space.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
NAV_MAPS = REPO / "app/src/main/assets/navigation/maps"
CALIB = REPO / "scripts/map_calib_points"

OLD_W, OLD_H = 2560, 1440
NEW_W, NEW_H = 1280, 720


def migrate_affine(mapping: dict) -> bool:
    """Return True if modified."""
    if mapping.get("type") != "affine":
        return False
    src = mapping.get("source_image_size") or {}
    w = int(src.get("width", 0))
    h = int(src.get("height", 0))
    if w == NEW_W and h == NEW_H:
        return False
    if w not in (0, OLD_W) or h not in (0, OLD_H):
        print(f"  skip unexpected source {w}x{h}", file=sys.stderr)
        return False
    transform = mapping.get("transform") or {}
    cx = list(transform.get("coord_x") or [])
    cy = list(transform.get("coord_y") or [])
    if len(cx) != 3 or len(cy) != 3:
        print("  skip bad transform", file=sys.stderr)
        return False
    # a,b ×2; c unchanged
    transform["coord_x"] = [round(cx[0] * 2, 9), round(cx[1] * 2, 9), round(cx[2], 9)]
    transform["coord_y"] = [round(cy[0] * 2, 9), round(cy[1] * 2, 9), round(cy[2], 9)]
    mapping["transform"] = transform
    mapping["source_image_size"] = {"width": NEW_W, "height": NEW_H}
    return True


def migrate_map(path: Path) -> list[str]:
    data = json.loads(path.read_text())
    notes: list[str] = []
    maint = data.get("maintenance")
    if isinstance(maint, dict):
        if maint.get("image_width") != NEW_W or maint.get("image_height") != NEW_H:
            maint["image_width"] = NEW_W
            maint["image_height"] = NEW_H
            notes.append("maintenance→1280x720")
    mapping = data.get("coordinate_mapping")
    if isinstance(mapping, dict) and migrate_affine(mapping):
        notes.append("affine→1280")
    # Halve wire_switch.popup_scroll when clearly REF-2560 (center x≈1280).
    # Leave map_list_swipe and smaller scrolls already authored @1280.
    ws = data.get("wire_switch")
    if isinstance(ws, dict):
        scroll = ws.get("popup_scroll")
        if isinstance(scroll, dict):
            x1 = scroll.get("x1")
            if isinstance(x1, (int, float)) and x1 >= 1200:
                for k in ("x1", "y1", "x2", "y2"):
                    if k in scroll and isinstance(scroll[k], (int, float)):
                        scroll[k] = int(round(scroll[k] / 2))
                notes.append("wire_switch.popup_scroll÷2")
    if notes:
        path.write_text(json.dumps(data, indent=2) + "\n")
    return notes


def migrate_calib(path: Path) -> bool:
    data = json.loads(path.read_text())
    space = data.get("pixel_space", "")
    points = data.get("points") or []
    if not points:
        return False
    # Already native?
    if "1280" in str(space) and "converted_to_ref" not in str(space):
        # check magnitudes
        max_ref = max(float(p.get("ref_x", 0)) for p in points)
        if max_ref <= 1280:
            data["pixel_space"] = "png_1280_native"
            path.write_text(json.dumps(data, indent=2) + "\n")
            return True
    changed = False
    for p in points:
        rx = float(p.get("ref_x", 0))
        ry = float(p.get("ref_y", 0))
        if rx > 1280 or ry > 720 or "converted_to_ref" in str(space) or space == "":
            # Prefer source_png_* if present
            if "source_png_x" in p and "source_png_y" in p:
                p["ref_x"] = float(p["source_png_x"])
                p["ref_y"] = float(p["source_png_y"])
            else:
                p["ref_x"] = round(rx / 2, 3)
                p["ref_y"] = round(ry / 2, 3)
            changed = True
    if changed or "converted_to_ref" in str(space):
        data["pixel_space"] = "png_1280_native"
        path.write_text(json.dumps(data, indent=2) + "\n")
        return True
    return False


def verify_calib(path: Path) -> float | None:
    """Re-fit residual using fit_map_affine math; return max residual or None."""
    from fit_map_affine import fit_affine  # type: ignore

    data = json.loads(path.read_text())
    pts = [
        (float(p["ref_x"]), float(p["ref_y"]), float(p["game_x"]), float(p["game_y"]))
        for p in data.get("points") or []
    ]
    if len(pts) < 3:
        return None
    _, _, max_res = fit_affine(pts)
    return max_res


def main() -> None:
    print("=== maps ===")
    for path in sorted(NAV_MAPS.glob("*.json")):
        notes = migrate_map(path)
        if notes:
            print(f"{path.name}: {', '.join(notes)}")

    print("=== calib points ===")
    sys.path.insert(0, str(REPO / "scripts"))
    for path in sorted(CALIB.glob("*.json")):
        if migrate_calib(path):
            print(f"updated {path.name}")
        res = verify_calib(path)
        if res is not None:
            print(f"  {path.name} max_abs_residual={res:.6f}")


if __name__ == "__main__":
    main()
