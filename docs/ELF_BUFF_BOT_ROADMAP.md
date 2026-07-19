# Elf Buff Bot — hoja de ruta

**Branch:** `Elf-Buff-Bot`  
**Base estable:** `main` v1.1.1 (`bot-farming-spot-elf-buff-potions`) merged into this branch.  
**Rol:** la **elf da buff** a otros jugadores (modo paralelo al farm/seeker actual).

---

## Visión del producto

La elf queda fija en un **spot recurrente**:

1. Viaja al spot (mapa / wire / punto) una vez al arrancar (reusa la navegación del farm bot).
2. Farmea **estática** (auto ON, no persigue mobs fuera del spot).
3. Cuando alguien se acerca (o entra en party / es aliado, según modo), **le da buff**.
4. Vuelve al idle de farm en el mismo spot sin irse del wire/mapa.

Esto es **independiente** del `enableElfBuff` actual, que es solo para el **PJ farm** que *va a buscar* buff.

| Concepto actual (seeker) | Nuevo (giver) |
|--------------------------|---------------|
| `enableElfBuff` + zona NPC | `botMode = elf_buff_giver` (o similar) |
| Viaja → espera → vuelve al farm | Se queda en el post |
| Icono HUD = “¿ya tengo buff?” | Trigger = “¿hay objetivo cerca?” |

---

## Modos de buff (objetivo)

| Modo | Comportamiento esperado |
|------|-------------------------|
| **Party** | Solo miembros del party (lista / marcadores de party). |
| **Aliados** | Party + guild / amigos / lista blanca configurable. |
| **Todos (público)** | Cualquier PJ que entre en radio / se acerque al spot. |

Cada modo compartirá el mismo motor (detectar → apuntar → castear → cooldown), cambiando solo el **filtro de objetivo**.

---

## Qué trae el bot chino (BotPlayZone) y qué replicar

Referencia: módulo **Elf / Minion** en BotPlayZone  
(https://botplayzone.wordpress.com/elf-minion-settings/) — la doc pública está muy vacía; el valor está en el **patrón de producto**, no en código portado.

Flujos / ideas a identificar y adaptar (cuando tengamos capturas del juego o del V2):

| Idea del chino / MU Helper | Cómo encaja aquí |
|----------------------------|------------------|
| Módulo aparte **Elf/Minion** vs farm | Nuevo `botMode` + loop dedicado; no mezclar con seek |
| Minion grinding / stay on spot | Reusar `FarmingLoop` + hold spot |
| Auto-buff en party (estilo MU Helper Fairy Elf) | Modo **Party** primero (UI de party más clara) |
| Buff range / skill selection | Perfil: skills de buff, orden, cooldown |
| Filtros de objetivo | Party / aliados / público |
| Cooldown por jugador / anti-spam | Evitar spamear el mismo nombre cada segundo |
| Seguir vivo / potear en el post | Reusar death + potion recovery del farm bot |
| Posiblemente summon / minion | **Fuera de alcance** de las primeras iteraciones |

No hay implementación de *buff-giver* en `mu-adb-bot` ni en este repo: solo seek/receive.

---

## Piezas reutilizables (ya en el repo)

- Navegación a spot: `NavigationOrchestrator.goToVisualLocation`
- Quedarse farmeando: `FarmingLoop` + `GameActions.ensureAutoMode`
- Muerte / revive / volver: `DeathActions`, `BotRecoveryActions`
- Pociones: `PotionCheckActions` / `PotionPurchaseActions`
- Perfil + ubicaciones: `BotProfile.botMode`, `LocationRepository`, `SpotPickerScreen`
- Visión / OCR: `NavigationVision`, `WireChannelOcr` (útil después para nombres / UI party)
- Patrón de backoff: `ElfBuffSeekGate` (adaptar a cooldowns de cast)

---

## Iteraciones propuestas

### Iteración 0 — Esqueleto (esta hoja de ruta)
- [x] Branch `Elf-Buff-Bot`
- [x] Roadmap documentado
- [x] Nombre de modo: `bot_mode = elf_buff_giver`

### Iteración 1 — Modo giver + post estático
- [x] Nuevo `botMode` sin tocar el seeker de farm.
- [x] UI: chips Farm / Elf Buff; farm spot = buff post en modo giver.
- [x] Startup: ir al spot → auto ON → loop estático + death/potions.
- [x] Sin castear buff todavía (valida el “estar ahí”).
- [ ] Probar en emulador con perfil en modo Elf Buff.

### Iteración 2 — Casteo manual/semiautomático
- [x] Templates Greater Defense / Greater Damage (`templates/mu/ui/skills/`).
- [x] Al Start (giver): match 1× → guarda coords; casts siguientes por tap.
- [x] Cast ambos skills; timer + overlay **Cast** / **Map**.
- [x] Logs `[ELF_GIVER] cast …` / `skill mapped …`.
- [x] Probar en emulador 5574.

### Iteración 3 — Modo Party
- Detectar UI de party (abierta o iconos en pantalla).
- Filtrar objetivos “en party”.
- Buff al membre que aparece / se acerca; cooldown por personaje si es posible.

### Iteración 4 — Detección de aproximación (público)
- [x] Nameplate structure: **`[GUILD]` gold → `SERVER` white → `NAME` green**.
- [x] Exclusion zones (user-marked): no detect / no focus en HUD + self.
- [x] Focus: mid del nameplate + offset Y; confirmar HUD de target.
- [x] Unfocus: tap **X** del focus HUD (no suelo).
- [x] Overlay debug + logs `plate bounds` / `skip … exclusion` / `focus HUD`.
- [ ] Affinar template X / umbral HUD si hace falta.
- [ ] Debounce multi-frame si hace falta.

### Iteración 5 — Aliados
- Lista blanca (nombres) y/o marcador de guild.
- Modo **Aliados** = party ∪ whitelist ∪ guild (según lo que la UI permita).

### Iteración 6 — Endurecer operación
- No salir del spot al buffear (salvo muerte / potions).
- Anti-stuck: si pierde el spot, re-navegar como el farm bot.
- Telemetría: buffs dados / hora, fallos de target, cooldown activo.
- Separar claramente en UI: “Buscar buff” (farm) vs “Dar buff” (elf).

---

## Riesgos / decisiones abiertas

1. **Cómo ve el juego a otros PJs** en 1280×720 (nombre, party frame, targeting) — hay que capturar UI real antes de I3–I5.
2. **Skills concretos** de la elf Immortal (buff party vs buff single).
3. ¿Auto farm ON mientras buffea, o pausar auto un instante al castear?
4. ¿Un perfil solo-elf, o el mismo perfil con `botMode`?
5. BotPlayZone: conviene **screenshots del V2 Elf/Minion** en una sesión dedicada para no inventar filtros.

---

## Primera implementación recomendada (siguiente chat)

Arrancar **Iteración 3** (party) cuando I2 esté validada en emulador: coords de skill + Cast/timer OK.
