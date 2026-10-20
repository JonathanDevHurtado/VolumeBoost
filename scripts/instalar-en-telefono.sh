#!/usr/bin/env bash
# =============================================================================
# Instala el APK en tu Redmi Note 13 Pro por USB (adb) y abre la app.
#   ./scripts/instalar-en-telefono.sh [ruta/al.apk]
# Requisitos en el teléfono: Opciones de desarrollador + Depuración USB (ver docs/COMPILAR_FEDORA44.md)
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="${1:-$ROOT/dist/VolumeBoost-debug.apk}"
ADB="$(command -v adb || true)"
[ -z "$ADB" ] && [ -x "${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb" ] && \
  ADB="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb"

if [ -z "$ADB" ]; then
  echo "ERROR: no encuentro adb. Instálalo con:  sudo dnf install android-tools" >&2
  exit 1
fi
if [ ! -f "$APK" ]; then
  echo "ERROR: no existe $APK. Compila primero con ./scripts/compilar.sh" >&2
  exit 1
fi

echo "Dispositivos conectados:"
"$ADB" devices
if ! "$ADB" devices | awk 'NR>1 && $2=="device"{found=1} END{exit !found}'; then
  echo
  echo "No hay ningún teléfono autorizado. Revisa:" >&2
  echo "  - cable USB de datos (no solo de carga) y modo 'Transferencia de archivos'" >&2
  echo "  - Depuración USB activada y aceptado el aviso 'Permitir depuración' en el teléfono" >&2
  echo "  - si dice 'unauthorized': desconecta, acepta el aviso en la pantalla y repite" >&2
  echo "  - si dice 'no permissions': sudo adb kill-server && sudo adb start-server" >&2
  exit 1
fi

"$ADB" install -r "$APK"
"$ADB" shell am start -n com.volumeboost.app/.MainActivity >/dev/null
echo "Instalada y abierta en el teléfono."
