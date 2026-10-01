#!/usr/bin/env python3
"""Update swamp / island-map calib state file.

Usage:
  python3 scripts/set_swamp_island_state.py --island 2
  python3 scripts/set_swamp_island_state.py --portal 1 2
  python3 scripts/set_swamp_island_state.py --clear-portal
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--island", type=int)
    ap.add_argument("--portal", nargs=2, type=int, metavar=("FROM", "TO"))
    ap.add_argument("--clear-portal", action="store_true")
    ap.add_argument("--map-id", default="swamp_of_peace_1")
    args = ap.parse_args()

    path = REPO / "scripts/map_calib_points" / f"{args.map_id}.island_state.json"
    state: dict = {"island": 1, "pending_portal": None}
    if path.exists():
        state.update(json.loads(path.read_text()))
    if args.island is not None:
        state["island"] = args.island
    if args.portal:
        state["pending_portal"] = {"from": args.portal[0], "to": args.portal[1]}
    if args.clear_portal:
        state["pending_portal"] = None
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(state, indent=2) + "\n")
    print(json.dumps(state))


if __name__ == "__main__":
    main()
