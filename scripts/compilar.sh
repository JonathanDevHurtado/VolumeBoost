#!/usr/bin/env bash
# =============================================================================
# Compila Volume Boost y deja el APK en  dist/
#
#   ./scripts/compilar.sh             APK de depuración  -> dist/VolumeBoost-debug.apk
#   ./scripts/compilar.sh --test      además corre las pruebas unitarias
#   ./scripts/compilar.sh --release   APK release (firmado si existe keystore.properties)
#
# Usa el JDK 21 e instalación del SDK que dejó scripts/instalar-sdk-fedora.sh.
# =============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS_DIR="${VB_TOOLS_DIR:-$HOME/Android}"

RUN_TESTS=0
BUILD_RELEASE=0
for arg in "$@"; do
  case "$arg" in
    --test) RUN_TESTS=1 ;;
    --release) BUILD_RELEASE=1 ;;
    -h|--help) sed -n '2,10p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "Opción desconocida: $arg"; exit 2 ;;
  esac
done

java_major() {
  "$1/bin/java" -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p'
}

# --- JDK: el portable de la instalación, o el JAVA_HOME actual si es 17..21 ---
if [ -x "$TOOLS_DIR/jdk-21/bin/java" ]; then
  export JAVA_HOME="$TOOLS_DIR/jdk-21"
elif [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ] \
     && [ "$(java_major "$JAVA_HOME")" -ge 17 ] && [ "$(java_major "$JAVA_HOME")" -le 21 ]; then
  : # usamos el JAVA_HOME existente
else
  echo "ERROR: no encuentro un JDK 17-21." >&2
  echo "Ejecuta primero  ./scripts/instalar-sdk-fedora.sh  (Fedora 44 solo trae Java 25/26)." >&2
  exit 1
fi
export PATH="$JAVA_HOME/bin:$PATH"

# --- Android SDK ---
export ANDROID_HOME="${ANDROID_HOME:-$TOOLS_DIR/Sdk}"
if [ ! -d "$ANDROID_HOME/platforms/android-35" ]; then
  echo "ERROR: no encuentro el Android SDK en $ANDROID_HOME" >&2
  echo "Ejecuta primero  ./scripts/instalar-sdk-fedora.sh" >&2
  exit 1
fi
if [ ! -f "$ROOT/local.properties" ]; then
  printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$ROOT/local.properties"
fi

echo "JDK : $JAVA_HOME ($(java_major "$JAVA_HOME"))"
echo "SDK : $ANDROID_HOME"

cd "$ROOT"
TASKS=()
if [ "$RUN_TESTS" -eq 1 ]; then TASKS+=(":app:testDebugUnitTest"); fi
if [ "$BUILD_RELEASE" -eq 1 ]; then TASKS+=(":app:assembleRelease"); else TASKS+=(":app:assembleDebug"); fi

./gradlew --console=plain "${TASKS[@]}"

mkdir -p "$ROOT/dist"
if [ "$BUILD_RELEASE" -eq 1 ]; then
  SRC="$(ls "$ROOT"/app/build/outputs/apk/release/*.apk | head -n1)"
  case "$SRC" in
    *unsigned*) DEST="$ROOT/dist/VolumeBoost-release-SIN-FIRMAR.apk" ;;
    *)          DEST="$ROOT/dist/VolumeBoost-release.apk" ;;
  esac
else
  SRC="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
  DEST="$ROOT/dist/VolumeBoost-debug.apk"
fi
cp -f "$SRC" "$DEST"

echo
echo "APK listo: $DEST ($(du -h "$DEST" | cut -f1))"
echo "Instálalo con:  ./scripts/instalar-en-telefono.sh"
