#!/usr/bin/env python3
"""Install harvested mapsui chips into assets and wire navigation JSON (<500).

Does not touch VIP Domain, Cross-only harvest maps, or maps that already have
navigation.behavior. Leaves existing Raklion/Plains/Kalima/Corrupted/Divine/LoD as-is.
"""

from __future__ import annotations

import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS_MAPS = ROOT / "app/src/main/assets/templates/mu/maps"
NAV = ROOT / "app/src/main/assets/navigation/maps"
HARVEST = ROOT / "logs/2026-08-10/mapsui_harvest"
HEADS = HARVEST / "heads_unique"
CHILDREN = HARVEST / "children"

DEFAULT_SWIPE = {
    "x1": 530,
    "y1": 708,
    "x2": 530,
    "y2": 600,
    "duration": 250,
}

# group folder under templates/mu/maps
FOLDER = {
    "lorencia": "Lorencia",
    "noria": "Noria",
    "elbeland": "Elbeland",
    "devias": "Devias",
    "dungeon": "Dungeon",
    "lost_tower": "Lost_tower",
    "atlans": "Atlans",
    "tarkan": "Tarkan",
    "aida": "Aida",
    "icarus": "Icarus",
    "kanturu_ruins": "Kanturu_ruins",
    "kanturu_relics": "Kanturu_relics",
    "swamp_of_peace": "Swamp_of_peace",
    "high_heaven": "High_heaven",
    "purgatory_of_misery": "Purgatory_of_misery",
    "endless_abyss": "Endless_abyss",
    "corridor_of_agony": "Corridor_of_agony",
}

# Multi-floor families: (family_slug, floors, head_src, child_src_dir)
MULTI = [
    ("dungeon", [1, 2, 3]),
    ("lost_tower", [1, 2, 3, 4, 5, 6, 7]),
    ("atlans", [1, 2]),
    ("tarkan", [1, 2]),
    ("aida", [1, 2]),
    ("icarus", [1, 2, 3]),
    ("kanturu_ruins", [1, 2]),
    ("kanturu_relics", [1, 2]),
]

# Singleton map_id → head harvest slug (used as map_option only, no head expand)
SINGLETONS = {
    "lorencia": "lorencia",
    "noria": "noria",
    "elbeland": "elbeland",
    "devias": "devias",
    "swamp_of_peace_1": "swamp_of_peace",
    "high_heaven_1": "high_heaven",
    "purgatory_of_misery_1": "purgatory_of_misery",
    "endless_abyss_1": "endless_abyss",
    "corridor_of_agony_1": "corridor_of_agony",
}

HEAD_INDEX = {
    "lorencia": "02_lorencia.png",
    "noria": "03_noria.png",
    "elbeland": "04_elbeland.png",
    "devias": "05_devias.png",
    "dungeon": "06_dungeon.png",
    "lost_tower": "07_lost_tower.png",
    "atlans": "08_atlans.png",
    "tarkan": "09_tarkan.png",
    "aida": "10_aida.png",
    "icarus": "11_icarus.png",
    "kanturu_ruins": "12_kanturu_ruins.png",
    "kanturu_relics": "13_kanturu_relics.png",
    "swamp_of_peace": "15_swamp_of_peace.png",
    "high_heaven": "25_high_heaven.png",
    "purgatory_of_misery": "26_purgatory_of_misery.png",
    "endless_abyss": "27_endless_abyss.png",
    "corridor_of_agony": "28_corridor_of_agony.png",
}


def pc_mapsui(folder: str, filename: str) -> str:
    return f"templates/maps/{folder}/mapsui/{filename}"


def ensure_dir(p: Path) -> None:
    p.mkdir(parents=True, exist_ok=True)


def copy_chip(src: Path, dst: Path) -> None:
    if not src.exists():
        raise FileNotFoundError(src)
    ensure_dir(dst.parent)
    shutil.copy2(src, dst)
    print(f"  asset {dst.relative_to(ROOT)}")


def head_src(family: str) -> Path:
    uniq = CHILDREN / family / "unique" / f"{family}_head.png"
    if uniq.exists():
        return uniq
    return HEADS / HEAD_INDEX[family]


