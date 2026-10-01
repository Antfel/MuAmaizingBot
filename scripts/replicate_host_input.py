#!/usr/bin/env python3
"""
Mirror host mouse gestures from a leader BlueStacks window into multiple emulators.

This is intended for "level fast": you operate one emulator (leader) and followers
receive the same taps/swipes via:
  adb -s <serial> shell input tap X Y
  adb -s <serial> shell input swipe X1 Y1 X2 Y2 DUR

Ridax (emulator-5584) can be excluded so it won't receive mirrored inputs.

Usage examples:
  python3 scripts/replicate_host_input.py --leader-window "RiddA" --leader-serial emulator-5564
  python3 scripts/replicate_host_input.py --leader-window "RiddA" --leader-serial emulator-5564 \
      --exclude-serial emulator-5584 --exclude-serial emulator-5574
"""

from __future__ import annotations

import argparse
import json
import queue
import select
import subprocess
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, wait
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

REPO = Path(__file__).resolve().parents[1]

# We reuse the exact mapping math from scripts/watch_map_tap_calib.py
# (letterbox_content + host_to_device_f).


def letterbox_content(
    win_w: int,
    win_h: int,
    game_w: int = 1280,
    game_h: int = 720,
) -> tuple[float, float, float, float]:
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
    """Map host click → device pixels as floats (0..1279/0..719), or None."""
    lx = hx - win["x"]
    ly = hy - win["y"]
    if not (0 <= lx <= win["w"] and 0 <= ly <= win["h"]):
        return None
    ox, oy, cw, ch = letterbox_content(win["w"], win["h"], game_w, game_h)
    if not (ox <= lx <= ox + cw and oy <= ly <= oy + ch):
        return None
    dx = (lx - ox) * (game_w - 1) / cw
    dy = (ly - oy) * (game_h - 1) / ch
    return dx, dy


def host_to_device(
    hx: int,
    hy: int,
    win: dict,
    game_w: int = 1280,
    game_h: int = 720,
) -> tuple[int, int] | None:
    mapped = host_to_device_f(hx, hy, win, game_w, game_h)
    if mapped is None:
        return None
    dx, dy = mapped
    return int(round(dx)), int(round(dy))


def adb_list_emulators() -> list[str]:
    """
    Return emulator serials from `adb devices -l`.

    We accept both `emulator-XXXX` and `127.0.0.1:XXXX`-style serials
    (BlueStacks sometimes exposes them differently).
    """
    proc = subprocess.run(["adb", "devices", "-l"], capture_output=True, text=True, check=False)
    lines = (proc.stdout or "").splitlines()
    out: list[str] = []
    for ln in lines:
        ln = ln.strip()
        if not ln or ln.startswith("List of devices"):
            continue
        # Example: "emulator-5564 device product:... model:..."
        parts = ln.split()
        if not parts:
            continue
        serial = parts[0]
        if serial.startswith("emulator-") or ":" in serial:
            out.append(serial)
    # Dedup preserving order
    seen = set()
    uniq: list[str] = []
    for s in out:
        if s in seen:
            continue
        seen.add(s)
        uniq.append(s)
    return uniq


def normalize_exclude_serials(exclude_serials: Iterable[str]) -> set[str]:
    return set(exclude_serials or [])

def add_bluestacks_serial_aliases(serials: set[str]) -> set[str]:
    """
    BlueStacks sometimes reports emulator serials as either:
      - emulator-XXXX
      - 127.0.0.1:(XXXX+1)   (common quirk)

    We expand exclusions to cover the common alias so we don't accidentally
    mirror to the excluded instance.
    """
    out = set(serials)
    for s in list(serials):
        if s.startswith("emulator-"):
            try:
                port = int(s.split("-", 1)[1])
            except Exception:
                continue
            out.add(f"127.0.0.1:{port + 1}")
        elif s.startswith("127.0.0.1:"):
            try:
                port = int(s.split(":", 1)[1])
            except Exception:
                continue
            # If adb shows port=XXXX+1, then emulator-XXXX is the counterpart.
            out.add(f"emulator-{max(0, port - 1)}")
    return out


def bluestacks_device_key(serial: str) -> str | None:
    """
    Collapse BlueStacks dual listings to one logical device id.

    emulator-5564  ↔  127.0.0.1:5565  → key "emu:5564"
    """
    if serial.startswith("emulator-"):
        try:
            return f"emu:{int(serial.split('-', 1)[1])}"
        except Exception:
            return None
    if serial.startswith("127.0.0.1:"):
        try:
            port = int(serial.split(":", 1)[1])
            # Common BlueStacks: adb TCP port = emulator base + 1
            return f"emu:{port - 1}"
        except Exception:
            return None
    return f"raw:{serial}"


