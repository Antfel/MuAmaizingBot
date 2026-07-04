#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.example.muamaizingbot"

resolve_device() {
  if [[ -n "${ADB_DEVICE:-}" ]]; then
    echo "$ADB_DEVICE"
    return
  fi
  adb devices | awk '/^emulator-[0-9]+\tdevice$/{print $1; exit}'
}

DEVICE="$(resolve_device)"
if [[ -z "$DEVICE" ]]; then
  adb connect 127.0.0.1:5555 2>/dev/null || true
  sleep 1
  DEVICE="$(resolve_device)"
fi
if [[ -z "$DEVICE" ]]; then
  DEVICE="127.0.0.1:5555"
fi

echo "==> Dispositivo: $DEVICE"

echo "==> Desinstalación segura MU Bot (BlueStacks)"
echo "    Ejecuta esto JUSTO después de abrir BlueStacks (antes de abrir el juego)."
echo ""

adb kill-server >/dev/null 2>&1 || true
sleep 1
adb start-server
adb connect "$DEVICE" >/dev/null 2>&1 || true
sleep 2
adb disconnect emulator-5554 >/dev/null 2>&1 || true

if ! adb -s "$DEVICE" get-state >/dev/null 2>&1; then
  echo "ERROR: $DEVICE no responde. Abre BlueStacks y activa ADB."
  exit 1
fi

echo "==> 1/4 Forzar cierre del bot"
adb -s "$DEVICE" shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true

echo "==> 2/4 Revocar permisos especiales (si la app responde)"
adb -s "$DEVICE" shell appops set "$PACKAGE" SYSTEM_ALERT_WINDOW deny >/dev/null 2>&1 || true
adb -s "$DEVICE" shell appops set "$PACKAGE" PROJECT_MEDIA deny >/dev/null 2>&1 || true

echo "==> 3/4 Desinstalar vía ADB"
if adb -s "$DEVICE" uninstall "$PACKAGE"; then
  echo ""
  echo "OK: app desinstalada."
  exit 0
fi

echo ""
echo "FALLÓ (Broken pipe / PackageManager roto en BlueStacks)."
echo ""
echo "Opciones (en orden):"
echo "  A) Reinicia BlueStacks por completo (Quit desde menú, no solo X)."
echo "     Al arrancar, NO abras el juego. Ejecuta de nuevo:"
echo "       ./scripts/uninstall_bluestacks.sh"
echo ""
echo "  B) En BlueStacks: Settings (engranaje) → Advanced → Android Debug Bridge ON"
echo "     Luego repite el script."
echo ""
echo "  C) Reset de la instancia Android (borra TODO en esa instancia):"
echo "     BlueStacks → Settings → User data → Factory reset"
echo "     (Pierdes apps/datos de esa instancia, pero limpia el PackageManager roto.)"
echo ""
echo "  D) Instalar encima sin desinstalar (después de reiniciar BlueStacks):"
echo "       ./scripts/install_bluestacks.sh"
exit 1
