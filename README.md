# MuAmaizingBot

Bot de farm para **MU Immortal** en BlueStacks (emulador Android). Automatiza navegación al spot, cambio de wire, buff de elf, compra de pociones y combate en auto.

**Hito estable:** Bot Farming Spot / Elf Buff / Potions  
**Release:** **v1.1.1**  
**Tag:** `bot-farming-spot-elf-buff-potions`

---

## Qué hace el bot

El loop prioriza mantenimiento y luego farm:

1. **Muerte** — detecta estado muerto, revive y vuelve al farm spot. Tras revive solo omite re-navegar si estás en el **mismo mapa** y las coords HUD están dentro del radio del spot (**≤ 5**, Manhattan). Si revive en otro mapa o lejos del punto, fuerza retorno al spot.
2. **Pociones** — si HP/mana están vacías (toggle en perfil), compra y regresa a farm.
3. **Elf buff** — si está activado y no hay icono de buff:
   - Navega a la zona de elf configurada, espera el buff y vuelve al farm spot.
   - Si falla **3 veces** seguidas (elf offline / otra zona), pausa la búsqueda **1 hora**; luego reintenta el ciclo.
   - Pause/Start **no** limpia ese cooldown; en el overlay: timer + botón **Reset**.
4. **Mapa / wire / spot** — si no estás en el mapa configurado, abre mapa, teleporta, cambia wire (OCR de canales tipo `MapName-NSwitch`) y toca el punto del farm spot.
5. **Farm** — asegura modo auto y mantiene el ciclo en el spot.

### Perfil configurable

Cada perfil define:

| Opción | Uso |
|--------|-----|
| **Farm spot** | Mapa, wire y coordenadas del punto de farm |
| **Elf buff** | Zona de la elf + toggle on/off |
| **Pociones** | Recuperación automática on/off |

Sin farm spot configurado el bot no puede navegar al punto de farm. Con elf buff desactivado, no busca buff (útil si la elf no está).

### Overlay

- Panel compacto; se colapsa solo a burbuja tras ~4s sin uso (tap para expandir).
- Controles Start/Pause.
- Si elf seek está activo: estado `Elf: ok` / `Elf: N/3` / timer de cooldown + **Reset**.

### Visión y captura

- Captura de pantalla en landscape (**1280×720 @ 240 DPI** recomendado).
- Templates bajo `templates/mu/` (mapa, UI, wires, buff, pociones).
- Switch de wire por **OCR** (ML Kit) + scroll de la lista; al encontrar el canal siempre toca la fila y Switch Line.
- Overlay flotante y Accessibility Service para taps/swipes.

---

## Requisitos

- macOS / Linux con **JDK 17+** y Android SDK (o Android Studio).
- **ADB** en el `PATH`.
- **BlueStacks** (o emulador compatible) con ADB activado.
- Resolución objetivo del cliente del juego: **1280×720**.

---

## Instalación

### 1. Clonar y preparar

```bash
git clone git@github.com:Antfel/MuAmaizingBot.git
cd MuAmaizingBot
git checkout bot-farming-spot-elf-buff-potions   # hito estable v1.1.1
```

### 2. Conectar BlueStacks por ADB

1. En BlueStacks: **Settings → Advanced → Android Debug Bridge** → activar.
2. Comprueba el dispositivo:

```bash
adb kill-server
adb start-server
adb connect 127.0.0.1:5555
adb devices
```

Deberías ver algo como `emulator-5584` o `127.0.0.1:5555` en estado `device`.

### 3. Compilar e instalar

```bash
./scripts/install_bluestacks.sh
# o forzando dispositivo:
./scripts/install_bluestacks.sh emulator-5584
```

El script detecta el ABI (`arm64` / `x86_64` / `arm32`), compila el flavor correcto e instala el APK.

APKs del Release:

| APK | Chip |
|-----|------|
| `MUAmaizingBot-1.1.1-arm64.apk` | BlueStacks Mac / ARM |
| `MUAmaizingBot-1.1.1-x86_64.apk` | BlueStacks PC Intel / AMD |

Desinstalar:

```bash
./scripts/uninstall_bluestacks.sh
```

### 4. Permisos en el emulador

Abre **MuAmaizingBot** y habilita, en este orden tipico:

1. **Accesibilidad** — servicio del bot (taps / swipes).
2. **Mostrar sobre otras apps** — overlay de control.
3. **Captura de pantalla** — conceder cuando la app lo pida (MediaProjection).
4. Notificaciones (Android 13+) si el sistema lo solicita.

### 5. Configurar y arrancar

1. Crea o elige un **perfil**.
2. Configura **Farm spot** (mapa, wire, punto en el minimapa).
3. Opcional: **Elf buff** (zona + toggle) y **pociones**.
4. Abre MU Immortal en el emulador (mismo display que captura el bot).
5. En el overlay o en la app: **Start**.

---

## Scripts útiles

| Script | Descripción |
|--------|-------------|
| `scripts/install_bluestacks.sh` | Build + install según ABI |
| `scripts/uninstall_bluestacks.sh` | Quita el paquete del emulador |
| `scripts/pull_debug_capture.sh` | Baja capturas de debug del dispositivo |

---

## Notas

- El bot actúa sobre la UI del juego con visión por plantillas; si BlueStacks cambia DPI/resolución, reajusta a **1280×720 @ 240 DPI**.
- Pause detiene el worker; al reanudar corre de nuevo el startup (mapa / wire / spot / buff según perfil).
- El backoff de elf buff **no** se reinicia con Pause/Start; usa **Reset** en el overlay o force-stop de la app.
- Radio de spot (arrival/farm): **5** coords Manhattan.
