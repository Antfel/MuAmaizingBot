# MuAmaizing Platform

Servicio de **alquiler de emuladores** (BlueStacks) con acceso remoto.

> **Separado de** [`MuAmaizingBot`](https://github.com/Antfel/MuAmaizingBot). El bot de farm no forma parte de este repo.

## Apps

| Path | Rol |
|------|-----|
| `apps/cloud` | API rental + registro de hosts |
| `apps/host` | Agent en Mac/Windows/Linux (ADB + start/restart + stream) |
| `apps/remote` | APK cliente (Parte 3+) |
| `apps/admin` | Panel web (Parte 4+) |
| `packages/protocol` | Contratos compartidos (tipos + paths) |

## Docs

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — diseño completo
- [`docs/PART1.md`](docs/PART1.md) — alcance de esta primera entrega

## Requisitos

- Node.js 20+
- `adb` en el PATH (solo para `apps/host`)

## Quick start (Parte 1)

```bash
npm install
npm run dev:cloud   # http://127.0.0.1:8787
# otra terminal:
export MU_CLOUD_URL=http://127.0.0.1:8787
export MU_HOST_TOKEN=dev-host-token
npm run dev:host
```

## License API

La validación de licencias sigue en tu API existente (`sessions/acquire`…).  
Este cloud **no** la reemplaza: más adelante el APK remoto autenticará ahí y usará rental con `session_id`.
