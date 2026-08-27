# Setup en tu Mac

Este repo es **independiente** de `MuAmaizingBot`.

## 1. Crear el repo en GitHub

En el Mac (Terminal o GitHub web), crea un repo vacío `muamaizing-platform` bajo tu usuario `Antfel` (privado o público).

## 2. Descargar este scaffold

Desde la página del Cloud Agent → **Artifacts** → descarga `muamaizing-platform-part1.zip`.

```bash
cd ~/Projects
unzip ~/Downloads/muamaizing-platform-part1.zip
cd muamaizing-platform
git init
git add .
git commit -m "Part 1: platform scaffold, cloud host registry, host agent"
git branch -M main
git remote add origin git@github.com:Antfel/muamaizing-platform.git
git push -u origin main
```

## 3. Abrir en Cursor Desktop

File → Open Folder → `~/Projects/muamaizing-platform`

No abras el repo del bot para este trabajo.

## 4. Correr Parte 1

```bash
npm install
npm run dev:cloud
```

Otra terminal:

```bash
export MU_CLOUD_URL=http://127.0.0.1:8787
export MU_HOST_TOKEN=dev-host-token
export MU_HOST_NAME=mac-mini-1
npm run dev:host
```

Con BlueStacks + ADB activos, el host reportará seriales. Comprueba:

```bash
curl -s http://127.0.0.1:8787/api/v1/hosts | jq
```

## 5. Siguiente conversación en Cursor Mac

Prompt sugerido:

> Continuamos Parte 2 según docs/PART1.md y docs/ARCHITECTURE.md:
> slots, cola de comandos start/restart, y persistencia simple.
> No toques MuAmaizingBot.
