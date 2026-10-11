# Prompt para el agente de diseño (copiar / pegar)

En Cursor local (rama donde ya está la UI funcional del configurador):

1. Asegúrate de tener estos docs (desde `origin/cursor/config-assistant-ux-bffa` si hace falta):
   - `docs/CONFIG_ASSISTANT_DESIGN_BRIEF.md`
   - `docs/CONFIG_ASSISTANT_UX.md`
   - `docs/ux-mocks/*`
2. Chat nuevo → pega el bloque de abajo.
3. Adjunta 1–2 screenshots actuales (como el de Estilo) con el mensaje.

---

## MENSAJE PARA PEGAR

```
Trabajo SOLO el acabado visual del configurador de perfil (Compose). La funcionalidad ya está; no rearmes flujos ni toques lógica del bot.

## Lee antes de codear
- docs/CONFIG_ASSISTANT_DESIGN_BRIEF.md  ← brief de acabado + tokens + checklist
- docs/CONFIG_ASSISTANT_UX.md            ← producto / IA de info
- docs/ux-mocks/*.jpg                    ← fuente de verdad visual

## Problema
La UI funcional se ve a medias: header blanco vs cuerpo negro, nav estrecha con labels cortados, chip Spot huérfano en el rail, bordes teal muy gruesos, cards/estados confusos, poca densidad para 1280×720. Debe acercarse a los mocks (panel denso slate + teal/amber).

## Alcance
- Theme/tokens, spacing, tipografía, shapes, chips, rows, steppers, sheets, top bar oscura
- SummaryChipRow arriba del contenido: [Ciclos] [Spot·Pet] [Bosses·Pet]
- Nav izq. fija Estilo/Lugares/Mantenimiento/Avanzado con labels legibles
- Selected states finos (1–2 dp), no marcos de 4–6 dp

## No tocar
- Persistencia / ProfileRepository / ModeRotationGate / bot loop
- Schema JSON
- Meter Licencia/Sistema dentro del nav del perfil
- “Mejoras” de producto fuera del brief

## Entrega
1. Lista corta de cambios visuales que harás (antes de codear mucho)
2. Implementa contra el checklist del DESIGN_BRIEF
3. Indica qué pantallas tocaste; pide screenshot de Estilo + Lugares si puedo probar en emulador

Empieza por unificar el chrome (top bar + canvas) y los chips de resumen; luego densidad de Estilo.
```
