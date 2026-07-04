#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
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

resolve_flavor() {
  local device="$1"
  local abi
  abi="$(adb -s "$device" shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r\n')"
  case "$abi" in
    arm64-v8a|armeabi-v7a) echo "arm64" ;;
    x86_64|x86) echo "x86_64" ;;
    *)
      echo "arm64"
      echo "WARN: ABI desconocido '$abi', usando flavor arm64" >&2
      ;;
  esac
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

FLAVOR="$(resolve_flavor "$DEVICE")"
ABI="$(adb -s "$DEVICE" shell getprop ro.product.cpu.abi 2>/dev/null | tr -d '\r\n')"
APK="$ROOT/app/build/outputs/apk/${FLAVOR}/debug/app-${FLAVOR}-debug.apk"
echo "==> ABI del emulador: ${ABI:-?} → flavor $FLAVOR"

echo "==> Compilando APK ($FLAVOR)"
GRADLE_TASK="assembleArm64Debug"
if [[ "$FLAVOR" == "x86_64" ]]; then
  GRADLE_TASK="assembleX86_64Debug"
fi
(cd "$ROOT" && ./gradlew "$GRADLE_TASK")

if [[ ! -f "$APK" ]]; then
  echo "ERROR: APK no encontrado: $APK"
  exit 1
fi

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
echo "ERROR: instalación fallida."
echo "  Intel Windows BlueStacks → usa MuAmaizingBot-*-x86_64-debug.apk"
echo "  Mac / ARM BlueStacks     → usa MuAmaizingBot-*-arm64-debug.apk"
echo "  Verifica ABI: adb shell getprop ro.product.cpu.abi"
exit 1
