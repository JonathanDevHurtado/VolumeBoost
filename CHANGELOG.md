# Changelog

Todos los cambios relevantes se documentan aquí. El formato sigue
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el versionado
[SemVer](https://semver.org/lang/es/).

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

[1.1.0]: https://github.com/JonathanDevHurtado/VolumeBoost/compare/v1.0.0...v1.1.0
[1.0.0]: https://github.com/JonathanDevHurtado/VolumeBoost/releases/tag/v1.0.0
