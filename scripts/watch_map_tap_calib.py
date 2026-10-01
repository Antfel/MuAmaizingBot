#!/usr/bin/env python3
"""Fast interactive map calib: intercept YOUR mouse clicks on the BlueStacks window.

No getevent/root needed. Watches host left-clicks over a BlueStacks window,
maps them into device 1280×720 pixels, waits for HUD arrival, records points.

Flow:
  1. Open the in-zone map in BlueStacks
  2. Click bosses/goldens with the mouse
  3. Close map (auto if still open) → wait stable HUD coords
  4. Repeat until --need; fit (+ optional --write)

Usage:
  python3 scripts/watch_map_tap_calib.py --device emulator-5584 \\
      --map-id corrupted_lands --window Ridax --need 6 --write

  # auto-pick window by matching adb screencap (best effort):
  python3 scripts/watch_map_tap_calib.py --device emulator-5584 --map-id corrupted_lands --write

Keys: undo | fit | write | quit
"""

from __future__ import annotations

import argparse
import json
import re
import select
import subprocess
import sys
import time
from pathlib import Path

import cv2
import numpy as np

REPO = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO / "scripts"))
from fit_map_affine import fit_affine, mapping_block, write_map  # noqa: E402
import watch_map_calib as w  # noqa: E402

CALIB_DIR = REPO / "scripts/map_calib_points"
SWIFT_SRC = REPO / "scripts/host_mouse_watch.swift"


TITLEBAR_FALLBACK_PX = 32
# Parchment only — exclude left map list and right zoom / close-X.
MAP_TAP_ROI = (310, 100, 1035, 610)
TAP_PIXEL_MIN_GAP = 28


def map_ready_for_landmark_tap(img: np.ndarray) -> bool:
    """True if the in-zone parchment is up.

    Events / chat can cover Random and the close-X, so [is_map_open] goes
    false while the map is still tappable. Fall back to parchment brightness.
    """
    if w.is_map_open(img):
        return True
    x1, y1, x2, y2 = MAP_TAP_ROI
    roi = img[y1:y2, x1:x2]
    if roi.size == 0:
        return False
    return float(np.mean(roi)) > 95.0


def _swift_bluestacks_windows() -> list[dict]:
    """Raw CGWindow rows: instance frames + Keymap Overlay content."""
    src = r"""
import Cocoa
let opts = CGWindowListOption(arrayLiteral: .optionAll, .excludeDesktopElements)
guard let wins = CGWindowListCopyWindowInfo(opts, kCGNullWindowID) as? [[String: Any]] else { exit(1) }
for w in wins {
  let owner = w[kCGWindowOwnerName as String] as? String ?? ""
  if !owner.contains("BlueStacks") { continue }
  let name = w[kCGWindowName as String] as? String ?? ""
  let wid = w[kCGWindowNumber as String] as? Int ?? 0
  let layer = w[kCGWindowLayer as String] as? Int ?? 0
  let b = w[kCGWindowBounds as String] as? [String: Double] ?? [:]
  let ww = Int(b["Width"] ?? 0); let hh = Int(b["Height"] ?? 0)
  if ww < 200 || hh < 200 { continue }
  let x = Int(b["X"] ?? 0); let y = Int(b["Y"] ?? 0)
  print("\(wid)|\(owner)|\(name)|\(layer)|\(x),\(y)|\(ww)x\(hh)")
}
"""
    proc = subprocess.run(["swift", "-"], input=src, capture_output=True, text=True)
    rows: list[dict] = []
    for line in (proc.stdout or "").splitlines():
        parts = line.strip().split("|")
        if len(parts) != 6:
            continue
        wid, owner, name, layer, pos, size = parts
        px, py = [int(v) for v in pos.split(",")]
        ww, hh = [int(v) for v in size.split("x")]
        rows.append(
            {
                "id": int(wid),
                "owner": owner,
                "name": name,
                "layer": int(layer),
                "x": px,
                "y": py,
                "w": ww,
                "h": hh,
            }
        )
    return rows


