---
name: install-and-verify
description: >-
  Builds and installs MUAmaizingBot on BlueStacks, then smoke-checks logcat for
  expected startup/mode tags. Use when the user says instala, install, deploy,
  verifica en emulador, 5554/5584, or after a bot fix that needs device proof.
---

# Install and verify

## Install

```bash
# default device resolution inside script; override as needed:
ADB_DEVICE=127.0.0.1:5555 ./scripts/install_bluestacks.sh
# or:
./scripts/install_bluestacks.sh emulator-5584
```

Script: `scripts/install_bluestacks.sh` — restarts adb, picks flavor (`x86_64` / `arm64` / `arm32`), `assemble*Debug`, installs APK `com.example.muamaizingbot`.

Needs network/`all` permissions for Gradle when sandbox blocks it. Compile task example: `:app:compileX86_64DebugKotlin`.

## Smoke verify

1. Confirm pid: `adb -s <dev> shell pidof com.example.muamaizingbot`
2. Ask user to Start bot **or** clear logcat and wait if already running.
3. Check recent logs for:
   - `[STARTUP] … mode=<expected>`
   - `branch=farm_bosses` or `branch=farming` matching profile
   - No unexpected `go_to_active_farm_spot` right after elf fail in bosses mode (post mode-aware fix)
4. Optional: `adb logcat -c` before repro, then dump focus file under `logs/YYYY-MM-DD/`.

## Don’t

- Don’t force-push or commit as part of install.
- Don’t assume UI port == adb serial (5554 → often `127.0.0.1:5555`).
