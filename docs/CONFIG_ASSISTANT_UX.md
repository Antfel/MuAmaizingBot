# Configurador asistente — diseño de experiencia

**Producto:** MuAmaizingBot (perfil / configuración)  
**Estado:** propuesta UX (sin implementación)  
**Objetivo:** que el usuario arme un perfil entendiendo *qué va a hacer el bot*, no pelear con 20 toggles sueltos.

---

## 1. Problema hoy

El perfil ya es potente (modos, Programación Ciclos/Horario, pets, elf, potes, bosses, combat focus…), pero la UI es un **scroll de opciones** con algo de ocultar/mostrar según modo.

Lo que duele:

- Hay que conocer el modelo mental del bot antes de configurar bien.
- Opciones relacionadas están lejos (Programación ↔ farm spot ↔ mapas bosses ↔ pet).
- Un detalle “avanzado” (hold, golden, dots) compite visualmente con lo imprescindible.
- Casos como *“Eversong en ciclos → al wire 1 va a farm y cambia pet”* sorprenden si no se leyó la hint.

---

## 2. Principio de diseño

> **Primero la intención, después el detalle.**

Dos puertas al mismo perfil JSON:

| Puerta | Para quién | Sensación |
|--------|------------|-----------|
| **Asistente** (“Crear / rearmar perfil”) | Primera vez, cambio de estilo | Guiado, preguntas cortas, resumen en español |
| **Editor por capas** | Ajuste fino diario | Rápido, secciones colapsables, avanzado oculto |

El asistente **no reemplaza** el editor. Escribe el perfil; el editor lo retoca.

---

## 3. Cómo lo sentiría el usuario

### Primera vez (perfil nuevo)

1. Abre Configuración → ve **dos botones claros**:
   - **Crear con asistente** (primario)
   - **Configuración avanzada** (secundario / texto)
2. Entra al asistente: fondo limpio, una pregunta por pantalla, barra de progreso (paso 2 de 6).
3. Responde en lenguaje de juego (“Quiero bosses y descansar en farm”), no en jerga interna.
4. Al final ve un **resumen humano** y **Empezar** / **Editar detalle**.
5. Sensación: “ya sé qué va a hacer” — no “espero no haber tocado mal un switch”.

### Usuario que ya tiene perfil

- Entra al **editor**: arriba un resumen de 3–4 líneas del estilo actual.
- Botón discreto: **Rearmar con asistente** (reutiliza valores actuales como defaults).
- Ajusta solo potes o hold sin repetir el wizard.
- Sensación: control sin ruido.

### Momento “¿por qué hizo eso?”

- El resumen y las hints del editor explican dependencias  
  (*Ciclos ON → tras una vuelta de mapas → farm X min → pet de farm*).
- Menos tickets tipo “bug de wire 1”; más “ah, es el descanso”.

---

## 4. Plantillas de intención (el corazón)

Cuatro estilos. Cada uno define **obligatorio / opcional / oculto**.

### A. Solo farm

| Obligatorio | Opcional | Oculto al inicio |
|-------------|----------|------------------|
| Farm spot (mapa, wire, punto) | Elf seek, potes, pet farm, random, combat focus | Hold bosses, Programación, mapas bosses |

**Resumen ejemplo:**  
*Farmea en Corrupted Lands wire 3. Compra potes si faltan. Busca elf si no hay buff.*

### B. Solo bosses

| Obligatorio | Opcional | Oculto al inicio |
|-------------|----------|------------------|
| ≥1 mapa de bosses | Golden, hold, pet bosses, potes, elf | Farm spot*, Programación |

\*Farm spot no se pide salvo que active Programación después.

**Resumen ejemplo:**  
*Recorre Eversong Forest (wires en orden). Tras cada kill: potes/elf si aplica. Sin descanso en farm.*

### C. Farm ↔ Bosses (Programación)

| Obligatorio | Opcional | Oculto al inicio |
|-------------|----------|------------------|
| Farm spot + mapas bosses + estrategia (Ciclos o Horario) + rest o horas | Pets farm/bosses, potes, elf | Detalles de hold/golden (paso avanzado corto) |

**Resumen ejemplo (Ciclos):**  
*Bosses en Eversong. Cuando termina una vuelta de mapas → farm spot 60 min (pet de farm) → vuelve a bosses (pet de bosses).*

