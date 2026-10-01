# Configurador asistente — diseño de experiencia

**Producto:** MuAmaizingBot (perfil / configuración)  
**Estado:** propuesta UX acordada (sin implementación UI aún)  
**Branch:** `cursor/config-assistant-ux-bffa` · PR #3  
**Mockups:** [`docs/ux-mocks/`](ux-mocks/)  
**Prompt para agente local:** [`docs/LOCAL_AGENT_PROMPT.md`](LOCAL_AGENT_PROMPT.md)

**Objetivo:** que el usuario arme un perfil entendiendo *qué va a hacer el bot*, no pelear con 20 toggles sueltos.

---

## 1. Problema hoy

El perfil ya es potente (modos, Programación Ciclos/Horario, pets por modo, elf, potes, bosses, combat focus…), pero la UI es un **scroll de opciones** Material3 densas/altas (Switch + OutlinedTextField + Card) poco aptas para **1280×720**.

Duele:

- Hay que conocer el modelo mental del bot antes de configurar bien.
- Opciones relacionadas están lejos (Programación ↔ farm spot ↔ mapas bosses ↔ pet).
- Pet de farm y pet de bosses son distintos en datos, pero la UI no lo comunica bien.
- Casos como *“Eversong en ciclos → al wire 1 va a farm y cambia pet”* sorprenden (es Programación MAP_LAP, no bug).

---

## 2. Principio de diseño

> **Primero la intención, después el detalle.**  
> **Cambios puntuales sin repetir el wizard.**

| Puerta | Para quién | Sensación |
|--------|------------|-----------|
| **Asistente** (“Crear / rearmar perfil”) | Primera vez, cambio de estilo | Guiado, 1 pregunta por pantalla |
| **Editor denso + chips** | Día a día / level-up | 2 taps: Angel→Imp, nuevo spot, reordenar mapas |

Mismo JSON de perfil. No hay modelo nuevo: UI + defaults + validación.

---

## 3. Dos niveles de navegación (APP vs PERFIL)

**Licencia y Sistema no van dentro del editor de perfil.**

```
☰ Configuración (APP)
├── Inicio
├── Perfiles  →  editar perfil
│                 ├── Estilo
│                 ├── Lugares
│                 ├── Mantenimiento
│                 └── Avanzado
├── Sistema      (idioma, velocidad, Telegram, DPI)
└── Licencia     (key, sesión, sync map pack)
```

Mockups: `ux-nav-map-overview.jpg`, `ux-app-drawer.jpg`, `ux-app-inicio.jpg`, `ux-app-perfiles.jpg`, `ux-app-sistema.jpg`, `ux-app-licencia.jpg`.

### APP — qué muestra cada pantalla

| Pantalla | Contenido |
|----------|-----------|
| **Inicio** | Perfil activo, modo, estado overlay / accesibilidad / captura / bot |
| **Perfiles** | Lista, activo, **Crear con asistente**, entrar al editor |
| **Sistema** | Idioma, velocidad bot, Telegram (chat/alertas/test), nota 1280×720 @ 240 DPI |
| **Licencia** | License key, device id, sesión, sync content pack |

### PERFIL — nav izquierda fija (siempre igual al abrir sheets)

| Sección | Contenido |
|---------|-----------|
| **Estilo** | Modo (solo farm / solo bosses / Farm↔Bosses / elf) · Programación off/Ciclos/Horario · rest o HH:MM · frase resumen · Rearmar con asistente |
| **Lugares** | Chips + filas: Farm spot (+ pet farm) · Bosses mapas+pet · Zona elf · Post buff/war si aplica |
| **Mantenimiento** | Potes + stacks · Elf seek on/off (zona en Lugares) · atajos pets · Random + dots · intervalo check pet |
| **Avanzado** | Combat focus / PK · hold · golden · params elf cast/war/pausa — **no** licencia/sistema |

Mockups: `ux-profile-estilo.jpg`, `ux-profile-lugares.jpg`, `ux-profile-mantenimiento.jpg`, `ux-profile-avanzado.jpg`.

Al abrir un sheet (Bosses, Spot…), **el nav izquierdo no cambia** (misma pantalla + overlay).

---

## 4. Pets: no son un valor global

En código hoy:

| Contexto | Storage |
|----------|---------|
| Farm / elf | `general_config` pet |
| Farm bosses | `killBossesConfig.pet` |
| Runtime | `BotProfile.effectivePetConfig()` según modo |

Con Programación, al pasar a farm valida pet de farm; al volver a bosses, pet de bosses.

### Chips de resumen (estilo acordado)

Pills compactas (no pet suelto global):

```
[ Ciclos · 60m ]   [ Spot: Corrupted w3 · Pet Imp ]   [ Bosses: 1 mapa · Pet Angel ]
```

- Tap **Spot** → sheet: pet farm + “Editar punto en mapa”.  
- Tap **Bosses** → sheet: pet bosses + ruta de mapas.  
- Tap **Ciclos** → sheet/sección Estilo (rest / estrategia).

Mock: `config-ux-chips-with-pet.jpg`, `config-ux-v2-summary.jpg`, `config-ux-v2-bosses-sheet.jpg`.

---

## 5. Cambios puntuales (level-up) — sin asistente completo

