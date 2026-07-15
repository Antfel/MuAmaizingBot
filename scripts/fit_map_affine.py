#!/usr/bin/env python3
"""Fit a 2D affine map from REF pixels to in-game HUD coordinates.

Pixels are in logical REF space (2560x1440), matching SpotPicker / FarmLocation.x,y.
Game coords are the values shown on the MU Immortal HUD.

Usage:
  python3 scripts/fit_map_affine.py \\
    --points 800,400,120,85  1800,400,210,90  1200,900,160,140 \\
    [--map-id plain_of_four_winds_1] [--write]

  Or JSON lines / file:
  python3 scripts/fit_map_affine.py --points-file scripts/map_calib_points/plains_1.json [--write]

Point file format:
  {
    "map_id": "plain_of_four_winds_1",
    "points": [
      {"ref_x": 800, "ref_y": 400, "game_x": 120, "game_y": 85},
      ...
    ]
  }

Requires at least 3 non-colinear points. Uses least squares if more than 3.
"""

from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path


REPO = Path(__file__).resolve().parents[1]
NAV_MAPS = REPO / "app/src/main/assets/navigation/maps"


def parse_point_token(token: str) -> tuple[float, float, float, float]:
    parts = [p.strip() for p in token.replace(" ", "").split(",")]
    if len(parts) != 4:
        raise ValueError(f"expected ref_x,ref_y,game_x,game_y got {token!r}")
    return tuple(float(p) for p in parts)  # type: ignore[return-value]


def load_points(args: argparse.Namespace) -> tuple[str | None, list[tuple[float, float, float, float]]]:
    map_id = args.map_id
    points: list[tuple[float, float, float, float]] = []
    if args.points_file:
        data = json.loads(Path(args.points_file).read_text())
        map_id = data.get("map_id", map_id)
        for row in data["points"]:
            points.append(
                (
                    float(row["ref_x"]),
                    float(row["ref_y"]),
                    float(row["game_x"]),
                    float(row["game_y"]),
                )
            )
    for token in args.points or []:
        points.append(parse_point_token(token))
    if len(points) < 3:
        raise SystemExit("need at least 3 points")
    return map_id, points


def fit_affine(points: list[tuple[float, float, float, float]]) -> tuple[list[float], list[float], float]:
    """Return (coord_x[a,b,c], coord_y[d,e,f], max_abs_residual)."""
    # Solve least squares for each row independently:
    # [px py 1] [a b c]^T = gx
    n = len(points)
    # Build normal equations 3x3
    ata = [[0.0] * 3 for _ in range(3)]
    atbx = [0.0] * 3
    atby = [0.0] * 3
    for px, py, gx, gy in points:
        row = (px, py, 1.0)
        for i in range(3):
            atbx[i] += row[i] * gx
            atby[i] += row[i] * gy
            for j in range(3):
                ata[i][j] += row[i] * row[j]

    def solve(ata_m: list[list[float]], atb: list[float]) -> list[float]:
        # Gaussian elimination with partial pivot
        m = [ata_m[i][:] + [atb[i]] for i in range(3)]
        for col in range(3):
            pivot = max(range(col, 3), key=lambda r: abs(m[r][col]))
            if abs(m[pivot][col]) < 1e-12:
                raise SystemExit("points are degenerate (colinear / singular)")
            m[col], m[pivot] = m[pivot], m[col]
            div = m[col][col]
            for j in range(col, 4):
                m[col][j] /= div
            for r in range(3):
                if r == col:
                    continue
                factor = m[r][col]
                for j in range(col, 4):
                    m[r][j] -= factor * m[col][j]
        return [m[i][3] for i in range(3)]

    ax = solve(ata, atbx)
    ay = solve(ata, atby)

    max_res = 0.0
    for px, py, gx, gy in points:
        px_hat = ax[0] * px + ax[1] * py + ax[2]
        py_hat = ay[0] * px + ay[1] * py + ay[2]
        max_res = max(max_res, abs(px_hat - gx), abs(py_hat - gy))
    return ax, ay, max_res


def mapping_block(coord_x: list[float], coord_y: list[float]) -> dict:
    return {
        "type": "affine",
        "version": 1,
        "source_image_size": {"width": 2560, "height": 1440},
        "transform": {
            "coord_x": [round(v, 9) for v in coord_x],
            "coord_y": [round(v, 9) for v in coord_y],
        },
    }


def write_map(map_id: str, block: dict) -> Path:
    path = NAV_MAPS / f"{map_id}.json"
    if not path.exists():
        raise SystemExit(f"map json not found: {path}")
    data = json.loads(path.read_text())
    data["coordinate_mapping"] = block
    # Ensure maint dims match current 1280 capture PNGs when present
    maint = data.setdefault("maintenance", {})
    if "image_width" in maint or "map_ui_image" in maint:
        maint["image_width"] = 1280
        maint["image_height"] = 720
    path.write_text(json.dumps(data, indent=2) + "\n")
    return path


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--points", nargs="*", help="ref_x,ref_y,game_x,game_y tokens")
    parser.add_argument("--points-file", help="JSON points file")
    parser.add_argument("--map-id", help="navigation map id (e.g. plain_of_four_winds_1)")
    parser.add_argument("--write", action="store_true", help="write coordinate_mapping into map JSON")
    parser.add_argument(
        "--broadcast-to",
        nargs="*",
        help="also write the same mapping to these map ids (e.g. temple_of_kalima_2 .. _9)",
    )
    args = parser.parse_args()

    map_id, points = load_points(args)
    coord_x, coord_y, max_res = fit_affine(points)
    block = mapping_block(coord_x, coord_y)

    print(json.dumps(block, indent=2))
    print(f"max_abs_residual={max_res:.6f} over {len(points)} points", file=sys.stderr)

    if args.write:
        if not map_id:
            raise SystemExit("--write requires --map-id or map_id in points file")
        targets = [map_id] + list(args.broadcast_to or [])
        for mid in targets:
            out = write_map(mid, block)
            print(f"wrote {out}", file=sys.stderr)


if __name__ == "__main__":
    main()
