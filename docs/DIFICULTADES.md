# Dificultades reales del proyecto

Este documento no es marketing. Recoge los problemas que aparecieron durante el desarrollo y la
auditoría, cómo se diagnosticaron y qué se aprendió. Sirve para que la siguiente persona no repita
el camino.

## 1. Fedora 44, JDK 25/26 y Gradle 8.10

**Síntoma:** Gradle no arrancaba o fallaba con `Unsupported class file major version`.

**Causa:** Fedora 44 trae JDK 25/26 por defecto; la combinación Gradle 8.10 + AGP 8.7 está probada
con JDK 17–21.

**Solución:** `scripts/instalar-sdk-fedora.sh` descarga un JDK 21 portable en `~/Android/jdk-21` y
los scripts lo usan sin cambiar el Java del sistema. En CI se fija JDK 17.

**Lección:** la versión de Java es parte del build reproducible, no un detalle del entorno.

## 2. Los efectos de audio globales están obsoletos

**Síntoma:** en algunos teléfonos el medidor sube pero el sonido no cambia.

**Causa:** `LoudnessEnhancer(0)` depende del fabricante; el audio por offload o con DRM puede
saltarse los efectos de software.

**Solución:** modo por app con `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`, caída automática y textos
que explican el límite. No se promete que funcione en todas las apps.

**Lección:** una limitación del sistema se documenta; no se tapa con un mensaje optimista.

## 3. Carrera al arrancar y parar el servicio

**Síntoma:** al volver a 100 % y pulsar `+` enseguida, el servicio podía quedar detenido con el
refuerzo activo.

**Causa:** `stopSelf()` inmediato competía con `startForegroundService()`.

**Solución:** parada diferida de 2,5 s, re-comprobación de `percent` y arranque agrupado con
`lastServiceStart` (mínimo 800 ms entre intentos).

**Lección:** los servicios de Android son una máquina de estados; hay que diseñar las carreras, no
confiar en que «normalmente» no pasen.

## 4. Android 14 exige tipo de servicio en primer plano

**Síntoma:** `MissingForegroundServiceTypeException` en Android 14.

**Solución:** `startForeground(id, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)` en API 34+,
permiso `FOREGROUND_SERVICE_SPECIAL_USE` y subtipo declarado en el manifest.

**Lección:** cada versión de Android añade requisitos de foreground services; hay que leer las notas
de la API objetivo.

## 5. Fugas de `AudioTrack` en los avisos

**Síntoma:** tras muchos clics, el audio podía fallar o el proceso retenía recursos.

**Causa:** pistas estáticas que no se liberaban si el usuario pulsaba rápido.

**Solución:** lista `live` con tope de 8, liberación programada a la duración del tono + 150 ms y
`release()` en `onDestroy`/`onStop`.

**Lección:** los recursos de audio son finitos y no los recoge el GC.

## 6. Gestos de «mantener presionado» sin accesibilidad

**Síntoma:** `setOnTouchListener` no genera clics de accesibilidad y TalkBack no podía activar los
botones.

**Solución:** `performClick()` en `ACTION_UP`, `contentDescription` en los controles y
`accessibilityLiveRegion` en el estado. Sigue siendo un área mejorable: lo ideal sería un
`Button` con `setOnLongClickListener` o un control accesible nativo.

**Lección:** un `OnTouchListener` que devuelve `true` debe llamar a `performClick()` y exponer una
acción accesible.

## 7. Probar sin teléfono

**Síntoma:** no había acceso a un dispositivo físico en el entorno de desarrollo original.

**Solución:** pruebas JVM de `BoostMath` y de la síntesis PCM, compilación real, lint, y un checklist
manual explícito en `docs/VERIFICACION.md`. No se afirmó que estuviera probado en hardware.

**Lección:** se puede verificar mucho sin dispositivo, pero hay que ser explícito sobre lo que falta:
audio real, HyperOS, Bluetooth y offload.

## 8. Iconos adaptativos y capas monocromas

**Síntoma:** el icono solo existía como vector adaptativo; los lanzadores antiguos y los iconos
temáticos de Android 13+ no tenían una variante correcta.

**Solución:** PNG legacy por densidad para `ic_launcher` y `ic_launcher_round`, más
`ic_launcher_monochrome.xml` para iconos temáticos.

**Lección:** un icono «que se ve» en el emulador no cubre todos los lanzadores.

## 9. Repositorio y secretos

**Síntoma:** el proyecto se distribuyó sin repositorio Git y con un `keystore.properties` local.

**Solución:** `.gitignore` para keystores, `local.properties`, `dist/` y APKs; `keystore.properties`
nunca se versiona. El build de release funciona sin firma si el archivo no existe.

**Lección:** la firma es un secreto de distribución; el código público no debe contenerla.
