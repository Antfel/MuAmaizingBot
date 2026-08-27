# MuAmaizing — Emulator Rental Architecture

**Producto:** alquiler de emuladores (BlueStacks) con acceso remoto desde celular.  
**Separado de:** `MuAmaizingBot` (farm bot). El bot puede ser un add-on futuro *dentro* del slot, no parte de este sistema.  
**Dominio:** `muamaizingbot.com`  
**Origen del diseño:** Cloud Agent conversation (2026-08).

---

## 1. Problema que resuelve

- Alquilar instancias de emulador a usuarios externos.
- El usuario controla **solo su** emulador desde el celular (privacidad frente a otros emuladores del host).
- Start / restart del emulador sin intervención del admin.
- Multi-host: Mac mini, Windows, etc. (agnóstico de SO a nivel cloud).
- Evitar AirMirror/AirDroid: chocan con `MediaProjection` si luego corre un bot en el emulador.

---

## 2. Principio de separación

| Producto | Repo / sistema | Rol |
|----------|----------------|-----|
| License / sessions (existente) | API actual | Identidad y derechos (`acquire` / `heartbeat` / `release`) |
| Emulator rental (nuevo) | Proyectos nuevos | Hosts, slots, stream, start/restart |
| Farm bot | `MuAmaizingBot` | Ignorar por ahora |

La license API se **reutiliza como auth**. La rental API es **capa aparte** (mismos servidores/dominio OK, contratos distintos).

---

## 3. Arquitectura (3 + 1 piezas)

```
[APK remoto]  ←→  [Cloud: api / relay]  ←→  [Host agent en PC]
       ↑                    ↑
  usuario final      [Admin web panel]
```

### 3.1 `muamaizing-host` (nuevo)
Daemon en cada máquina física (Mac / Windows / Linux).

- Descubre instancias BlueStacks + `adb devices`
- Heartbeat al cloud
- Start / stop / restart de instancias asignadas
- Stream por ADB/scrcpy (**sin** MediaProjection)
- Auth: `host_token` (infra), **no** license key de usuario

### 3.2 `muamaizing-cloud` (nuevo, o módulo en API existente)
Backend en `api.muamaizingbot.com` (+ `relay.` si se separa media).

- Auth vía sesión de license (`session_id`)
- Inventario de hosts / instances / slots
- Comandos al host (`start_slot`, `restart_slot`)
- Rooms / señalización WebRTC o relay WebSocket
- Admin API

### 3.3 `muamaizing-remote` (nuevo)
APK Android del arrendatario.

- Login con license key → `sessions/acquire`
- Lista de **sus** slots
- Start / Restart (si capabilities lo permiten)
- Fullscreen: video + toques
- **No** es el bot; solo mando remoto

### 3.4 Admin web
Panel en `muamaizingbot.com` / `admin.`

- Ver hosts e instancias
- Crear slots, asignar leases
- Revocar / forzar stop

Puede vivir dentro del monorepo cloud.

---

## 4. Repos recomendados

**Opción A — monorepo (preferida para protocolo único):**

```
muamaizing-platform/
  apps/cloud          # API + admin
  apps/host           # agent multi-OS
  apps/remote         # APK
  packages/protocol   # contratos versionados
```

**Opción B — repos separados:**

```
muamaizing-cloud
muamaizing-host
muamaizing-remote
```

**No mezclar** código de rental dentro de `MuAmaizingBot`.

---

## 5. Modelo de datos mínimo

```
Host          id, name, os, last_heartbeat, status, host_token_hash
Instance      id, host_id, name, adb_serial?, vendor_instance_id, state
Slot          id, instance_id, label, capabilities[]  # remote, start, restart
Lease         user/license → slot_id, expires_at
SessionRemote id, slot_id, room_token, started_at   # conexión de stream
```

**License (existente, reutilizar):**

```
LicenseKey → max_sessions, status (active/revoked/expired)
BotSession → acquire / heartbeat / release  (ya existe)
```

