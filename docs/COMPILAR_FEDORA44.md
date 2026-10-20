# Compilar Volume Boost en Fedora 44

Tiempo estimado: 15–25 minutos la primera vez (descarga ~1 GB entre SDK y Gradle). Las siguientes
compilaciones tardan 1–2 minutos.

## Por qué no basta `sudo dnf install java gradle`

Fedora 44 solo ofrece OpenJDK **25** y **26**. Este proyecto fija **Gradle 8.10.2 + Android Gradle
Plugin 8.7.3 + Kotlin 2.0.21**, que necesitan JDK **17 a 21**. Con Java 25 verás errores como
`Unsupported class file major version 69`. La solución es usar un JDK 21 portable que vive en tu
carpeta personal y no interfiere con el Java del sistema.

## Opción A — Automática (recomendada)

```bash
cd ~/VolumeBoost          # la carpeta del proyecto
./scripts/instalar-sdk-fedora.sh
./scripts/compilar.sh --test
```

Qué hace el primer script: instala con `dnf` (`curl unzip tar android-tools`), descarga el JDK 21
Temurin a `~/Android/jdk-21`, descarga las *command-line tools* del Android SDK a `~/Android/Sdk`,
acepta las licencias, instala `platform-tools`, `platforms;android-35` y `build-tools;35.0.0`, y crea
`local.properties`. Se puede repetir sin problema: salta lo que ya está hecho.

Resultado: **`dist/VolumeBoost-debug.apk`**.

Cambiar la carpeta de instalación: `VB_TOOLS_DIR=/ruta ./scripts/instalar-sdk-fedora.sh` (y usar la
misma variable al compilar).

## Opción B — Manual, paso a paso

```bash
# 1. Paquetes
sudo dnf install -y curl unzip tar android-tools

# 2. JDK 21 portable
mkdir -p ~/Android/jdk-21 && cd ~/Android
curl -L -o jdk21.tar.gz "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
tar -xzf jdk21.tar.gz -C jdk-21 --strip-components=1

# 3. Android SDK (línea de comandos)
mkdir -p ~/Android/Sdk/cmdline-tools && cd /tmp
curl -LO https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-11076708_latest.zip
mv cmdline-tools ~/Android/Sdk/cmdline-tools/latest

# 4. Componentes
export JAVA_HOME=~/Android/jdk-21 PATH=~/Android/jdk-21/bin:$PATH
yes | ~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --licenses
~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

# 5. Compilar
cd ~/VolumeBoost
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :app:testDebugUnitTest :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

(En ARM64 cambia `x64` por `aarch64` en la URL del JDK.)

## Opción C — Android Studio

```bash
flatpak install flathub com.google.AndroidStudio
```

Abre la carpeta del proyecto. Android Studio trae su propio JDK 21 y descarga el SDK desde su
asistente. Menú **Build → Build APK(s)**, o usa el botón ▶ con el teléfono conectado. Si Android
Studio propone "actualizar Gradle/AGP", responde que **no** (las versiones ya son compatibles entre sí).

## Instalar en el Redmi Note 13 Pro

### Por USB con adb (lo más cómodo)

1. En el teléfono: **Ajustes → Acerca del teléfono** → toca 7 veces **Versión de HyperOS/MIUI** para activar el modo desarrollador.
2. **Ajustes → Ajustes adicionales → Opciones de desarrollador**: activa **Depuración USB** y
   **Instalar vía USB** (Xiaomi puede pedir iniciar sesión con tu cuenta Mi y/o tener SIM).
3. Conecta con un cable **de datos**, elige "Transferencia de archivos" y acepta el aviso
   "¿Permitir depuración USB?".
4. En Fedora: `./scripts/instalar-en-telefono.sh`

Si `adb devices` dice `unauthorized`: desconecta, vuelve a conectar y acepta el aviso. Si dice
`no permissions`: `sudo adb kill-server && sudo adb start-server`.

### Sin adb

Copia `dist/VolumeBoost-debug.apk` al teléfono (cable, KDE Connect, o en Fedora
`cd dist && python3 -m http.server 8000` y abre `http://IP-DE-TU-PC:8000` en el navegador del
teléfono). Ábrelo desde el administrador de archivos y concede "Instalar apps desconocidas" a esa
app cuando lo pida. Si Play Protect avisa, es normal en APK propios: elige "Instalar de todos modos".

## APK release firmado (opcional)

El APK de depuración ya es instalable y suficiente para uso personal. Para uno `release` (más
pequeño, optimizado con R8):

```bash
~/Android/jdk-21/bin/keytool -genkeypair -v -keystore volumeboost.jks \
  -alias volumeboost -keyalg RSA -keysize 2048 -validity 10000

cat > keystore.properties <<'EOF'
storeFile=volumeboost.jks
storePassword=TU_CLAVE
keyAlias=volumeboost
keyPassword=TU_CLAVE
EOF

./scripts/compilar.sh --release     # -> dist/VolumeBoost-release.apk
```

`keystore.properties` y `*.jks` ya están en `.gitignore`. **Guarda el `.jks`**: sin él no podrás
actualizar la app instalada con una versión nueva.

## Después de instalar

1. Abre la app y acepta el aviso de seguridad auditiva y el permiso de notificaciones.
2. Pon música, sube el volumen del teléfono al máximo (la app tiene un botón para eso) y sube el
   medidor. En Xiaomi, mira [`SOLUCION_PROBLEMAS.md`](SOLUCION_PROBLEMAS.md) para evitar que el
   sistema mate la app en segundo plano.
