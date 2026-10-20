# Contribuir a Volume Boost

Gracias por el interés. Este es un proyecto **propietario source-available**: el código se publica
para auditoría y las contribuciones se aceptan bajo los términos de la sección 4 de
[`LICENSE`](LICENSE). Si no estás de acuerdo con esos términos, no envíes cambios.

## Antes de empezar

- Lee [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md) y [`docs/DECISIONES.md`](docs/DECISIONES.md).
- Para cambios grandes, abre un issue con el problema, la propuesta y el impacto.
- No incluyas datos personales, keystores, tokens ni capturas con información privada.

## Flujo

```bash
git clone https://github.com/JonathanDevHurtado/VolumeBoost.git
cd VolumeBoost
git checkout -b feat/mi-mejora
# edita, prueba
./gradlew testDebugUnitTest lintDebug assembleDebug
git commit -m "feat: describe el cambio"
git push origin feat/mi-mejora
```

Abre un Pull Request contra `main`.

## Convención de commits

Usamos [Conventional Commits](https://www.conventionalcommits.org/):

- `feat:` nueva funcionalidad
- `fix:` corrección de un bug
- `docs:` documentación
- `test:` pruebas
- `refactor:` cambio interno sin cambiar comportamiento
- `build:` Gradle, dependencias, CI
- `chore:` mantenimiento

Ejemplo: `fix(service): evita parada prematura al volver a 100 %`.

## Estándares de código

- Kotlin oficial, 4 espacios, sin tabs.
- La lógica pura va en `BoostMath` y debe tener pruebas.
- No añadas dependencias sin justificarlo en el PR (el proyecto presume de no tenerlas).
- Respeta el hilo principal en `BoostEngine`; si necesitas concurrencia, documenta el porqué.
- No dejes `printStackTrace`; usa `Log` con etiqueta.
- Los textos visibles van en `strings.xml`; no los incrustes en el layout.
- Comenta el **por qué**, no el **qué**.

## Checklist del PR

- [ ] El cambio tiene un issue o una explicación clara.
- [ ] `./gradlew testDebugUnitTest` pasa.
- [ ] `./gradlew lintDebug` no añade errores nuevos.
- [ ] `./gradlew assembleDebug` compila.
- [ ] Actualicé `CHANGELOG.md` si el cambio es visible.
- [ ] Actualicé la documentación afectada.
- [ ] No incluí secretos ni archivos generados (`build/`, `dist/`, `.gradle/`).

## Revisión

Se revisa el diseño, la seguridad, la accesibilidad y las pruebas. Un PR puede rechazarse si
aumenta la complejidad sin una ventaja clara. La decisión final es del mantenedor.

## Código de conducta

Al participar aceptas [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md).