Entitlements sugeridos en la license (extensión futura, no mezclar con bot hoy):

- `remote` — puede usar APK de alquiler
- `slot:<id>` — slot asignado
- `bot` — (futuro) farm bot dentro del emulador

---

## 6. Flujos

### Descubrimiento
Host heartbeat → cloud actualiza Instance.state → admin ve inventario.

### Usuario inicia slot
1. APK: `POST /api/v1/sessions/acquire` (license existente)
2. APK: `POST /api/v1/rental/slots/:id/start` (nueva API; exige session válida)
3. Cloud encola comando al host
4. Host arranca BlueStacks → ADB `device`
5. Host abre scrcpy/stream → cloud enlaza room
6. APK recibe video + envía toques

### Reinicio
APK → `POST .../restart` → host reinicia **solo** esa instancia → re-attach stream.

### Segundo host (escala)
Instalar mismo `muamaizing-host` en otra máquina → registrar con token → instancias aparecen en la **misma** lista de renta. Host agnóstico de SO.

---

## 7. Dominio

| Subdominio | Uso |
|------------|-----|
| `muamaizingbot.com` | Landing / panel |
| `api.muamaizingbot.com` | License + Rental API |
| `relay.muamaizingbot.com` | Señalización / TURN / WS media |

---

## 8. Relación con License API (separada pero reutilizada)

**Reutilizar:**
- `POST /api/v1/sessions/acquire`
- `POST /api/v1/sessions/heartbeat`
- `POST /api/v1/sessions/release`
- Admin de keys / revocación / `max_sessions`

**No meter en sessions:**
- hosts, slots, stream, start/restart

**Patrón:**
```
License session  =  ¿quién y con qué derechos?
Rental API       =  ¿qué emulador y cómo controlarlo?
Host agent       =  token de infra, no license de usuario
```

Opcional más adelante: campo `app: "remote" | "bot"` en acquire para separar contadores sin unificar productos.

---

## 9. Por qué no AirMirror

Android solo permite **una** sesión `MediaProjection`. Apps de espejo y un bot que captura pelean y se desconectan.  
El stream del alquiler debe ir por **host + ADB/scrcpy** (privilegios shell), no MediaProjection dentro del emulador.

---

## 10. MVP

1. Un host (cualquier OS) + 1–N instancias
2. Cloud: register host, slots, start, room join
3. APK: login license → start → ver/tocar
4. Admin: asignar slot a license/user
5. Sin bot, sin multi-plan fancy

---

## 11. Fuera de alcance (ahora)

- Integración del farm bot
- AirMirror / captura MediaProjection para el remoto
- Que el APK emita video (el emisor de video es el host)

---

## 12. Cómo continuar en Cursor (Mac)

1. Crear repo vacío `muamaizing-platform` (o los 3 repos).
2. Copiar este documento a `docs/ARCHITECTURE.md`.
3. Abrir esa carpeta en Cursor Desktop (Mac).
4. Prompt inicial sugerido:

> Implementa el MVP de alquiler de emuladores según `docs/ARCHITECTURE.md`.
> Empieza por `packages/protocol` + stub de `apps/cloud` (rental endpoints)
> y `apps/host` (heartbeat + adb discover). No toques MuAmaizingBot.

5. Esta conversación Cloud Agent permanece en:
   https://cursor.com/agents/bc-01a0401b-8201-7700-9f07-f7550308bffa
   (continuar desde Mac/web si quieres seguir el hilo aquí).

---

## 13. Checklist de decisión (cerrado en diseño)

- [x] Producto separado del bot
- [x] Multi-host agnóstico de SO
- [x] License API reutilizada solo para auth/sesión
- [x] Rental API nueva para slots/stream
- [x] Host agent con host_token
- [x] Stream vía scrcpy/ADB, no MediaProjection
- [x] Usuario puede start/restart su slot
- [x] Privacidad: un slot = una instancia
