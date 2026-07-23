# Farm Bosses — hoja de ruta

**Branch:** `feature/farm-bosses`  
**Base:** `main` v1.1.4  
**Producto:** modo dedicado `botMode = farm_bosses`.

---

## Secuencia (rediseño)

Config usuario: **solo lista ordenada de mapas** (sin spots ni coords).

```
Start
  → death/revive inmediato (prioridad)
  → GENERAL: potions / elf buff (si aplica)
  → teleport primer mapa + Wire 1
  → open mapa zona
  → findAll template boss_vivo (+ golden si toggle)
  → si 0: siguiente wire → si no hay wires: siguiente mapa
  → si 1+: tap best score → close map → wait nav
→ pelear mientras `boss_focus` visible
  → al perder focus: esperar 5s → si sigue ausente: POST-KILL (potions/elf)
  → hunt otro boss en el wire / avanzar wire-mapa

Death
  → revive → GENERAL (potions / elf) → checkpoint map/wire
```

Validaciones generales (buff, potions) en **startup**, **post-revive** y **post-kill** — no a mitad de pelea.

**Elf buff:** misma zona/`enableElfBuff` del perfil que Farm (`LocationRepository` elf_buff).

---

## Assets

| Template | Path |
|----------|------|
| Boss vivo (calavera flame) | `templates/mu/ui/map/boss_alive.png` |
| Golden vivo | `templates/mu/ui/map/golden_alive.png` |
| Boss focus (emblema circular top bar) | `templates/mu/ui/targeting/boss_focus.png` (circularMask) |

Capturados desde emulator-5584. Fight usa solo `boss_focus`; al perderlo espera 5s y re-hunt / next map.

---

## Config JSON

```json
"kill_bosses_config": {
  "enabled": true,
  "include_golden_mobs": false,
  "hold_sec": 90,
  "maps": ["plain_of_four_winds_2", "..."],
  "spots": []
}
```

Perfiles viejos con `spots[]` migran a `maps` (ids únicos en orden).

---

## Criterio estable para merge a `main`

- [ ] Teleport + wire cycle + open map
- [ ] findAll bosses (2+ iconos) + tap
- [ ] Focus + Auto se mantienen
- [ ] Post-kill buff/pots + return checkpoint
- [ ] Death → revive → checkpoint map/wire

---

## Archivos clave

| Pieza | Path |
|-------|------|
| State | `bot/bosses/BossHuntState.kt` |
| Loop | `bot/bosses/FarmBossesLoop.kt` |
| Hunt | `bot/bosses/BossMapHuntActions.kt` |
| findAll | `vision/template/PcTemplateMatcher.findAllTemplates` |
| Config | `profile/KillBossesConfig.kt` (`maps`) |
| Enganche | `bot/loop/BotPriorityLoop.kt` |
