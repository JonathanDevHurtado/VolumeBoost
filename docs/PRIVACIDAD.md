# Privacidad

**Volume Boost no recopila datos.**

- No tiene permiso `INTERNET`.
- No incluye SDKs de analítica, publicidad, crash reporting ni redes sociales.
- No crea cuentas ni pide identificadores.
- No lee contactos, ubicación, almacenamiento ni micrófono.
- `allowBackup="false"`: Android no sube sus preferencias a la nube.

## Datos que se guardan en el dispositivo

Solo `SharedPreferences` locales con:

- si los sonidos de aviso están activados;
- si el modo compatible está activado;
- si ya se mostró el aviso de seguridad;
- si ya se pidió el permiso de notificaciones.

Se borran al desinstalar la app. No salen del teléfono.

## Permisos

| Permiso | Uso | Cuándo |
|---|---|---|
| `MODIFY_AUDIO_SETTINGS` | Crear `LoudnessEnhancer` | Al subir de 100 % |
| `FOREGROUND_SERVICE` | Mantener el proceso | Al subir de 100 % |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Tipo exigido en Android 14+ | Igual |
| `POST_NOTIFICATIONS` | Notificación del servicio | Android 13+, se pide una vez |

No se solicita ningún permiso peligroso (cámara, ubicación, contactos, almacenamiento).

## Terceros

El APK no incluye librerías de terceros más allá del runtime de Kotlin/AndroidX que añade el propio
build. No hay llamadas de red.

Contacto de privacidad: **JonathanHurtadoDev@proton.me**.