def list_bluestacks_windows() -> list[dict]:
    """Named instance windows with game content rect (Keymap Overlay if present)."""
    raw = _swift_bluestacks_windows()
    overlays = [r for r in raw if "keymap overlay" in r["name"].lower()]
    instances: list[dict] = []
    seen: set[str] = set()
    for row in raw:
        if row["owner"] != "BlueStacks" or row["layer"] != 0:
            continue
        if not row["name"] or "keymap overlay" in row["name"].lower():
            continue
        if row["w"] < 300 or row["h"] < 280:
            continue
        key = f"{row['name']}|{row['w']}x{row['h']}"
        if key in seen:
            continue
        seen.add(key)
        inst = dict(row)
        overlay = None
        inside = [
            o
            for o in overlays
            if inst["x"] <= o["x"] + o["w"] / 2.0 <= inst["x"] + inst["w"]
            and inst["y"] <= o["y"] + o["h"] / 2.0 <= inst["y"] + inst["h"]
        ]
        if inside:
            overlay = max(
                inside,
                key=lambda o: o["w"] * o["h"],
            )
        if overlay:
            inst["content_x"] = float(overlay["x"])
            inst["content_y"] = float(overlay["y"])
            inst["content_w"] = float(overlay["w"])
            inst["content_h"] = float(overlay["h"])
            inst["content_src"] = "overlay"
        else:
            client_w = float(inst["w"])
            client_h = float(inst["h"] - TITLEBAR_FALLBACK_PX)
            ox, oy, cw, ch = letterbox_content(client_w, client_h)
            inst["content_x"] = float(inst["x"]) + ox
            inst["content_y"] = float(inst["y"] + TITLEBAR_FALLBACK_PX) + oy
            inst["content_w"] = cw
            inst["content_h"] = ch
            inst["content_src"] = "titlebar-letterbox"
        instances.append(inst)
    return instances


def letterbox_content(win_w: int, win_h: int, game_w: int = 1280, game_h: int = 720) -> tuple[float, float, float, float]:
    """Content rect (ox,oy,cw,ch) inside window that fits game aspect."""
    scale = min(win_w / game_w, win_h / game_h)
    cw, ch = game_w * scale, game_h * scale
    ox, oy = (win_w - cw) / 2.0, (win_h - ch) / 2.0
    return ox, oy, cw, ch


def host_to_device_f(
    hx: float,
    hy: float,
    win: dict,
    game_w: int = 1280,
    game_h: int = 720,
) -> tuple[float, float] | None:
    """Map host click → device pixels using the game content rect (not window chrome)."""
    ox = float(win.get("content_x", win["x"]))
    oy = float(win.get("content_y", win["y"]))
    cw = float(win.get("content_w", win["w"]))
    ch = float(win.get("content_h", win["h"]))
    if cw <= 1 or ch <= 1:
        return None
    if not (ox <= hx <= ox + cw and oy <= hy <= oy + ch):
        return None
    dx = (hx - ox) * (game_w - 1) / cw
    dy = (hy - oy) * (game_h - 1) / ch
    return dx, dy


def host_to_device(
    hx: int,
    hy: int,
    win: dict,
    game_w: int = 1280,
    game_h: int = 720,
) -> tuple[int, int] | None:
    """Map host click (top-left global) → device pixels, or None if outside content."""
    mapped = host_to_device_f(hx, hy, win, game_w, game_h)
    if mapped is None:
        return None
    dx, dy = mapped
    return int(round(dx)), int(round(dy))


def pick_window(name: str | None) -> dict:
    wins = list_bluestacks_windows()
    if not wins:
        raise SystemExit(
            "no BlueStacks windows found "
            "(need a named instance, e.g. Ridax, at least ~300x280; "
            "maximize the window if it is still hidden)"
        )
    print("BlueStacks windows:", flush=True)
    for win in wins:
        print(
            f"  - {win['name']}: {win['w']}x{win['h']} @ ({win['x']},{win['y']}) "
            f"content={int(win['content_w'])}x{int(win['content_h'])} "
            f"({win.get('content_src', '?')})",
            flush=True,
        )
    if name:
        for win in wins:
            if win["name"].lower() == name.lower():
                return win
        raise SystemExit(f"window {name!r} not found")
    # Prefer mid-size instance windows over huge managers
    ranked = sorted(wins, key=lambda w: abs((w["w"] / max(w["h"], 1)) - (1280 / 720)))
    return ranked[0]


