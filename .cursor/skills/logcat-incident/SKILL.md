---
name: logcat-incident
description: >-
  Pulls and analyzes BlueStacks/adb logcat for MUAmaizingBot incidents (wrong
  mode behavior, unexpected farm nav, map spam, elf/recovery failures). Use when
  the user mentions 5554/5564/5574/5584, logcat, incidente, "revisa los logs",
  farm_bosses vs farm, or unexpected Navigation/Recovery. Do not use for pure
  code review without runtime evidence.
---

# Logcat incident analysis

## Devices

BlueStacks ADB often remaps:

| Emulator UI | adb serial (typical) |
|---|---|
| 5554 | `127.0.0.1:5555` or `emulator-5554` |
| 5564 | `127.0.0.1:5565` / `emulator-5564` |
| 5574 | `emulator-5574` |
| 5584 | `emulator-5584` |

Always `adb devices -l` first. Prefer the serial the user names.

Package: `com.example.muamaizingbot`

## Workflow

1. **Resolve device** and `pidof com.example.muamaizingbot`.
2. **Dump** to `logs/YYYY-MM-DD/`:
   - `logcat_<port>_raw.txt` — full buffer or `-t 20000`
   - `logcat_<port>_focus.txt` — filtered tags
3. **Filter tags** (rg):
   `BotLoop|BotWorker|FarmBosses|Navigation|Recovery|ElfBuff|CoordOcr|CurrentMapOcr|MapCheck|MapWindow|Death|Potion|Disconnect|WireSwitch|AutoMode|GameActions|PROFILE|STARTUP`
4. **Timeline** key branches:
   - `branch=farm_bosses|farming|elf_buff|off_spot|wrong_map`
   - `mode=farm_bosses|farm|…`
   - `go_to_active_farm_spot` / `boss checkpoint` / `navigating reason=`
   - `hasBuff=` / `elf-route-failed` / `checkpoint reason=`
5. **Verdict** in chat: root cause chain (3–7 steps) + whether mode switched vs wrong recovery destination. Save a short `*_analysis.md` only if useful.

## Heuristics (known failure classes)

- **Farm Exp lookalike while farm_bosses**: `go_to_active_farm_spot` + `corrupted_lands` after `elf-route-failed` / `recoverFromLostState` — mode may still be bosses; recovery was farm-centric.
- **Map spam on spot**: `[SPOT] atSpot=false` with truncated coords (`161→61/16/6`); sticky only covers null OCR.
- **False missing buff**: `hasBuff=false` mid-fight then `true` after relocate — icon occlusion.

## Output

Concise Spanish/English matching the user. Lead with the causal chain, not a dump of greps.
