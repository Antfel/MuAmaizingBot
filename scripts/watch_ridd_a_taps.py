#!/usr/bin/env python3
"""Watch host mouse taps on the RiddA BlueStacks window only.

No ADB injection. No mirroring. Just capture + print device coords @1280×720.

Usage:
  python3 scripts/watch_ridd_a_taps.py
  python3 scripts/watch_ridd_a_taps.py --window RiddA

Keys: q / quit / exit
"""

from __future__ import annotations

import argparse
import json
import select
import subprocess
import sys
import time
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
SWIFT_SRC = REPO / "scripts/host_mouse_watch.swift"


def letterbox_content(
    win_w: int,
    win_h: int,
    game_w: int = 1280,
    game_h: int = 720,
) -> tuple[float, float, float, float]:
    scale = min(win_w / game_w, win_h / game_h)
    cw, ch = game_w * scale, game_h * scale
    ox, oy = (win_w - cw) / 2.0, (win_h - ch) / 2.0
    return ox, oy, cw, ch


def host_to_device(
    hx: int,
    hy: int,
    win: dict,
    game_w: int = 1280,
    game_h: int = 720,
) -> tuple[int, int] | None:
    lx = hx - win["x"]
    ly = hy - win["y"]
    if not (0 <= lx <= win["w"] and 0 <= ly <= win["h"]):
        return None
    ox, oy, cw, ch = letterbox_content(win["w"], win["h"], game_w, game_h)
    if not (ox <= lx <= ox + cw and oy <= ly <= oy + ch):
        return None
    dx = int(round((lx - ox) * (game_w - 1) / cw))
    dy = int(round((ly - oy) * (game_h - 1) / ch))
    return dx, dy


def list_bluestacks_windows() -> list[dict]:
    src = r"""
import Cocoa
let opts = CGWindowListOption(arrayLiteral: .optionAll, .excludeDesktopElements)
guard let wins = CGWindowListCopyWindowInfo(opts, kCGNullWindowID) as? [[String: Any]] else { exit(1) }
var seen = Set<String>()
for w in wins {
  let owner = w[kCGWindowOwnerName as String] as? String ?? ""
  let name = w[kCGWindowName as String] as? String ?? ""
  let wid = w[kCGWindowNumber as String] as? Int ?? 0
  let layer = w[kCGWindowLayer as String] as? Int ?? 0
  if owner != "BlueStacks" || name.isEmpty || layer != 0 { continue }
  let b = w[kCGWindowBounds as String] as? [String: Double] ?? [:]
  let ww = Int(b["Width"] ?? 0); let hh = Int(b["Height"] ?? 0)
  if ww < 300 || hh < 300 { continue }
  let key = "\(name)|\(ww)x\(hh)"
  if seen.contains(key) { continue }
  seen.insert(key)
  print("\(wid)|\(name)|\(Int(b["X"] ?? 0)),\(Int(b["Y"] ?? 0))|\(ww)x\(hh)")
}
"""
    proc = subprocess.run(["swift", "-"], input=src, capture_output=True, text=True)
    rows: list[dict] = []
    for line in (proc.stdout or "").splitlines():
        parts = line.strip().split("|")
        if len(parts) != 4:
            continue
        wid, name, pos, size = parts
        px, py = [int(v) for v in pos.split(",")]
        ww, hh = [int(v) for v in size.split("x")]
        rows.append({"id": int(wid), "name": name, "x": px, "y": py, "w": ww, "h": hh})
    return rows


def pick_window(name: str, *, quiet: bool = False) -> dict:
    wins = list_bluestacks_windows()
    if not wins:
        raise SystemExit("no BlueStacks windows found")
    if not quiet:
        print("BlueStacks windows:", flush=True)
        for win in wins:
            print(f"  - {win['name']}: {win['w']}x{win['h']} @ ({win['x']},{win['y']})", flush=True)
    for win in wins:
        if win["name"].lower() == name.lower():
            return win
    raise SystemExit(f"window {name!r} not found")


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


def stdin_cmd(timeout_s: float = 0.0) -> str | None:
    if not sys.stdin or not sys.stdin.isatty():
        return None
    r, _, _ = select.select([sys.stdin], [], [], timeout_s)
    if not r:
        return None
    line = sys.stdin.readline()
    return line.strip().lower() if line else None


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--window", default="RiddA", help="BlueStacks window title")
    args = ap.parse_args()

    win = pick_window(args.window)
    ox, oy, cw, ch = letterbox_content(win["w"], win["h"])
    print(
        f"watching taps on {win['name']!r} {win['w']}x{win['h']} "
        f"letterbox={cw:.1f}x{ch:.1f} offset=({ox:.1f},{oy:.1f})",
        flush=True,
    )
    print("Click inside RiddA. Keys: quit", flush=True)

    stream = HostClickStream()
    pending_down: tuple[int, int] | None = None
    n = 0
    win_refresh_at = 0.0

    try:
        while True:
            cmd = stdin_cmd(0.0)
            if cmd in ("q", "quit", "exit"):
                break

            now = time.time()
            if now >= win_refresh_at:
                try:
                    win = pick_window(args.window, quiet=True)
                except SystemExit:
                    pass
                win_refresh_at = now + 2.0

            ev = stream.read_edge(0.05)
            if ev is None:
                continue

            if ev.get("edge") == "down":
                pending_down = (int(ev["x"]), int(ev["y"]))
                continue
            if ev.get("edge") != "up" or pending_down is None:
                continue

            hx, hy = int(ev["x"]), int(ev["y"])
            mapped = host_to_device(hx, hy, win) or host_to_device(*pending_down, win)
            pending_down = None
            if mapped is None:
                continue

            dx, dy = mapped
            n += 1
            print(
                f"TAP {n} device=({dx},{dy}) host=({hx},{hy}) window={win['name']}",
                flush=True,
            )
    finally:
        stream.stop()


if __name__ == "__main__":
    main()
