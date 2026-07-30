---
name: bot-mode-checklist
description: >-
  Checklist that recovery, navigation, elf/potion/inventory returns, and loop
  branches stay mode-aware (farm, farm_bosses, elf_buff_giver, elf_buff_war).
  Use when editing BotRecoveryActions, BotPriorityLoop, ElfBuffNavigation,
  PotionPurchase, InventoryRecycle, MapCheckActions, or when the user mentions
  modo, farm_bosses, recovery, checkpoint, or "no debe ir al farm spot".
---

# Bot mode checklist

Modes (`BotMode` / `bot_mode`):

| Mode | Resume destination |
|---|---|
| `farm` | Farm map + spot (`goToActiveFarmSpot`) |
| `elf_buff_giver` | Farm / buff post spot |
| `farm_bosses` | Boss checkpoint map+wire (`FarmBossesLoop.currentCheckpointOrCursor`) |
| `elf_buff_war` | War post (`ElfBuffWarPostActions`) |

## Before shipping a change

- [ ] Failure paths call **mode-aware** recovery (`recoverFromLostState` or explicit boss/war/farm nav) — never hardcode farm spot for all modes.
- [ ] Success paths that teleport (elf, potion shop) **defer** return for `farm_bosses` to checkpoint (loop `post-elf` / `post-potion`) instead of farm spot.
- [ ] `MapCheckActions.isInConfiguredMap()` already mode-aware — do not reuse its `true` plus **farm spot coords** for bosses/war.
- [ ] Logs include `mode=` or branch that makes the destination obvious (`boss checkpoint` vs `go_to_active_farm_spot`).
- [ ] War skips map validation; do not force farm OCR/spot checks there.

## Code hotspots

- `bot/recovery/BotRecoveryActions.kt`
- `bot/loop/BotPriorityLoop.kt`
- `bot/maintenance/ElfBuffNavigationActions.kt`
- `bot/maintenance/PotionPurchaseActions.kt`
- `bot/maintenance/MapCheckActions.kt`

## Anti-pattern

```text
// BAD: farm-only recovery while profile may be farm_bosses
BotRecoveryActions.navigateToFarmWithRetry("elf-route-failed")
```
