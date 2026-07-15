# Elf Buff Bot — hoja de ruta

**Branch:** `Elf-Buff-Bot`  
**Base estable:** `bot-farming-spot-elf-buff-potions` (`main` @ 8556778)  
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
- [ ] Decidir nombre de modo en perfil (`elf_buff_giver` vs toggle)

### Iteración 1 — Modo giver + post estático
- Nuevo `botMode` (o perfil “Elf Buff Bot”) sin tocar el seeker.
- UI: elegir farm spot = **buff post** (puede reusar el farm spot del perfil).
- Startup: ir al spot → auto ON → loop que **solo** mantiene farm estático + death/potions.
- **Sin** castear buff todavía (valida el “estar ahí”).

### Iteración 2 — Casteo manual/semiautomático
- Templates / coords de skill(s) de buff en la barra.
- Acción: tap skill (self / ground / target según skill del juego).
- Trigger temporal: timer o botón de prueba (“buff ahora”).
- Logs claros `[ELF_GIVER] cast …`.

### Iteración 3 — Modo Party
- Detectar UI de party (abierta o iconos en pantalla).
- Filtrar objetivos “en party”.
- Buff al membre que aparece / se acerca; cooldown por personaje si es posible.

### Iteración 4 — Detección de aproximación (público)
- Definir radio / heurística (nombre cerca del centro, popup, silhouette).
- Modo **Todos**: si hay PJ en rango → cast → cooldown.
- Debounce para no castear en vacío.

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

Arrancar **Iteración 1**: `botMode = elf_buff_giver` + loop estático en el spot, reutilizando navegación/farm/death/potions, sin lógica de cast todavía. Así el branch queda ejecutable y separado del farm seeker desde el día 1.
