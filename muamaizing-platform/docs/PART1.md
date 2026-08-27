# Parte 1 — Scaffold + Host registry

## Objetivo

Dejar corriendo un **cloud stub** y un **host agent** que:

1. Se registra / hace heartbeat
2. Reporta dispositivos ADB detectados
3. Expone inventario en la API

Sin stream, sin APK, sin start/restart real de BlueStacks todavía.

## Entregables

- [x] Monorepo `muamaizing-platform`
- [x] `packages/protocol` — tipos y paths
- [x] `apps/cloud` — `POST /api/v1/hosts/heartbeat`, `GET /api/v1/hosts`
- [x] `apps/host` — poll ADB + heartbeat loop
- [ ] Parte 2 — comandos `start` / `restart` + slots
- [ ] Parte 3 — APK remoto + rooms/stream
- [ ] Parte 4 — admin panel + leases

## Cómo probar

```bash
npm install
npm run dev:cloud
MU_HOST_TOKEN=dev-host-token MU_CLOUD_URL=http://127.0.0.1:8787 npm run dev:host
curl -s http://127.0.0.1:8787/api/v1/hosts | jq
```
