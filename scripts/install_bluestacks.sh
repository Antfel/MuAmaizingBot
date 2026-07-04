#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.example.muamaizingbot"

resolve_device() {
  if [[ -n "${ADB_DEVICE:-}" ]]; then
    echo "$ADB_DEVICE"
    return
  fi
  local emu
  emu="$(adb devices | awk '/^emulator-[0-9]+\tdevice$/{print $1; exit}')"
  if [[ -n "$emu" ]]; then
    echo "$emu"
    return
  fi
  if adb devices | awk '/127\.0\.0\.1:5555\tdevice$/{found=1} END{exit !found}'; then
    echo "127.0.0.1:5555"
    return
  fi
  echo ""
}

echo "==> Reiniciando ADB"
adb kill-server >/dev/null 2>&1 || true
sleep 1
adb start-server
adb connect 127.0.0.1:5555 >/dev/null 2>&1 || true
sleep 2

DEVICE="$(resolve_device)"
if [[ -z "$DEVICE" ]]; then
  echo "ERROR: no hay emulador conectado. Activa ADB en BlueStacks."
  adb devices -l
  exit 1
fi
echo "==> Dispositivo: $DEVICE"

echo "==> Compilando APK (arm64-v8a)"
(cd "$ROOT" && ./gradlew assembleDebug)

echo "==> Deteniendo app"
adb -s "$DEVICE" shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true

echo "==> Instalando en $DEVICE"
if adb -s "$DEVICE" install -r --no-incremental "$APK"; then
  echo "OK: instalado"
  exit 0
fi

echo "==> Reintento: push + pm install"
adb -s "$DEVICE" push "$APK" /data/local/tmp/mubot.apk
if adb -s "$DEVICE" shell pm install -r -t /data/local/tmp/mubot.apk; then
  echo "OK: instalado (push)"
  exit 0
fi

echo ""
echo "ERROR: Broken pipe en PackageManager de BlueStacks."
echo "  1. Cierra BlueStacks por completo y ábrelo de nuevo"
echo "  2. Settings > Advanced > Android debug bridge = ON"
echo "  3. Vuelve a ejecutar: ./scripts/install_bluestacks.sh"
exit 1
