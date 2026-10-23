# Verificación

## Entorno usado

| Herramienta | Versión |
|---|---|
| JDK | Eclipse Temurin 17.0.13 |
| Gradle | 8.10.2 (wrapper) |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 |
| Android SDK Platform | 35 |
| Build Tools | 35.0.0 |

## Comandos ejecutados

```bash
export JAVA_HOME=/ruta/al/jdk-17
./gradlew --no-daemon clean testDebugUnitTest lintDebug
./gradlew --no-daemon assembleRelease bundleRelease
```

Resultado de la última ejecución (2026-10-23):

- `assembleDebug`: **BUILD SUCCESSFUL**, APK de depuración generado
- `testDebugUnitTest`: **BUILD SUCCESSFUL**, 17 pruebas
- `lintDebug`: **BUILD SUCCESSFUL**, 0 errores y 4 avisos
- `assembleRelease`: **BUILD SUCCESSFUL**, APK firmado

## Verificación del rediseño (2026-10-23)

Dispositivo: **Nokia 5.4, Android 12 (API 31)**, instalación del APK debug.

| Comprobación | Resultado |
|---|---|
| Arranque de `MainActivity` sin fallos | Correcto |
| Atajos 100 / 110 / 125 / 150 % | El medidor, el estado y la selección cambian |
| Botones `−` / `+` | Paso de 5 % correcto (100 → 105 %) |
| Servicio en primer plano al superar 100 % | Activo (`isForeground=true`) |
| Efecto de audio | `LoudnessEnhancer` en la sesión 0 confirmado con `dumpsys media.audio_flinger` |
| Volver a 100 % | El efecto se libera y el servicio se detiene tras ~2,5 s |
| Sonido de prueba | El botón alterna entre `Probar sonido` y `Detener prueba` |
| Diseño vertical y horizontal | Ambas variantes se inflan y son desplazables |
| Lint | 0 errores, 4 avisos (targetSdk, plurales y respaldo) |

## Release 1.2.0

| Artefacto | Tamaño | SHA-256 |
|---|---:|---|
| `VolumeBoost-1.2.0.apk` | 104 KB | `3079061d4f22a87146d1d6fffc0d919bf1c3e591345700afd0b538e87e9c9306` |
| `VolumeBoost-1.2.0.aab` | 121 KB | `e6f9399bd12e03514488e4bf46dcf0bdf8514fc4ea645032b0b5eaafa417403f` |

Firma comprobada con `apksigner verify --print-certs`:

```
Signer #1 certificate DN: CN=Jonathan Hurtado, OU=VolumeBoost,
  O=JonathanDevHurtado, L=Bogota, C=CO
```

El AAB también pasa `jarsigner -verify`. La clave de firma no está en el
repositorio; se guarda fuera y `keystore.properties` está ignorado por Git.

## Qué cubren las pruebas

| Área | Pruebas |
|---|---|
| Ganancia en dB | 100 %, 125 %, 150 %, clamp y monotonía |
| Pasos de 5 % | subida, bajada, límites e idempotencia |
| Fracción del medidor | 0..1 y fuera de rango |
| Frecuencia del aviso | 600 Hz..1300 Hz |
| Síntesis PCM | longitud, silencio, no clipping, ataque/cierre |
| Sfx | que no se generen muestras fuera de rango |

## Qué **no** está verificado

- Audio real en un Redmi Note 13 Pro (HyperOS).
- Comportamiento con audífonos Bluetooth y volumen absoluto.
- Apps con DRM/offload (Netflix, YouTube Music, etc.).
- Tablets, tamaños de fuente grandes y modo claro forzado.
- Permiso de notificaciones denegado en Android 13+.

## Checklist manual en dispositivo

Marca cada punto antes de publicar una release:

- [ ] Instalar el APK debug y abrir la app.
- [ ] Aceptar el aviso de oído y el permiso de notificaciones.
- [ ] Poner el volumen multimedia al máximo.
- [ ] Reproducir el sonido de prueba a 100 % y a 150 %; se oye la diferencia.
- [ ] Probar con una app de música real; si no cambia, activar modo por app y reiniciar la reproducción.
- [ ] Mantener `Subir` hasta 150 %; la aguja llega suave y suena la campanita.
- [ ] Mantener `Bajar` hasta 100 %; el servicio se detiene tras ~2,5 s.
- [ ] Bloquear la pantalla con refuerzo activo; el audio sigue y la notificación está visible.
- [ ] Pulsar «Volver a 100 %» en la notificación; el refuerzo se apaga.
- [ ] Matar la app desde recientes con refuerzo activo; el refuerzo se apaga.
- [ ] Girar la pantalla; el nivel se conserva.
- [ ] Revisar que no hay distorsión a 150 % con el contenido habitual.
