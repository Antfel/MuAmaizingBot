# Map calibration control points

Collect **3+** landmarks per map (REF pixels from SpotPicker + HUD game coords).

1. Open SpotPicker, select the map, tap a landmark → note **Pixel (refX, refY)**.
2. Walk the character to that same world spot → note **HUD (gameX, gameY)**.
3. Repeat for 3 well-separated points.
4. Fill a JSON below and run:

```bash
python3 scripts/fit_map_affine.py --points-file scripts/map_calib_points/<map>.json --write
```

For Kalima, calibrate once on `temple_of_kalima_1` then copy `coordinate_mapping` to floors 2–9
(or run with `--write` once and a small loop).

**Raklion 3:** stub at `scripts/map_calib_points/raklion_3.json` (`wire: 6`). SpotPicker shows it via maintenance reference even before affine. After 3 points:

```bash
python3 scripts/fit_map_affine.py --points-file scripts/map_calib_points/raklion_3.json --write
```

Still need `mapsui/raklion_3.png`, `mapsui/raklion_head.png`, and `current/raklion_3_current.png` for teleport (Elf buff / Farm Bosses).

Prefer **in-zone** map-open references (not world teleport list) for Corrupted / Land of Demons.