| Quiere | Hace |
|--------|------|
| Angel → Imp en bosses | Chip Bosses → Pet bosses → Imp → Listo |
| Nuevo farm spot | Chip Spot → Editar punto → SpotPicker |
| Otros mapas bosses | Chip Bosses → Mapas → catálogo / drag orden |
| Cambiar estilo entero (solo farm → ciclos) | **Rearmar con asistente** |

Asistente = armar/rearmar intención. Level-up = cirugía desde chips.

---

## 6. Mapas de bosses

- Lista ordenada = orden del ciclo (wires 1..N por mapa, luego siguiente mapa).  
- **Drag and drop** para reordenar (no borrar y re-agregar).  
- Derecha: catálogo + buscar + check.  
- Un solo mapa (Eversong) también cierra “vuelta” al terminar wires si Ciclos ON → descanso farm (comportamiento actual `ModeRotationGate.noteBossLapComplete`).

Mock: `config-ux-v2-maps-reorder.jpg`.

---

## 7. Ubicaciones en el mapa (farm spot / elf)

Misma UX tipo `SpotPickerScreen` actual:

1. Elegir **mapa**  
2. Elegir **wire**  
3. **Tocar** el punto en el minimapa (zoom +/−)  
4. Guardar  

**Ocultar** el campo “nombre opcional” (no aporta).  
Mismo flujo para Farm spot y Zona elf (cambia título / qué location persiste).

Mock: `config-ux-v2-spot-picker.jpg`.

---

## 8. Lenguaje visual (emulador 1280×720)

| Evitar | Preferir |
|--------|----------|
| Switch + OutlinedTextField + Guardar por card | Fila densa ~36–40 dp |
| TextField para ints con rango | Stepper − / + o chips |
| Scroll infinito de cards altas | Lista + **bottom sheet** / panel para editar |
| Asistente con filas densas | Asistente: **CTAs grandes**; editor: filas densas |

Componentes sugeridos: `SummaryChipRow`, `CompactSettingsRow`, `InlineStepper`, `EditSheet`, nav landscape opcional (izq secciones / der detalle).

---

## 9. Plantillas de intención

### A. Solo farm
Obligatorio: farm spot. Opcional: elf, potes, pet farm, random, focus.  
Oculto: Programación, mapas bosses.

### B. Solo bosses
Obligatorio: ≥1 mapa. Opcional: golden, hold, pet bosses, potes, elf.  
Sin Programación: terminar wires **no** manda a farm spot.

### C. Farm ↔ Bosses
Obligatorio: spot + mapas + Ciclos|Horario + rest/horas.  
Pets **ambos**. Resumen debe mencionar descanso y cambio de pet.

### D. Elf buff
Obligatorio: post + params cast/war. Opcional: pet.

---

## 10. Flujo del asistente (resumen)

0. Estilo (4 botones grandes)  
1. Núcleo (spot / mapas / Programación)  
2. Mantenimiento (potes, elf, pets)  
3. Combate opcional (skip defaults)  
4. Resumen humano + Empezar / Editar detalle  

---

## 11. Mapa a código existente

| Pieza | UX |
|-------|-----|
| `botMode` | Estilo |
| `modeRotation` | Estilo / chip Ciclos |
| `LocationRepository` farm/elf | Lugares / SpotPicker |
| `killBossesConfig.maps` + `.pet` | Chip/sheet Bosses |
| Pet `general_config` | Chip/sheet Spot |
| Potes, elf seek, random | Mantenimiento |
| Focus, hold, golden, elf fine | Avanzado |
| `ProfileConfigureScreen` | Editor denso por secciones |
| Drawer Inicio/Perfiles/Sistema/Licencia | Sin meter en nav del perfil |

---

## 12. Gates de Start

| Estilo | Bloquea si falta |
|--------|------------------|
| Solo farm | Farm spot |
| Solo bosses | ≥1 mapa |
| Farm ↔ Bosses | Spot + mapas + rest/horas OK |
| Elf | Post |

---

## 13. Fases de implementación

| Fase | Entrega |
|------|---------|
| **P0** | Chips resumen + dependencias + copy Ciclos (sin wizard) |
| **P1** | Asistente pasos 0–4 |
| **P2** | Editor nav Estilo/Lugares/Mantenimiento/Avanzado + sheets densos |
| **P3** | Drag mapas, SpotPicker sin nombre, Rearmar, gates Start |

---

## 14. Fuera de alcance

- LLM generando configs  
- Cambiar schema JSON del perfil (salvo UX)  
- Asistente en overlay flotante  
- Producto alquiler de emuladores (`muamaizing-platform`) — **otro proyecto**

---

## 15. Escenarios de sensación

1. Nuevo solo farm &lt;3 min  
2. Ciclos Eversong — resumen menciona descanso + pets  
3. Solo bosses — wire 1 no implica farm  
4. Level-up: cambiar solo pet bosses en 2 taps  
5. Reordenar mapas por drag  
6. Power user: hold/golden en Avanzado ≤2 taps desde resumen  

---

## 16. Veredicto

El usuario debe sentir: elegí un **estilo**, sé qué pasa al cerrar la vuelta, cambios puntuales son **chips**, y Licencia/Sistema viven en el **menú app**, no en el perfil.
