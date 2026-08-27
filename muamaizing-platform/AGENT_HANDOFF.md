# Agent handoff — MuAmaizing Emulator Rental (Parte 1 hecha)

**Lee esto primero.** Este directorio es un producto **separado** de `MuAmaizingBot` (farm bot).

## Contexto de producto

- Servicio de **alquiler de emuladores** BlueStacks.
- Usuario controla **solo su** instancia desde celular (privacidad).
- Host agent en Mac/Windows/Linux (agnóstico de SO).
- Stream vía **ADB/scrcpy**, NO AirMirror/MediaProjection (conflicto con captura).
- License API existente (`sessions/acquire|heartbeat|release`) se reutiliza solo como **auth**; rental es API aparte.
- Dominio: `muamaizingbot.com`.

## Estado actual (Parte 1)

Monorepo listo y smoke-testeado:

| Path | Rol |
|------|-----|
| `packages/protocol` | Tipos + paths API |
| `apps/cloud` | `POST /api/v1/hosts/heartbeat`, `GET /api/v1/hosts` |
| `apps/host` | Heartbeat loop + `adb devices` |
| `apps/remote` | Placeholder APK |
| `apps/admin` | Placeholder panel |

Docs: `docs/ARCHITECTURE.md`, `docs/PART1.md`, `docs/SETUP_MAC.md`.

```bash
npm install
npm run dev:cloud          # :8787
MU_CLOUD_URL=http://127.0.0.1:8787 MU_HOST_TOKEN=dev-host-token npm run dev:host
curl -s http://127.0.0.1:8787/api/v1/hosts | jq
```

## Siguiente: Parte 2

1. Modelo Slot + Lease (aunque sea in-memory/SQLite).
2. Cola de comandos en heartbeat (`start_instance`, `restart_instance`).
3. Host ejecuta comandos (BlueStacks Multi-Instance + ADB).
4. Endpoints rental protegidos por `session_id` de license (stub OK al inicio).

**No implementes** todavía APK stream ni integres el farm bot.

## Reglas

- No merges este código dentro de la app Android del bot.
- Si este folder vive temporalmente bajo el repo MuAmaizingBot, el objetivo es moverlo a repo propio `Antfel/muamaizing-platform`.
- Mantén protocol versionado en `packages/protocol`.

## Origen

Diseñado en Cloud Agent: https://cursor.com/agents/bc-01a0401b-8201-7700-9f07-f7550308bffa
