#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.example.muamaizingbot"
REMOTE="/sdcard/Android/data/${PACKAGE}/files/debug_capture"
LOCAL="./debug_capture"

resolve_device() {
  if [[ -n "${ADB_DEVICE:-}" ]]; then
    echo "$ADB_DEVICE"
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
mkdir -p "$LOCAL"
adb -s "$DEVICE" pull "$REMOTE/." "$LOCAL/" 2>&1
echo ""
echo "Frames en: $LOCAL"
ls -la "$LOCAL"
