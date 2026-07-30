---
name: regression-from-logs
description: >-
  Turns a bad logcat incident into a concrete regression guard: forbidden log
  patterns, expected branches, and a minimal manual or scripted retest. Use when
  the user says regresión, no debe volver a pasar, test plan from logs, or after
  root-causing an incident and wanting a lasting check.
---

# Regression from logs

## Workflow

1. **Pin the bug signature** from the incident (1–3 log lines that must not recur in the wrong context), e.g.:
   - In `mode=farm_bosses` / `branch=farm_bosses`: forbid `go_to_active_farm_spot` after `elf-route-failed` (should be boss checkpoint / light boss-map recovery).
   - On farm spot: forbid streak of `atSpot=false` with truncated coords triggering map taps without sticky.
2. **Write the guard** (pick lightest that works):
   - **Manual checklist** in chat / `logs/.../retest.md`
   - **rg assertions** on a captured focus log after repro
   - Optional future: unit test around pure helpers (truncation heuristic) if extracted
3. **Retest steps** (device):
   - Mode + map/wire starting state
   - Action that triggered the bug (elf seek fail, OCR noise, …)
   - Success criteria: allowed tags present; forbidden absent for N minutes / one repro cycle
4. Tie to skills: run **install-and-verify**, then **logcat-incident** on the new dump.

## Template

```markdown
## Regression: <title>
Signature (forbid): `...`
Allowed: `branch=farm_bosses` + `boss checkpoint` / `on boss checkpoint map`
Setup: profile mode=… map=…
Repro: …
Pass: …
```

## Output

One regression card the user can re-run later; keep it short.