def dedupe_follower_serials(serials: list[str]) -> list[str]:
    """
    Keep one ADB serial per physical BlueStacks instance.

    Prefer emulator-* over 127.0.0.1:* so we don't double-tap the same
    instance (which shows up as open-then-close on that follower).
    """
    by_key: dict[str, str] = {}
    order: list[str] = []
    # Prefer emulator-* first
    ranked = sorted(
        serials,
        key=lambda s: (0 if s.startswith("emulator-") else 1, s),
    )
    for s in ranked:
        key = bluestacks_device_key(s) or s
        if key in by_key:
            continue
        by_key[key] = s
        order.append(s)
    return order


def list_bluestacks_windows() -> list[dict]:
    """Return [{id,name,x,y,w,h}, ...] via Swift CGWindowList."""
    src = r"""
import Cocoa
// Include off-primary displays.
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
  if ww < 400 || hh < 400 { continue }
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


def pick_window(name: str) -> dict:
    wins = list_bluestacks_windows()
    if not wins:
        raise SystemExit("no BlueStacks windows found")
    for win in wins:
        if win["name"].lower() == name.lower():
            return win
    # Provide a helpful hint
    print("BlueStacks windows found:", flush=True)
    for win in wins:
        print(f"  - {win['name']}: {win['w']}x{win['h']} @ ({win['x']},{win['y']})", flush=True)
    raise SystemExit(f"window title {name!r} not found")


@dataclass
class MirrorGesture:
    is_tap: bool
    dx1: int
    dy1: int
    dx2: int
    dy2: int
    dur_ms: int
    dist: int
    host_down: tuple[int, int]
    host_up: tuple[int, int]


def adb_tap(serial: str, x: int, y: int) -> None:
    subprocess.run(
        ["adb", "-s", serial, "shell", "input", "tap", str(x), str(y)],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )


def adb_swipe(serial: str, x1: int, y1: int, x2: int, y2: int, dur_ms: int) -> None:
    subprocess.run(
        [
            "adb",
            "-s",
            serial,
            "shell",
            "input",
            "swipe",
            str(x1),
            str(y1),
            str(x2),
            str(y2),
            str(dur_ms),
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )


def dispatch_gesture(followers: list[str], g: MirrorGesture) -> None:
    """Send the same gesture to all followers in parallel."""
    if not followers:
        return

    def one(serial: str) -> None:
        if g.is_tap:
            adb_tap(serial, g.dx1, g.dy1)
        else:
            adb_swipe(serial, g.dx1, g.dy1, g.dx2, g.dy2, g.dur_ms)

    with ThreadPoolExecutor(max_workers=max(1, len(followers))) as pool:
        wait([pool.submit(one, s) for s in followers])


class HostDragStream:
    def __init__(self, swift_path: Path) -> None:
        self.proc = subprocess.Popen(
            ["swift", str(swift_path)],
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

    def read_event(self, timeout_s: float = 0.05) -> dict | None:
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


def stdin_cmd(timeout_s: float = 0.02) -> str | None:
    if not sys.stdin or not sys.stdin.isatty():
        return None
    r, _, _ = select.select([sys.stdin], [], [], timeout_s)
    if not r:
        return None
    line = sys.stdin.readline()
    return line.strip().lower() if line else None


def clamp_int(v: int, lo: int, hi: int) -> int:
    return max(lo, min(hi, v))


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--leader-window", required=True, help="BlueStacks window title (e.g. RiddA)")
    ap.add_argument("--leader-serial", required=True, help="ADB serial for leader emulator (e.g. emulator-5564)")
    ap.add_argument(
        "--exclude-serial",
        action="append",
        default=[],
        help="ADB serials to exclude from mirroring (default includes emulator-5584 if present).",
    )
    ap.add_argument("--swift", default=str(REPO / "scripts/host_mouse_drag_watch.swift"))
    ap.add_argument("--tap-dist-device", type=int, default=12, help="max (Manhattan) dist to classify as tap")
    ap.add_argument(
        "--tap-max-dur-ms",
        type=int,
        default=250,
        help="max gesture duration (ms) for a small move to still be classified as tap",
    )
    ap.add_argument("--swipe-dur-ms-min", type=int, default=80)
    ap.add_argument("--swipe-dur-ms-max", type=int, default=420)
    ap.add_argument(
        "--followers-refresh-s",
        type=float,
        default=2.0,
        help="how often to refresh adb follower list (avoid adb devices on every tap)",
    )
    args = ap.parse_args()

    exclude_serials = normalize_exclude_serials(args.exclude_serial)
    # Keep Ridax off by default if user didn't specify.
    exclude_serials.add("emulator-5584")
    exclude_serials = add_bluestacks_serial_aliases(exclude_serials)

    # Exclude leader itself and any explicit excludes (plus BlueStacks aliases).
    target_excludes = add_bluestacks_serial_aliases(
        set(exclude_serials) | {args.leader_serial}
    )

    leader_win = pick_window(args.leader_window)
    print(
        f"Capturing host mouse over leader window={leader_win['name']!r} "
        f"bounds={leader_win['w']}x{leader_win['h']} @({leader_win['x']},{leader_win['y']})",
        flush=True,
    )
    print(f"Leader adb serial={args.leader_serial} (not mirrored)", flush=True)
    print(f"Excluding serials: {sorted(target_excludes)}", flush=True)

    stream = HostDragStream(Path(args.swift))
    pending: dict | None = None
    win_refresh_at = 0.0
    followers_refresh_at = 0.0
    followers: list[str] = []

    # Capture thread never blocks on adb: gestures go to a queue, sender drains ASAP.
    work: queue.Queue[MirrorGesture | None] = queue.Queue()
    followers_lock = threading.Lock()

    def refresh_followers(force: bool = False) -> list[str]:
        nonlocal followers, followers_refresh_at
        now = time.time()
        if not force and now < followers_refresh_at and followers:
            return followers
        all_serials = adb_list_emulators()
        raw = [s for s in all_serials if s not in target_excludes]
        followers = dedupe_follower_serials(raw)
        followers_refresh_at = now + args.followers_refresh_s
        return followers

    def sender_loop() -> None:
        while True:
            item = work.get()
            if item is None:
                work.task_done()
                break
            with followers_lock:
                targets = list(refresh_followers())
            if not targets:
                work.task_done()
                continue
            t0 = time.time()
            dispatch_gesture(targets, item)
            elapsed_ms = int((time.time() - t0) * 1000)
            kind = "tap" if item.is_tap else f"swipe dur={item.dur_ms}ms"
            print(
                f"[mirror] {kind} dist={item.dist} "
                f"host={item.host_down}->{item.host_up} "
                f"dev=({item.dx1},{item.dy1})->({item.dx2},{item.dy2}) "
                f"followers={targets} adb={elapsed_ms}ms q={work.qsize()}",
                flush=True,
            )
            work.task_done()

    sender = threading.Thread(target=sender_loop, name="adb-mirror-sender", daemon=True)
    sender.start()
    refresh_followers(force=True)
    print(f"Followers (deduped): {followers}", flush=True)
    print("Ready — clicks on leader are queued immediately (no settle block).", flush=True)

    try:
        while True:
            cmd = stdin_cmd(0.0)
            if cmd in ("q", "quit", "exit"):
                break

            # Window can move/resize; refresh bounds periodically.
            now = time.time()
            if now >= win_refresh_at:
                try:
                    leader_win = pick_window(args.leader_window)
                except SystemExit:
                    pass
                win_refresh_at = now + 1.0

            # Always drain mouse events — never skip reads for settle/adb.
            ev = stream.read_event(0.02)
            if ev is None:
                continue

            edge = ev.get("edge")
            if edge == "down":
                pending = ev
                continue
            if edge != "up" or pending is None:
                continue

            try:
                down_x = int(pending["x"])
                down_y = int(pending["y"])
                down_ts = int(pending.get("ts", 0))
                up_x = int(ev["x"])
                up_y = int(ev["y"])
                up_ts = int(ev.get("ts", down_ts))
            except Exception:
                pending = None
                continue

            down_dev = host_to_device(down_x, down_y, leader_win)
            up_dev = host_to_device(up_x, up_y, leader_win)
            pending = None
            if down_dev is None or up_dev is None:
                continue

            dx1, dy1 = down_dev
            dx2, dy2 = up_dev

            dur_ms = int(ev.get("dur_ms", max(0, up_ts - down_ts)))
            if dur_ms <= 0:
                dur_ms = max(0, up_ts - down_ts)
            dur_ms = clamp_int(dur_ms, args.swipe_dur_ms_min, args.swipe_dur_ms_max)

            dist = abs(dx2 - dx1) + abs(dy2 - dy1)
            is_tap = dist <= args.tap_dist_device and (dur_ms <= args.tap_max_dur_ms or dist == 0)

            g = MirrorGesture(
                is_tap=is_tap,
                dx1=dx1,
                dy1=dy1,
                dx2=dx2,
                dy2=dy2,
                dur_ms=dur_ms,
                dist=dist,
                host_down=(down_x, down_y),
                host_up=(up_x, up_y),
            )
            work.put(g)
            print(
                f"[queue] {'tap' if is_tap else 'swipe'} "
                f"dev=({dx1},{dy1})->({dx2},{dy2}) q={work.qsize()}",
                flush=True,
            )
    finally:
        work.put(None)
        sender.join(timeout=2.0)
        stream.stop()


if __name__ == "__main__":
    main()

