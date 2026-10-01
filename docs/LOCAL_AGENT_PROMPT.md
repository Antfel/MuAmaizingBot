# Prompt para el agente local (copiar / pegar)

Usa este archivo así:

1. En el Mac: `git fetch && git checkout cursor/config-assistant-ux-bffa` (o mergea/rebasea esta rama a tu rama de trabajo del bot).
2. Abre **Cursor Desktop** en la carpeta `MUAmaizingBot`.
3. Chat nuevo → pega el bloque de abajo **completo** (o escribe `@docs/LOCAL_AGENT_PROMPT.md` y `@docs/CONFIG_ASSISTANT_UX.md`).

Los mockups están en `docs/ux-mocks/`.

---

## MENSAJE PARA PEGAR EN EL CHAT LOCAL

```
Eres el agente que modifica MuAmaizingBot en este repo.

## Contexto (léelo antes de codear)
Lee y toma como fuente de verdad:
- docs/CONFIG_ASSISTANT_UX.md
- docs/ux-mocks/ (mockups acordados)

Este diseño salió de una conversación Cloud Agent sobre un configurador más asistente + panel denso para emulador 1280×720. Aún NO está implementado en UI (solo docs/mocks en esta rama).

## Decisiones ya cerradas (no reabrir sin preguntarme)
1. Dos niveles de nav:
   - APP (drawer ☰): Inicio, Perfiles, Sistema, Licencia
   - PERFIL (al editar): Estilo, Lugares, Mantenimiento, Avanzado
   Licencia/Sistema NUNCA dentro del editor de perfil.

2. Asistente = crear/rearmar estilo de juego.
   Editor denso + chips = cambios puntuales (level-up: pet, spot, mapas).

3. Pets NO son globales:
   - Pet farm → general_config / chip Spot
   - Pet bosses → killBossesConfig.pet / chip Bosses
   Chips: [Ciclos · Nm] [Spot: … · Pet X] [Bosses: N mapas · Pet Y]

4. Mapas bosses: catálogo + lista ordenada con DRAG para reordenar.

5. Spot / elf: mapa + wire + tap en minimapa. OCULTAR nombre opcional.

6. UI densa: filas/steppers/sheets; evitar Switch+OutlinedTextField gordos por cada setting.
   Asistente sí usa botones grandes.

7. Programación Ciclos: al completar vuelta de mapas (incluido 1 mapa al terminar wires) → farm rest + puede cambiar pet. Es comportamiento actual (ModeRotationGate), documentarlo en UI; no es bug.

8. No mezclar con el producto de alquiler de emuladores (muamaizing-platform). Ese es otro repo/PR.

## Qué quiero que hagas cuando trabaje el configurador
- Implementar por fases del doc: P0 → P1 → P2 → P3, salvo que indique otra.
- Reutilizar ProfileConfigureScreen, SpotPicker, LocationRepository, ProfileRepository, modeRotation, killBossesConfig — sin inventar schema JSON nuevo.
- Mantener mode-aware recovery / bot modes (farm, farm_bosses, elf_*) intactos al tocar UI.
- Si algo del doc choca con código actual, dilo y propone el cambio mínimo.

## Qué NO hacer
- No reescribir el bot loop “de pasada”.
- No meter Sistema/Licencia en el nav del perfil.
- No unificar pet farm y pet bosses en un solo campo.
- No implementar alquiler de emuladores aquí.

Confirma que leíste CONFIG_ASSISTANT_UX.md y lista en 5 líneas el plan P0 cuando te pida implementar.
```

---

## Variante corta (si el contexto ya está en el chat)

```
@docs/CONFIG_ASSISTANT_UX.md @docs/LOCAL_AGENT_PROMPT.md
Sigue las decisiones cerradas del prompt. Trabajamos el configurador asistente del bot; fase P0 primero salvo que diga otra.
```