def calib_path(map_id: str) -> Path:
    return CALIB_DIR / f"{map_id}.json"


def island_state_path(map_id: str) -> Path:
    return CALIB_DIR / f"{map_id}.island_state.json"


def load_calib_doc(map_id: str) -> dict:
    path = calib_path(map_id)
    if not path.exists():
        return {
            "map_id": map_id,
            "pixel_space": "png_1280_native",
            "method": "host_mouse_tap_watcher",
            "points": [],
            "portals": [],
        }
    data = json.loads(path.read_text())
    data.setdefault("map_id", map_id)
    data.setdefault("pixel_space", "png_1280_native")
    data.setdefault("method", "host_mouse_tap_watcher")
    data.setdefault("points", [])
    data.setdefault("portals", [])
    return data


def save_calib_doc(map_id: str, doc: dict) -> Path:
    CALIB_DIR.mkdir(parents=True, exist_ok=True)
    path = calib_path(map_id)
    path.write_text(json.dumps(doc, indent=2) + "\n")
    return path


def save_points(map_id: str, points: list[dict]) -> Path:
    """Back-compat: update points while preserving portals/metadata."""
    doc = load_calib_doc(map_id)
    doc["points"] = points
    return save_calib_doc(map_id, doc)


def read_island_state(map_id: str) -> dict:
    path = island_state_path(map_id)
    if not path.exists():
        return {"island": 1, "pending_portal": None}
    try:
        data = json.loads(path.read_text())
    except json.JSONDecodeError:
        return {"island": 1, "pending_portal": None}
    data.setdefault("island", 1)
    data.setdefault("pending_portal", None)
    return data


def write_island_state(map_id: str, state: dict) -> None:
    CALIB_DIR.mkdir(parents=True, exist_ok=True)
    island_state_path(map_id).write_text(json.dumps(state, indent=2) + "\n")


def do_fit(points: list[dict], *, write: bool, map_id: str, pixel_space: str = "png_1280_native") -> None:
    if len(points) < 3:
        print(f"need ≥3 points (have {len(points)})", flush=True)
        return
    fit = [(p["ref_x"], p["ref_y"], p["game_x"], p["game_y"]) for p in points]
    ax, ay, res = fit_affine(fit)
    # Host-tap calib records device 1280×720 pixels; keep affine in that space.
    if pixel_space in ("png_1280_native", "png_1280"):
        src_w, src_h = 1280, 720
    else:
        src_w, src_h = 2560, 1440
    block = mapping_block(ax, ay, source_width=src_w, source_height=src_h)
    print(json.dumps(block, indent=2), flush=True)
    print(f"max_abs_residual={res:.6f} n={len(points)} source={src_w}x{src_h}", flush=True)
    for rx, ry, gx, gy in fit:
        px = ax[0] * rx + ax[1] * ry + ax[2]
        py = ay[0] * rx + ay[1] * ry + ay[2]
        print(
            f"  ({rx:.0f},{ry:.0f}) pred=({px:.1f},{py:.1f}) true=({gx},{gy}) "
            f"err=({abs(px-gx):.2f},{abs(py-gy):.2f})",
            flush=True,
        )
    if write:
        out = write_map(map_id, block)
        print(f"wrote {out}", flush=True)


def stdin_cmd(timeout_s: float = 0.02) -> str | None:
    if not sys.stdin or not sys.stdin.isatty():
        return None
    r, _, _ = select.select([sys.stdin], [], [], timeout_s)
    if not r:
        return None
    line = sys.stdin.readline()
    return line.strip().lower() if line else None


