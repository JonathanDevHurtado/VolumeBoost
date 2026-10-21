# 📦 Publicar una release — Volume Boost

Guía del proceso usado para empaquetar y publicar las versiones oficiales.

---

## 🔢 Versionado

Se usa [SemVer](https://semver.org/lang/es/):

| Cambio | Ejemplo |
|---|---|
| Corrección compatible | `1.2.1` |
| Nueva función compatible | `1.3.0` |
| Cambio incompatible | `2.0.0` |

Cada release incrementa `versionCode` en `app/build.gradle.kts`. El tag de Git
lleva la `v`: `v1.2.0`.

---

## 🔐 Firma

La clave de release **nunca se sube al repositorio**. Debe guardarse fuera del
proyecto, con copia de seguridad y contraseña en un gestor de secretos.

`keystore.properties` (ignorado por Git) apunta a la clave:

```properties
storeFile=/ruta/segura/VolumeBoost-release.jks
storePassword=...
keyAlias=volumeboost
keyPassword=...
```

Sin ese archivo, `assembleRelease` genera un APK **sin firmar**. No se publica.

---

## 🛠️ Compilar los artefactos

```bash
export JAVA_HOME=/ruta/al/jdk-17

./gradlew --no-daemon clean testDebugUnitTest lintDebug
./gradlew --no-daemon assembleRelease bundleRelease
```

Salidas:

| Artefacto | Ruta |
|---|---|
| APK firmado | `app/build/outputs/apk/release/app-release.apk` |
| AAB firmado | `app/build/outputs/bundle/release/app-release.aab` |
| Mapping R8 | `app/build/outputs/mapping/release/mapping.txt` |

Verificar la firma:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

---

## 🧾 Nombres y checksums

Los assets se renombran con el patrón:

```text
VolumeBoost-1.2.0.apk
VolumeBoost-1.2.0.aab
VolumeBoost-1.2.0-docs.zip
VolumeBoost-1.2.0-mapping.txt   # solo si hay R8
SHA256SUMS.txt
```

Generar el archivo de integridad:

```bash
sha256sum VolumeBoost-1.2.0.apk \
          VolumeBoost-1.2.0.aab \
          VolumeBoost-1.2.0-docs.zip \
          VolumeBoost-1.2.0-mapping.txt > SHA256SUMS.txt
```

---

## 🚀 Publicar en GitHub

1. Crear el tag y subirlo:
   ```bash
   git tag -a v1.2.0 -m "Volume Boost 1.2.0"
   git push origin main --tags
   ```
2. Crear la release en GitHub con el título `🚀 Volume Boost 1.2.0`.
3. Adjuntar APK, AAB, documentación, mapping y `SHA256SUMS.txt`.
4. Marcar la release como **Latest**.
5. Comprobar que el enlace `releases/latest` del README funciona.

---

## ✅ Checklist

- [ ] `versionCode` y `versionName` actualizados.
- [ ] `CHANGELOG.md` actualizado.
- [ ] Tests y lint sin errores nuevos.
- [ ] APK y AAB firmados y verificados.
- [ ] Checksums generados y comprobados.
- [ ] Documentación incluida en el ZIP.
- [ ] Release publicada con notas y emojis.
- [ ] Clave de firma respaldada fuera del repositorio.