**Resumen ejemplo (Horario):**  
*Farm spot de 08:00 a 14:00. Bosses el resto del día.*

### D. Elf buff (giver / war)

| Obligatorio | Opcional | Oculto al inicio |
|-------------|----------|------------------|
| Post (spot) + params de cast / war | Pet | Farm bosses, Programación |

**Resumen ejemplo:**  
*Da buff en post configurado. Pausa 1s entre ciclos. Modo war: off.*

---

## 5. Flujo del asistente (pantalla a pantalla)

Progreso visual: `● ● ○ ○ ○ ○` + título corto.

### Paso 0 — Bienvenida

```
¿Cómo quieres usar este perfil?

[ Solo farmear en un spot      ]
[ Solo matar bosses            ]
[ Alternar farm y bosses       ]  ← Programación
[ Dar buff de elf              ]

Más tarde puedes cambiar esto o abrir configuración avanzada.
```

**Sensación:** elección de producto, no de checkbox.

---

### Paso 1 — Núcleo del estilo

**Solo farm** → picker de farm spot (reutiliza SpotPicker actual).  
**Solo bosses** → lista ordenada de mapas (igual que FarmBossesConfig).  
**Farm ↔ Bosses** → primero estrategia:

```
¿Cómo alternas?

( ) Ciclos — al terminar los mapas de bosses, descanso en el farm spot
( ) Horario — horas fijas Spot / Bosses (hora del celular)
```

Luego: mapas bosses → farm spot → (si Ciclos) minutos de descanso → (si Horario) HH:MM.

**Solo elf** → post + war on/off + pausa entre ciclos.

**Sensación:** solo ve lo que ese estilo necesita.

---

### Paso 2 — Mantenimiento (mismo para casi todos)

Preguntas sí/no + mínimos:

```
¿Recuperar potes automáticamente?     [Sí] [No]
  → si Sí: stacks HP / MP (defaults OK)

¿Buscar elf si no tienes buff?        [Sí] [No]
  → si Sí: zona elf (picker) si falta

¿Usar pet distinto por modo?          [Sí] [No]
  → si Farm↔Bosses y Sí: pet farm + pet bosses
  → si un solo modo: un pet
```

**Sensación:** checklist de supervivencia, no laboratorio.

---

### Paso 3 — Combate (opcional, skippeable)

```
[ Continuar con defaults ]     [ Ajustar combate ]

Si ajusta:
- Combat focus / PK mode
- Random teleport (far dots) — solo farm / rotación
- Bosses: hold sec, golden mobs
```

**Sensación:** el 80% sale en 2 minutos; el power user no está bloqueado.

---

### Paso 4 — Resumen + confirmar

```
Tu perfil: “Eversong ciclos”

Estilo: Farm ↔ Bosses (Ciclos)
Bosses: Eversong Forest (wires en orden)
Tras cada vuelta → farm spot 60 min
Pets: farm = … · bosses = …
Potes: sí · Elf seek: sí

[ Empezar a usar ]   [ Editar un detalle ]   [ Atrás ]
```

“Editar un detalle” abre el **editor por capas** con la sección relevante expandida.

**Sensación:** contrato claro de comportamiento (incluye el “vuelve a farm al cerrar la vuelta”).

---

## 6. Editor por capas (día a día)

Misma pantalla de perfil, reorganizada:

```
┌─────────────────────────────────────────┐
│ Perfil: Eversong ciclos                 │
│ Farm ↔ Bosses · Ciclos · descanso 60m   │
│ Spot: Corrupted w3 · Bosses: 1 mapa     │
│ [ Rearmar con asistente ]               │
├─────────────────────────────────────────┤
│ ▼ Estilo de juego                       │
│     Modo / Programación / resumen       │
│ ▼ Lugares                               │
│     Farm spot · Mapas bosses · Elf zona │
│ ▼ Mantenimiento                         │
│     Potes · Elf · Pet                   │
│ ▸ Combate y avanzado                    │
│     Focus, random, hold, golden…        │
└─────────────────────────────────────────┘
```

Reglas UX del editor:

1. **Resumen siempre visible** (intención en una frase).
2. **Dependencias explícitas** — si Ciclos ON y falta farm spot: chip rojo “Falta farm spot para el descanso”.
3. **Avanzado colapsado** por defecto.
4. Cards densas OK aquí; el asistente ya filtró el miedo inicial.
5. Info (ⓘ) explica *efecto en runtime*, no solo la etiqueta  
   (ej. Ciclos: “Al volver a wire 1 tras el último mapa, pasa a farm y puede cambiar pet”).

---

## 7. Microcopys clave (tono)

| Situación | Texto propuesto |
|-----------|-----------------|
| Chip Programación | `Tras 1 vuelta de bosses → farm {n} min` |
| Cambio de pet | `Al cambiar de segmento se valida el pet del modo nuevo` |
| Solo bosses | `Sin Programación: no va al farm spot solo por terminar wires` |
| Asistente skip | `Puedes cambiar esto después en Configuración` |

Evitar jerga interna en el asistente (`MAP_LAP`, `SEGMENT_REST`). El editor puede mostrar el nombre técnico en ⓘ si hace falta.

---

## 8. Mapa a lo que ya existe (sin inventar backend)

| Pieza actual | Rol en la nueva UX |
|--------------|-------------------|
| `botMode` | Paso 0 / Estilo |
| `modeRotation` | Paso 1 estilo C |
| `LocationRepository` farm / elf | Pickers del asistente y sección Lugares |
| `killBossesConfig.maps` | Paso bosses |
| `enablePotionRecovery` + stacks | Paso mantenimiento |
| `enableElfBuff` + zona | Paso mantenimiento |
| `enablePet` / pet por modo | Paso mantenimiento |
| `enableCombatFocus`, random, hold, golden | Paso combate / Avanzado |
| `ProfileConfigureScreen` | Se convierte en editor por capas |
| Spot / potion screens | Se reutilizan embebidos o como destinos del asistente |

No hace falta nuevo modelo de datos: el asistente es **UI + defaults + validación de completitud**.

---

## 9. Criterios de “perfil listo para Play”

El botón Play / Start puede usar las mismas reglas que el asistente:

| Estilo | Bloquea Start si falta |
|--------|-------------------------|
| Solo farm | Farm spot |
| Solo bosses | ≥1 mapa bosses |
| Farm ↔ Bosses | Spot + ≥1 mapa + (rest OK o horas válidas) |
| Elf | Post configurado |

Mensaje: *“Falta farm spot para el descanso de Ciclos”* → CTA al paso/sección.

---

## 10. Fuera de alcance (esta propuesta)

- IA / LLM generando configs
- Cambiar el JSON del perfil
- Asistente dentro del overlay flotante (solo app de configuración)
- Multi-idioma del copy (se traduce después como el resto de strings)

---

## 11. Fases de implementación sugeridas

| Fase | Entrega | Valor |
|------|---------|--------|
| **P0** | Resumen de perfil + dependencias en el editor actual | Claridad inmediata |
| **P1** | Asistente pasos 0–4 (sin combate fino) | Onboarding |
| **P2** | Editor colapsable por capas | Día a día |
| **P3** | Paso combate + “Rearmar” + gates de Start | Redondeo |

---

## 12. Escenarios de prueba de sensación (manual)

1. **Nuevo → Solo farm** — ¿sale jugando en &lt;3 min con spot + potes default?  
2. **Nuevo → Ciclos Eversong** — ¿el resumen menciona descanso y pet?  
3. **Solo bosses sin Programación** — ¿entiende que wire 1 **no** implica ir a farm?  
4. **Power user** — ¿llega a hold/golden en ≤2 taps desde el resumen?  
5. **Rearmar** — ¿conserva mapas/spot y solo cambia estilo?

---

## 13. Veredicto de producto

El usuario debe sentir:

1. **Elegí un estilo de juego**, no “configuré un sistema”.  
2. **Sé qué hará al terminar los wires / la vuelta**.  
3. **Puedo afinar sin miedo** porque lo avanzado está guardado detrás.  
4. **El asistente y el editor hablan el mismo idioma** (mismo resumen).

Si solo reordenamos cards sin resumen + dependencias + asistente corto, seguirá sintiéndose panel de avión.  
Si solo hay asistente sin editor por capas, frustrará al que ya sabe.