class HostClickStream:
    def __init__(self) -> None:
        self.proc = subprocess.Popen(
            ["swift", str(SWIFT_SRC)],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            bufsize=1,
        )

    def stop(self) -> None:
        if self.proc.poll() is None:
            self.proc.terminate()
            try:
                self.proc.wait(timeout=1.5)
            except subprocess.TimeoutExpired:
                self.proc.kill()

    def read_edge(self, timeout_s: float = 0.05) -> dict | None:
        assert self.proc.stdout
        r, _, _ = select.select([self.proc.stdout], [], [], timeout_s)
        if not r:
            return None
        line = self.proc.stdout.readline()
        if not line:
            return None
        try:
            return json.loads(line.strip())
        except json.JSONDecodeError:
            return None


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--device", default="emulator-5584")
    ap.add_argument("--map-id", required=True)
    ap.add_argument("--window", default=None, help="BlueStacks window title (e.g. Cecilia)")
    ap.add_argument("--need", type=int, default=6)
    ap.add_argument("--write", action="store_true")
    ap.add_argument("--resume", action="store_true")
    ap.add_argument("--arrival-timeout", type=float, default=90.0)
    ap.add_argument(
        "--tap-only",
        action="store_true",
        help="only capture tap pixels (no OCR). Print each tap; pair coords externally.",
    )
    ap.add_argument(
        "--islands",
        action="store_true",
        help="island-aware OCR calib: tag points with island; record portals via state file",
    )
    ap.add_argument("--island", type=int, default=1, help="starting island id for --islands")
    args = ap.parse_args()

    win = pick_window(args.window)
    print(
        f"using window {win['name']!r} {win['w']}x{win['h']} "
        f"content=({win['content_x']:.0f},{win['content_y']:.0f}) "
        f"{win['content_w']:.0f}x{win['content_h']:.0f} src={win.get('content_src')}",
        flush=True,
    )
    if win["content_w"] < 900:
        print(
            "AVISO: ventana chica — agranda Ridax (pantalla completa) "
            "si los taps caen en el zoom / close-X",
            flush=True,
        )

    bounds = w.map_coord_bounds(args.map_id)
    print(f"map={args.map_id} device={args.device} bounds={bounds}", flush=True)
    if args.tap_only:
        print(
            "TAP-ONLY mode: click bosses/goldens; report HUD coords in chat. "
            "Keys: quit",
            flush=True,
        )
    elif args.islands:
        write_island_state(
            args.map_id,
            {"island": int(args.island), "pending_portal": None},
        )
        print(
            "ISLANDS mode: LOCAL taps only (no cross-island). "
            f"State file: {island_state_path(args.map_id).name} "
            '(set {"island":N} or {"pending_portal":{"from":A,"to":B}}). '
            "Keys: undo | fit | write | quit",
            flush=True,
        )
    else:
        print("Open the MAP, click bosses/goldens. Keys: undo | fit | write | quit", flush=True)

    doc = load_calib_doc(args.map_id)
    if args.islands:
        doc["method"] = "island_local_tap_plus_ocr"
    points: list[dict] = list(doc.get("points") or [])
    portals: list[dict] = list(doc.get("portals") or [])
    if args.resume:
        print(f"resumed {len(points)} points, {len(portals)} portals", flush=True)
    elif not args.resume and args.islands:
        # fresh island run unless --resume
        points = []
        portals = []
        doc["points"] = points
        doc["portals"] = portals
        save_calib_doc(args.map_id, doc)
        print("starting fresh island calib doc", flush=True)

    stream = HostClickStream()
    time.sleep(0.4)
    pending_down: tuple[int, int] | None = None
    busy = False
    tap_idx = len(points)
    pending_path = CALIB_DIR / f"{args.map_id}.pending_taps.jsonl"
    if args.tap_only and pending_path.exists():
        for line in pending_path.read_text().splitlines():
            line = line.strip()
            if not line:
                continue
            try:
                tap_idx = max(tap_idx, int(json.loads(line).get("n") or 0))
            except json.JSONDecodeError:
                pass
        if tap_idx > len(points):
            print(
                f"esperando HUD del TAP {tap_idx} — no clickees otro icono",
                flush=True,
            )

    try:
        while True:
            if not args.tap_only and len(points) >= args.need:
                break
            cmd = stdin_cmd(0.0)
            if cmd in ("q", "quit", "exit"):
                break
            if not args.tap_only:
                if cmd in ("u", "undo"):
                    if points:
                        points.pop()
                        doc["points"] = points
                        save_calib_doc(args.map_id, doc)
                        print(f"undo → {len(points)} points", flush=True)
                    continue
                if cmd in ("f", "fit"):
                    do_fit(
                        points,
                        write=False,
                        map_id=args.map_id,
                        pixel_space=str(doc.get("pixel_space", "png_1280_native")),
                    )
                    continue
                if cmd in ("w", "write"):
                    do_fit(
                        points,
                        write=True,
                        map_id=args.map_id,
                        pixel_space=str(doc.get("pixel_space", "png_1280_native")),
                    )
                    continue

            ev = stream.read_edge(0.05)
            if ev is None or busy:
                continue

            if ev.get("edge") == "down":
                pending_down = (int(ev["x"]), int(ev["y"]))
                continue
            if ev.get("edge") != "up" or pending_down is None:
                continue

            hx, hy = int(ev["x"]), int(ev["y"])
            mapped_f = host_to_device_f(hx, hy, win) or host_to_device_f(*pending_down, win)
            pending_down = None
            if mapped_f is None:
                continue

            tx_f, ty_f = mapped_f
            tx, ty = int(round(tx_f)), int(round(ty_f))
            x1, y1, x2, y2 = MAP_TAP_ROI
            if not (x1 <= tx <= x2 and y1 <= ty <= y2):
                print(
                    f"  ignore click outside parchment device=({tx_f:.3f},{ty_f:.3f}) "
                    f"(lista / zoom / close-X)",
                    flush=True,
                )
                continue

            if not args.tap_only:
                try:
                    frame = w.screencap(args.device)
                except Exception as exc:
                    print(f"  ignore: screencap failed ({exc})", flush=True)
                    continue
                if not map_ready_for_landmark_tap(frame):
                    print(
                        "  ignore: mapa cerrado — abre el mapa de zona y click un icono",
                        flush=True,
                    )
                    continue

            if any(
                abs(tx - int(p["ref_x"])) + abs(ty - int(p["ref_y"])) < TAP_PIXEL_MIN_GAP
                for p in points
            ):
                print(
                    f"  reject: tap ({tx},{ty}) too close to a saved landmark — pick another icon",
                    flush=True,
                )
                continue

            if args.tap_only:
                paired = len(load_calib_doc(args.map_id).get("points") or [])
                if tap_idx > paired:
                    print(
                        f"  ignore: esperando HUD del TAP {tap_idx} "
                        f"(un clic a la vez)",
                        flush=True,
                    )
                    continue
                tap_idx += 1
                print(
                    f"\n=== TAP {tap_idx} device=({tx_f:.4f},{ty_f:.4f}) "
                    f"host=({hx},{hy}) ===\n"
                    f"  → un solo punto. Dime el HUD al llegar (ej. 120,85). "
                    f"No clickees otro icono todavía.",
                    flush=True,
                )
                # Append pending stub without game coords for later pairing
                pending_path = CALIB_DIR / f"{args.map_id}.pending_taps.jsonl"
                CALIB_DIR.mkdir(parents=True, exist_ok=True)
                with pending_path.open("a") as f:
                    f.write(
                        json.dumps(
                            {
                                "n": tap_idx,
                                "ref_x": tx_f,
                                "ref_y": ty_f,
                                "host_x": hx,
                                "host_y": hy,
                                "ts": time.time(),
                            }
                        )
                        + "\n"
                    )
                continue

            busy = True
            state = read_island_state(args.map_id) if args.islands else {"island": None, "pending_portal": None}
            island = state.get("island")
            pending_portal = state.get("pending_portal")
            kind = "portal" if pending_portal else ("island_local" if args.islands else "host_mouse")
            print(
                f"\n=== point {len(points)+1} tap device=({tx},{ty}) host=({hx},{hy})"
                + (f" island={island}" if island is not None else "")
                + (f" PORTAL {pending_portal['from']}→{pending_portal['to']}" if pending_portal else "")
                + " ===",
                flush=True,
            )
            print("  waiting ARRIVAL (close map if still open)…", flush=True)

            deadline = time.time() + 8.0
            while time.time() < deadline:
                img = w.screencap(args.device)
                if not w.is_map_open(img):
                    break
                time.sleep(0.35)
            else:
                w.ensure_map_closed(args.device)

            coords = w.wait_stable_coords(
                args.device,
                timeout_s=args.arrival_timeout,
                settle_s=2.0,
                poll_s=0.45,
                bounds=bounds,
                require_stable=True,
            )
            busy = False
            if coords is None:
                print("  FAILED: no stable arrival — click again", flush=True)
                continue

            gx, gy = coords
            if any(abs(gx - p["game_x"]) + abs(gy - p["game_y"]) < 15 for p in points):
                print(f"  reject: arrival ({gx},{gy}) too close — pick another spot", flush=True)
                continue

            row = {
                "label": f"{'portal' if pending_portal else 'manual'}_{len(points)+1}",
                "ref_x": float(tx),
                "ref_y": float(ty),
                "game_x": int(gx),
                "game_y": int(gy),
                "kind": kind,
                "host_x": hx,
                "host_y": hy,
            }
            if island is not None:
                row["island"] = int(island)
            points.append(row)
            doc["points"] = points

            if pending_portal:
                portal_row = {
                    "id": f"portal_{pending_portal['from']}_to_{pending_portal['to']}",
                    "from_island": int(pending_portal["from"]),
                    "to_island": int(pending_portal["to"]),
                    "ref_x": float(tx),
                    "ref_y": float(ty),
                    "game_x": int(gx),
                    "game_y": int(gy),
                }
                portals.append(portal_row)
                doc["portals"] = portals
                # clear pending portal; move current island to destination
                write_island_state(
                    args.map_id,
                    {"island": int(pending_portal["to"]), "pending_portal": None},
                )
                print(
                    f"  PORTAL saved {portal_row['id']} arrival=({gx},{gy}) → now island {pending_portal['to']}",
                    flush=True,
                )
            path = save_calib_doc(args.map_id, doc)
            print(
                f"  ARRIVED ({gx},{gy}) ← tap ({tx},{ty})  [{len(points)}/{args.need}] → {path}",
                flush=True,
            )
            print("  Open map, click next LOCAL landmark (or set pending_portal in state file).", flush=True)

        if args.tap_only:
            print(f"tap-only stopped. pending log: scripts/map_calib_points/{args.map_id}.pending_taps.jsonl", flush=True)
        elif len(points) >= 3:
            do_fit(
                points,
                write=args.write,
                map_id=args.map_id,
                pixel_space=str(doc.get("pixel_space", "png_1280_native")),
            )
            if args.write:
                # ensure bounds exist for swamp-like maps
                nav = REPO / "app/src/main/assets/navigation/maps" / f"{args.map_id}.json"
                if nav.exists():
                    m = json.loads(nav.read_text())
                    if "coordinate_bounds" not in m:
                        m["coordinate_bounds"] = {
                            "x_min": 0,
                            "x_max": 400,
                            "y_min": 0,
                            "y_max": 400,
                        }
                        nav.write_text(json.dumps(m, indent=2) + "\n")
                        print(f"added coordinate_bounds to {nav.name}", flush=True)
        else:
            doc["points"] = points
            doc["portals"] = portals
            save_calib_doc(args.map_id, doc)
            print(f"stopped with {len(points)} points, {len(portals)} portals", flush=True)
    finally:
        stream.stop()


if __name__ == "__main__":
    main()
