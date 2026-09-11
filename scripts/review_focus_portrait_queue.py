#!/usr/bin/env python3
"""List / apply labels for focus-portrait review_queue.

Usage:
  ./scripts/review_focus_portrait_queue.py --list
  ./scripts/review_focus_portrait_queue.py --apply 132501_123_pj_m0.20.png empty
  ./scripts/review_focus_portrait_queue.py --apply-batch labels.txt
      # labels.txt lines: <raw_file> <pj|boss|empty|other|skip>

Applying copies raw into dataset/raw, appends manifest, marks queue row done.
Then run: ./scripts/export_focus_portrait_bank.py
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LABELS = ("pj", "boss", "empty", "other", "skip")


def find_dataset() -> Path:
    logs = ROOT / "logs"
    for c in sorted(logs.glob("*/focus_portrait_dataset"), reverse=True):
        if (c / "labels" / "manifest.csv").exists():
            return c
    raise SystemExit("no focus_portrait_dataset")


def list_pending(ds: Path) -> list[dict]:
    q = ds / "review_queue" / "queue.csv"
    if not q.exists():
        return []
    rows = []
    with q.open() as f:
        for row in csv.DictReader(f):
            if row.get("status") == "pending":
                rows.append(row)
    return rows


def append_manifest(ds: Path, raw_name: str, label: str, notes: str) -> None:
    man = ds / "labels" / "manifest.csv"
    crop_name = raw_name.replace(".png", "_hud.png")
    # Prefer queue hud if present; else blank crop name still ok for classifier (uses raw).
    with man.open("a", newline="") as f:
        csv.writer(f).writerow(
            [
                dt.datetime.now().isoformat(timespec="seconds"),
                "emulator-5584",
                "coach_review",
                label,
                raw_name,
                crop_name if (ds / "crops" / crop_name).exists() else "",
                notes,
            ]
        )


def apply_one(ds: Path, raw_file: str, label: str, notes: str = "") -> None:
    qdir = ds / "review_queue"
    src = qdir / raw_file
    if not src.exists():
        raise SystemExit(f"missing {src}")
    if label == "skip":
        mark_queue(ds, raw_file, "skipped", "", notes or "skipped")
        print(f"skipped {raw_file}")
        return
    if label not in ("pj", "boss", "empty", "other"):
        raise SystemExit(f"bad label {label}")

    dest_raw = ds / "raw" / raw_file
    shutil.copy2(src, dest_raw)
    hud_src = qdir / raw_file.replace(".png", "_hud.png")
    if hud_src.exists():
        (ds / "crops").mkdir(exist_ok=True)
        shutil.copy2(hud_src, ds / "crops" / hud_src.name)
    face_src = qdir / raw_file.replace(".png", "_face.png")
    if face_src.exists():
        (ds / "face_crops").mkdir(exist_ok=True)
        shutil.copy2(face_src, ds / "face_crops" / face_src.name)

    append_manifest(
        ds,
        raw_file,
        label,
        notes or "coach_review",
    )
    mark_queue(ds, raw_file, "done", label, notes)
    print(f"applied {raw_file} → {label}")


def mark_queue(ds: Path, raw_file: str, status: str, true_label: str, notes: str) -> None:
    q = ds / "review_queue" / "queue.csv"
    rows = list(csv.DictReader(q.open()))
    fields = list(rows[0].keys()) if rows else []
    for row in rows:
        if row.get("raw_file") == raw_file and row.get("status") == "pending":
            row["status"] = status
            row["true_label"] = true_label
            if notes:
                row["notes"] = notes
    with q.open("w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        w.writerows(rows)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--apply", nargs=2, metavar=("RAW", "LABEL"))
    ap.add_argument("--apply-batch", type=Path, default=None)
    ap.add_argument("--notes", default="")
    args = ap.parse_args()
    ds = find_dataset()

    if args.list:
        pending = list_pending(ds)
        print(f"pending={len(pending)} dataset={ds}")
        for row in pending[-30:]:
            print(
                f"  {row['raw_file']}  pred={row['pred']} margin={row['margin']} "
                f"reason={row['reason']}  hud={row['hud_file']}"
            )
        return 0

    if args.apply:
        apply_one(ds, args.apply[0], args.apply[1], args.notes)
        return 0

    if args.apply_batch:
        for line in args.apply_batch.read_text().splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split()
            if len(parts) < 2:
                continue
            apply_one(ds, parts[0], parts[1], " ".join(parts[2:]))
        return 0

    ap.print_help()
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
