# Brief de diseño — acabado visual del configurador

**Audiencia:** agente local que ya tiene la UI **funcional** y debe pulir el look&feel.  
**No es** rediseño de flujos ni de modelo de datos.  
**Fuente de verdad visual:** `docs/ux-mocks/*.jpg` + reglas abajo.  
**Fuente de verdad de producto:** `docs/CONFIG_ASSISTANT_UX.md`.

---

## 1. Objetivo

Que la implementación se vea y se sienta como los mockups: **panel denso de emulador 1280×720**, no un prototipo Material3 con bordes gruesos, header blanco suelto y cards inconsistentes.

**Permitido:** cambiar Compose theme, spacing, tipografía, colores, shape, componentes de presentación.  
**Prohibido sin preguntar:** cambiar persistencia, lógica de modos, Programación, pickers que ya funcionan, schema JSON.

---

## 2. Qué está mal hoy (referido al screenshot actual)

Problemas típicos a corregir (no lista exhaustiva de bugs):

| Problema | Por qué duele | Dirección |
|----------|---------------|-----------|
| Header blanco (`MU Amaizing Bot`) chocando con cuerpo negro | Rompe composición; parece 2 apps | Unificar chrome oscuro (o top bar dark) |
| Nav izq. estrecha + labels cortados (`Mant.`, `Avanz.`) | Poco legible en landscape | Nav ~240–280 dp, labels completos o icon+tooltip |
| Chip Spot solo en el rail, sin fila de chips arriba del contenido | El resumen debía ser **horizontal** sobre el panel | `SummaryChipRow` arriba: Ciclos · Spot+Pet · Bosses+Pet |
| Cards de estilo con borde teal grueso / estados “Off / Elf buff / Listo” confusos | Ruido visual, jerarquía poco clara | Una selección clara; badges de estado más chicos o en el resumen |
| Toggle Mundo/War con tipografía negra sobre teal | Contraste y estilo “app genérica” | Tokens del design kit (abajo) |
| Botones pie `Borrar` / `Volver` pesados y de bajo contraste | Compiten con el contenido | Footer compacto secondary/outline |
| Densidad Material default (mucho aire, stroke fuerte) | En 720p se come la pantalla | Filas ~36–40 dp, stroke 1 dp, radius 8–10 |

---

## 3. Design kit (tokens a implementar)

Definir en `ui/theme` (o similar) y **usar en todo el editor de perfil**:

```
Background canvas:     #121820 / #1B222C
Surface panel:         #243041
Surface elevated:      #2C3A4F
Hairline / divider:    #3A4A5C  (1 dp)
Text primary:          #F2F5F8
Text secondary:        #9AA8B5
Accent teal:           #2BB8A9
Accent amber (chips):  #D4A15A
Danger (borrar):       #C45C5C  (texto/outline, no fill enorme)
Radius:                8–10 dp (chips 999/full OK)
Row height:            36–40 dp
Section title:         titleSmall / labelLarge, no headline enorme
Stroke:                1 dp (selected 1.5–2 dp teal, no 4–6 dp)
```

**Evitar:** purple gradients, glow, pills multi-shadow, tipografía Inter/Roboto por defecto si el tema ya puede usar una sans más definida del proyecto, cards con elevation alta.

---

## 4. Layout target (Estilo — como conversamos)

```
┌──────────────────────────────────────────────────────────┐
│ Top bar OSCURA (☰ + título)                              │
├────────────┬─────────────────────────────────────────────┤
│ PERFIL     │  [Ciclos·60m] [Spot·Pet] [Bosses·Pet]       │  ← chips
│ nombre     │  Título sección                             │
│            │  Contenido denso                            │
│ Estilo  ●  │                                             │
│ Lugares    │                                             │
│ Mantenim.  │                                             │
│ Avanzado   │                                             │
│            │                                             │
│ [Rearmar]  │                                             │
└────────────┴─────────────────────────────────────────────┘
```

- Nav izquierda **fija** al cambiar de sección y al abrir sheets.  
- Chips de resumen **arriba del contenido**, no solo un chip huérfano en el rail.  
- Selección de estilo: grid 2×2 **compacto**; selected = fill sutil + stroke 1.5 teal, no marco grueso.  
- Sub-opción Mundo / War: segmented control denso bajo la card Elf (solo si aplica).

Comparar con: `docs/ux-mocks/ux-profile-estilo.jpg`, `config-ux-v2-summary.jpg`, `config-ux-chips-with-pet.jpg`.

---

## 5. Checklist por superficie

### Estilo
- [ ] Top bar coherente con el tema oscuro  
- [ ] Chips resumen (Ciclos / Spot+Pet / Bosses+Pet)  
- [ ] 4 estilos en grid denso; un solo selected claro  
- [ ] Programación (si Farm↔Bosses) debajo, sin cards gigantes  
- [ ] Rearmar = outline teal en el rail, no compite con selected Estilo  

### Lugares
- [ ] Filas `Farm spot ›` / `Bosses ›` / `Zona elf ›`  
- [ ] Sheet Bosses: pet chips + lista mapas (ver `config-ux-v2-bosses-sheet.jpg`)  

### Mantenimiento / Avanzado
- [ ] Filas densas + steppers/toggles compactos (ver mocks `ux-profile-mantenimiento.jpg`, `ux-profile-avanzado.jpg`)  
- [ ] Sin OutlinedTextField alto por cada int  

### Spot picker
- [ ] Sin campo nombre opcional  
- [ ] Mapa protagonista; controles compactos arriba (`config-ux-v2-spot-picker.jpg`)  

### Mapas bosses
- [ ] Drag handles visibles; catálogo a la derecha (`config-ux-v2-maps-reorder.jpg`)  

---

## 6. Criterio de “listo”

1. Screenshot landscape se parece a los mocks (misma jerarquía, no pixel-perfect obligatorio).  
2. En 1280×720 se ve **Estilo completo + chips** sin scroll absurdo.  
3. Selected states legibles a 1 metro del monitor (stroke fino + fill, no solo borde gordo).  
4. No se rompió navegación ni guardado de perfil.

---

## 7. Archivos típicos a tocar

- `ui/theme/Color.kt`, `Theme.kt`, `Type.kt`  
- Pantallas nuevas del configurador / `ProfileConfigureScreen` (o equivalentes ya creados en local)  
- Componentes: chips, rows, steppers, sheets — **extraer** a `ui/components/` si están inline  

No tocar: `bot/`, license API, recovery, ModeRotationGate (salvo copy ⓘ).

---

## 8. Mockups obligatorios a abrir

| Archivo | Usar como |
|---------|-----------|
| `ux-profile-estilo.jpg` | Pantalla Estilo |
| `config-ux-chips-with-pet.jpg` | Fila de chips |
| `config-ux-v2-summary.jpg` | Editor + nav |
| `config-ux-v2-bosses-sheet.jpg` | Sheet Bosses |
| `config-ux-v2-maps-reorder.jpg` | Mapas |
| `config-ux-v2-spot-picker.jpg` | Spot |
| `ux-profile-lugares.jpg` / `mantenimiento` / `avanzado` | Secciones |
| `ux-nav-map-overview.jpg` | No mezclar APP drawer con nav perfil |
