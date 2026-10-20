# Solución de problemas

## Al compilar

| Mensaje                                                          | Causa y arreglo |
|------------------------------------------------------------------|-----------------|
| `Unsupported class file major version 69` (o 68/70)              | Estás usando Java 25/26. Usa `./scripts/compilar.sh` (trae su JDK 21) o `export JAVA_HOME=~/Android/jdk-21`. |
| `SDK location not found`                                         | Falta `local.properties`. Corre `./scripts/instalar-sdk-fedora.sh` o crea el archivo con `sdk.dir=/home/TU_USUARIO/Android/Sdk`. |
| `Failed to install the following Android SDK packages ... licenses` | `yes \| ~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --licenses` |
| `Could not resolve com.android.tools.build:gradle` / `Could not GET ...` | Sin Internet o proxy. La primera vez descarga ~300 MB de Gradle y plugins. Reintenta; revisa el proxy/VPN. |
| `aapt2 ... Syntax error` o `Daemon startup failed`              | Poca RAM: cierra programas, o baja `org.gradle.jvmargs` en `gradle.properties`. |
| `error: ... ` en un `.kt` (primera compilación)                 | El código no pudo compilarse donde se escribió. Copia el error completo y se corrige. |
| `adb: no permissions`                                            | `sudo adb kill-server && sudo adb start-server` |

Limpiar y reintentar: `./gradlew clean` y luego `./scripts/compilar.sh`.

## Al usarla en el Redmi (HyperOS / MIUI)

**El sonido no cambia al subir el medidor**
1. Pulsa **"Probar con sonido de prueba"** y alterna 100 % ↔ 150 %. Si ahí sí cambia, el efecto
   funciona y la otra app es la que lo ignora (offload/DRM).
2. Activa **"Modo compatible (por app)"** y **reinicia la reproducción** en tu app de música
   (las apps anuncian su sesión de audio solo al empezar a sonar).
3. Comprueba que el volumen del teléfono esté **al máximo** (tarjeta "Volumen multimedia").
4. Apps como Netflix/YouTube Music con DRM u offload pueden ignorar cualquier refuerzo: es una
   limitación del sistema, no de la app.

**Se apaga solo al bloquear la pantalla o pasar un rato**
HyperOS mata apps en segundo plano con agresividad. Ajusta:
- **Ajustes → Aplicaciones → Administrar aplicaciones → Volume Boost → Inicio automático**: activar.
- **Ahorro de batería** (en la misma pantalla) → **Sin restricciones**.
- En **Recientes**, mantén pulsada la tarjeta de la app y elige el **candado**.
- Deja los permisos de **notificaciones** concedidos (la notificación mantiene el servicio visible).

**Distorsión o crujidos**
Es el límite físico: el audio ya estaba cerca del 0 dBFS y ×1,5 lo satura. Baja a 110–125 %, o baja
el volumen dentro de la propia app de música y compénsalo con el medidor.

**Con audífonos Bluetooth no cambia el volumen real**
Con *volumen absoluto* el teléfono delega el volumen al audífono; el refuerzo digital sigue
aplicándose, pero el audífono puede limitar. Desactiva "Volumen absoluto" en Opciones de desarrollador
si quieres probar.

**Aviso de "volumen alto" al usar audífonos**
Android limita por seguridad el volumen con audífonos cableados hasta que lo confirmas. La app no lo
salta; confírmalo desde los botones físicos.

**No aparece la notificación**
Android 13+ pide permiso. Si lo negaste: Ajustes → Notificaciones → Volume Boost. El refuerzo
funciona igual mientras la app esté abierta, pero en segundo plano conviene tenerla.

## Desinstalar / volver a 100 %

- Pulsa **100 %** en la app, o **"Volver a 100 %"** en la notificación.
- Desinstalar: `adb uninstall com.volumeboost.app` o desde Ajustes → Aplicaciones.
