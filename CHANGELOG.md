# Changelog

Todos los cambios relevantes se documentan aquí. El formato sigue
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el versionado
[SemVer](https://semver.org/lang/es/).

## [1.3.0] — 2026-10-23

### Añadido

- Diseño de dos columnas para pantallas en horizontal.
- Iconos vectoriales para sumar, restar, subir, bajar, reproducir, detener, volumen, sonido y modo.
- `android:fullBackupContent="false"` para completar la política de copias de seguridad.
- Paquete de release firmado (APK y AAB) y `VolumeBoost-1.3.0-docs.zip`.

### Cambiado

- Rediseño de la interfaz: cabecera de identidad, medidor con estado, atajos, control fino y
  tarjetas separadas de refuerzo manual, volumen, preferencias y prueba.
- README, guía de publicación, verificación y seguridad sin emojis.
- El medidor coloca las etiquetas 100 / 150 junto al arco y la de 125 dentro, separadas del subtítulo.
- `versionCode` 4 y `versionName` 1.3.0.

### Corregido

- Punto rojo al inicio del arco del medidor por el remate del degradado circular.
- El subtítulo del medidor se solapaba con las etiquetas de los extremos.

## [1.2.0] — 2026-10-21

Versión de distribución: mismo comportamiento que 1.1.0, ahora con paquete de
release firmado y documentación descargable.

### Añadido

- APK de release firmado y AAB para Google Play.
- Paquete de documentación `VolumeBoost-1.2.0-docs.zip`.
- `SHA256SUMS.txt` para verificar la integridad de las descargas.
- Sección de descarga y guía de publicación en el README.
- [`docs/PUBLICAR.md`](docs/PUBLICAR.md) con el proceso de release y custodia de claves.

### Cambiado

- `versionCode` 3 y `versionName` 1.2.0.
- README y verificación actualizados con los artefactos de release.

## [1.1.0] — 2026-10-20

### Añadido
- Iconos legacy por densidad y capa monocroma para iconos temáticos de Android 13+.
- `contentDescription` dinámico del medidor y región accesible para el estado.
- Pruebas de síntesis PCM (`SfxTest`) y casos límite de `BoostMath`.
- Documentación de arquitectura, decisiones (ADR), dificultades, privacidad y verificación.
- CI de GitHub Actions, plantillas de issues/PR, Dependabot y política de seguridad.
- Licencia propietaria source-available y guía de contribución.

### Cambiado
- `versionCode` 2 y `versionName` 1.1.0.
- Se detiene el sonido de prueba también en `onDestroy` (además de `onStop`).
- README reescrito con estado de verificación real y límites explícitos.

### Corregido
- Fuga potencial de `AudioTrack` si la actividad se destruye sin pasar por `onStop`.
- Accesibilidad del medidor y del texto de estado.

## [1.0.0] — 2026-10-19

### Añadido
- Primera versión: medidor 100–150 %, pasos de 5 %, atajos, mantener presionado, sonidos,
  servicio en primer plano, modo global y modo por app, sonido de prueba.
- Pruebas unitarias de `BoostMath`.
- Scripts de compilación e instalación para Fedora 44.

[1.3.0]: https://github.com/JonathanDevHurtado/VolumeBoost/compare/v1.2.0...v1.3.0
[1.2.0]: https://github.com/JonathanDevHurtado/VolumeBoost/compare/v1.1.0...v1.2.0
[1.1.0]: https://github.com/JonathanDevHurtado/VolumeBoost/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/JonathanDevHurtado/VolumeBoost/releases/tag/v1.0.0
