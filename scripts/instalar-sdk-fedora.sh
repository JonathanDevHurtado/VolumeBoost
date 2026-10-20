#!/usr/bin/env bash
# =============================================================================
# Prepara Fedora 44 para compilar Volume Boost (se ejecuta UNA sola vez).
#
#   1. Instala con dnf: curl, unzip, tar y android-tools (adb)
#   2. Descarga un JDK 21 portable (Temurin) en ~/Android/jdk-21
#        -> Fedora 44 solo trae Java 25/26 y Gradle 8.10 NO funciona con ellos.
#           Este JDK vive en tu carpeta y no toca el Java del sistema.
#   3. Descarga las herramientas de línea de comandos del Android SDK
#   4. Acepta licencias e instala platform-tools, platforms;android-35, build-tools;35.0.0
#   5. Crea local.properties con la ruta del SDK
#
# Variables opcionales:
#   VB_TOOLS_DIR      carpeta de instalación (por defecto ~/Android)
#   CMDLINE_VERSION   número de versión de las command-line tools (por defecto 11076708)
# =============================================================================
set -euo pipefail

TOOLS_DIR="${VB_TOOLS_DIR:-$HOME/Android}"
SDK_DIR="$TOOLS_DIR/Sdk"
JDK_DIR="$TOOLS_DIR/jdk-21"
CMDLINE_VERSION="${CMDLINE_VERSION:-11076708}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

say() { printf '\n\033[1;33m==> %s\033[0m\n' "$*"; }

mkdir -p "$TOOLS_DIR" "$SDK_DIR"

say "1/5  Paquetes del sistema (pide tu contraseña de sudo)"
sudo dnf install -y curl unzip tar android-tools

say "2/5  JDK 21 portable"
if [ -x "$JDK_DIR/bin/java" ]; then
  echo "Ya existe: $JDK_DIR"
else
  case "$(uname -m)" in
    x86_64)  JDK_ARCH="x64" ;;
    aarch64) JDK_ARCH="aarch64" ;;
    *) echo "Arquitectura no soportada: $(uname -m)"; exit 1 ;;
  esac
  TMP_JDK="$(mktemp -d)"
  curl -fL --retry 3 -o "$TMP_JDK/jdk.tar.gz" \
    "https://api.adoptium.net/v3/binary/latest/21/ga/linux/${JDK_ARCH}/jdk/hotspot/normal/eclipse"
  mkdir -p "$JDK_DIR"
  tar -xzf "$TMP_JDK/jdk.tar.gz" -C "$JDK_DIR" --strip-components=1
  rm -rf "$TMP_JDK"
fi
export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
java -version

say "3/5  Herramientas de línea de comandos del Android SDK"
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
if [ -x "$SDKMANAGER" ]; then
  echo "Ya instaladas."
else
  TMP_SDK="$(mktemp -d)"
  curl -fL --retry 3 -o "$TMP_SDK/cmdline.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_VERSION}_latest.zip"
  unzip -q "$TMP_SDK/cmdline.zip" -d "$TMP_SDK"
  mkdir -p "$SDK_DIR/cmdline-tools"
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mv "$TMP_SDK/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  rm -rf "$TMP_SDK"
fi

say "4/5  Licencias y componentes del SDK (descarga ~1 GB)"
# 'yes' recibe SIGPIPE cuando sdkmanager termina: es normal, por eso el '|| true'.
yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses >/dev/null || true
"$SDKMANAGER" --sdk_root="$SDK_DIR" --install \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0"

if [ ! -d "$SDK_DIR/platforms/android-35" ] || [ ! -d "$SDK_DIR/build-tools/35.0.0" ]; then
  echo "ERROR: faltan componentes del SDK. Revisa los mensajes de arriba." >&2
  exit 1
fi

say "5/5  local.properties"
printf 'sdk.dir=%s\n' "$SDK_DIR" > "$ROOT/local.properties"
echo "Escrito $ROOT/local.properties"

say "Listo. Ahora compila con:  ./scripts/compilar.sh"
