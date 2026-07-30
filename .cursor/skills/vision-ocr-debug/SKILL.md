---
name: vision-ocr-debug
description: >-
  Debugs MUAmaizingBot vision/OCR issues: HUD coords truncation, map name OCR,
  template match scores, ROI captures, player-arrow experiments. Use when the
  user mentions CoordOcr, off_spot, sticky, template score, captura, ROI,
  flecha, map OCR, Corrupled, or false positive matches. Do not use for
  non-vision loop bugs.
---

# Vision / OCR debug

## Resolution

Primary target: BlueStacks **1280×720**. Scale ROIs from 2560×1440 refs with `sx=W/2560`, `sy=H/1440`.

## Workflow

1. **Get evidence**: recent log lines (`CoordOcr`, `CurrentMapOcr`, `NavigationVision`, `MapWindow`) and/or screencap:
   - `adb -s <dev> exec-out screencap -p > logs/YYYY-MM-DD/capture_<port>.png`
   - or `scripts/pull_debug_capture.sh` if present
2. **Classify failure**:
   - **Truncation**: target `161,171` → `61` / `16` / `6` / `17` — treat as weak OCR, not real move.
   - **Null/empty OCR**: sticky already may apply; confirm miss count.
   - **Map name weak**: HUD typo (`Corrupled`) → `isWeak` → open-map fallback.
   - **Template FP**: global best score ≫ local score at truth (e.g. arrow CCOEFF).
3. **Local experiment** (Python/OpenCV ok in workspace): crop ROI, print raw OCR if available, rank template scores at truth vs global argmax.
4. **Recommend fix class**: sticky/heuristic (cheap) vs better ROI/preprocess vs CV gate (expensive). Prefer ROI evidence over theory.

## Key code

- `vision/coordinate/CoordinateReader.kt` (`CoordOcr`)
- `vision/map/CurrentMapOcr.kt`
- `vision/navigation/NavigationVision.kt`
- `bot/navigation/MapWindowActions.kt`
- Affine: `scripts/fit_map_affine.py`, `CoordinateMapping`

## Output

State: truth location (if known), best false hit, scores, and whether shipping a detector is ROI-positive.
