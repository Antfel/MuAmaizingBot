#!/usr/bin/env bash
# Pull debug frames from the emulator.
#
# Env:
#   ADB_DEVICE   — device serial (default: emulator-5574 if present)
#   LATEST=1     — pull only the newest session folder (elf_buff_* / record_*)
#   KEEP_REMOTE=1 — do not delete sessions on device after pull (default: wipe remote)
#   CLEAN_LOCAL=1 — wipe ./debug_capture before pull
set -euo pipefail

PACKAGE="com.example.muamaizingbot"
REMOTE="/sdcard/Android/data/${PACKAGE}/files/debug_capture"
LOCAL="./debug_capture"

resolve_device() {
  if [[ -n "${ADB_DEVICE:-}" ]]; then
    echo "$ADB_DEVICE"
    return
  fi
  if adb devices | awk '/^emulator-5574\tdevice$/{found=1} END{exit !found}'; then
    echo "emulator-5574"
    return
  fi
  adb devices | awk '/^emulator-[0-9]+\tdevice$/{print $1; exit}'
}

adb connect 127.0.0.1:5555 2>/dev/null || true
DEVICE="$(resolve_device)"
if [[ -z "$DEVICE" ]]; then
  DEVICE="127.0.0.1:5555"
fi
echo "==> Dispositivo: $DEVICE"

if [[ "${CLEAN_LOCAL:-0}" == "1" ]]; then
  echo "==> Limpiando $LOCAL"
  rm -rf "${LOCAL:?}"/*
fi
mkdir -p "$LOCAL"

if ! adb -s "$DEVICE" shell "ls '$REMOTE'" >/dev/null 2>&1; then
  echo "Sin capturas remotas en $REMOTE"
  exit 0
fi

if [[ "${LATEST:-0}" == "1" ]]; then
  LATEST_DIR="$(
    adb -s "$DEVICE" shell "ls -1 '$REMOTE'" 2>/dev/null \
      | tr -d '\r' \
      | grep -E '^(elf_buff_|record_)' \
      | sort \
      | tail -1
  )"
  if [[ -z "$LATEST_DIR" ]]; then
    echo "No hay sesiones elf_buff_* / record_* en el dispositivo"
    exit 0
  fi
  echo "==> Solo última sesión: $LATEST_DIR"
  mkdir -p "$LOCAL/$LATEST_DIR"
  adb -s "$DEVICE" pull "$REMOTE/$LATEST_DIR/." "$LOCAL/$LATEST_DIR/" 2>&1
  PULLED="$LATEST_DIR"
else
  echo "==> Pull completo $REMOTE → $LOCAL"
  adb -s "$DEVICE" pull "$REMOTE/." "$LOCAL/" 2>&1
  PULLED="*"
fi

if [[ "${KEEP_REMOTE:-0}" != "1" ]]; then
  echo "==> Limpiando remoto (KEEP_REMOTE=1 para conservar)"
  if [[ "$PULLED" == "*" ]]; then
    adb -s "$DEVICE" shell "rm -rf '$REMOTE'/*" 2>&1 || true
  else
    adb -s "$DEVICE" shell "rm -rf '$REMOTE/$PULLED'" 2>&1 || true
  fi
fi

echo ""
echo "Frames en: $LOCAL"
ls -la "$LOCAL" | head -40
echo ""
echo "Tips: LATEST=1  |  CLEAN_LOCAL=1  |  KEEP_REMOTE=1"