def install_assets() -> None:
    print("=== install mapsui assets ===")
    for family, floors in MULTI:
        folder = FOLDER[family]
        mapsui = ASSETS_MAPS / folder / "mapsui"
        copy_chip(head_src(family), mapsui / f"{family}_head.png")
        for n in floors:
            src = CHILDREN / family / "unique" / f"{family}_{n}.png"
            copy_chip(src, mapsui / f"{family}_{n}.png")

    for map_id, slug in SINGLETONS.items():
        folder = FOLDER[slug]
        mapsui = ASSETS_MAPS / folder / "mapsui"
        # option chip = harvested zone row (direct teleport, no head expand)
        src = HEADS / HEAD_INDEX[slug]
        copy_chip(src, mapsui / f"{map_id}.png")


def nav_block_multi(family: str, floor: int, folder: str) -> dict:
    return {
        "behavior": "direct_teleport",
        "map_head_template": pc_mapsui(folder, f"{family}_head.png"),
        "map_option_template": pc_mapsui(folder, f"{family}_{floor}.png"),
        "enter_wait": 10,
        "map_list_swipe": dict(DEFAULT_SWIPE),
    }


def nav_block_single(map_id: str, folder: str) -> dict:
    return {
        "behavior": "direct_teleport",
        "map_option_template": pc_mapsui(folder, f"{map_id}.png"),
        "enter_wait": 10,
        "map_list_swipe": dict(DEFAULT_SWIPE),
    }


def wire_json() -> None:
    print("=== wire navigation JSON ===")
    patched = []
    skipped = []

    for family, floors in MULTI:
        folder = FOLDER[family]
        for n in floors:
            mid = f"{family}_{n}"
            path = NAV / f"{mid}.json"
            data = json.loads(path.read_text())
            lvl = (data.get("requirements") or {}).get("min_level", 0)
            if isinstance(lvl, (int, float)) and lvl >= 500:
                skipped.append((mid, "level>=500"))
                continue
            nav = data.get("navigation")
            if isinstance(nav, dict) and nav.get("behavior") in ("direct_teleport", "modal_enter"):
                skipped.append((mid, "already navigable"))
                continue
            data["navigation"] = nav_block_multi(family, n, folder)
            path.write_text(json.dumps(data, indent=2) + "\n")
            patched.append(mid)

    for mid, slug in SINGLETONS.items():
        path = NAV / f"{mid}.json"
        data = json.loads(path.read_text())
        lvl = (data.get("requirements") or {}).get("min_level", 0)
        if isinstance(lvl, (int, float)) and lvl >= 500:
            skipped.append((mid, "level>=500"))
            continue
        nav = data.get("navigation")
        if isinstance(nav, dict) and nav.get("behavior") in ("direct_teleport", "modal_enter"):
            skipped.append((mid, "already navigable"))
            continue
        data["navigation"] = nav_block_single(mid, FOLDER[slug])
        path.write_text(json.dumps(data, indent=2) + "\n")
        patched.append(mid)

    print(f"patched {len(patched)}: {', '.join(patched)}")
    if skipped:
        print(f"skipped {len(skipped)}")
        for mid, why in skipped:
            print(f"  {mid}: {why}")


def verify() -> None:
    print("=== verify ===")
    missing = []
    for family, floors in MULTI:
        folder = FOLDER[family]
        for n in floors:
            mid = f"{family}_{n}"
            data = json.loads((NAV / f"{mid}.json").read_text())
            nav = data.get("navigation") or {}
            for key in ("map_head_template", "map_option_template"):
                rel = nav.get(key, "").replace("templates/maps/", "templates/mu/maps/")
                p = ROOT / "app/src/main/assets" / rel
                if not p.exists():
                    missing.append(str(p))
    for mid, slug in SINGLETONS.items():
        data = json.loads((NAV / f"{mid}.json").read_text())
        nav = data.get("navigation") or {}
        rel = nav.get("map_option_template", "").replace("templates/maps/", "templates/mu/maps/")
        p = ROOT / "app/src/main/assets" / rel
        if not p.exists():
            missing.append(str(p))
    if missing:
        print("MISSING FILES:")
        for m in missing:
            print(" ", m)
        raise SystemExit(1)
    print("all wired templates exist")


def main() -> None:
    install_assets()
    wire_json()
    verify()


if __name__ == "__main__":
    main()
