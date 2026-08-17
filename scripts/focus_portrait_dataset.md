# Focus portrait detector — dataset protocol

Branch: `feature/focus-portrait-detector`

Goal: replace fragile `clear_x` CCORR-alone “enemy still focused” with a **portrait-in-HUD** signal (train offline, light runtime).

## Portrait ROI rule (important)

Event skins usually decorate the **circular rim / frame**, not the whole face.

- **Mark / train on the INNER FACE only** (eyes–nose–mouth band inside the disk).
- **Do not** include: metallic ring, event chrome, clear-X badge on the rim.
- Outer circle may still be used later as a search band; the **classifier crop** should stay face-inner so it stays skin-agnostic.

### Locked from user mark (5584 capture `110551_113_gui_roi`)

Orange face circle on HUD crop → screen @1280×720:

| | value |
|--|--|
| Circle center | `(548, 42)` |
| Radius (inner) | `17` |
| Face rect (train crop) | `(532, 26)–(564, 58)` → **32×32** |

Verify overlay: `logs/2026-08-10/focus_portrait_dataset/crops/face_roi_verify.png`

## Labels

| Label | Meaning |
|-------|---------|
| `pj` | Enemy/ally **player** focus HUD with face/portrait visible |
| `boss` | Boss focus emblem (no player face / different chrome) |
| `empty` | No focus panel (open world / post-kill / floor in top band) |
| `other` | Ambiguous (store open, map, death UI overlapping HUD) |
| `pending` | Captured, not labeled yet |

## What we need from you (priority order)

1. **Mark portrait ROI** on one good `pj` screenshot (like clear_x: draw a box on the face slot only).
2. **Samples** (full screen 1280×720), mix of maps/chars if possible:

| Class | Target count (v1) | How |
|-------|-------------------|-----|
| `pj` | 40–80 | Focus different PJs (armor, wings, event skins) |
| `boss` | 20–40 | Golden + normal bosses with emblem up |
| `empty` | 40–80 | Same maps **without** focus; include post-kill spam case |
| `other` | 10–20 | Optional noise |

3. Prefer **same lighting/maps** you actually farm (Kalima, Plains, etc.).

## Capture helper

```bash
# One shot + interactive label
./scripts/capture_focus_portrait.py emulator-5584 --tag pj1

# Burst while you fight (label later in manifest.csv)
./scripts/capture_focus_portrait.py emulator-5584 --burst 30 --tag fight

# Force label
./scripts/capture_focus_portrait.py emulator-5584 --label empty --tag postkill
```

Files land in `logs/YYYY-MM-DD/focus_portrait_dataset/{raw,crops,labels/manifest.csv}`.

Provisional crop band `@1280×720`: `(470,0)–(620,90)` — will tighten after your portrait box.

## Out of scope for v1 runtime

- Face identity / who the PJ is  
- Generic phone face detectors (ML Kit) as primary signal  

## Next after samples

1. Lock portrait ROI from your mark  
2. Tiny binary or 3-class bench (`pj` vs `empty` vs `boss`)  
3. Compare vs clear_x FP on the post-kill floor case  
